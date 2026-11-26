package com.glia.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
import androidx.core.content.ContextCompat
import com.glia.assistant.MainActivity
import com.glia.assistant.R
import com.glia.assistant.audio.AudioRecorder
import com.glia.assistant.commands.CognitiveNotesStore
import com.glia.assistant.commands.CommandEngine
import com.glia.assistant.commands.CommandParser
import com.glia.assistant.commands.VoiceCommand
import com.glia.assistant.core.GliaPrefs
import com.glia.assistant.stt.VoskModelStore
import com.glia.assistant.stt.VoskStt
import com.glia.assistant.tts.GliaTts
import com.glia.assistant.ui.GliaMode
import com.glia.assistant.ui.GliaState
import com.glia.assistant.ui.NotesActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
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

    private sealed class AudioMsg {
        data class Chunk(val data: ShortArray, val generation: Int) : AudioMsg()
        data class Flush(val ack: CompletableDeferred<Unit>) : AudioMsg()
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Erro no servico", error)
        GliaState.setLastError(error.message ?: "Erro interno")
    }
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + serviceJob + exceptionHandler)

    private lateinit var recorder: AudioRecorder
    private lateinit var stt: VoskStt
    private lateinit var tts: GliaTts
    private lateinit var commandEngine: CommandEngine
    private lateinit var notes: CognitiveNotesStore

    private val audioQueue = Channel<AudioMsg>(128)
    private var pipelineJob: Job? = null
    private var wakeJob: Job? = null

    @Volatile private var captureGeneration = 0
    @Volatile private var capturing = false
    @Volatile private var wakeTriggered = false
    @Volatile private var shuttingDown = false
    @Volatile private var recognizerReady = false
    @Volatile private var heardSpeech = false
    @Volatile private var lastSpeechAt = 0L
    @Volatile private var pipelineGeneration = 0
    @Volatile private var wakeGeneration = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        recorder = AudioRecorder()
        recorder.onAmplitude = { amplitude -> GliaState.setAmplitude(amplitude) }
        recorder.onBuffer = { data ->
            if (capturing) audioQueue.trySend(AudioMsg.Chunk(data, captureGeneration))
        }
        recorder.onError = { error -> GliaState.setLastError("Audio: $error") }
        stt = VoskStt(this)
        tts = GliaTts(this)
        commandEngine = CommandEngine(this)
        notes = CognitiveNotesStore(this)
        tts.onReady = { GliaState.setTtsStatus(tts.status()) }
        tts.onTtsError = { GliaState.setLastError("Voz: $it") }
        GliaState.setTtsStatus(tts.status())
        scope.launch { consumeAudio() }
        scope.launch(Dispatchers.IO) { initVosk() }
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

    private suspend fun consumeAudio() {
        for (msg in audioQueue) {
            when (msg) {
                is AudioMsg.Chunk -> {
                    if (msg.generation == captureGeneration) processAudio(msg.data)
                }
                is AudioMsg.Flush -> msg.ack.complete(Unit)
            }
        }
    }

    private fun processAudio(data: ShortArray) {
        if (!recognizerReady || shuttingDown) return
        val text = runCatching { stt.feed(data, data.size) }.getOrNull()?.trim().orEmpty()
        if (text.isEmpty()) return
        when (GliaState.mode.value) {
            GliaMode.IDLE -> if (isWakeWord(text)) triggerWake()
            GliaMode.LISTENING -> {
                heardSpeech = true
                lastSpeechAt = SystemClock.elapsedRealtime()
                GliaState.setPartial(text)
            }
            else -> Unit
        }
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

    private suspend fun ensureRecognizer(): Boolean {
        if (recognizerReady) return true
        return withContext(Dispatchers.IO) { initVosk() }
    }

    private fun initVosk(): Boolean {
        val embedded = VoskModelStore.ensureModel(this)
        val dirs = buildList {
            embedded?.let { add(it) }
            addAll(VoskStt.defaultModelDirs(this@GliaVoiceService))
        }.distinctBy { it.absolutePath }
        for (dir in dirs) {
            if (stt.init(dir.absolutePath)) {
                recognizerReady = true
                GliaState.setSttStatus("pronto (${dir.name})")
                return true
            }
        }
        recognizerReady = false
        GliaState.setSttStatus("indisponivel")
        GliaState.setLastError("Modelo Vosk indisponivel (asset ausente ou corrompido)")
        return false
    }

    private fun startWakeWordLoop() {
        if (shuttingDown || pipelineJob?.isActive == true) return
        wakeGeneration++
        val generation = wakeGeneration
        wakeJob?.cancel()
        wakeJob = scope.launch {
            if (!ensureRecognizer()) {
                GliaState.setAssistantStatus("erro: modelo de voz ausente")
                return@launch
            }
            stt.finalize()
            wakeTriggered = false
            capturing = true
            captureGeneration++
            wakeLock = acquireLock(wakeLock, "glia:wake")
            try {
                if (!recorder.start()) {
                    GliaState.setLastError("Nao foi possivel abrir o microfone")
                    return@launch
                }
                GliaState.setMode(GliaMode.IDLE)
                GliaState.setPartial("")
                GliaState.setAssistantStatus("aguardando \"Ola Glia\"")
                updateNotification("Diga \"Ola Glia\"")
                while (isActive && !shuttingDown && !wakeTriggered && generation == wakeGeneration) {
                    delay(250)
                }
            } finally {
                if (generation == wakeGeneration) {
                    capturing = false
                    captureGeneration++
                    recorder.stop()
                }
                releaseLock(wakeLock)
                wakeLock = null
            }
        }
    }

    private fun triggerWake() {
        if (wakeTriggered || shuttingDown) return
        wakeTriggered = true
        wakeGeneration++
        wakeJob?.cancel()
        recorder.stop()
        stt.finalize()
        scope.launch {
            delay(120)
            vibrate()
            beep()
            bringToFront()
            startListening(bypassWake = false)
        }
    }

    private fun startListening(bypassWake: Boolean) {
        if (shuttingDown) return
        if (bypassWake && !GliaPrefs.isTileActive(this)) {
            GliaState.setAssistantStatus("ative o tile Glia para usar a voz")
            return
        }
        wakeTriggered = true
        wakeGeneration++
        wakeJob?.cancel()
        pipelineGeneration++
        pipelineJob?.cancel()
        pipelineJob = scope.launch {
            if (bypassWake) {
                flushAudio()
                recorder.stop()
            }
            if (!ensureRecognizer()) return@launch
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
            val text = captureCommand(timeout, 1_200L) ?: break
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
        speakAndWait(result.spoken, 30_000L)
        return true
    }

    private suspend fun handleCognitiveOffload() {
        speakAndWait("Claro. Pode falar o que esta na sua mente. Estou ouvindo.", 30_000L)
        val thought = captureCommand(30_000L, 1_800L)
        if (thought.isNullOrBlank()) {
            speakAndWait("Nao consegui ouvir. Quando quiser, diga de novo: quero diminuir a carga cognitiva.", 30_000L)
            return
        }
        notes.add(thought)
        val confirmation = "Certo, armazenei seus pensamentos. Foi bom pensar com voce."
        GliaState.setResponse(confirmation)
        speakAndWait(confirmation, 30_000L)
        openNotes()
    }

    private suspend fun captureCommand(timeoutMs: Long, silenceMs: Long): String? {
        if (!ensureRecognizer()) return null
        GliaState.setMode(GliaMode.LISTENING)
        GliaState.setPartial("")
        heardSpeech = false
        lastSpeechAt = SystemClock.elapsedRealtime()
        captureGeneration++
        val generation = captureGeneration
        capturing = true
        if (!recorder.start()) {
            capturing = false
            GliaState.setLastError("Nao foi possivel abrir o microfone")
            return null
        }
        val started = SystemClock.elapsedRealtime()
        try {
            while (SystemClock.elapsedRealtime() - started < timeoutMs) {
                delay(80)
                if (heardSpeech && SystemClock.elapsedRealtime() - lastSpeechAt >= silenceMs) break
            }
        } finally {
            if (generation == captureGeneration) capturing = false
            recorder.stop()
        }
        flushAudio()
        val finalText = runCatching { stt.finalize() }.getOrDefault("").trim()
        val partial = GliaState.partialText.value.trim()
        GliaState.setPartial("")
        return when {
            finalText.isNotEmpty() -> finalText
            partial.isNotEmpty() -> partial
            else -> null
        }
    }

    private suspend fun flushAudio() {
        val ack = CompletableDeferred<Unit>()
        if (audioQueue.trySend(AudioMsg.Flush(ack)).isSuccess) {
            withTimeoutOrNull(3_000L) { ack.await() }
        }
    }

    private suspend fun speakAndWait(text: String, timeoutMs: Long) {
        if (text.isBlank() || shuttingDown) return
        GliaState.setMode(GliaMode.SPEAKING)
        tts.speak(text)
        val readyDeadline = SystemClock.elapsedRealtime() + 8_000L
        while (!tts.isReady() && SystemClock.elapsedRealtime() < readyDeadline) delay(50)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (tts.isSpeaking() && SystemClock.elapsedRealtime() < deadline && !shuttingDown) {
            delay(100)
        }
        if (tts.isSpeaking()) tts.stop()
    }

    private suspend fun testTts() {
        speakAndWait(
            "Ola. Eu sou a Glia. Posso dizer a hora, a data, seus compromissos, " +
                "ligar a lanterna, abrir aplicativos e guardar seus pensamentos.",
            30_000L
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
        recorder.stop()
        GliaState.setMode(GliaMode.IDLE)
        GliaState.setPartial("")
        if (!shuttingDown && GliaPrefs.isTileActive(this)) startWakeWordLoop()
    }

    private fun cancelPipeline(stopTts: Boolean) {
        pipelineGeneration++
        wakeGeneration++
        pipelineJob?.cancel()
        pipelineJob = null
        wakeJob?.cancel()
        wakeJob = null
        recorder.stop()
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
            Log.w(TAG, "Nao foi possivel abrir as notas", e)
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
            Log.w(TAG, "Nao foi possivel trazer a Glia para frente", e)
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
            Log.w(TAG, "Falha ao atualizar notificacao", e)
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
        serviceJob.cancel()
        runCatching { recorder.stop() }
        runCatching { stt.release() }
        runCatching { tts.shutdown() }
        instance = null
        super.onDestroy()
    }
}
