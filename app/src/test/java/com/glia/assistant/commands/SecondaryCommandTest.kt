package com.glia.assistant.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondaryCommandTest {

    @Test
    fun `comandos funcionam sem palavra de ativacao`() {
        assertEquals(VoiceCommand.Time, SecondaryCommand.parse("que horas são?"))
        assertEquals(VoiceCommand.Date, SecondaryCommand.parse("que dia é hoje?"))
        assertEquals(VoiceCommand.Flashlight(true), SecondaryCommand.parse("ligar a lanterna"))
        assertEquals(VoiceCommand.OpenApp("camera"), SecondaryCommand.parse("abra a câmera"))
    }

    @Test
    fun `prefixo opcional Glia e ignorado`() {
        assertEquals("que horas sao", SecondaryCommand.normalize("Glia, que horas são?"))
        assertEquals(VoiceCommand.Time, SecondaryCommand.parse("Olá Glia, que horas são?"))
        assertEquals(
            VoiceCommand.OpenApp("camera"),
            SecondaryCommand.parse("Glia, abra a câmera, por favor")
        )
    }

    @Test
    fun `apenas Glia nao dispara comando`() {
        assertTrue(SecondaryCommand.parse("Glia") is VoiceCommand.Unknown)
        assertTrue(SecondaryCommand.parse("Olá Glia") is VoiceCommand.Unknown)
        assertEquals("", SecondaryCommand.normalize("Glia"))
    }

    @Test
    fun `pensamentos e notas funcionam sem Glia`() {
        assertEquals(
            VoiceCommand.StartCognitiveOffload,
            SecondaryCommand.parse("quero diminuir a carga cognitiva")
        )
        assertEquals(VoiceCommand.ReadNotes, SecondaryCommand.parse("quais são meus pensamentos?"))
    }

    @Test
    fun `saudacoes e cortesias sozinhas nao viram comando`() {
        assertTrue(SecondaryCommand.parse("olá") is VoiceCommand.Unknown)
        assertTrue(SecondaryCommand.parse("por favor") is VoiceCommand.Unknown)
        assertTrue(SecondaryCommand.parse("") is VoiceCommand.Unknown)
    }

    @Test
    fun `fala comum nao aciona a assistente`() {
        assertTrue(SecondaryCommand.parse("conte uma piada") is VoiceCommand.Unknown)
        assertTrue(SecondaryCommand.parse("como vai você?") is VoiceCommand.Unknown)
        assertTrue(SecondaryCommand.parse("glioblastoma") is VoiceCommand.Unknown)
    }
}
