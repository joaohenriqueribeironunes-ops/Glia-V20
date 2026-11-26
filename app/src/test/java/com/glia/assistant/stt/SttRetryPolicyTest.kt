package com.glia.assistant.stt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SttRetryPolicyTest {

    @Test
    fun `erro interno do cliente dispara nova tentativa sem offline`() {
        assertTrue(SttRetryPolicy.shouldRetryWithoutOffline(SttRetryPolicy.ERROR_CLIENT_CODE))
        assertTrue(SttRetryPolicy.shouldRetryWithoutOffline(2))
    }

    @Test
    fun `outros erros nao disparam nova tentativa`() {
        assertFalse(SttRetryPolicy.shouldRetryWithoutOffline(3)) // ERROR_AUDIO
        assertFalse(SttRetryPolicy.shouldRetryWithoutOffline(4)) // ERROR_NETWORK
        assertFalse(SttRetryPolicy.shouldRetryWithoutOffline(9)) // INVALIDACAO DE PERMISSAO
    }

    @Test
    fun `primeira tentativa prefere offline e segunda nao`() {
        assertTrue(SttRetryPolicy.preferOffline(0))
        assertFalse(SttRetryPolicy.preferOffline(1))
        assertTrue(SttRetryPolicy.MAX_ATTEMPTS == 2)
    }

    @Test
    fun `erros normais de escuta sao silenciosos`() {
        assertTrue(SttRetryPolicy.isSilentError(5)) // NO_MATCH
        assertTrue(SttRetryPolicy.isSilentError(6)) // SPEECH_TIMEOUT
        assertTrue(SttRetryPolicy.isSilentError(8)) // RECOGNIZER_BUSY
        assertFalse(SttRetryPolicy.isSilentError(2)) // ERROR_CLIENT
    }
}
