package com.glia.assistant.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePlannerTest {

    @Test
    fun `primeira tentativa usa ganho do usuario e offline`() {
        val plan = CapturePlanner.plan(0, 3.5f)
        assertEquals(0, plan.attempt)
        assertEquals(3.5f, plan.gain, 0.0001f)
        assertTrue(plan.preferOffline)
        assertTrue(plan.amplifiedSource)
    }

    @Test
    fun `segunda tentativa mantem ganho sem exigir offline`() {
        val plan = CapturePlanner.plan(1, 2f)
        assertEquals(2f, plan.gain, 0.0001f)
        assertFalse(plan.preferOffline)
        assertTrue(plan.amplifiedSource)
    }

    @Test
    fun `terceira tentativa usa microfone normal com ganho padrao`() {
        val plan = CapturePlanner.plan(2, 4f)
        assertFalse(plan.amplifiedSource)
        assertFalse(plan.preferOffline)
        assertEquals(MicGain.DEFAULT, plan.gain, 0.0001f)
    }

    @Test
    fun `tentativas invalidas sao limitadas ao intervalo`() {
        assertEquals(0, CapturePlanner.plan(-1, 2f).attempt)
        assertEquals(SttRetryPolicy.MAX_ATTEMPTS - 1, CapturePlanner.plan(99, 2f).attempt)
        assertEquals(MicGain.MAX, CapturePlanner.plan(0, 99f).gain, 0.0001f)
    }
}
