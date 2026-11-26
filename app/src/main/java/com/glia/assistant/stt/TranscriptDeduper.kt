package com.glia.assistant.stt

import com.glia.assistant.commands.CommandParser

/**
 * Evita que a sobreposição entre janelas repetida o mesmo comando.
 * A comparação é pura para permitir testes determinísticos.
 */
class TranscriptDeduper(private val suppressMs: Long = CaptureWindow.WINDOW_MS) {
    private var lastText: String = ""
    private var lastAt: Long = Long.MIN_VALUE

    fun accept(text: String, nowMs: Long): Boolean {
        val normalized = CommandParser.normalize(text)
        if (normalized.isEmpty()) return false
        if (normalized == lastText && nowMs - lastAt < suppressMs) return false
        lastText = normalized
        lastAt = nowMs
        return true
    }

    fun reset() {
        lastText = ""
        lastAt = Long.MIN_VALUE
    }
}
