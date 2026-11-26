package com.glia.assistant.stt

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.ParcelFileDescriptor
import android.os.SystemClock
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
}

/**
 * Captura o microfone com ganho de software e envia o PCM resultante ao
 * reconhecedor do sistema via [RecognizerIntent.EXTRA_AUDIO_SOURCE][android.speech.RecognizerIntent.EXTRA_AUDIO_SOURCE].
 * Isso dá à Glia controle real sobre o alcance do microfone sem exigir um
 * modelo de reconhecimento embutido.
 */
class AmplifiedMic(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_BYTES = 640 // 20 ms em PCM16 mono
        private const val PRE_ROLL_FRAMES = 10
        private const val TAIL_SILENCE_FRAMES = 5 // 100 ms para o STT finalizar
    }

    /**
     * Grava até uma fala terminar. Retorna null quando não há fala dentro de
     * [timeoutMs], para que o loop do serviço continue com o microfone ligado.
     */
    fun capture(timeoutMs: Long, gain: Float): AmplifiedAudioSource? {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("permissão de microfone negada")
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) throw IllegalStateException("microfone indisponível neste aparelho")

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, SAMPLE_RATE / 2 * 2)
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("microfone indisponível neste aparelho")
        }

        val effectiveGain = MicGain.clamp(gain)
        // Com ganho de software o AGC nativo comprime duas vezes o sinal e
        // destrói a forma de onda; ele só é usado quando não há ganho extra.
        val useNativeAgc = effectiveGain <= MicGain.MIN
        var agc: AutomaticGainControl? = null
        var suppressor: NoiseSuppressor? = null
        var started = false
        try {
            if (useNativeAgc) {
                runCatching { AutomaticGainControl.create(recorder.audioSessionId) }
                    ?.getOrNull()?.let { effect ->
                        agc = effect
                        runCatching { effect.enabled = true }
                    }
            }
            runCatching { NoiseSuppressor.create(recorder.audioSessionId) }
                ?.getOrNull()?.let { effect ->
                    suppressor = effect
                    runCatching { effect.enabled = true }
                }

            recorder.startRecording()
            started = true

            val dir = File(context.filesDir, "stt")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "amplified.pcm")
            if (file.exists()) file.delete()
            val frame = ByteArray(FRAME_BYTES)
            val preRoll = ArrayDeque<ByteArray>()
            val conditioner = PcmConditioner()
            var conditionState = PcmConditioner.initial()
            val gate = SpeechGate()
            var state = SpeechGate.initial()
            var speechDetected = false
            val deadline = SystemClock.elapsedRealtime() + timeoutMs

            FileOutputStream(file, false).use { out ->
                while (SystemClock.elapsedRealtime() < deadline) {
                    val read = recorder.read(frame, 0, frame.size)
                    if (read == 0) continue
                    if (read < 0) {
                        throw IllegalStateException("falha ao ler o microfone ($read)")
                    }
                    val processed = conditioner.process(frame, read, effectiveGain, conditionState)
                    conditionState = processed.second
                    val amplified = processed.first
                    val rms = MicGain.rms(amplified, 0, amplified.size)
                    val (next, decision) = gate.feed(state, rms)
                    state = next

                    when (decision) {
                        SpeechGate.Decision.WAIT -> {
                            if (!state.speaking) {
                                preRoll.addLast(amplified)
                                if (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                            }
                        }
                        SpeechGate.Decision.START -> {
                            speechDetected = true
                            for (pending in preRoll) out.write(pending)
                            preRoll.clear()
                            out.write(amplified)
                        }
                        SpeechGate.Decision.KEEP -> out.write(amplified)
                        SpeechGate.Decision.END, SpeechGate.Decision.MAX -> {
                            out.write(amplified)
                            break
                        }
                    }
                }
                // Silêncio final ajuda o reconhecedor a fechar a hipótese de fala.
                if (speechDetected) out.write(ByteArray(FRAME_BYTES * TAIL_SILENCE_FRAMES))
            }

            if (!speechDetected) {
                runCatching { if (file.exists()) file.delete() }
                return null
            }

            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return AmplifiedAudioSource(
                descriptor = descriptor,
                file = file,
                sampleRate = SAMPLE_RATE,
                channelCount = 1,
                encoding = AudioFormat.ENCODING_PCM_16BIT
            )
        } finally {
            if (started) runCatching { recorder.stop() }
            recorder.release()
            agc?.let { runCatching { it.release() } }
            suppressor?.let { runCatching { it.release() } }
        }
    }

}
