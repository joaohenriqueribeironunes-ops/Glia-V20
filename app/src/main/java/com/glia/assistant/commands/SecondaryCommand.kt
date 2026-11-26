package com.glia.assistant.commands

/**
 * Comandos secundários ouvidos diretamente, sem palavra de ativação.
 *
 * O microfone fica ligado enquanto o tile estiver ativo; qualquer frase
 * reconhecida é normalizada e entregue ao [CommandParser]. O prefixo "Glia"
 * continua sendo ignorado por compatibilidade, mas não é necessário.
 */
object SecondaryCommand {
    private val WAKE_TOKENS = setOf("glia", "clia")
    private val GREETINGS = setOf("ola", "oi", "hey")
    private val FILLERS = setOf("por", "favor", "pode", "poderia", "me", "faca")

    /** Remove saudações, o prefixo opcional e cortesias antes do comando. */
    fun normalize(raw: String): String {
        var tokens = CommandParser.normalize(raw)
            .split(' ')
            .filter { it.isNotBlank() }
        if (tokens.isNotEmpty() && tokens.first() in GREETINGS) tokens = tokens.drop(1)
        if (tokens.isNotEmpty() && tokens.first() in WAKE_TOKENS) tokens = tokens.drop(1)
        while (tokens.isNotEmpty() && tokens.first() in FILLERS) tokens = tokens.drop(1)
        while (tokens.isNotEmpty() && tokens.last() in FILLERS) tokens = tokens.dropLast(1)
        return tokens.joinToString(" ").trim()
    }

    /** Devolve o comando já sem palavra de ativação. */
    fun parse(raw: String): VoiceCommand = CommandParser.parse(normalize(raw))
}
