package com.glia.assistant.stt

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PcmConditionerTest {

    private fun pcm(values: ShortArray): ByteArray {
        val out = ByteArray(values.size * 2)
        values.forEachIndexed { index, value ->
            out[index * 2] = (value.toInt() and 0xFF).toByte()
            out[index * 2 + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun decode(bytes: ByteArray): ShortArray = ShortArray(bytes.size / 2) { index ->
        (bytes[index * 2].toInt() or (bytes[index * 2 + 1].toInt() shl 8)).toShort()
    }

    @Test
    fun `filtro passa alta remove componente continue`() {
        val conditioner = PcmConditioner()
        val block = pcm(ShortArray(200) { 1000 })
        var state = PcmConditioner.initial()
        var last = 0.toShort()
        repeat(200) {
            val result = conditioner.process(block, 2, 1f, state)
            last = decode(result.first)[0]
            state = result.second
        }
        assertTrue(abs(last.toInt()) < 50)
    }

    @Test
    fun `estado preserva continuidade entre blocos`() {
        val conditioner = PcmConditioner()
        val samples = ShortArray(32) { if (it % 2 == 0) 4000 else -3000 }
        val whole = conditioner.process(pcm(samples), 2f)

        var state = PcmConditioner.initial()
        val firstBlock = pcm(samples.copyOfRange(0, 16))
        val first = conditioner.process(firstBlock, firstBlock.size, 2f, state)
        state = first.second
        val secondBlock = pcm(samples.copyOfRange(16, 32))
        val second = conditioner.process(secondBlock, secondBlock.size, 2f, state)
        assertArrayEquals(whole, first.first + second.first)
    }

    @Test
    fun `picos amplificados nao estouram PCM16`() {
        val conditioner = PcmConditioner()
        val decoded = decode(
            conditioner.process(
                pcm(ShortArray(64) { if (it % 2 == 0) 32000 else -32000 }),
                4f
            )
        )
        assertTrue(decoded.all { it.toInt() in -32768..32767 })
        assertTrue(abs(decoded.last().toInt()) > 30000)
    }
}
