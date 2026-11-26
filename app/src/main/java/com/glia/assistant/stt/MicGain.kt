package com.glia.assistant.stt

import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Ganho de microfone puro, sem dependência do Android.
 *
 * O limite é suave (tanh) para que uma fala próxima não estoure o PCM em
 * 32767; isso amplifica falas distantes sem introduzir cortes duros.
 */
object MicGain {
    const val MIN = 1.0f
    const val MAX = 4.0f
    const val DEFAULT = 2.0f

    fun clamp(value: Float): Float = value.coerceIn(MIN, MAX)

    fun amplify(sample: Short, gain: Float): Short {
        val normalized = sample.toFloat() / 32768f * clamp(gain)
        return (tanh(normalized) * 32767f).toInt().toShort()
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
