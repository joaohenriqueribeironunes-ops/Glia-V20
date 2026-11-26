package com.glia.assistant.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Voz neural feminina pt-BR embarcada (VITS/Piper "Dii") via sherpa-onnx.
 *
 * Todo o processamento acontece no aparelho: o texto é sintetizado pelo modelo
 * ONNX e reproduzido em PCM float. Não há rede, não há TextToSpeech do sistema
 * e nenhum áudio é enviado para fora do dispositivo.
 */
class GliaTts(private val context: Context) {
    companion object {
        private const val TAG = "GliaTts"
        private const val SPEED = 1.03f
        private const val READY_TIMEOUT_MS = 90_000L
    }

    var onReady: (() -> Unit)? = null
    var onTtsError: ((String) -> Unit)? = null

    private val initLatch = CountDownLatch(1)
    private val executor = Executors.newSingleThreadExecutor { thread ->
        Thread(thread, "glia-neural-tts").apply { priority = Thread.NORM_PRIORITY }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)
    private val queued = AtomicInteger(0)

    @Volatile private var engine: OfflineTts? = null
    @Volatile private var speaking = false
    @Volatile private var failure: String? = null
    @Volatile private var shutdown = false
    @Volatile private var activeTrack: AudioTrack? = null

    init {
        executor.execute {
            try {
                val dir = NeuralVoiceStore.ensureModel(context)
                    ?: throw IllegalStateException("Modelo neural não encontrado no APK")
                val vits = OfflineTtsVitsModelConfig(
                    model = File(dir, NeuralVoiceLayout.MODEL_NAME).absolutePath,
                    tokens = File(dir, NeuralVoiceLayout.TOKENS_NAME).absolutePath,
                    dataDir = File(dir, NeuralVoiceLayout.ESPEAK_DIR).absolutePath,
                    lexicon = "",
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = vits,
                        numThreads = 1,
                        debug = false,
                        provider = "cpu"
                    ),
                    maxNumSentences = 1,
                    silenceScale = 0.2f
                )
                engine = OfflineTts(assetManager = null, config = config)
                try { Log.i(TAG, "Voz neural pronta: pt-BR feminina (Dii)") } catch (_: Throwable) { }
                onReady?.invoke()
            } catch (e: Throwable) {
                failure = e.message ?: e.javaClass.simpleName
                Log.w(TAG, "Falha ao carregar voz neural", e)
                reportError("Não foi possível carregar a voz neural: $failure")
            } finally {
                initLatch.countDown()
            }
        }
    }

    fun isReady(): Boolean = engine != null

    /** Inclui itens na fila: o chamador pode esperar a fala terminar com segurança. */
    fun isSpeaking(): Boolean = speaking || queued.get() > 0

    fun status(): String = when {
        engine != null -> "pronta · pt-BR feminina · Dii (offline)"
        failure != null -> "indisponível: $failure"
        else -> "carregando modelo neural…"
    }

    fun speak(text: String) {
        if (text.isBlank() || shutdown) return
        val token = generation.get()
        queued.incrementAndGet()
        executor.execute {
            try {
                if (token != generation.get() || shutdown) return@execute
                if (!initLatch.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    reportError("Tempo limite ao carregar a voz neural")
                    return@execute
                }
                val tts = engine
                if (tts == null || token != generation.get() || shutdown) return@execute
                speaking = true
                try {
                    val audio = tts.generate(text, 0, SPEED)
                    play(audio.samples, audio.sampleRate, token)
                } catch (e: Throwable) {
                    Log.w(TAG, "Falha ao sintetizar fala", e)
                    reportError("Falha ao falar: ${e.message ?: e.javaClass.simpleName}")
                } finally {
                    speaking = false
                    activeTrack = null
                }
            } finally {
                queued.decrementAndGet()
            }
        }
    }

    private fun play(samples: FloatArray, sampleRate: Int, token: Int) {
        if (samples.isEmpty() || token != generation.get() || shutdown) return
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferSize = maxOf(minBuffer, samples.size * 4)
        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManagerCompat.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
        }
        activeTrack = track
        val finished = CountDownLatch(1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            track.setNotificationMarkerPosition(samples.size)
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(audioTrack: AudioTrack?) { finished.countDown() }
                    override fun onPeriodicNotification(audioTrack: AudioTrack?) = Unit
                },
                handler
            )
        }
        try {
            track.play()
            var offset = 0
            val chunk = 8192
            while (offset < samples.size && token == generation.get() && !shutdown) {
                val count = minOf(chunk, samples.size - offset)
                val written = track.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
            }
            if (token == generation.get() && !shutdown) {
                val durationMs = samples.size.toLong() * 1000L / sampleRate.toLong()
                finished.await(durationMs + 2_000L, TimeUnit.MILLISECONDS)
            }
        } finally {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    track.setPlaybackPositionUpdateListener(null)
                }
                track.stop()
            } catch (_: Throwable) { }
            try { track.release() } catch (_: Throwable) { }
            if (activeTrack === track) activeTrack = null
        }
    }

    /** Cancela a fala atual e todos os comandos ainda na fila. */
    fun stop() {
        generation.incrementAndGet()
        speaking = false
        try { activeTrack?.stop() } catch (_: Throwable) { }
        try { activeTrack?.release() } catch (_: Throwable) { }
        activeTrack = null
    }

    fun shutdown() {
        shutdown = true
        stop()
        executor.shutdownNow()
        try { engine?.release() } catch (_: Throwable) { }
        engine = null
    }

    private fun reportError(message: String) {
        try { Log.w(TAG, message) } catch (_: Throwable) { }
        onTtsError?.invoke(message)
    }

    private object AudioManagerCompat {
        const val STREAM_MUSIC = 3
    }
}
