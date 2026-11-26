package com.glia.assistant.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingTailTest {

    @Test
    fun `acumula ate a capacidade sem perder amostras`() {
        val tail = RollingTail(capacity = 10, keepOnRotate = 6)
        val chunk = byteArrayOf(1, 2, 3, 4)
        tail.append(chunk, chunk.size)
        assertEquals(4, tail.length)
        assertTrue(tail.snapshot().contentEquals(chunk))
    }

    @Test
    fun `apos rotacionar mantem os ultimos bytes`() {
        val tail = RollingTail(capacity = 10, keepOnRotate = 6)
        val first = ByteArray(10) { (it + 1).toByte() }
        tail.append(first, first.size)
        assertEquals(6, tail.length)
        assertEquals(5, tail.snapshot()[0].toInt())

        val next = byteArrayOf(11, 12, 13, 14)
        tail.append(next, next.size)
        assertEquals(6, tail.length)
        val snapshot = tail.snapshot()
        assertEquals(9, snapshot[0].toInt())
        assertEquals(10, snapshot[1].toInt())
        assertEquals(11, snapshot[2].toInt())
        assertEquals(14, snapshot[5].toInt())
    }

    @Test
    fun `sobreposicao de audio tem 500 ms`() {
        val tail = RollingTail(
            capacity = CaptureWindow.overlapBytes() * 2,
            keepOnRotate = CaptureWindow.overlapBytes()
        )
        val block = ByteArray(CaptureWindow.overlapBytes() * 2)
        repeat(4) { tail.append(block, block.size) }
        assertEquals(CaptureWindow.overlapBytes(), tail.snapshot().size)
    }
}
