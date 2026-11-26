package com.glia.assistant.audio

import kotlin.math.sqrt

/** RMS em Kotlin puro, sem JNI, suficiente para detectar fala no loop de escuta. */
class AudioProcessor {
    fun rms(pcm: ShortArray, length: Int): Float {
        val n = length.coerceAtMost(pcm.size)
        if (n <= 0) return 0f
        var sum = 0.0
        for (i in 0 until n) {
            val v = pcm[i] / 32768.0
            sum += v * v
        }
        return sqrt(sum / n).toFloat()
    }
}
