package com.glia.assistant.stt

/**
 * Política pura para recuperar erros do SpeechRecognizer sem depender do Android.
 *
 * ERROR_CLIENT (2) é o "erro interno do reconhecedor". No HyperOS/MIUI ele
 * aparece quando EXTRA_PREFER_OFFLINE é solicitado sem modelo offline, quando o
 * reconhecedor fica inválido após cancelamento ou quando o fabricante não aceita
 * EXTRA_AUDIO_SOURCE. ERROR_AUDIO (3) tem o mesmo efeito.
 *
 * Tentativas:
 * 0 = microfone amplificado + offline
 * 1 = microfone amplificado + motor padrão
 * 2 = microfone do sistema + motor padrão
 */
object SttRetryPolicy {
    const val ERROR_CLIENT_CODE = 2
    const val ERROR_AUDIO_CODE = 3
    const val SPEECH_TIMEOUT_CODE = 6

    fun shouldRetry(errorCode: Int): Boolean =
        errorCode == ERROR_CLIENT_CODE || errorCode == ERROR_AUDIO_CODE

    /** Mantido para compatibilidade com a correção da V23. */
    fun shouldRetryWithoutOffline(errorCode: Int): Boolean = shouldRetry(errorCode)

    fun preferOffline(attempt: Int): Boolean = attempt == 0

    fun useAmplifiedSource(attempt: Int): Boolean = attempt < 2

    const val MAX_ATTEMPTS = 3

    /**
     * Erros que são normais no ciclo de escuta contínua e não devem aparecer
     * como "último erro" na interface.
     */
    fun isSilentError(errorCode: Int): Boolean = errorCode in setOf(
        5, // ERROR_NO_MATCH
        6, // ERROR_SPEECH_TIMEOUT
        8  // ERROR_RECOGNIZER_BUSY
    )
}
