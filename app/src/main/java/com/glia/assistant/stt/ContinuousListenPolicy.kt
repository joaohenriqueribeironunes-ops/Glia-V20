package com.glia.assistant.stt

/**
 * Política do microfone contínuo, isolada do Android para testes.
 *
 * O tile ligado mantém o microfone aberto e o SpeechRecognizer consumindo
 * janelas contínuas: não existe backoff nem detector de fala que impeça a
 * captura. A Glia descarta transcrições capturadas enquanto ela mesma fala,
 * para não disparar comandos com a própria voz.
 */
object ContinuousListenPolicy {
    /** Limite de segurança por janela; o sistema costuma terminar antes. */
    const val LISTEN_TIMEOUT_MS = 10_000L

    /** Sem janela de silêncio: a próxima escuta começa imediatamente. */
    const val RESTART_DELAY_MS = 0L

    fun ignoreWhileSpeaking(isSpeaking: Boolean): Boolean = isSpeaking
}
