package com.glia.assistant.stt

/**
 * Plano puro de cada tentativa de escuta.
 *
 * Centraliza a decisão que antes estava espalhada no serviço: qual ganho
 * usar, se o PCM vai para o reconhecedor e se o modo offline é preferido.
 */
object CapturePlanner {
    data class Plan(
        val attempt: Int,
        val preferOffline: Boolean,
        val amplifiedSource: Boolean,
        val gain: Float
    )

    fun plan(attempt: Int, userGain: Float): Plan {
        val safeAttempt = attempt.coerceIn(0, SttRetryPolicy.MAX_ATTEMPTS - 1)
        val amplified = SttRetryPolicy.useAmplifiedSource(safeAttempt)
        return Plan(
            attempt = safeAttempt,
            preferOffline = SttRetryPolicy.preferOffline(safeAttempt),
            amplifiedSource = amplified,
            gain = if (amplified) MicGain.clamp(userGain) else MicGain.DEFAULT
        )
    }
}
