package com.glia.assistant.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

/** Áudio PCM já amplificado, pronto para ser passado ao SpeechRecognizer. */
class AmplifiedAudioSource(
    val descriptor: ParcelFileDescriptor,
    val file: File,
    val sampleRate: Int,
    val channelCount: Int,
    val encoding: Int
) {
    fun close() {
        runCatching { descriptor.close() }
        runCatching { if (file.exists()) file.delete() }
    }

    companion object {
        /** Cópia independente para cada tentativa de reconhecimento. */
        fun fromPcm(
            context: Context,
            pcm: ByteArray,
            sampleRate: Int = CaptureWindow.SAMPLE_RATE,
            channelCount: Int = 1,
            encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
            prefix: String = "attempt"
        ): AmplifiedAudioSource {
            val dir = File(context.filesDir, "stt")
            if (!dir.exists()) dir.mkdirs()
            val file = File.createTempFile(prefix, ".pcm", dir)
            FileOutputStream(file, false).use { it.write(pcm) }
            return AmplifiedAudioSource(
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
                file = file,
                sampleRate = sampleRate,
                channelCount = channelCount,
                encoding = encoding
            )
        }
    }
}

/**
 * Microfone contínuo: uma única [AudioRecord] fica aberta desde a ativação do
 * tile até o desligamento. Todas as amostras entram em janelas sobrepostas,
 * sem detector de fala e sem fechar o capturador entre reconhecimentos.
 */
class AmplifiedMic(private val context: Context) {
    companion object {
        private const val FRAME_BYTES = 640 // 20 ms em PCM16 mono
    }

    private val conditioner = PcmConditioner()
    private var conditionState = PcmConditioner.initial()
    private val tail = RollingTail(
        capacity = CaptureWindow.overlapBytes() * 2,
        keepOnRotate = CaptureWindow.overlapBytes()
    )

    private var recorder: AudioRecord? = null
    private var agc: AutomaticGainControl? = null
    private var suppressor: NoiseSuppressor? = null
    private var gain: Float = MicGain.DEFAULT

    @Volatile
    private var closed = false

    /** Abre o microfone uma vez. Deve ser chamado na thread do loop de escuta. */
    fun start(gain: Float) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("permissão de microfone negada")
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            CaptureWindow.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) throw IllegalStateException("microfone indisponível neste aparelho")

        val created = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            CaptureWindow.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, CaptureWindow.SAMPLE_RATE)
        )
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            throw IllegalStateException("microfone indisponível neste aparelho")
        }

        closed = false
        this.gain = MicGain.clamp(gain)
        // O AGC nativo comprime duas vezes quando já há ganho de software.
        if (this.gain <= MicGain.MIN) {
            runCatching { AutomaticGainControl.create(created.audioSessionId) }
                .getOrNull()?.let { effect ->
                    agc = effect
                    runCatching { effect.enabled = true }
                }
        }
        runCatching { NoiseSuppressor.create(created.audioSessionId) }
            .getOrNull()?.let { effect ->
                suppressor = effect
                runCatching { effect.enabled = true }
            }

        created.startRecording()
        recorder = created
    }

    /**
     * Bloqueia até completar uma janela e devolve o PCM condicionado. Retorna
     * null quando [stop] foi chamado. O gravador permanece aberto entre janelas.
     */
    fun nextWindow(): AmplifiedAudioSource? {
        val rec = recorder ?: return null
        if (closed) return null

        val dir = File(context.filesDir, "stt")
        if (!dir.exists()) dir.mkdirs()
        val file = File.createTempFile("window-", ".pcm", dir)
        val frame = ByteArray(FRAME_BYTES)
        var written = 0

        try {
            FileOutputStream(file, false).use { out ->
                val overlap = tail.snapshot()
                if (overlap.isNotEmpty()) {
                    out.write(overlap)
                    written += overlap.size
                }
                while (!closed && !CaptureWindow.shouldEmit(written)) {
                    val read = rec.read(frame, 0, frame.size)
                    if (read == 0) continue
                    if (read < 0) throw IllegalStateException("falha ao ler o microfone ($read)")
                    val processed = conditioner.process(frame, read, gain, conditionState)
                    conditionState = processed.second
                    val audio = processed.first
                    out.write(audio, 0, audio.size)
                    written += audio.size
                    tail.append(audio, audio.size)
                }
                if (closed) {
                    runCatching { if (file.exists()) file.delete() }
                    return null
                }
                // Silêncio curto para o reconhecedor fechar a hipótese de fala.
                out.write(ByteArray(CaptureWindow.tailBytes()))
            }
        } catch (error: Exception) {
            runCatching { if (file.exists()) file.delete() }
            throw error
        }

        return AmplifiedAudioSource(
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
            file = file,
            sampleRate = CaptureWindow.SAMPLE_RATE,
            channelCount = 1,
            encoding = AudioFormat.ENCODING_PCM_16BIT
        )
    }

    /** Fecha o microfone somente quando a Glia é desligada. */
    fun stop() {
        closed = true
        recorder?.let { rec ->
            runCatching { rec.stop() }
            rec.release()
        }
        recorder = null
        agc?.let { runCatching { it.release() } }
        agc = null
        suppressor?.let { runCatching { it.release() } }
        suppressor = null
    }
}
