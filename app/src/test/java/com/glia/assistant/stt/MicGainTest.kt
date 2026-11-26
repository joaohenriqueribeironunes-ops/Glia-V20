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
    fun `ganho e linear na faixa normal da fala`() {
        assertEquals(0.5f, MicGain.limit(0.5f), 0.00001f)
        val original = 1000.toShort()
        val amplified = MicGain.amplify(original, 2f)
        assertTrue(abs(amplified.toInt() - 2000) <= 2)
    }

    @Test
    fun `limitador suave evita clipping duro nos picos`() {
        val limited = MicGain.limit(2f)
        assertTrue(limited < 1f)
        assertTrue(limited > MicGain.LIMITER_KNEE)

        val loud = MicGain.amplify(32000.toShort(), 4f)
        assertTrue(loud.toInt() in -32768..32767)
        assertTrue(abs(loud.toInt()) > 30000)
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
