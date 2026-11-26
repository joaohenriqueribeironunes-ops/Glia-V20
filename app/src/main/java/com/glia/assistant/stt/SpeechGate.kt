package com.glia.assistant.stt

/**
 * Detector de fala puro, usado pelo [AmplifiedMic].
 *
 * O limite de início se adapta ao ruído do ambiente: em uma sala silenciosa
 * uma fala fraca inicia a captura; em um ambiente ruidoso o limite sobe o
 * suficiente para não disparar com o ruído de fundo. A decisão é uma máquina
 * de estados determinística, sem Android, para cobertura total em testes JVM.
 */
class SpeechGate(
    private val startFrames: Int = DEFAULT_START_FRAMES,
    private val endFrames: Int = DEFAULT_END_FRAMES,
    private val maxSpeechFrames: Int = DEFAULT_MAX_SPEECH_FRAMES
) {
    data class State(
        val noiseFloor: Float = MIN_NOISE,
        val aboveFrames: Int = 0,
        val speaking: Boolean = false,
        val speechFrames: Int = 0,
        val silenceFrames: Int = 0
    )

    enum class Decision { WAIT, START, KEEP, END, MAX }

    companion object {
        const val FRAME_MS = 20
        const val MIN_NOISE = 0.0005f
        const val MAX_NOISE = 0.008f
        const val NOISE_MULTIPLIER = 1.8f
        const val MIN_START_RMS = 0.003f
        const val MAX_START_RMS = 0.05f
        const val MIN_END_RMS = 0.0012f
        const val END_MULTIPLIER = 1.6f
        const val DEFAULT_START_FRAMES = 2
        const val DEFAULT_END_FRAMES = 25
        const val DEFAULT_MAX_SPEECH_FRAMES = 500

        fun initial(): State = State()
    }

    /** Limiar para iniciar: acompanha o ruído, mas nunca fica abaixo do piso. */
    fun startThreshold(state: State): Float =
        (state.noiseFloor * NOISE_MULTIPLIER).coerceIn(MIN_START_RMS, MAX_START_RMS)

    /** Limiar para encerrar: sempre um pouco abaixo do limiar de início. */
    fun endThreshold(state: State): Float =
        (state.noiseFloor * END_MULTIPLIER).coerceIn(MIN_END_RMS, startThreshold(state) * 0.8f)

    /** Processa um frame (20 ms) e devolve o próximo estado e a decisão. */
    fun feed(state: State, rms: Float): Pair<State, Decision> {
        val level = if (rms.isNaN() || rms.isInfinite() || rms < 0f) 0f else rms
        if (!state.speaking) {
            val noise = adaptNoise(state.noiseFloor, level)
            val updated = state.copy(noiseFloor = noise)
            if (level > startThreshold(updated)) {
                val above = state.aboveFrames + 1
                if (above >= startFrames) {
                    return updated.copy(
                        aboveFrames = 0,
                        speaking = true,
                        speechFrames = 1,
                        silenceFrames = 0
                    ) to Decision.START
                }
                return updated.copy(aboveFrames = above) to Decision.WAIT
            }
            return updated.copy(aboveFrames = 0) to Decision.WAIT
        }

        val speechFrames = state.speechFrames + 1
        val silenceFrames = if (level < endThreshold(state)) state.silenceFrames + 1 else 0
        if (silenceFrames >= endFrames) return finished(state) to Decision.END
        if (speechFrames >= maxSpeechFrames) return finished(state) to Decision.MAX
        return state.copy(speechFrames = speechFrames, silenceFrames = silenceFrames) to Decision.KEEP
    }

    private fun finished(state: State): State = state.copy(
        aboveFrames = 0,
        speaking = false,
        speechFrames = 0,
        silenceFrames = 0
    )

    /** Ruído sobe devagar e desce rápido quando o ambiente fica mais calmo. */
    private fun adaptNoise(current: Float, level: Float): Float {
        val target = level.coerceIn(MIN_NOISE, MAX_NOISE)
        val factor = if (target < current) 0.2f else 0.02f
        return (current + (target - current) * factor).coerceIn(MIN_NOISE, MAX_NOISE)
    }
}
