package com.glia.assistant.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureWindowTest {

    @Test
    fun `janela cobre tres segundos em pcm16 mono`() {
        assertEquals(48_000, CaptureWindow.frames(CaptureWindow.WINDOW_MS))
        assertEquals(96_000, CaptureWindow.windowBytes())
        assertEquals(16_000, CaptureWindow.overlapBytes())
    }

    @Test
    fun `sobreposicao e menor que a janela e silencio final e curto`() {
        assertTrue(CaptureWindow.overlapBytes() < CaptureWindow.windowBytes())
        assertTrue(CaptureWindow.tailBytes() > 0)
        assertTrue(CaptureWindow.tailBytes() < CaptureWindow.windowBytes() / 10)
    }

    @Test
    fun `emite somente quando a janela foi preenchida`() {
        assertFalse(CaptureWindow.shouldEmit(CaptureWindow.windowBytes() - 1))
        assertTrue(CaptureWindow.shouldEmit(CaptureWindow.windowBytes()))
        assertTrue(CaptureWindow.shouldEmit(CaptureWindow.windowBytes() + 640))
    }
}
