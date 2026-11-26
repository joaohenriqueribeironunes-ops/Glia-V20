package com.glia.assistant.stt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptDeduperTest {

    @Test
    fun `primeira transcricao e aceita`() {
        assertTrue(TranscriptDeduper().accept("que horas sao", 1_000L))
    }

    @Test
    fun `repeticao na janela seguinte e ignorada`() {
        val deduper = TranscriptDeduper(suppressMs = 1_000L)
        assertTrue(deduper.accept("que horas sao", 1_000L))
        assertFalse(deduper.accept("Que horas são?", 1_500L))
    }

    @Test
    fun `mesmo texto depois da janela volta a ser aceito`() {
        val deduper = TranscriptDeduper(suppressMs = 1_000L)
        assertTrue(deduper.accept("abra a camera", 1_000L))
        assertTrue(deduper.accept("abra a camera", 2_100L))
    }

    @Test
    fun `texto vazio nunca e aceito`() {
        val deduper = TranscriptDeduper()
        assertFalse(deduper.accept("   ", 1_000L))
    }

    @Test
    fun `reset permite repetir imediatamente`() {
        val deduper = TranscriptDeduper(suppressMs = 10_000L)
        assertTrue(deduper.accept("ligue a lanterna", 1_000L))
        deduper.reset()
        assertTrue(deduper.accept("ligue a lanterna", 1_001L))
    }
}
