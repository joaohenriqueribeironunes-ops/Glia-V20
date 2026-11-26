package com.glia.assistant.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceRankingTest {
    @Test
    fun preferePortuguesBrasilLocalFeminino() {
        val voices = listOf(
            VoiceCandidate("pt-br-x-ptb-local", "pt-BR", false, 400),
            VoiceCandidate("pt-br-x-ptd-local", "pt_BR", false, 300),
            VoiceCandidate("en-us-x-sfg-local", "en-US", false, 500)
        )
        assertEquals("pt-br-x-ptd-local", VoiceRanking.pick(voices)?.name)
    }

    @Test
    fun vozLocalVenceVozDeRede() {
        val voices = listOf(
            VoiceCandidate("pt-br-network", "pt-BR", true, 500),
            VoiceCandidate("pt-br-local", "pt-BR", false, 200)
        )
        assertEquals("pt-br-local", VoiceRanking.pick(voices)?.name)
    }

    @Test
    fun usaPortuguesGenericoQuandoNaoHaPtBr() {
        val voices = listOf(
            VoiceCandidate("pt-pt-local", "pt-PT", false, 300),
            VoiceCandidate("en-us-local", "en-US", false, 500)
        )
        assertEquals("pt-pt-local", VoiceRanking.pick(voices)?.name)
    }

    @Test
    fun semVozPortuguesaRetornaNull() {
        val voices = listOf(VoiceCandidate("en-us-local", "en-US", false, 500))
        assertNull(VoiceRanking.pick(voices))
        assertNull(VoiceRanking.pick(emptyList()))
    }

    @Test
    fun desempatePorQualidadeENome() {
        val voices = listOf(
            VoiceCandidate("pt-br-b", "pt-BR", false, 300),
            VoiceCandidate("pt-br-a", "pt-BR", false, 300)
        )
        assertEquals("pt-br-a", VoiceRanking.pick(voices)?.name)
    }

    @Test
    fun indiciosFemininosSaoPontuados() {
        assertEquals(3, VoiceRanking.femaleScore("pt-br-x-ptd-local"))
        assertEquals(3, VoiceRanking.femaleScore("voice-female-1"))
        assertEquals(1, VoiceRanking.femaleScore("pt-br-x-ptb-local"))
        assertEquals(1, VoiceRanking.femaleScore("voice-male-1"))
        assertEquals(2, VoiceRanking.femaleScore("pt-br-neutral"))
    }
}
