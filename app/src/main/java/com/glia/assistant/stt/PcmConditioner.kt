package com.glia.assistant.stt

import kotlin.math.abs

/**
 * Condicionamento de PCM puro, sem Android, para melhorar a precisão do STT.
 *
 * Remove DC e ruído de baixa frequência (vento, manuseio do aparelho) com um
 * filtro passa-alta de primeiro ordem e aplica o ganho com o limitador suave
 * de [MicGain]. O estado é explícito para que blocos consecutivos sejam
 * processados como um fluxo contínuo e para permitir testes determinísticos.
 */
class PcmConditioner(
    private val highPassAlpha: Float = HIGH_PASS_ALPHA
) {
    data class State(
        val previousInput: Float = 0f,
        val previousOutput: Float = 0f
    )

    companion object {
        /** ~77 Hz a 16 kHz: corta rumble sem atingir a faixa da fala. */
        const val HIGH_PASS_ALPHA = 0.97f

        fun initial(): State = State()
    }

    /**
     * Processa [length] bytes de PCM16 little-endian e devolve o bloco
     * condicionado junto com o estado atualizado.
     */
    fun process(
        pcm: ByteArray,
        length: Int,
        gain: Float,
        state: State
    ): Pair<ByteArray, State> {
        val safeLength = length.coerceIn(0, pcm.size)
        val out = ByteArray(safeLength)
        var previousInput = state.previousInput
        var previousOutput = state.previousOutput
        var i = 0
        while (i + 1 < safeLength) {
            val raw = (pcm[i].toInt() or (pcm[i + 1].toInt() shl 8)).toShort()
            val input = raw.toFloat() / 32768f
            // y[n] = a * (y[n-1] + x[n] - x[n-1]) elimina o componente DC.
            val filtered = highPassAlpha * (previousOutput + input - previousInput)
            previousInput = input
            val output = MicGain.limit(filtered * MicGain.clamp(gain))
            val encoded = (output * 32767f).toInt() and 0xFFFF
            out[i] = (encoded and 0xFF).toByte()
            out[i + 1] = ((encoded shr 8) and 0xFF).toByte()
            previousOutput = filtered
            i += 2
        }
        return out to State(previousInput, previousOutput)
    }

    /** Versão conveniente para um bloco único, usada em testes e diagnóstico. */
    fun process(pcm: ByteArray, gain: Float): ByteArray =
        process(pcm, pcm.size, gain, initial()).first

    /** Confere que o ganho não estoura o intervalo PCM16. */
    fun withinPcm16(value: Float): Boolean = abs(value) <= 1f
}
