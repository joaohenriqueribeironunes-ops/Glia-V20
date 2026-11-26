package com.glia.assistant.stt

import com.glia.assistant.commands.CommandParser
import com.glia.assistant.commands.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordTest {

    @Test
    fun `glia sozinho ativa e nao gera comando`() {
        val match = WakeWord.detect("Glia")
        assertTrue(match.activated)
        assertEquals("", match.command)
        assertFalse(match.hasCommand)
    }

    @Test
    fun `caixa acento e pontuacao nao impedem a ativacao`() {
        assertTrue(WakeWord.isWakeWord("GLIA!"))
        assertTrue(WakeWord.isWakeWord("glía"))
        assertTrue(WakeWord.isWakeWord("Glia,"))
    }

    @Test
    fun `variantes antigas continuam aceitas`() {
        assertTrue(WakeWord.isWakeWord("Olá Glia"))
        assertTrue(WakeWord.isWakeWord("Olá, Glia!"))
        assertTrue(WakeWord.isWakeWord("hey glia"))
        assertTrue(WakeWord.isWakeWord("Clia"))
    }

    @Test
    fun `comando na mesma frase e aproveitado`() {
        val match = WakeWord.detect("Glia, que horas são?")
        assertTrue(match.activated)
        assertEquals("que horas sao", match.command)
        assertTrue(CommandParser.parse(match.command) is VoiceCommand.Time)
    }

    @Test
    fun `comandos de abertura e lanterna preservam o verbo`() {
        assertEquals("abra a camera", WakeWord.detect("Glia, abra a câmera").command)
        assertEquals("ligue a lanterna", WakeWord.detect("Glia, por favor, ligue a lanterna").command)
    }

    @Test
    fun `prefixos e palavras parecidas nao disparam`() {
        assertFalse(WakeWord.isWakeWord("glioblastoma"))
        assertFalse(WakeWord.isWakeWord("glial"))
        assertFalse(WakeWord.isWakeWord("olia"))
        assertFalse(WakeWord.isWakeWord(""))
    }
}
