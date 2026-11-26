package com.glia.assistant.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechGateTest {

    private fun run(gate: SpeechGate, state: SpeechGate.State, rms: Float, frames: Int): Pair<SpeechGate.State, SpeechGate.Decision> {
        var current = state
        var decision = SpeechGate.Decision.WAIT
        repeat(frames) { decision = gate.feed(current, rms).let { current = it.first; it.second } }
        return current to decision
    }

    @Test
    fun `silencio nao inicia captura e mantem piso de ruido`() {
        val gate = SpeechGate()
        val (state, decision) = run(gate, SpeechGate.initial(), 0f, 50)
        assertEquals(SpeechGate.Decision.WAIT, decision)
        assertFalse(state.speaking)
        assertEquals(SpeechGate.MIN_NOISE, state.noiseFloor, 0.00001f)
    }

    @Test
    fun `fala fraca inicia apos dois frames`() {
        val gate = SpeechGate()
        val first = gate.feed(SpeechGate.initial(), 0.008f)
        assertEquals(SpeechGate.Decision.WAIT, first.second)
        val second = gate.feed(first.first, 0.008f)
        assertEquals(SpeechGate.Decision.START, second.second)
        assertTrue(second.first.speaking)
    }

    @Test
    fun `ruido abaixo do limiar eleva o limiar de inicio`() {
        val gate = SpeechGate()
        val initialThreshold = gate.startThreshold(SpeechGate.initial())
        val (state, decision) = run(gate, SpeechGate.initial(), 0.0025f, 200)
        assertEquals(SpeechGate.Decision.WAIT, decision)
        assertTrue(gate.startThreshold(state) > initialThreshold)
    }

    @Test
    fun `silencio encerra apos vinte frames`() {
        val gate = SpeechGate(endFrames = 20)
        var state = SpeechGate.initial()
        repeat(2) { state = gate.feed(state, 0.02f).first }
        assertTrue(state.speaking)
        val end = run(gate, state, 0f, 20)
        assertEquals(SpeechGate.Decision.END, end.second)
        assertFalse(end.first.speaking)
    }

    @Test
    fun `captura termina no limite maximo de fala`() {
        val gate = SpeechGate(maxSpeechFrames = 5)
        var state = SpeechGate.initial()
        repeat(2) { state = gate.feed(state, 0.02f).first }
        val last = run(gate, state, 0.02f, 4)
        assertEquals(SpeechGate.Decision.MAX, last.second)
    }

    @Test
    fun `valores invalidos de rms sao tratados como silencio`() {
        val gate = SpeechGate()
        val (state, decision) = run(gate, SpeechGate.initial(), Float.NaN, 10)
        assertEquals(SpeechGate.Decision.WAIT, decision)
        assertFalse(state.speaking)
    }
}
