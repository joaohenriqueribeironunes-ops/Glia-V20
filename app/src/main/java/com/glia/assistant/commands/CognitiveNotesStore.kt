package com.glia.assistant.commands

import android.content.Context
import java.io.File
import java.util.Base64
import java.util.UUID

data class CognitiveNote(
    val id: String,
    val createdAtMs: Long,
    val text: String
)

/**
 * Armazenamento local dos "pensamentos" do descarregamento cognitivo.
 *
 * Formato de uma linha por nota: id TAB timestamp TAB texto-em-Base64.
 * Base64 evita qualquer problema com tabs, quebras de linha e acentos.
 * A escrita e atomica (arquivo temporario + rename).
 */
class CognitiveNotesStore(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    @Synchronized
    fun add(text: String, nowMs: Long = System.currentTimeMillis()): CognitiveNote? {
        val clean = text.trim().take(MAX_NOTE_CHARS)
        if (clean.isBlank()) return null
        val note = CognitiveNote(UUID.randomUUID().toString(), nowMs, clean)
        val notes = all().toMutableList()
        notes.add(note)
        write(notes)
        return note
    }

    @Synchronized
    fun all(): List<CognitiveNote> {
        if (!file.isFile) return emptyList()
        return try {
            file.readLines()
                .asSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { parseLine(it) }
                .sortedByDescending { it.createdAtMs }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val notes = all().toMutableList()
        val removed = notes.removeAll { it.id == id }
        if (removed) write(notes)
        return removed
    }

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
    }

    fun count(): Int = all().size

    fun summary(): String {
        val notes = all()
        if (notes.isEmpty()) return "Voc\u00ea ainda n\u00e3o guardou nenhum pensamento."
        val total = if (notes.size == 1) "1 pensamento guardado" else "${notes.size} pensamentos guardados"
        val latest = notes.first().text.replace(Regex("\\s+"), " ").trim()
        val preview = if (latest.length > 240) latest.take(240) + "..." else latest
        return "Voc\u00ea tem $total. O mais recente: $preview"
    }

    private fun write(notes: List<CognitiveNote>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        val body = buildString {
            appendLine(HEADER)
            notes.sortedBy { it.createdAtMs }.forEach { note ->
                val encoded = Base64.getEncoder().encodeToString(note.text.toByteArray(Charsets.UTF_8))
                append(note.id).append('\t').append(note.createdAtMs).append('\t')
                    .append(encoded).append('\n')
            }
        }
        tmp.writeText(body, Charsets.UTF_8)
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            file.writeText(body, Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun parseLine(line: String): CognitiveNote? {
        val parts = line.split('\t')
        if (parts.size != 3) return null
        val id = parts[0].ifBlank { return null }
        val createdAt = parts[1].toLongOrNull() ?: return null
        val text = try {
            String(Base64.getDecoder().decode(parts[2]), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (text.isBlank()) return null
        return CognitiveNote(id, createdAt, text)
    }

    companion object {
        const val FILE_NAME = "cognitive-notes.tsv"
        const val MAX_NOTE_CHARS = 4000
        private const val HEADER = "# glia-notes-v1"
    }
}
