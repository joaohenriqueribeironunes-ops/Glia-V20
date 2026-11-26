package com.glia.assistant.commands

import java.util.Locale

data class VoiceCandidate(
    val name: String,
    val localeTag: String,
    val networkRequired: Boolean,
    val quality: Int
)

/**
 * Escolhe a voz mais adequada entre as instaladas no motor TTS do aparelho.
 * Prioriza pt-BR local, sem rede, de maior qualidade e com indicio feminino.
 */
object VoiceRanking {
    fun pick(candidates: List<VoiceCandidate>): VoiceCandidate? {
        if (candidates.isEmpty()) return null
        val normalized = candidates.map { it.copy(localeTag = it.localeTag.replace('_', '-').lowercase(Locale.ROOT)) }
        val ptBr = normalized.filter { it.localeTag.startsWith("pt-br") }
        val pool = if (ptBr.isNotEmpty()) ptBr else normalized.filter { it.localeTag.startsWith("pt") }
        if (pool.isEmpty()) return null
        return pool.sortedWith(
            compareByDescending<VoiceCandidate> { femaleScore(it.name) }
                .thenBy { it.networkRequired }
                .thenByDescending { it.quality }
                .thenBy { it.name }
        ).first()
    }

    fun femaleScore(name: String): Int {
        val n = name.lowercase(Locale.ROOT)
        return when {
            n.contains("female") || n.contains("fem-") || n.contains("ptd") -> 3
            n.contains("male") || n.contains("ptb") -> 1
            else -> 2
        }
    }
}
