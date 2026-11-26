package com.glia.assistant.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    @Test
    fun normalizaAcentosPontuacaoECaixa() {
        assertEquals("ola glia que horas sao", CommandParser.normalize("Olá, Glia! Que horas são?"))
    }

    @Test
    fun reconheceHoraEmVariacoes() {
        assertEquals(VoiceCommand.Time, CommandParser.parse("Que horas são?"))
        assertEquals(VoiceCommand.Time, CommandParser.parse("me diga a hora"))
        assertEquals(VoiceCommand.Time, CommandParser.parse("hora"))
    }

    @Test
    fun reconheceDataEmVariacoes() {
        assertEquals(VoiceCommand.Date, CommandParser.parse("Que dia é hoje?"))
        assertEquals(VoiceCommand.Date, CommandParser.parse("qual a data de hoje"))
        assertEquals(VoiceCommand.Date, CommandParser.parse("data"))
    }

    @Test
    fun reconheceCalendarioComPeriodo() {
        assertEquals(VoiceCommand.Calendar(1), CommandParser.parse("Tenho compromissos hoje?"))
        assertEquals(VoiceCommand.Calendar(2), CommandParser.parse("tenho compromissos amanhã"))
        assertEquals(VoiceCommand.Calendar(7), CommandParser.parse("minha agenda desta semana"))
        assertEquals(VoiceCommand.Calendar(30), CommandParser.parse("eventos do mês"))
        assertEquals(VoiceCommand.Calendar(7), CommandParser.parse("tenho alguma reunião?"))
    }

    @Test
    fun reconheceLanternaComEstado() {
        assertEquals(VoiceCommand.Flashlight(true), CommandParser.parse("ligar a lanterna"))
        assertEquals(VoiceCommand.Flashlight(false), CommandParser.parse("desligar a lanterna"))
        assertEquals(VoiceCommand.Flashlight(null), CommandParser.parse("lanterna"))
        assertEquals(VoiceCommand.Flashlight(true), CommandParser.parse("abra a lanterna"))
    }

    @Test
    fun reconheceAbrirAplicativo() {
        assertEquals(VoiceCommand.OpenApp("whatsapp"), CommandParser.parse("Abra o WhatsApp"))
        assertEquals(VoiceCommand.OpenApp("camera"), CommandParser.parse("abrir a câmera"))
        assertEquals(VoiceCommand.OpenApp("configuracoes"), CommandParser.parse("abre configurações"))
        assertEquals(VoiceCommand.OpenApp("youtube"), CommandParser.parse("inicie o YouTube"))
    }

    @Test
    fun reconheceDescarregamentoCognitivo() {
        assertEquals(
            VoiceCommand.StartCognitiveOffload,
            CommandParser.parse("Glia, quero diminuir a carga cognitiva")
        )
        assertEquals(
            VoiceCommand.StartCognitiveOffload,
            CommandParser.parse("quero fazer um descarregamento cognitivo")
        )
        assertEquals(
            VoiceCommand.StartCognitiveOffload,
            CommandParser.parse("guardar meus pensamentos")
        )
    }

    @Test
    fun reconheceLeituraDePensamentos() {
        assertEquals(VoiceCommand.ReadNotes, CommandParser.parse("quais são meus pensamentos?"))
        assertEquals(VoiceCommand.ReadNotes, CommandParser.parse("ler minhas notas"))
        assertEquals(VoiceCommand.ReadNotes, CommandParser.parse("o que eu guardei"))
    }

    @Test
    fun reconheceAjuda() {
        assertEquals(VoiceCommand.Help, CommandParser.parse("ajuda"))
        assertEquals(VoiceCommand.Help, CommandParser.parse("quais comandos você tem?"))
    }

    @Test
    fun textoVazioOuDesconhecidoNaoQuebra() {
        assertTrue(CommandParser.parse("") is VoiceCommand.Unknown)
        assertTrue(CommandParser.parse("   ") is VoiceCommand.Unknown)
        assertTrue(CommandParser.parse("conte uma piada") is VoiceCommand.Unknown)
    }

    @Test
    fun abrirSemNomeNaoViraComandoDeApp() {
        assertTrue(CommandParser.parse("abrir") is VoiceCommand.Unknown)
        assertTrue(CommandParser.parse("abra o") is VoiceCommand.Unknown)
    }
}
