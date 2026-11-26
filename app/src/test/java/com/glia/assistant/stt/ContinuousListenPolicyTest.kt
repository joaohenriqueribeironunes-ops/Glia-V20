package com.glia.assistant.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousListenPolicyTest {
    @Test
    fun `microfone reinicia sem atraso`() {
        assertEquals(0L, ContinuousListenPolicy.RESTART_DELAY_MS)
        assertTrue(ContinuousListenPolicy.LISTEN_TIMEOUT_MS > 0L)
    }

    @Test
    fun `transcricao propria e ignorada durante fala`() {
        assertTrue(ContinuousListenPolicy.ignoreWhileSpeaking(true))
        assertFalse(ContinuousListenPolicy.ignoreWhileSpeaking(false))
    }
}
