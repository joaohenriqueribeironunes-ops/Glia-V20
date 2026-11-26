package com.glia.assistant.tts

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Contrato validável do modelo neural pt-BR feminino embutido (sem Android). */
object NeuralVoiceLayout {
    const val ASSET_NAME = "glia-neural-voice.zip"
    const val DIR_NAME = "glia-neural-voice"
    const val MODEL_NAME = "pt_BR-dii-high.onnx"
    const val TOKENS_NAME = "tokens.txt"
    const val ESPEAK_DIR = "espeak-ng-data"
    const val ZIP_SHA256 = "813eb4e520f115bdb80337e4aa5b05d3d6999ec2e1e1541bf0137ca5ad2b7c38"
    const val EXPECTED_UNCOMPRESSED_BYTES = 64_254_245L
    const val EXPECTED_ENTRY_COUNT = 11

    val REQUIRED_FILES: List<String> = listOf(
        MODEL_NAME,
        "$MODEL_NAME.json",
        TOKENS_NAME,
        "$ESPEAK_DIR/intonations",
        "$ESPEAK_DIR/phondata",
        "$ESPEAK_DIR/phondata-manifest",
        "$ESPEAK_DIR/phonindex",
        "$ESPEAK_DIR/phontab",
        "$ESPEAK_DIR/pt_dict",
        "$ESPEAK_DIR/lang/roa/pt",
        "$ESPEAK_DIR/lang/roa/pt-BR"
    )

    /** Remove o prefixo do zip e rejeita entradas inseguras. */
    fun safeRelative(entryName: String): String? {
        // O zip é gerado no Windows; entra com '\\' em alguns nomes. Normalizamos
        // o separador antes de validar, mas continuamos rejeitando absolutos,
        // unidades e qualquer segmento de travessia.
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.contains(':')) return null
        val prefix = "$DIR_NAME/"
        if (!normalized.startsWith(prefix)) return null
        val relative = normalized.removePrefix(prefix)
        if (relative.isEmpty()) return ""
        if (relative.split('/').any { it == ".." || it == "." || it.isEmpty() }) return null
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

    /** Retorna null quando o zip é seguro e completo; caso contrário, o motivo. */
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
        if (missing.isNotEmpty()) return "arquivos obrigatórios ausentes: ${missing.joinToString()}"
        val unknown = files.filter { it !in REQUIRED_FILES }
        if (unknown.isNotEmpty()) return "arquivos inesperados no zip: ${unknown.joinToString()}"
        return null
    }
}
