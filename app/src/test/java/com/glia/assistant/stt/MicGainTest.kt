package com.glia.assistant.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MicGainTest {

    @Test
    fun `ganho e limitado entre um e quatro`() {
        assertEquals(1f, MicGain.clamp(0.2f))
        assertEquals(4f, MicGain.clamp(9f))
        assertEquals(2.5f, MicGain.clamp(2.5f))
    }

    @Test
    fun `amplificacao aumenta fala fraca sem estourar PCM`() {
        val original = 1000.toShort()
        val amplified = MicGain.amplify(original, 2f)
        assertTrue(amplified > original)

        val loud = MicGain.amplify(32000.toShort(), 4f)
        assertTrue(loud.toInt() in -32768..32767)
        assertTrue(abs(loud.toInt()) <= 32767)
    }

    @Test
    fun `rms separa silencio de fala`() {
        val silence = ByteArray(64)
        val speech = ByteArray(64)
        for (i in speech.indices step 2) {
            speech[i] = 0x00
            speech[i + 1] = 0x40 // 16384
        }
        assertTrue(MicGain.rms(silence, 0, silence.size) < 0.001f)
        assertTrue(MicGain.rms(speech, 0, speech.size) > 0.4f)
    }
}
