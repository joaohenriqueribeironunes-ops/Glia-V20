package com.glia.assistant.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SttRetryPolicyTest {

    @Test
    fun `erro interno e erro de audio disparam nova tentativa`() {
        assertTrue(SttRetryPolicy.shouldRetry(SttRetryPolicy.ERROR_CLIENT_CODE))
        assertTrue(SttRetryPolicy.shouldRetry(SttRetryPolicy.ERROR_AUDIO_CODE))
        assertFalse(SttRetryPolicy.shouldRetry(4)) // ERROR_NETWORK
        assertFalse(SttRetryPolicy.shouldRetry(9)) // INVALIDACAO DE PERMISSAO
    }

    @Test
    fun `silencio nunca troca para o microfone ao vivo`() {
        assertFalse(SttRetryPolicy.shouldRetry(5))
        assertFalse(SttRetryPolicy.shouldRetry(6))
        // 5/6 na tentativa 0 podem repetir uma vez, ainda com PCM amplificado.
        assertTrue(SttRetryPolicy.shouldRetryAfterAmplified(5, 0))
        assertTrue(SttRetryPolicy.shouldRetryAfterAmplified(6, 0))
        // Na tentativa 1 a proxima seria a 2 (microfone ao vivo); nao permitir.
        assertFalse(SttRetryPolicy.shouldRetryAfterAmplified(5, 1))
        assertFalse(SttRetryPolicy.shouldRetryAfterAmplified(6, 1))
        assertFalse(SttRetryPolicy.shouldRetryAfterAmplified(5, 2))
    }

    @Test
    fun `erros reais de fonte ainda podem usar o microfone ao vivo`() {
        assertTrue(SttRetryPolicy.shouldRetryAfterAmplified(2, 0))
        assertTrue(SttRetryPolicy.shouldRetryAfterAmplified(2, 1))
        assertTrue(SttRetryPolicy.shouldRetryAfterAmplified(3, 1))
        assertFalse(SttRetryPolicy.shouldRetryAfterAmplified(4, 1))
    }

    @Test
    fun `compatibilidade com a politica sem offline`() {
        assertTrue(SttRetryPolicy.shouldRetryWithoutOffline(2))
        assertTrue(SttRetryPolicy.shouldRetryWithoutOffline(3))
        assertFalse(SttRetryPolicy.shouldRetryWithoutOffline(4))
    }

    @Test
    fun `tentativas alternam offline e microfone amplificado`() {
        assertTrue(SttRetryPolicy.preferOffline(0))
        assertFalse(SttRetryPolicy.preferOffline(1))
        assertFalse(SttRetryPolicy.preferOffline(2))
        assertEquals(3, SttRetryPolicy.MAX_ATTEMPTS)
    }

    @Test
    fun `duas primeiras tentativas usam fonte amplificada`() {
        assertTrue(SttRetryPolicy.useAmplifiedSource(0))
        assertTrue(SttRetryPolicy.useAmplifiedSource(1))
        assertFalse(SttRetryPolicy.useAmplifiedSource(2))
    }

    @Test
    fun `erros normais de escuta sao silenciosos`() {
        assertTrue(SttRetryPolicy.isSilentError(5)) // NO_MATCH
        assertTrue(SttRetryPolicy.isSilentError(SttRetryPolicy.SPEECH_TIMEOUT_CODE))
        assertTrue(SttRetryPolicy.isSilentError(8)) // RECOGNIZER_BUSY
        assertFalse(SttRetryPolicy.isSilentError(2)) // ERROR_CLIENT
    }
}
