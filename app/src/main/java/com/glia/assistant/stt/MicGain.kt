package com.glia.assistant.stt

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Ganho de microfone puro, sem dependência do Android.
 *
 * Abaixo de [LIMITER_KNEE] o ganho é linear: a forma de onda da fala é
 * preservada, o que melhora o reconhecimento e a impressão de voz. O `tanh`
 * só atua nos picos, evitando distorção harmônica e clipping duro.
 */
object MicGain {
    const val MIN = 1.0f
    const val MAX = 4.0f
    const val DEFAULT = 2.0f
    const val LIMITER_KNEE = 0.85f

    fun clamp(value: Float): Float = value.coerceIn(MIN, MAX)

    /** Limiter suave: linear na faixa normal e compressivo apenas nos picos. */
    fun limit(value: Float): Float {
        val magnitude = abs(value)
        if (magnitude <= LIMITER_KNEE) return value
        val excess = (magnitude - LIMITER_KNEE) / (1f - LIMITER_KNEE)
        val limited = LIMITER_KNEE + (1f - LIMITER_KNEE) * tanh(excess)
        return if (value < 0f) -limited else limited
    }

    fun amplify(sample: Short, gain: Float): Short {
        val normalized = sample.toFloat() / 32768f * clamp(gain)
        return (limit(normalized) * 32767f).toInt().toShort()
    }

    /** RMS de um bloco PCM16 little-endian; usado para detectar fala. */
    fun rms(pcm: ByteArray, offset: Int, length: Int): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        var count = 0
        var i = offset
        val end = minOf(offset + length, pcm.size)
        while (i + 1 < end) {
            val sample = (pcm[i].toInt() or (pcm[i + 1].toInt() shl 8)).toShort()
            val value = sample.toFloat() / 32768f
            sum += value * value
            count++
            i += 2
        }
        if (count == 0) return 0f
        return sqrt(sum / count).toFloat()
    }
}
