package com.glia.assistant.stt

/**
 * Política pura para recuperar erros do SpeechRecognizer sem depender do Android.
 *
 * ERROR_CLIENT (2) é o "erro interno do reconhecedor". No HyperOS/MIUI ele
 * aparece quando EXTRA_PREFER_OFFLINE é solicitado e o motor de voz do sistema
 * não oferece reconhecimento offline, ou quando o reconhecedor fica em estado
 * inválido após um cancelamento. A correção é recriar o reconhecedor e tentar
 * uma vez sem a exigência de offline.
 */
object SttRetryPolicy {
    const val ERROR_CLIENT_CODE = 2
    const val SPEECH_TIMEOUT_CODE = 6

    /** Código de erro do Android que justifica uma segunda tentativa. */
    fun shouldRetryWithoutOffline(errorCode: Int): Boolean = errorCode == ERROR_CLIENT_CODE

    /** Primeira tentativa prefere offline; a segunda aceita o motor padrão. */
    fun preferOffline(attempt: Int): Boolean = attempt == 0

    /** Número máximo de tentativas por escuta. */
    const val MAX_ATTEMPTS = 2

    /**
     * Erros que são normais no ciclo de wake word e não devem aparecer como
     * "último erro" na interface.
     */
    fun isSilentError(errorCode: Int): Boolean = errorCode in setOf(
        5, // ERROR_NO_MATCH
        6, // ERROR_SPEECH_TIMEOUT
        8  // ERROR_RECOGNIZER_BUSY
    )
}
