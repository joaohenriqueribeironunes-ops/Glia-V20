package com.glia.assistant.stt

import com.glia.assistant.commands.CommandParser

/**
 * Detector determinístico da palavra de ativação.
 *
 * A forma principal é apenas **"Glia"**. Variantes como "Olá Glia" continuam
 * aceitas para compatibilidade, mas não são necessárias. A detecção usa o
 * token exato normalizado — nunca `startsWith` — para não disparar em palavras
 * como "glioblastoma".
 */
object WakeWord {
    /** Palavra única que ativa a assistente. */
    const val PRIMARY = "glia"

    private val TOKENS = setOf("glia", "clia")
    private val AFTER_FILLERS = setOf("por", "favor", "pode", "poderia", "me", "faca")

    data class Match(val activated: Boolean, val command: String) {
        val hasCommand: Boolean get() = command.isNotBlank()
    }

    /**
     * Detecta a ativação e devolve o que foi dito depois dela.
     *
     * "Glia" → comando vazio; "Glia, que horas são?" → "que horas sao".
     * O texto devolvido já está normalizado e pode ir direto ao `CommandParser`.
     */
    fun detect(raw: String): Match {
        val tokens = CommandParser.normalize(raw)
            .split(' ')
            .filter { it.isNotBlank() }
        val index = tokens.indexOfFirst { it in TOKENS }
        if (index < 0) return Match(false, "")

        var remainder = tokens.drop(index + 1)
        while (remainder.isNotEmpty() && remainder.first() in AFTER_FILLERS) {
            remainder = remainder.drop(1)
        }
        return Match(true, remainder.joinToString(" ").trim())
    }

    fun isWakeWord(raw: String): Boolean = detect(raw).activated
}
