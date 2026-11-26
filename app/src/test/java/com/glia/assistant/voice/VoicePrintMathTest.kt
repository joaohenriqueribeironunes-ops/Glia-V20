package com.glia.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePrintMathTest {

    @Test
    fun `rigor e limitado entre 0_35 e 0_75`() {
        assertEquals(VoicePrintMath.MIN_THRESHOLD, VoicePrintMath.clampThreshold(0.1f))
        assertEquals(VoicePrintMath.MAX_THRESHOLD, VoicePrintMath.clampThreshold(2f))
        assertEquals(0.55f, VoicePrintMath.clampThreshold(0.55f))
    }

    @Test
    fun `media das amostras preserva a mesma voz`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        val average = VoicePrintMath.average(listOf(a, b))
        assertTrue(average != null)
        assertEquals(1f, average!![0], 0.0001f)
        assertEquals(0f, average[1], 0.0001f)
    }

    @Test
    fun `media rejeita amostras invalidas`() {
        assertTrue(VoicePrintMath.average(emptyList()) == null)
        assertTrue(
            VoicePrintMath.average(
                listOf(floatArrayOf(1f, 0f), floatArrayOf(1f))
            ) == null
        )
        assertTrue(
            VoicePrintMath.average(
                listOf(floatArrayOf(Float.NaN, 0f))
            ) == null
        )
    }

    @Test
    fun `cosseno separa voz igual de voz diferente`() {
        val owner = floatArrayOf(1f, 0f)
        val same = floatArrayOf(2f, 0f)
        val other = floatArrayOf(0f, 1f)
        assertEquals(1f, VoicePrintMath.cosine(owner, same), 0.0001f)
        assertEquals(0f, VoicePrintMath.cosine(owner, other), 0.0001f)
        assertEquals(0f, VoicePrintMath.cosine(owner, floatArrayOf()), 0.0001f)
    }

    @Test
    fun `somente a voz treinada passa do rigor`() {
        val reference = floatArrayOf(1f, 0f)
        assertTrue(
            VoicePrintMath.isOwner(reference, floatArrayOf(0.9f, 0.1f), 0.55f)
        )
        assertFalse(
            VoicePrintMath.isOwner(reference, floatArrayOf(0.2f, 0.9f), 0.55f)
        )
        assertFalse(VoicePrintMath.isOwner(null, floatArrayOf(1f, 0f), 0.55f))
    }

    @Test
    fun `serializacao das amostras e reversivel e validada`() {
        val samples = listOf(floatArrayOf(0.1f, -0.2f), floatArrayOf(0.3f, 0.4f))
        val bytes = VoicePrintMath.encode(samples)
        val decoded = VoicePrintMath.decode(bytes)
        assertEquals(2, decoded.size)
        assertEquals(samples[0][1], decoded[0][1], 0.0001f)
        assertEquals(samples[1][0], decoded[1][0], 0.0001f)

        assertTrue(VoicePrintMath.decode(bytes.copyOf(6)).isEmpty())
        assertTrue(VoicePrintMath.decode(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)).isEmpty())
        val corrupt = bytes.copyOf()
        corrupt[12] = 0x00 // NaN em little-endian: 00 00 C0 7F
        corrupt[13] = 0x00
        corrupt[14] = 0xC0.toByte()
        corrupt[15] = 0x7F
        assertTrue(VoicePrintMath.decode(corrupt).isEmpty())
    }
}
