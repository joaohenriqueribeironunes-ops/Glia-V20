package com.glia.assistant.stt

/**
 * Janelas fixas de áudio para escuta contínua.
 *
 * Diferente de um detector de fala, toda amostra capturada entra em alguma
 * janela: o microfone nunca é fechado enquanto o tile estiver ativo. As janelas
 * se sobrepõem para que uma fala cortada na borda não seja perdida.
 */
object CaptureWindow {
    const val SAMPLE_RATE = 16_000
    const val WINDOW_MS = 3_000L
    const val OVERLAP_MS = 500L
    const val TAIL_SILENCE_MS = 100L

    fun frames(ms: Long): Int = (SAMPLE_RATE * ms / 1000L).toInt()

    fun bytes(ms: Long): Int = frames(ms) * 2

    fun windowBytes(): Int = bytes(WINDOW_MS)

    fun overlapBytes(): Int = bytes(OVERLAP_MS)

    fun tailBytes(): Int = bytes(TAIL_SILENCE_MS)

    fun shouldEmit(writtenBytes: Int): Boolean = writtenBytes >= windowBytes()
}
