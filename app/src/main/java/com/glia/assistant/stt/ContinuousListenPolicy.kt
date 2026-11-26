package com.glia.assistant.stt

/**
 * Política do microfone contínuo, isolada do Android para testes.
 *
 * O tile ligado mantém o SpeechRecognizer reiniciando logo após cada fala:
 * não existe backoff entre escutas, mesmo quando o sistema encerra por silêncio.
 * A Glia também descarta transcrições capturadas enquanto ela mesma fala, para
 * não disparar comandos com a própria voz.
 */
object ContinuousListenPolicy {
    /** Limite de segurança; o próprio SpeechRecognizer costuma terminar antes. */
    const val LISTEN_TIMEOUT_MS = 30_000L

    /** Sem janela de silêncio: a próxima escuta começa imediatamente. */
    const val RESTART_DELAY_MS = 0L

    fun ignoreWhileSpeaking(isSpeaking: Boolean): Boolean = isSpeaking
}
