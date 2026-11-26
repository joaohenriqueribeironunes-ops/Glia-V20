package com.glia.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.glia.assistant.MainActivity
import com.glia.assistant.R
import com.glia.assistant.commands.CognitiveNotesStore
import com.glia.assistant.commands.CommandEngine
import com.glia.assistant.commands.SecondaryCommand
import com.glia.assistant.commands.VoiceCommand
import com.glia.assistant.core.GliaPrefs
import com.glia.assistant.stt.AmplifiedAudioSource
import com.glia.assistant.stt.AmplifiedMic
import com.glia.assistant.stt.CapturePlanner
import com.glia.assistant.stt.ContinuousListenPolicy
import com.glia.assistant.stt.SttRetryPolicy
import com.glia.assistant.stt.SystemStt
import com.glia.assistant.voice.VoiceCheck
import com.glia.assistant.voice.VoiceDecision
import com.glia.assistant.tts.GliaTts
import com.glia.assistant.ui.GliaMode
import com.glia.assistant.ui.GliaState
import com.glia.assistant.ui.NotesActivity
import com.glia.assistant.voice.VoicePrintStore
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

/**
 * Serviço de voz com microfone contínuo: enquanto o tile estiver ligado, o
 * reconhecimento é reiniciado imediatamente após cada fala. O microfone só é
 * liberado quando o usuário desliga a Glia pela central de controle.
 */
class GliaVoiceService : Service() {
    companion object {
        private const val TAG = "GliaVoice"
        const val CHANNEL_ID = "glia_voice"
        private const val NOTIF_ID = 1001

        const val ACTION_START_LISTENING = "com.glia.assistant.START_LISTENING"
        const val ACTION_STOP = "com.glia.assistant.STOP"
        const val ACTION_TEST_TTS = "com.glia.assistant.TEST_TTS"
        private const val VOICE_REJECTED_CODE = -2

        @Volatile
        var instance: GliaVoiceService? = null
            private set
    }

