package com.glia.assistant.voice

/** Resultado puro da verificação de voz; não depende do Android. */
enum class VoiceCheck {
    /** Filtro desligado: não há treino ativo. */
    SKIPPED,

    /** Voz reconhecida como do dono. */
    ALLOW,

    /** Voz de outra pessoa. */
    DENY,

    /** Falha interna (modelo ausente/corrompido ou PCM inválido). */
    UNAVAILABLE
}

/** Política de decisão isolada para testes rigorosos. */
object VoiceDecision {
    /**
     * Quando o verificador falha, o chamador decide se a assistente continua
     * funcionando sem filtro. A Glia escolhe [allowOnUnavailable] = true para
     * não ficar muda por uma falha interna do modelo.
     */
    fun allow(check: VoiceCheck, allowOnUnavailable: Boolean): Boolean = when (check) {
        VoiceCheck.SKIPPED, VoiceCheck.ALLOW -> true
        VoiceCheck.DENY -> false
        VoiceCheck.UNAVAILABLE -> allowOnUnavailable
    }
}
