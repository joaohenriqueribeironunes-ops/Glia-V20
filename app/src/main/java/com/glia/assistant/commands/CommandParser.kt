package com.glia.assistant.commands

import java.text.Normalizer
import java.util.Locale

/** Comandos de voz reconhecidos localmente, sem LLM. */
sealed class VoiceCommand {
    data object Time : VoiceCommand()
    data object Date : VoiceCommand()
    data class Calendar(val daysAhead: Int) : VoiceCommand()
    /** null = alternar (toggle). */
    data class Flashlight(val turnOn: Boolean?) : VoiceCommand()
    data class OpenApp(val query: String) : VoiceCommand()
    data object StartCognitiveOffload : VoiceCommand()
    data object ReadNotes : VoiceCommand()
    data object Help : VoiceCommand()
    data class Unknown(val text: String) : VoiceCommand()
}

/**
 * Parser deterministico de comandos em portugues.
 *
 * Normaliza acentos, pontuacao e caixa antes de comparar. Nao depende de Android,
 * por isso e coberto por testes JVM puros.
 */
object CommandParser {
    private val OPEN_APP = Regex(
        "^(abra|abrir|abre|inicie|iniciar|execute|executar)\\s+(?:(?:o|a|os|as)\\s+)?(.+)$"
    )
    private val ACCENTS = Regex("\\p{M}+")
    private val NON_WORD = Regex("[^a-z0-9 ]")
    private val SPACES = Regex("\\s+")

    private val CALENDAR_WORDS = listOf(
        "compromisso", "compromissos", "agenda", "calendario", "evento", "eventos",
        "reuniao", "reunioes", "consulta", "consultas"
    )
    private val ARTICLES = setOf("o", "a", "os", "as")

    fun normalize(raw: String): String {
        val lower = raw.lowercase(Locale.ROOT)
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val noAccents = ACCENTS.replace(decomposed, "")
        return SPACES.replace(NON_WORD.replace(noAccents, " "), " ").trim()
    }

    fun parse(raw: String): VoiceCommand {
        val text = normalize(raw)
        if (text.isBlank()) return VoiceCommand.Unknown(raw)

        if (isCognitiveOffload(text)) return VoiceCommand.StartCognitiveOffload
        if (isReadNotes(text)) return VoiceCommand.ReadNotes

        val openApp = OPEN_APP.matchEntire(text)
        if (openApp != null) {
            val query = openApp.groupValues[2].trim()
            return when {
                query.isBlank() || query in ARTICLES -> VoiceCommand.Unknown(raw)
                query.contains("lanterna") -> VoiceCommand.Flashlight(flashlightState(text) ?: true)
                else -> VoiceCommand.OpenApp(query)
            }
        }

        if (text.contains("lanterna")) return VoiceCommand.Flashlight(flashlightState(text))
        if (isCalendar(text)) return VoiceCommand.Calendar(calendarDays(text))
        if (isTime(text)) return VoiceCommand.Time
        if (isDate(text)) return VoiceCommand.Date
        if (isHelp(text)) return VoiceCommand.Help
        return VoiceCommand.Unknown(raw)
    }

    private fun isCognitiveOffload(text: String): Boolean {
        if (text.contains("carga cognitiva")) return true
        if (text.contains("descarregamento cognitivo")) return true
        if (text.contains("descarregar a mente")) return true
        if (text.contains("guardar meus pensamentos")) return true
        if (text.contains("anotar pensamento")) return true
        if (text.contains("tirar da cabeca")) return true
        if (text.contains("quero") && text.contains("pensamento")) return true
        return false
    }

    private fun isReadNotes(text: String): Boolean {
        if (text.contains("meus pensamentos") || text.contains("minhas notas")) return true
        if (text.contains("ler notas") || text.contains("ler meus pensamentos")) return true
        if (text.contains("o que eu guardei") || text.contains("meus registros")) return true
        return false
    }

    private fun isCalendar(text: String): Boolean = CALENDAR_WORDS.any { word ->
        Regex("\\b$word\\b").containsMatchIn(text)
    }

    private fun calendarDays(text: String): Int = when {
        text.contains("hoje") -> 1
        text.contains("amanha") -> 2
        text.contains("semana") -> 7
        text.contains("mes") -> 30
        else -> 7
    }

    private fun flashlightState(text: String): Boolean? = when {
        text.contains("desligar") || text.contains("desliga") ||
            text.contains("apagar") || text.contains("apaga") || text.contains("desligue") -> false
        text.contains("ligar") || text.contains("liga") ||
            text.contains("acender") || text.contains("acende") || text.contains("ligue") -> true
        else -> null
    }

    private fun isTime(text: String): Boolean {
        if (Regex("\\b(que horas|horas sao|qual a hora|que hora|hora agora|me diga a hora|diga a hora)\\b")
                .containsMatchIn(text)
        ) return true
        return text == "hora" || text == "horas"
    }

    private fun isDate(text: String): Boolean {
        if (Regex("\\b(que dia|qual a data|que data|data de hoje|dia de hoje|hoje e que dia|me diga a data)\\b")
                .containsMatchIn(text)
        ) return true
        return text == "data" || text == "dia"
    }

    private fun isHelp(text: String): Boolean =
        text.contains("ajuda") || text.contains("o que voce faz") ||
            text.contains("quais comandos") || text.contains("comandos")
}
