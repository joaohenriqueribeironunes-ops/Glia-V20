package com.glia.assistant.commands

import java.text.Normalizer
import java.util.Locale

data class AppEntry(val label: String, val packageName: String)

/** Correlaciona o nome falado com os aplicativos instalados. */
object AppMatcher {
    private val ACCENTS = Regex("\\p{M}+")
    private val NON_WORD = Regex("[^a-z0-9 ]")
    private val SPACES = Regex("\\s+")

    fun normalize(raw: String): String {
        val lower = raw.lowercase(Locale.ROOT)
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val noAccents = ACCENTS.replace(decomposed, "")
        return SPACES.replace(NON_WORD.replace(noAccents, " "), " ").trim()
    }

    fun score(query: String, label: String): Int {
        val q = normalize(query)
        val l = normalize(label)
        if (q.isBlank() || l.isBlank()) return 0
        return when {
            l == q -> 100
            l.startsWith(q) -> 80
            q.startsWith(l) -> 70
            l.contains(q) -> 60
            q.contains(l) -> 50
            else -> 0
        }
    }

    fun bestMatch(query: String, apps: List<AppEntry>): AppEntry? {
        var best: AppEntry? = null
        var bestScore = 0
        for (app in apps) {
            val score = score(query, app.label)
            if (score > bestScore ||
                (score == bestScore && score > 0 && app.label.length < (best?.label?.length ?: Int.MAX_VALUE))
            ) {
                best = app
                bestScore = score
            }
        }
        return best
    }
}