    private enum class ListeningPhase { IDLE, AWAITING_THOUGHT }

    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Erro no serviço", error)
        GliaState.setLastError(error.message ?: "Erro interno")
    }
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + serviceJob + exceptionHandler)

    private lateinit var stt: SystemStt
    private lateinit var amplifiedMic: AmplifiedMic
    private lateinit var voicePrint: VoicePrintStore
    private lateinit var tts: GliaTts
    private lateinit var commandEngine: CommandEngine
    private lateinit var notes: CognitiveNotesStore

    private var listenJob: Job? = null
    private var commandJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var shuttingDown = false
    @Volatile private var listenGeneration = 0
    @Volatile private var phase = ListeningPhase.IDLE
    private var voiceUnavailableNotified = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        stt = SystemStt(this)
        amplifiedMic = AmplifiedMic(this)
        voicePrint = VoicePrintStore(this)
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
            startContinuousListening()
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
                cancelWork(stopTts = true)
                scope.launch {
                    testTts()
                    startContinuousListening()
                }
                return START_STICKY
            }
            ACTION_START_LISTENING -> {
                startContinuousListening()
                if (!GliaPrefs.isTileActive(this)) {
                    GliaState.setAssistantStatus("ative o tile Glia para usar a voz")
                }
                return START_STICKY
            }
            else -> {
                startContinuousListening()
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

    /** Escuta única. O loop externa reinicia na sequência para manter o mic ligado. */
    private suspend fun listenOnce(timeoutMs: Long): String? {
        for (attempt in 0 until SttRetryPolicy.MAX_ATTEMPTS) {
            if (shuttingDown || !stt.isAvailable()) return null
            val plan = CapturePlanner.plan(attempt, GliaPrefs.micGain(this))
            val outcome = listenOnceAttempt(timeoutMs, plan)
            if (outcome.text != null) return outcome.text
            if (outcome.errorCode == VOICE_REJECTED_CODE) return null
            val code = outcome.errorCode ?: continue
            val retryable = if (plan.amplifiedSource) {
                SttRetryPolicy.shouldRetryAfterAmplified(code)
            } else {
                SttRetryPolicy.shouldRetry(code)
            }
            if (retryable && attempt < SttRetryPolicy.MAX_ATTEMPTS - 1) {
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
        plan: CapturePlanner.Plan
    ): ListenOutcome {
        if (shuttingDown || !stt.isAvailable()) {
            return ListenOutcome(
                null,
                -1,
                "Reconhecimento de fala indisponível neste aparelho"
            )
        }

        // Nas tentativas amplificadas o PCM é capturado antes do STT e também
        // serve para verificar a voz. Na última tentativa o reconhecedor escuta
        // o microfone ao vivo; não há PCM para verificar, então o filtro é
        // desativado para não descartar a fala do próprio usuário.
        var source: AmplifiedAudioSource? = null
        var voiceCheck = VoiceCheck.SKIPPED
        if (plan.amplifiedSource) {
            val captured = withContext(Dispatchers.IO) {
                runCatching { amplifiedMic.capture(timeoutMs, plan.gain) }
            }
            val audio = captured.getOrNull()
            if (captured.isFailure) {
                Log.w(
                    TAG,
                    "Captura amplificada falhou; usando microfone do sistema",
                    captured.exceptionOrNull()
                )
            } else if (audio == null) {
                return ListenOutcome(
                    null,
                    SttRetryPolicy.SPEECH_TIMEOUT_CODE,
                    "nenhuma fala detectada"
                )
            } else {
                voiceCheck = withContext(Dispatchers.IO) { voicePrint.verify(audio.file) }
                source = audio
            }
        }
        notifyVoiceUnavailable(voiceCheck)
        val fromOwner = VoiceDecision.allow(voiceCheck, allowOnUnavailable = true)

        return withContext(Dispatchers.Main) {
            try {
                val token = ++listenGeneration
                val result = CompletableDeferred<ListenOutcome>()
                val started = stt.listen(object : SystemStt.Listener {
                    override fun onReady() {
                        if (token == listenGeneration) {
                            GliaState.setAssistantStatus(
                                if (phase == ListeningPhase.AWAITING_THOUGHT) {
                                    "ouvindo pensamento"
                                } else {
                                    "microfone ligado — diga um comando"
                                }
                            )
                        }
                    }

                    override fun onPartial(text: String) {
                        if (token != listenGeneration) return
                        if (!tts.isSpeaking()) GliaState.setPartial(text)
                    }

                    override fun onRms(rms: Float) {
                        if (token == listenGeneration) {
                            GliaState.setAmplitude(((rms + 2f) / 12f).coerceIn(0f, 1f))
                        }
                    }

                    override fun onResult(text: String) {
                        if (token != listenGeneration) return
                        listenGeneration++
                        result.complete(
                            if (!fromOwner) {
                                ListenOutcome(null, VOICE_REJECTED_CODE, "voz não reconhecida")
                            } else {
                                ListenOutcome(text.ifBlank { null })
                            }
                        )
                    }

                    override fun onError(code: Int, message: String) {
                        if (token != listenGeneration) return
                        listenGeneration++
                        result.complete(ListenOutcome(null, code, message))
                    }
                }, plan.preferOffline, source)
                if (!started) {
                    listenGeneration++
                    ListenOutcome(
                        null,
                        -1,
                        "Reconhecimento de fala indisponível neste aparelho"
                    )
                } else {
                    val answer = withTimeoutOrNull(timeoutMs) { result.await() }
                        ?: ListenOutcome(null, SttRetryPolicy.SPEECH_TIMEOUT_CODE, "nenhuma fala detectada")
                    if (token == listenGeneration && result.isActive) {
                        listenGeneration++
                        stt.cancel()
                    }
                    answer
                }
            } finally {
                source?.close()
            }
        }
    }

    /** Avisa uma única vez quando o filtro de voz falha por motivo técnico. */
    private fun notifyVoiceUnavailable(check: VoiceCheck) {
        if (check == VoiceCheck.UNAVAILABLE) {
            if (!voiceUnavailableNotified) {
                voiceUnavailableNotified = true
                GliaState.setLastError("Filtro de voz indisponível; ouvindo sem filtro")
            }
        } else {
            voiceUnavailableNotified = false
        }
    }

    /**
     * Único loop de microfone. Não há janela de silêncio entre as escutas: após
     * qualquer resultado (inclusive silêncio), a próxima começa na mesma thread.
     */
    private fun startContinuousListening() {
        if (shuttingDown || !GliaPrefs.isTileActive(this) || listenJob?.isActive == true) return
        wakeLock = acquireLock(wakeLock, "glia:mic")
        phase = ListeningPhase.IDLE
        GliaState.setMode(GliaMode.IDLE)
        GliaState.setPartial("")
        GliaState.setAssistantStatus("microfone ligado")
        updateNotification("Microfone ligado — diga um comando")
        listenJob = scope.launch {
            while (isActive && !shuttingDown && GliaPrefs.isTileActive(this@GliaVoiceService)) {
                val heard = listenOnce(ContinuousListenPolicy.LISTEN_TIMEOUT_MS)
                if (!isActive || shuttingDown) break
                val delayMs = ContinuousListenPolicy.RESTART_DELAY_MS
                if (delayMs > 0) delay(delayMs)
                if (!heard.isNullOrBlank()) dispatch(heard)
            }
            releaseLock(wakeLock)
            wakeLock = null
        }
    }

    /** Executa somente comandos reconhecidos; fala comum é ignorada em silêncio. */
    private fun dispatch(text: String) {
        if (shuttingDown || ContinuousListenPolicy.ignoreWhileSpeaking(tts.isSpeaking())) return

        if (phase == ListeningPhase.AWAITING_THOUGHT) {
            scope.launch { saveThought(text) }
            return
        }

        val command = SecondaryCommand.parse(text)
        if (command is VoiceCommand.Unknown) return
        processCommand(command)
    }

    private fun processCommand(command: VoiceCommand) {
        if (commandJob?.isActive == true) return
        // Acende a tela quando o comando chega com o aparelho bloqueado.
        bringToFront()
        commandJob = scope.launch {
            try {
                handleCommand(command)
            } finally {
                commandJob = null
            }
        }
    }

    private suspend fun handleCommand(command: VoiceCommand) {
        GliaState.setMode(GliaMode.THINKING)
        GliaState.setResponse("")
        if (command is VoiceCommand.StartCognitiveOffload) {
            phase = ListeningPhase.AWAITING_THOUGHT
            speakAndWait("Claro. Pode falar o que está na sua mente. Estou ouvindo.", 45_000L)
            return
        }
        val result = commandEngine.execute(command)
        phase = ListeningPhase.IDLE
        GliaState.setResponse(result.spoken)
        if (result.openNotes) openNotes()
        speakAndWait(result.spoken, 45_000L)
    }

    private suspend fun saveThought(text: String) {
        phase = ListeningPhase.IDLE
        notes.add(text)
        val confirmation = "Certo, armazenei seus pensamentos. Foi bom pensar com você."
        GliaState.setResponse(confirmation)
        GliaState.setMode(GliaMode.THINKING)
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
    }

    private fun cancelWork(stopTts: Boolean) {
        listenGeneration++
        listenJob?.cancel()
        listenJob = null
        commandJob?.cancel()
        commandJob = null
        scope.launch(Dispatchers.Main) { stt.cancel() }
        if (stopTts) tts.stop()
        phase = ListeningPhase.IDLE
        GliaState.setMode(GliaMode.IDLE)
        GliaState.setPartial("")
    }

    private fun stopEverything() {
        shuttingDown = true
        cancelWork(stopTts = true)
        scope.launch(Dispatchers.Main) { stt.destroy() }
        releaseLock(wakeLock)
        wakeLock = null
        GliaState.setAssistantStatus("desligada")
        GliaState.setPartial("")
        GliaState.setResponse("")
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
                    putExtra(MainActivity.EXTRA_SHOW, true)
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

    override fun onDestroy() {
        shuttingDown = true
        listenJob?.cancel()
        commandJob?.cancel()
        scope.launch(Dispatchers.Main) { stt.destroy() }
        tts.shutdown()
        serviceJob.cancel()
        instance = null
        super.onDestroy()
    }
}
