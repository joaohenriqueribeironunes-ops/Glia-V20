package com.glia.assistant.llm

import java.time.LocalDateTime
import java.time.ZoneId

fun buildPrompt(userText: String): String {
    val safe = userText.replace("<|", "< |").replace("|>", "| >").trim()
    val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.systemDefault())
    val system = "Voce e a Glia, assistente pessoal offline em portugues. " +
        "Responda de forma curta e natural. " +
        "Data atual: ${now.dayOfMonth}/${now.monthValue}/${now.year}. " +
        "Para data, hora ou acao no dispositivo voce deve usar uma ferramenta. " +
        "Se nao usar ferramenta, responda normalmente."

    return "<|im_start|>system\n$system<|im_end|>\n" +
        "<|im_start|>user\n$safe<|im_end|>\n" +
        "<|im_start|>assistant\n thinking\n\n\n\n"
}
