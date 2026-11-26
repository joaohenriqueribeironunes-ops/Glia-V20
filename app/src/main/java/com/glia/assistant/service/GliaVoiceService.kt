package com.glia.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.glia.assistant.MainActivity
import com.glia.assistant.R
import com.glia.assistant.commands.CognitiveNotesStore
import com.glia.assistant.commands.CommandEngine
import com.glia.assistant.commands.CommandParser
import com.glia.assistant.commands.VoiceCommand
import com.glia.assistant.core.GliaPrefs
import com.glia.assistant.stt.SttRetryPolicy
import com.glia.assistant.stt.SystemStt
import com.glia.assistant.tts.GliaTts
import com.glia.assistant.ui.GliaMode
import com.glia.assistant.ui.GliaState
import com.glia.assistant.ui.NotesActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class GliaVoiceService : Service() {
    companion object {
        private const val TAG = "GliaVoice"
        const val CHANNEL_ID = "glia_voice"
        private const val NOTIF_ID = 1001

        const val ACTION_START_LISTENING = "com.glia.assistant.START_LISTENING"
        const val ACTION_STOP = "com.glia.assistant.STOP"
        const val ACTION_TEST_TTS = "com.glia.assistant.TEST_TTS"

        @Volatile
        var instance: GliaVoiceService? = null
            private set
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Erro no serviço", error)
        GliaState.setLastError(error.message ?: "Erro interno")
    }
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + serviceJob + exceptionHandler)

    private lateinit var stt: SystemStt
    private lateinit var tts: GliaTts
    private lateinit var commandEngine: CommandEngine
    private lateinit var notes: CognitiveNotesStore

    private var wakeJob: Job? = null
    private var pipelineJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var shuttingDown = false
    @Volatile private var wakeGeneration = 0
    @Volatile private var pipelineGeneration = 0
    @Volatile private var listenGeneration = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        stt = SystemStt(this)
        tts = GliaTts(this)
        commandEngine = CommandEngine(this)
        notes = CognitiveNotesStore(this)
        tts.onReady = { GliaState.setTtsStatus(tts.status()) }
        tts.onTtsError = { GliaState.setLastError("Voz: $it") }
        GliaState.setTtsStatus(tts.status())
        GliaState.setSttStatus(
            if (stt.isAvailable()) "reconhecimento do sistema" else "indisponível neste aparelho"
        )
        if (GliaPrefs.isTileActive(this)) {
            startWakeWordLoop()
        } else {
            GliaState.setAssistantStatus("desligada")
            GliaState.setMode(GliaMode.IDLE)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground("Iniciando Glia...")
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_TEST_TTS -> {
                cancelPipeline(stopTts = true)
                scope.launch { testTts() }
                return START_STICKY
            }
            ACTION_START_LISTENING -> {
                startListening(bypassWake = true)
                return START_STICKY
            }
            else -> {
                startWakeWordLoop()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class ListenOutcome(
        val text: String?,
        val errorCode: Int? = null,
        val errorMessage: String? = null
    )

    /**
     * Escuta única via SpeechRecognizer. Se o fabricante devolver ERROR_CLIENT
     * (erro interno do microfone), recriamos o reconhecedor e repetimos a escuta
     * sem EXTRA_PREFER_OFFLINE antes de mostrar uma falha ao usuário.
     */
    private suspend fun listenOnce(timeoutMs: Long, mode: GliaMode): String? {
        for (attempt in 0 until SttRetryPolicy.MAX_ATTEMPTS) {
            if (shuttingDown || !stt.isAvailable()) return null
            val outcome = listenOnceAttempt(
                timeoutMs,
                mode,
                SttRetryPolicy.preferOffline(attempt)
            )
            if (outcome.text != null) return outcome.text
            val code = outcome.errorCode ?: continue
            if (SttRetryPolicy.shouldRetryWithoutOffline(code) &&
                attempt < SttRetryPolicy.MAX_ATTEMPTS - 1
            ) {
                withContext(Dispatchers.Main) { stt.reset() }
                continue
            }
            if (!SttRetryPolicy.isSilentError(code)) {
                GliaState.setLastError("Microfone: ${outcome.errorMessage ?: "erro $code"}")
            }
            return null
        }
        return null
    }

    private suspend fun listenOnceAttempt(
        timeoutMs: Long,
        mode: GliaMode,
        preferOffline: Boolean
    ): ListenOutcome = withContext(Dispatchers.Main) {
        if (shuttingDown || !stt.isAvailable()) {
            return@withContext ListenOutcome(
                null,
                -1,
                "Reconhecimento de fala indisponível neste aparelho"
            )
        }
        val token = ++listenGeneration
        val result = CompletableDeferred<ListenOutcome>()
        val started = stt.listen(object : SystemStt.Listener {
            override fun onReady() {
                if (token == listenGeneration) GliaState.setAssistantStatus("ouvindo")
            }

            override fun onPartial(text: String) {
                if (token != listenGeneration) return
                if (mode == GliaMode.LISTENING) GliaState.setPartial(text)
                if (mode == GliaMode.IDLE && isWakeWord(text)) {
                    triggerWake(text)
                }
            }

            override fun onRms(rms: Float) {
                if (token == listenGeneration) {
                    GliaState.setAmplitude(((rms + 2f) / 12f).coerceIn(0f, 1f))
                }
            }

            override fun onResult(text: String) {
                if (token != listenGeneration) return
                listenGeneration++
                result.complete(ListenOutcome(text.ifBlank { null }))
            }

            override fun onError(code: Int, message: String) {
                if (token != listenGeneration) return
                listenGeneration++
                result.complete(ListenOutcome(null, code, message))
            }
        }, preferOffline)
        if (!started) {
            listenGeneration++
            return@withContext ListenOutcome(
                null,
                -1,
                "Reconhecimento de fala indisponível neste aparelho"
            )
        }
        val answer = withTimeoutOrNull(timeoutMs) { result.await() }
            ?: ListenOutcome(null, 6, "nenhuma fala detectada")
        if (token == listenGeneration && result.isActive) {
            listenGeneration++
            stt.cancel()
        }
        answer
    }

    private fun isWakeWord(text: String): Boolean {
        val normalized = CommandParser.normalize(text)
        if (normalized.contains("ola glia") || normalized.contains("alo glia") ||
            normalized.contains("hey glia")
        ) return true
        return normalized.split(' ').any { token ->
            token == "glia" || token == "clia" || token.startsWith("glia")
        }
    }

    private fun startWakeWordLoop() {
        if (shuttingDown || pipelineJob?.isActive == true || wakeJob?.isActive == true) return
        wakeGeneration++
        val generation = wakeGeneration
        wakeJob = scope.launch {
            wakeLock = acquireLock(wakeLock, "glia:wake")
            GliaState.setMode(GliaMode.IDLE)
            GliaState.setPartial("")
            GliaState.setAssistantStatus("aguardando \"Olá Glia\"")
            updateNotification("Diga \"Olá Glia\"")
            var failures = 0
            while (isActive && !shuttingDown && generation == wakeGeneration &&
                GliaPrefs.isTileActive(this@GliaVoiceService)
            ) {
                val heard = listenOnce(10_000L, GliaMode.IDLE)
                if (!isActive || generation != wakeGeneration || shuttingDown) break
                if (heard.isNullOrBlank()) {
                    failures++
                    delay(if (failures > 2) 1_200L else 350L)
                    continue
                }
                failures = 0
                if (isWakeWord(heard)) {
                    triggerWake(heard)
                    return@launch
                }
                delay(200L)
            }
            releaseLock(wakeLock)
            wakeLock = null
        }
    }

    private fun triggerWake(text: String) {
        if (shuttingDown) return
        wakeGeneration++
        wakeJob?.cancel()
        listenGeneration++
        scope.launch(Dispatchers.Main) { stt.cancel() }
        scope.launch {
            delay(120)
            vibrate()
            beep()
            bringToFront()
            GliaState.setResponse(text)
            startListening(bypassWake = false)
        }
    }

    private fun startListening(bypassWake: Boolean) {
        if (shuttingDown) return
        if (bypassWake && !GliaPrefs.isTileActive(this)) {
            GliaState.setAssistantStatus("ative o tile Glia para usar a voz")
            return
        }
        wakeGeneration++
        wakeJob?.cancel()
        listenGeneration++
        scope.launch(Dispatchers.Main) { stt.cancel() }
        pipelineGeneration++
        pipelineJob?.cancel()
        wakeLock = acquireLock(wakeLock, "glia:command")
        pipelineJob = scope.launch {
            resetForCommand()
            try {
                conversationLoop()
            } finally {
                if (!shuttingDown) returnToWake()
            }
        }
    }

    private suspend fun conversationLoop() {
        var first = true
        while (!shuttingDown && GliaPrefs.isTileActive(this)) {
            val timeout = if (first) 12_000L else 8_000L
            val text = listenOnce(timeout, GliaMode.LISTENING) ?: break
            first = false
            if (!handleText(text)) break
        }
    }

    private suspend fun handleText(text: String): Boolean {
        val command = CommandParser.parse(text)
        GliaState.setMode(GliaMode.THINKING)
        GliaState.setResponse("")
        if (command is VoiceCommand.StartCognitiveOffload) {
            handleCognitiveOffload()
            return false
        }
        val result = commandEngine.execute(command)
        GliaState.setResponse(result.spoken)
        if (result.openNotes) openNotes()
        speakAndWait(result.spoken, 45_000L)
        return true
    }

    private suspend fun handleCognitiveOffload() {
        speakAndWait("Claro. Pode falar o que está na sua mente. Estou ouvindo.", 45_000L)
        val thought = listenOnce(30_000L, GliaMode.LISTENING)
        if (thought.isNullOrBlank()) {
            speakAndWait(
                "Não consegui ouvir. Quando quiser, diga de novo: quero diminuir a carga cognitiva.",
                45_000L
            )
            return
        }
        notes.add(thought)
        val confirmation = "Certo, armazenei seus pensamentos. Foi bom pensar com você."
        GliaState.setResponse(confirmation)
        speakAndWait(confirmation, 45_000L)
        openNotes()
    }

    private suspend fun speakAndWait(text: String, timeoutMs: Long) {
        if (text.isBlank() || shuttingDown) return
        GliaState.setMode(GliaMode.SPEAKING)
        tts.speak(text)
        val readyDeadline = SystemClock.elapsedRealtime() + 90_000L
        while (!tts.isReady() && SystemClock.elapsedRealtime() < readyDeadline) delay(100)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (tts.isSpeaking() && SystemClock.elapsedRealtime() < deadline && !shuttingDown) {
            delay(100)
        }
        if (tts.isSpeaking()) tts.stop()
    }

    private suspend fun testTts() {
        speakAndWait(
            "Olá. Eu sou a Glia. Posso dizer a hora, a data, seus compromissos, " +
                "ligar a lanterna, abrir aplicativos e guardar seus pensamentos.",
            45_000L
        )
        GliaState.setMode(GliaMode.IDLE)
    }

    private fun resetForCommand() {
        GliaState.resetForCommand()
        GliaState.setMode(GliaMode.LISTENING)
        GliaState.setAssistantStatus("ouvindo")
        updateNotification("Ouvindo...")
    }

    private fun returnToWake() {
        scope.launch(Dispatchers.Main) { stt.cancel() }
        GliaState.setMode(GliaMode.IDLE)
        GliaState.setPartial("")
        if (!shuttingDown && GliaPrefs.isTileActive(this)) startWakeWordLoop()
    }

    private fun cancelPipeline(stopTts: Boolean) {
        pipelineGeneration++
        wakeGeneration++
        listenGeneration++
        pipelineJob?.cancel()
        pipelineJob = null
        wakeJob?.cancel()
        wakeJob = null
        scope.launch(Dispatchers.Main) { stt.cancel() }
        if (stopTts) tts.stop()
        GliaState.setMode(GliaMode.IDLE)
        GliaState.setPartial("")
    }

    private fun stopEverything() {
        shuttingDown = true
        cancelPipeline(stopTts = true)
        releaseLock(wakeLock)
        wakeLock = null
        GliaState.setMode(GliaMode.IDLE)
        GliaState.setPartial("")
        GliaState.setResponse("")
        GliaState.setAssistantStatus("desligada")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun openNotes() {
        try {
            startActivity(
                Intent(this, NotesActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível abrir as notas", e)
        }
    }

    private fun bringToFront() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    putExtra(MainActivity.EXTRA_WAKE, true)
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível trazer a Glia para frente", e)
        }
    }

    private fun ensureForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Glia",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Escuta e comandos da Glia"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, GliaVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Glia")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_glia)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_glia, "Parar", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildNotification(text))
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao atualizar notificação", e)
        }
    }

    private fun acquireLock(existing: PowerManager.WakeLock?, tag: String): PowerManager.WakeLock? {
        return try {
            val manager = getSystemService(PowerManager::class.java)
            val lock = existing ?: manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
                setReferenceCounted(false)
            }
            lock.acquire(15 * 60 * 1000L)
            lock
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao adquirir wake lock", e)
            null
        }
    }

    private fun releaseLock(lock: PowerManager.WakeLock?) {
        try {
            if (lock?.isHeld == true) lock.release()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao liberar wake lock", e)
        }
    }

    private fun vibrate() {
        try {
            val effect = VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)?.vibrate(effect)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao vibrar", e)
        }
    }

    private fun beep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            scope.launch {
                delay(250)
                tone.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao tocar beep", e)
        }
    }

    override fun onDestroy() {
        shuttingDown = true
        releaseLock(wakeLock)
        wakeLock = null
        pipelineJob?.cancel()
        wakeJob?.cancel()
        scope.launch(Dispatchers.Main) { stt.destroy() }
        tts.shutdown()
        serviceJob.cancel()
        instance = null
        super.onDestroy()
    }
}
