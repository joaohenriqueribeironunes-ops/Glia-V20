package com.glia.assistant.llm

import java.io.File
import java.io.FileInputStream

/** Diretorios usados na busca, injetados para permitir testes sem Android. */
data class ModelDirectories(
    val savedPath: String?,
    val privateDir: File,
    val appDownloadDir: File?,
    val publicDirs: List<File>
)

data class DiscoveryResult(
    val file: File?,
    val note: String?,
    val copiedToPrivate: Boolean = false
)

/**
 * Descoberta pura do Ternary Bonsai 2 4B (sem Context, sem android.jar).
 * Usa apenas java.io para poder ser submetida a testes JVM rigorosos.
 */
object ModelDiscovery {
    const val USER_MODEL_FILE_NAME = "user-model.gguf"
    const val RECOMMENDED_FILE_NAME = "Ternary-Bonsai-2-4B-Q2_0.gguf"
    const val EXPECTED_MODEL_SIZE = 1_212_103_520L
    const val MAX_MODEL_BYTES = 3_000_000_000L
    const val MIN_MODEL_BYTES = 1_024L
    const val PART_SUFFIX = ".part"

    fun privateModelFile(privateDir: File): File = File(privateDir, USER_MODEL_FILE_NAME)

    fun isGgufMagic(bytes: ByteArray, offset: Int = 0): Boolean =
        bytes.size >= offset + 4 &&
            bytes[offset] == 'G'.code.toByte() &&
            bytes[offset + 1] == 'G'.code.toByte() &&
            bytes[offset + 2] == 'U'.code.toByte() &&
            bytes[offset + 3] == 'F'.code.toByte()

    fun isValidGguf(file: File, expectedSize: Long = -1L): Boolean {
        if (!file.isFile) return false
        val len = file.length()
        if (len < MIN_MODEL_BYTES || len > MAX_MODEL_BYTES) return false
        if (expectedSize > 0L && len != expectedSize) return false
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 && isGgufMagic(magic)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun candidateFiles(dirs: ModelDirectories): List<File> {
        val result = ArrayList<File>()
        val seen = HashSet<String>()
        fun add(file: File?) {
            if (file == null) return
            if (seen.add(file.absolutePath)) result.add(file)
        }
        add(dirs.savedPath?.takeIf { it.isNotBlank() }?.let { File(it) })
        add(privateModelFile(dirs.privateDir))
        add(File(dirs.privateDir, RECOMMENDED_FILE_NAME))
        dirs.appDownloadDir?.let { dir ->
            add(File(dir, RECOMMENDED_FILE_NAME))
            add(File(dir, USER_MODEL_FILE_NAME))
        }
        dirs.publicDirs.forEach { dir -> add(File(dir, RECOMMENDED_FILE_NAME)) }
        return result
    }

    @Synchronized
    fun discover(
        dirs: ModelDirectories,
        expectedSize: Long = EXPECTED_MODEL_SIZE
    ): DiscoveryResult {
        var fallback: Pair<File, String>? = null

        for (candidate in candidateFiles(dirs)) {
            if (!isValidGguf(candidate)) continue
            val note = if (candidate.length() == expectedSize) null else
                "Modelo GGUF com tamanho diferente do 4B Q2_0 (${candidate.length()} bytes); usando assim mesmo."

            if (candidate.absolutePath == privateModelFile(dirs.privateDir).absolutePath) {
                return DiscoveryResult(candidate, note, false)
            }

            if (candidate.length() == expectedSize) {
                val copied = copyToPrivate(dirs.privateDir, candidate)
                if (copied != null) return DiscoveryResult(copied, null, true)
                return DiscoveryResult(
                    candidate,
                    "Modelo encontrado em ${candidate.absolutePath}, mas a copia interna falhou; usando o caminho original.",
                    false
                )
            }

            if (fallback == null) fallback = candidate to (note ?: "")
        }

        fallback?.let { (candidate, note) ->
            val copied = copyToPrivate(dirs.privateDir, candidate)
            if (copied != null) return DiscoveryResult(copied, note, true)
            return DiscoveryResult(candidate, "$note Copia interna falhou; usando ${candidate.absolutePath}.", false)
        }

        return DiscoveryResult(
            null,
            "Modelo Ternary Bonsai 2 4B (Q2_0) nao encontrado. Importe $RECOMMENDED_FILE_NAME nas configuracoes.",
            false
        )
    }

    /** Copia atomica via arquivo .part, validando o resultado antes de publicar. */
    fun copyToPrivate(privateDir: File, source: File): File? {
        val target = privateModelFile(privateDir)
        val part = File(privateDir, "$USER_MODEL_FILE_NAME$PART_SUFFIX")
        if (part.exists() && !part.delete()) return null
        return try {
            source.inputStream().use { input ->
                part.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
            }
            if (!isValidGguf(part)) {
                part.delete()
                return null
            }
            if (target.exists() && !target.delete()) {
                part.delete()
                return null
            }
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            if (!isValidGguf(target)) {
                target.delete()
                return null
            }
            target
        } catch (_: Exception) {
            part.delete()
            null
        }
    }
}
