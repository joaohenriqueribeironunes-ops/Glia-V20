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
    private val file: File,
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
        private const val START_THRESHOLD = 0.015f
        private const val END_THRESHOLD = 0.006f
        private const val END_FRAMES = 25 // 500 ms de silêncio
        private const val MAX_FRAMES = 500 // 10 s por fala
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

        var agc: AutomaticGainControl? = null
        var suppressor: NoiseSuppressor? = null
        var started = false
        try {
            runCatching { AutomaticGainControl.create(recorder.audioSessionId) }
                ?.getOrNull()?.let { effect ->
                    agc = effect
                    runCatching { effect.enabled = true }
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
            var speaking = false
            var silenceFrames = 0
            var speechFrames = 0
            val deadline = SystemClock.elapsedRealtime() + timeoutMs

            FileOutputStream(file, false).use { out ->
                while (SystemClock.elapsedRealtime() < deadline) {
                    val read = recorder.read(frame, 0, frame.size)
                    if (read <= 0) continue
                    val amplified = amplifyChunk(frame, read, gain)
                    val rms = MicGain.rms(amplified, 0, amplified.size)

                    if (!speaking) {
                        preRoll.addLast(amplified)
                        if (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                        if (rms >= START_THRESHOLD) {
                            speaking = true
                            for (pending in preRoll) out.write(pending)
                            preRoll.clear()
                        }
                        continue
                    }

                    out.write(amplified)
                    speechFrames++
                    if (rms < END_THRESHOLD) silenceFrames++ else silenceFrames = 0
                    if (silenceFrames >= END_FRAMES || speechFrames >= MAX_FRAMES) break
                }
            }

            if (!speaking) {
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

    private fun amplifyChunk(frame: ByteArray, length: Int, gain: Float): ByteArray {
        val out = ByteArray(length)
        var i = 0
        while (i + 1 < length) {
            val raw = (frame[i].toInt() or (frame[i + 1].toInt() shl 8)).toShort()
            val value = MicGain.amplify(raw, gain).toInt() and 0xFFFF
            out[i] = (value and 0xFF).toByte()
            out[i + 1] = ((value shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }
}
