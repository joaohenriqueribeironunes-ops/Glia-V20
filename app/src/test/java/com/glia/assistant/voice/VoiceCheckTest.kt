package com.glia.assistant.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCheckTest {

    @Test
    fun `sem treino e voz do dona sao aceitas`() {
        assertTrue(VoiceDecision.allow(VoiceCheck.SKIPPED, allowOnUnavailable = false))
        assertTrue(VoiceDecision.allow(VoiceCheck.ALLOW, allowOnUnavailable = false))
    }

    @Test
    fun `voz estranha e sempre rejeitada`() {
        assertFalse(VoiceDecision.allow(VoiceCheck.DENY, allowOnUnavailable = true))
    }

    @Test
    fun `falha interna pode ser contornada por politica`() {
        assertFalse(VoiceDecision.allow(VoiceCheck.UNAVAILABLE, allowOnUnavailable = false))
        assertTrue(VoiceDecision.allow(VoiceCheck.UNAVAILABLE, allowOnUnavailable = true))
    }
}
