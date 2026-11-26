package com.glia.assistant.stt

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Contrato validavel do modelo Vosk PT-BR embutido (sem Android). */
object VoskLayout {
    const val ASSET_NAME = "vosk-model-small-pt-0.3.zip"
    const val DIR_NAME = "vosk-model-small-pt-0.3"
    const val ZIP_SHA256 = "6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7"
    const val EXPECTED_UNCOMPRESSED_BYTES = 53_552_717L
    const val EXPECTED_ENTRY_COUNT = 16

    val REQUIRED_FILES: List<String> = listOf(
        "README",
        "final.mdl",
        "disambig_tid.int",
        "phones.txt",
        "Gr.fst",
        "HCLr.fst",
        "mfcc.conf",
        "word_boundary.int",
        "ivector/final.ie",
        "ivector/final.dubm",
        "ivector/final.mat",
        "ivector/global_cmvn.stats",
        "ivector/online_cmvn.conf",
        "ivector/splice.conf"
    )

    /** Remove o prefixo de diretorio do zip e rejeita entradas inseguras. */
    fun safeRelative(entryName: String): String? {
        if (entryName.startsWith("/") || entryName.contains('\\') || entryName.contains(':')) return null
        val prefix = "$DIR_NAME/"
        if (!entryName.startsWith(prefix)) return null
        val relative = entryName.removePrefix(prefix)
        if (relative.isEmpty()) return ""
        if (relative.split('/').any { it == ".." || it == "." }) return null
        return relative
    }

    fun isValidModelDir(dir: File): Boolean {
        if (!dir.isDirectory) return false
        return REQUIRED_FILES.all { name ->
            val file = File(dir, name)
            file.isFile && file.length() > 0L
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun uncompressedBytes(zip: ZipFile): Long =
        zip.entries().toList().sumOf { if (it.isDirectory) 0L else it.size }

    /** Retorna null quando o zip e seguro e completo; caso contrario, o motivo. */
    fun validateZip(zip: ZipFile): String? {
        val names = zip.entries().toList().map { it.name }
        if (names.size != EXPECTED_ENTRY_COUNT) {
            return "quantidade de entradas inesperada: ${names.size} (esperado $EXPECTED_ENTRY_COUNT)"
        }
        val files = HashSet<String>()
        for (name in names) {
            val relative = safeRelative(name) ?: return "entrada insegura no zip: $name"
            if (relative.isEmpty()) continue
            val entry: ZipEntry = zip.getEntry(name) ?: return "entrada ausente: $name"
            if (!entry.isDirectory) files.add(relative)
        }
        val missing = REQUIRED_FILES.filter { it !in files }
        if (missing.isNotEmpty()) return "arquivos obrigatorios ausentes: ${missing.joinToString()}"
        val unknown = files.filter { it !in REQUIRED_FILES }
        if (unknown.isNotEmpty()) return "arquivos inesperados no zip: ${unknown.joinToString()}"
        return null
    }
}
