package com.glia.assistant.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.glia.assistant.MainActivity
import com.glia.assistant.R
import com.glia.assistant.audio.AudioRecorder
import com.glia.assistant.core.GliaMemory
import com.glia.assistant.core.GliaPrefs
import com.glia.assistant.llm.LlamaBridge
import com.glia.assistant.llm.ModelStore
import com.glia.assistant.llm.TokenCallback
import com.glia.assistant.llm.ToolCallParser
import com.glia.assistant.llm.buildPrompt
import com.glia.assistant.stt.VoskStt
import com.glia.assistant.tts.GliaTts
import com.glia.assistant.tools.BuiltInRouter
import com.glia.assistant.tools.ContextDeviceController
import com.glia.assistant.tools.ToolBus
import com.glia.assistant.tools.ToolCall
import com.glia.assistant.ui.GliaMode
import com.glia.assistant.ui.GliaState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
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
        const val ACTION_TEST_LLM = "com.glia.assistant.TEST_LLM"
        const val ACTION_RELOAD_MODEL = "com.glia.assistant.RELOAD_MODEL"

        @Volatile
        var instance: GliaVoiceService? = null
    }

    private sealed class AudioMsg {
        class Chunk(val data: ShortArray, val generation: Int) : AudioMsg()
        class Flush(val ack: CompletableDeferred<Unit>) : AudioMsg()
    }

    private val serviceJob = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Erro em coroutine", e)
        GliaState.setLastError(e.message ?: e.toString())
    }
    private val scope = CoroutineScope(serviceJob + Dispatchers.Default + exceptionHandler)

    private lateinit var recorder: AudioRecorder
    private lateinit var stt: VoskStt
    private lateinit var tts: GliaTts
    private lateinit var llm: LlamaBridge
    private lateinit var toolBus: ToolBus

    private val audioQueue = Channel<AudioMsg>(capacity = 128)
    private var pipelineJob: Job? = null
    private var wakeJob: Job? = null

    @Volatile private var captureGeneration = 0
    @Volatile private var capturing = false
    @Volatile private var wakeTriggered = false
    @Volatile private var shuttingDown = false
    @Volatile private var recognizerReady = false
    @Volatile private var modelReady = false
    @Volatile private var modelLoading = false
    @Volatile private var modelError: String? = null
    @Volatile private var heardSpeech = false
    @Volatile private var lastSpeechAt = 0L

    private var pipelineGeneration = 0
    private var wakeGeneration = 0
    private var modelWakeLock: PowerManager.WakeLock? = null
    private var genWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        recorder = AudioRecorder()
        stt = VoskStt(this)
        tts = GliaTts(this)
        llm = LlamaBridge
        toolBus = ToolBus(ContextDeviceController(this))

        GliaState.setTtsStatus(tts.status())
        tts.onReady = { GliaState.setTtsStatus(tts.status()) }
        tts.onTtsError = { msg ->
            GliaState.setLastError(msg)
            GliaState.setTtsStatus(tts.status())
        }
        recorder.onAmplitude = { GliaState.setAmplitude(it) }
        recorder.onError = { msg ->
            GliaState.setLastError(msg)
            updateNotification("Erro de audio")
        }
        recorder.onBuffer = { buf ->
            val gen = captureGeneration
            if (capturing) {
                if (!audioQueue.trySend(AudioMsg.Chunk(buf, gen)).isSuccess) {
                    Log.w(TAG, "Fila de audio cheia; buffer descartado")
                }
            }
        }

        scope.launch {
            for (msg in audioQueue) {
                when (msg) {
                    is AudioMsg.Chunk -> {
                        if (msg.generation == captureGeneration) {
                            try {
                                processAudio(msg.data)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "processAudio", e)
                            }
                        }
                    }
                    is AudioMsg.Flush -> msg.ack.complete(Unit)
                }
            }
        }

        scope.launch(Dispatchers.IO) { initVosk() }
        if (GliaPrefs.isTileActive(this)) startModelLoad() else GliaState.setModelStatus("desligada")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Inicializando..."))
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_TEST_TTS -> {
                cancelPipeline()
                scope.launch { testTts() }
                return START_STICKY
            }
            ACTION_TEST_LLM -> {
                cancelPipeline()
                scope.launch { testLlm() }
                return START_STICKY
            }
            ACTION_RELOAD_MODEL -> {
                cancelPipeline()
                reloadModel()
                return START_STICKY
            }
            ACTION_START_LISTENING -> {
                startListening(bypassWake = true)
                return START_STICKY
            }
            else -> {
                if (pipelineJob?.isActive != true) startWakeWordLoop()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startModelLoad() {
        if (modelLoading) return
        modelLoading = true
        modelReady = false
        GliaState.setModelStatus("carregando...")
        modelWakeLock = acquireLock(modelWakeLock, "Glia:model")
        scope.launch(Dispatchers.IO) {
            try {
                loadModelBlocking()
            } catch (e: Throwable) {
                modelError = e.message ?: e.toString()
                GliaState.setModelStatus("erro: $modelError")
                Log.e(TAG, "Falha ao carregar modelo", e)
            } finally {
                modelLoading = false
                releaseLock(modelWakeLock)
                modelWakeLock = null
            }
        }
    }

    private fun loadModelBlocking() {
        val resolved = ModelStore.discoverModel(this, GliaPrefs.modelPath(this))
        val file = resolved.first
        resolved.second?.let { GliaState.setLastError(it) }
        if (file == null) {
            modelError = resolved.second ?: "Modelo .gguf nao encontrado"
            GliaState.setModelStatus("erro: $modelError")
            return
        }

        val threads = GliaPrefs.threads(this)

        if (!file.isFile || file.length() < 1024) {
            modelError = "Arquivo de modelo invalido: ${file.name}"
            GliaState.setModelStatus("erro: $modelError")
            return
        }
        val plan = GliaMemory.plan(this, file.length(), GliaPrefs.contextSize(this))
        if (!plan.ok) {
            modelError = plan.message.ifBlank { "Memoria insuficiente para o modelo" }
            GliaState.setModelStatus("erro: $modelError")
            return
        }
        GliaState.setModelStatus("carregando ${file.name}...")
        val ok = llm.loadModel(
            file.absolutePath,
            plan.contextTokens,
            threads,
            plan.budgetBytes
        )
        if (ok) {
            modelReady = true
            modelError = null
            GliaPrefs.setModelPath(this, file.absolutePath)
            val kv = if (llm.usesQuantizedKv()) "KV q8_0" else "KV F16"
            val realCtx = llm.contextSize()
            GliaState.setModelStatus(
                "pronto (${file.name}, ${file.length() / (1024 * 1024)} MB, " +
                    "ctx $realCtx, $kv, teto ${plan.budgetBytes / (1024 * 1024)} MB)"
            )
            Log.i(TAG, "Modelo carregado: ${file.absolutePath} (${plan.message})")
            return
        }

        modelReady = false
        val err = llm.lastError()
        modelError = err?.ifBlank { "falha ao carregar ${file.name}" } ?: "falha ao carregar ${file.name}"
        Log.e(TAG, "Falha ao carregar ${file.absolutePath}: $err")
        GliaState.setModelStatus("erro: $modelError")
    }

    private fun initVosk(): Boolean {
        for (dir in VoskStt.defaultModelDirs(this)) {
            if (!dir.isDirectory) continue
            if (stt.init(dir.absolutePath)) {
                recognizerReady = true
                GliaState.setSttStatus("pronto (${dir.name})")
                return true
            }
        }
        recognizerReady = false
        GliaState.setSttStatus("modelo Vosk nao encontrado")
        return false
    }

    private fun startWakeWordLoop() {
        if (shuttingDown) return
        if (pipelineJob?.isActive == true) return

        val gen = ++wakeGeneration
        val old = wakeJob
        wakeJob = null
        old?.cancel()

        wakeJob = scope.launch {
            try {
                if (!recognizerReady) initVosk()
                if (!recognizerReady) {
                    GliaState.setMode(GliaMode.IDLE)
                    GliaState.setLastError(
                        "Modelo Vosk nao encontrado. Copie vosk-model-small-pt-0.3 para o armazenamento interno."
                    )
                    updateNotification("STT indisponivel")
                    return@launch
                }
                try {
                    stt.finalize()
                } catch (_: Exception) {
                }
                wakeTriggered = false
                capturing = true
                captureGeneration++
                if (!recorder.start()) {
                    capturing = false
                    GliaState.setLastError("Falha ao iniciar microfone")
                    updateNotification("Microfone indisponivel")
                    return@launch
                }
                GliaState.setMode(GliaMode.IDLE)
                updateNotification("Diga \"Glia\"")
                while (isActive && gen == wakeGeneration && !wakeTriggered) {
                    delay(250)
                }
            } finally {
                if (gen == wakeGeneration) {
                    capturing = false
                    recorder.stop()
                }
            }
        }
    }

    private suspend fun triggerWake() {
        if (wakeTriggered) return
        wakeTriggered = true
        wakeGeneration++
        val wj = wakeJob
        wakeJob = null
        wj?.cancelAndJoin()

        capturing = false
        captureGeneration++
        recorder.stop()
        try {
            stt.finalize()
        } catch (_: Exception) {
        }
        delay(120)
        vibrate(80)
        beep()
        startListening(bypassWake = false)
    }

    private fun startListening(bypassWake: Boolean) {
        if (bypassWake && !GliaPrefs.isTileActive(this)) {
            GliaState.setMode(GliaMode.IDLE)
            return
        }
        val gen = ++pipelineGeneration
        if (pipelineJob?.isActive == true) llm.stop()
        wakeTriggered = true
        pipelineJob?.cancel()
        pipelineJob = scope.launch {
            try {
                if (bypassWake) {
                    wakeGeneration++
                    val wj = wakeJob
                    wakeJob = null
                    wj?.cancelAndJoin()
                    capturing = false
                    captureGeneration++
                    recorder.stop()
                    flushAudio()
                    if (!initVosk()) {
                        GliaState.setLastError("Modelo Vosk nao encontrado")
                        return@launch
                    }
                }
                GliaState.resetForCommand()
                conversationLoop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline", e)
                GliaState.setLastError(e.message ?: e.toString())
            } finally {
                if (gen == pipelineGeneration && !shuttingDown) {
                    pipelineJob = null
                    returnToWake()
                }
            }
        }
    }

    private suspend fun conversationLoop() {
        var first = true
        while (currentCoroutineContext().isActive) {
            val timeout = if (first) 12000L else 7000L
            first = false
            val text = captureCommand(timeout, 1200L) ?: break
            generateAndSpeak(text)
        }
    }

    private suspend fun captureCommand(timeoutMs: Long, silenceMs: Long): String? {
        if (!recognizerReady && !initVosk()) return null
        GliaState.setMode(GliaMode.LISTENING)
        GliaState.setPartial("")
        heardSpeech = false
        lastSpeechAt = SystemClock.elapsedRealtime()
        captureGeneration++
        capturing = true
        if (!recorder.start()) {
            capturing = false
            GliaState.setLastError("Falha ao iniciar microfone")
            return null
        }

        val start = SystemClock.elapsedRealtime()
        try {
            while (currentCoroutineContext().isActive) {
                val now = SystemClock.elapsedRealtime()
                if (GliaState.amplitude.value > 0.012f) {
                    heardSpeech = true
                    lastSpeechAt = now
                }
                if (heardSpeech && now - lastSpeechAt >= silenceMs) break
                if (now - start >= timeoutMs) break
                delay(80)
            }
        } finally {
            capturing = false
            captureGeneration++
            recorder.stop()
        }

        flushAudio()
        val finalText = try {
            stt.finalize()
        } catch (e: Exception) {
            Log.e(TAG, "finalize", e)
            ""
        }
        val text = finalText.ifBlank { GliaState.partialText.value }.trim()
        return text.ifBlank { null }
    }

    private suspend fun flushAudio(timeoutMs: Long = 3000) {
        val ack = CompletableDeferred<Unit>()
        withTimeoutOrNull(timeoutMs) {
            audioQueue.send(AudioMsg.Flush(ack))
            ack.await()
        }
    }

    private suspend fun processAudio(buf: ShortArray) {
        if (!recognizerReady) return
        val text = stt.feed(buf, buf.size) ?: return
        if (text.isBlank()) return
        when (GliaState.mode.value) {
            GliaMode.IDLE -> {
                if (!wakeTriggered && isWakeWord(text.lowercase())) triggerWake()
            }
            GliaMode.LISTENING -> {
                heardSpeech = true
                lastSpeechAt = SystemClock.elapsedRealtime()
                GliaState.setPartial(text)
            }
            else -> {
            }
        }
    }

    private fun isWakeWord(text: String): Boolean {
        val tokens = text.lowercase().split(Regex("[^a-zA-Z0-9\u00C0-\u017F]+")).filter { it.isNotBlank() }
        return tokens.any { it.startsWith("glia") }
    }

    private suspend fun generateAndSpeak(userText: String) {
        GliaState.setMode(GliaMode.THINKING)
        GliaState.setResponse("")
        GliaState.setLastError("")
        updateNotification("Pensando...")

        if (modelLoading) {
            GliaState.setResponse("Carregando modelo...")
            while (modelLoading && currentCoroutineContext().isActive) delay(250)
        }
        if (!llm.isLoaded()) {
            val detail = modelError ?: llm.lastError()
            val msg = if (detail.isNullOrBlank()) {
                "Modelo de IA nao carregado."
            } else {
                "Modelo de IA nao carregado: $detail"
            }
            GliaState.setResponse(msg)
            GliaState.setLastError(msg)
            updateNotification("Modelo indisponivel")
            speakAndWait(msg, 20000)
            return
        }

        genWakeLock = acquireLock(genWakeLock, "Glia:llm")
        try {
            val params = LlamaBridge.GenerationParams(
                temperature = GliaPrefs.temperature(this),
                topP = GliaPrefs.topP(this),
                topK = GliaPrefs.topK(this),
                minP = GliaPrefs.minP(this),
                presencePenalty = GliaPrefs.presencePenalty(this),
                repetitionPenalty = GliaPrefs.repeatPenalty(this),
                maxTokens = GliaPrefs.maxTokens(this)
            )
            val prompt = buildPrompt(userText)
            val started = SystemClock.elapsedRealtime()
            var tokens = 0
            val sentenceBuffer = StringBuilder()

            val callback = object : TokenCallback {
                override fun onToken(token: String) {
                    tokens++
                    GliaState.appendResponse(token)
                    sentenceBuffer.append(token)
                    flushSentences(sentenceBuffer, force = false)
                }

                override fun onDone() {
                    flushSentences(sentenceBuffer, force = true)
                }

                override fun onError(message: String) {
                    GliaState.setLastError(message)
                    Log.e(TAG, "LLM: $message")
                }
            }

            llm.generate(prompt, callback, params)
            val deadline = SystemClock.elapsedRealtime() + 10 * 60 * 1000L
            while (llm.isGenerating() &&
                currentCoroutineContext().isActive &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                delay(100)
            }
            if (llm.isGenerating()) {
                llm.stop()
                GliaState.setLastError("Geracao excedeu 10 minutos")
            }

            val elapsed = SystemClock.elapsedRealtime() - started
            val stats = llm.lastGenerationStats()
            if (stats.size >= 6) {
                val tps = stats[4] / 100.0
                GliaState.setDetailedGenerationStats(
                    "prefill ${stats[2]}ms · decode ${stats[3]}ms · " +
                        String.format(java.util.Locale.getDefault(), "%.1f", tps) +
                        " tok/s · ${stats[1]} tokens (prompt ${stats[0]}, reuse ${stats[5]})"
                )
            } else {
                GliaState.setGenerationStats(elapsed, tokens)
            }
            updateNotification("Respondendo...")

            val response = GliaState.responseText.value
            if (response.isBlank()) {
                val fallback = "Nao consegui gerar uma resposta."
                GliaState.setResponse(fallback)
                GliaState.setLastError(llm.lastError()?.ifBlank { "Resposta vazia do modelo" } ?: "Resposta vazia do modelo")
                speakAndWait(fallback, 20000)
            } else {
                val toolCall = ToolCallParser.parse(response) ?: BuiltInRouter.route(userText)
                if (toolCall != null) {
                    executeToolAndSpeak(toolCall)
                } else {
                    speakAndWait(null, 180000)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "generateAndSpeak", e)
            GliaState.setLastError(e.message ?: e.toString())
            speakAndWait("Ocorreu um erro ao responder.", 20000)
        } finally {
            releaseLock(genWakeLock)
            genWakeLock = null
        }
    }

    private suspend fun executeToolAndSpeak(toolCall: ToolCall) {
        GliaState.setMode(GliaMode.THINKING)
        updateNotification("Executando ferramenta...")
        val result = toolBus.execute(toolCall.name, toolCall.args)
        if (result == null) {
            val fallback = "Nao encontrei a ferramenta ${toolCall.name} para executar."
            GliaState.setResponse(fallback)
            GliaState.setLastError(fallback)
            updateNotification("Ferramenta indisponivel")
            speakAndWait(fallback, 20000)
            return
        }
        GliaState.setResponse(result.spoken)
        speakAndWait(result.spoken, 20000)
    }

    private fun flushSentences(buffer: StringBuilder, force: Boolean) {
        while (true) {
            val text = buffer.toString()
            if (text.isEmpty()) return
            val idx = text.indexOfFirst { it == '.' || it == '!' || it == '?' || it == '\n' }
            if (idx >= 0 && (idx < text.length - 1 || force)) {
                val sentence = text.substring(0, idx + 1).trim()
                buffer.delete(0, idx + 1)
                if (sentence.isNotBlank()) {
                    GliaState.setMode(GliaMode.SPEAKING)
                    tts.speak(sentence)
                }
                continue
            }
            if (force) {
                buffer.clear()
                val rest = text.trim()
                if (rest.isNotBlank()) {
                    GliaState.setMode(GliaMode.SPEAKING)
                    tts.speak(rest)
                }
            } else if (text.length >= 160) {
                val cut = text.lastIndexOf(' ')
                if (cut > 40) {
                    val sentence = text.substring(0, cut).trim()
                    buffer.delete(0, cut + 1)
                    if (sentence.isNotBlank()) {
                        GliaState.setMode(GliaMode.SPEAKING)
                        tts.speak(sentence)
                    }
                    continue
                }
            }
            return
        }
    }

    private suspend fun speakAndWait(text: String?, timeoutMs: Long) {
        if (text != null) tts.speak(text)
        val readyDeadline = SystemClock.elapsedRealtime() + 8000
        while (!tts.isReady() &&
            SystemClock.elapsedRealtime() < readyDeadline &&
            currentCoroutineContext().isActive
        ) {
            delay(100)
        }
        if (!tts.isReady()) {
            GliaState.setTtsStatus(tts.status())
            GliaState.setLastError("TTS nao inicializou; resposta exibida na tela")
            tts.stop()
            return
        }

        GliaState.setMode(GliaMode.SPEAKING)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (tts.isSpeaking() &&
            currentCoroutineContext().isActive &&
            SystemClock.elapsedRealtime() < deadline
        ) {
            delay(100)
        }
        if (tts.isSpeaking()) {
            tts.stop()
            GliaState.setLastError("TTS excedeu o tempo; fala interrompida")
        }
        GliaState.setTtsStatus(tts.status())
    }

    private suspend fun testTts() {
        val msg = "Teste de voz da Glia. Se voce esta ouvindo, a sintese de voz esta funcionando."
        GliaState.setResponse(msg)
        tts.speak(msg)
        speakAndWait(null, 30000)
        if (!shuttingDown && pipelineJob?.isActive != true) {
            GliaState.setMode(GliaMode.IDLE)
            startWakeWordLoop()
        }
    }

    private suspend fun testLlm() {
        generateAndSpeak("Diga apenas: teste do modelo concluido.")
        if (!shuttingDown && pipelineJob?.isActive != true) {
            GliaState.setMode(GliaMode.IDLE)
            startWakeWordLoop()
        }
    }

    private fun cancelPipeline() {
        pipelineGeneration++
        pipelineJob?.cancel()
        pipelineJob = null
        wakeGeneration++
        val wj = wakeJob
        wakeJob = null
        wj?.cancel()
        llm.stop()
        capturing = false
        captureGeneration++
        scope.launch { recorder.stop() }
    }

    private fun reloadModel() {
        if (modelLoading) return
        updateNotification("Recarregando modelo...")
        scope.launch {
            llm.stop()
            withContext(Dispatchers.IO) { llm.unload() }
            modelReady = false
            modelError = null
            startModelLoad()
        }
    }

    private fun returnToWake() {
        if (shuttingDown) return
        capturing = false
        captureGeneration++
        recorder.stop()
        GliaState.setMode(GliaMode.IDLE)
        GliaState.setPartial("")
        startWakeWordLoop()
    }

    private fun stopEverything() {
        shuttingDown = true
        pipelineGeneration++
        wakeGeneration++
        pipelineJob?.cancel()
        pipelineJob = null
        wakeJob?.cancel()
        wakeJob = null
        capturing = false
        captureGeneration++
        scope.launch {
            llm.stop()
            tts.stop()
            recorder.stop()
            GliaState.setMode(GliaMode.IDLE)
            GliaState.setPartial("")
            GliaState.setResponse("")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun acquireLock(existing: PowerManager.WakeLock?, tag: String): PowerManager.WakeLock? {
        if (existing?.isHeld == true) return existing
        val pm = getSystemService(PowerManager::class.java) ?: return existing
        return pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
            setReferenceCounted(false)
            acquire(15 * 60 * 1000L)
        }
    }

    private fun releaseLock(lock: PowerManager.WakeLock?) {
        try {
            if (lock?.isHeld == true) lock.release()
        } catch (_: Exception) {
        }
    }

    private fun vibrate(ms: Long) {
        try {
            val v = getSystemService(Vibrator::class.java) ?: return
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    private fun beep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            scope.launch {
                delay(250)
                try {
                    tone.release()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            nm.notify(NOTIF_ID, buildNotification(text))
        } catch (e: Exception) {
            Log.w(TAG, "notify: ${e.message}")
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, GliaVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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

    override fun onDestroy() {
        shuttingDown = true
        wakeGeneration++
        pipelineGeneration++
        scope.cancel()
        try {
            recorder.stop()
        } catch (_: Exception) {
        }
        try {
            stt.release()
        } catch (_: Exception) {
        }
        try {
            tts.shutdown()
        } catch (_: Exception) {
        }
        try {
            llm.stop()
            llm.unload()
        } catch (_: Exception) {
        }
        releaseLock(modelWakeLock)
        releaseLock(genWakeLock)
        modelWakeLock = null
        genWakeLock = null
        instance = null
        super.onDestroy()
    }
}
