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
