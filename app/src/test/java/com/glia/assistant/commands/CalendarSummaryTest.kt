package com.glia.assistant.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CalendarSummaryTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-11-26T12:00:00Z").toEpochMilli()

    private fun at(hour: Int, minute: Int): Long =
        Instant.parse("2026-11-26T%02d:%02d:00Z".format(hour, minute)).toEpochMilli()

    @Test
    fun semEventosFalaQueNaoHaCompromissos() {
        val text = CalendarSummary.format(emptyList(), 1, now, zone)
        assertEquals("Você não tem compromissos hoje.", text)
    }

    @Test
    fun formataHoraCheiaEMeia() {
        assertEquals("14h", CalendarSummary.speakTime(at(14, 0), zone))
        assertEquals("14h30", CalendarSummary.speakTime(at(14, 30), zone))
        assertEquals("9h05", CalendarSummary.speakTime(at(9, 5), zone))
    }

    @Test
    fun listaAteTresCompromissosComHoraETitulo() {
        val events = listOf(
            CalendarEvent("Reunião", at(14, 30), false),
            CalendarEvent("Dentista", at(16, 0), false)
        )
        val text = CalendarSummary.format(events, 1, now, zone)
        assertTrue(text.contains("2 compromissos hoje"))
        assertTrue(text.contains("às 14h30, Reunião"))
        assertTrue(text.contains("às 16h, Dentista"))
    }

    @Test
    fun eventoDeDiaInteiroNaoMostraHora() {
        val text = CalendarSummary.format(
            listOf(CalendarEvent("Aniversário", at(0, 0), true)), 1, now, zone
        )
        assertTrue(text.contains("o dia todo, Aniversário"))
        assertFalse(text.contains("às 0h"))
    }

    @Test
    fun ignoraEventosPassados() {
        val past = at(9, 0)
        val future = at(18, 0)
        val text = CalendarSummary.format(
            listOf(CalendarEvent("Passado", past, false), CalendarEvent("Futuro", future, false)),
            1, now, zone
        )
        assertFalse(text.contains("Passado"))
        assertTrue(text.contains("Futuro"))
        assertTrue(text.contains("1 compromisso hoje"))
    }

    @Test
    fun maisDeTresEventosIndicaQueMostraOsPrimeiros() {
        val events = (13..17).map { hour -> CalendarEvent("Evento $hour", at(hour, 0), false) }
        val text = CalendarSummary.format(events, 7, now, zone)
        assertTrue(text.contains("5 compromissos nos próximos 7 dias"))
        assertTrue(text.contains("Os primeiros são"))
        assertTrue(text.contains("Evento 13"))
        assertFalse(text.contains("Evento 16"))
    }
}
