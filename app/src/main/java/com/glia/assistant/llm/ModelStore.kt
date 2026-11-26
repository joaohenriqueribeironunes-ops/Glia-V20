package com.glia.assistant.llm

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream

/**
 * V20.5: localiza automaticamente o Ternary Bonsai 2 4B (Q2_0).
 *
 * Ordem de busca: caminho salvo nas preferencias, filesDir/user-model.gguf,
 * filesDir/Ternary-Bonsai-2-4B-Q2_0.gguf, diretorio Download especifico do app
 * e pastas publicas Download/Documents. Quando o arquivo e encontrado fora do
 * armazenamento privado, ele e copiado atomicamente para user-model.gguf.
 * Nenhum asset 2B e embutido e a copia e validada por magic GGUF + tamanho.
 */
object ModelStore {
    private const val TAG = "ModelStore"

    const val USER_MODEL_FILE_NAME = "user-model.gguf"
    const val RECOMMENDED_FILE_NAME = "Ternary-Bonsai-2-4B-Q2_0.gguf"
    const val EXPECTED_MODEL_SIZE = 1_212_103_520L
    const val EXPECTED_SHA256 = "64a5183b44921bdb5ac4a6f5f92007821d6ebe5c66997d3c147fd214ab9433d4"
    const val MAX_MODEL_BYTES = 3_000_000_000L

    /** Constante mantida para compatibilidade com preferencias antigas. */
    const val SOURCE_USER = "user_4b"
    const val SOURCE_EMBEDDED = "embedded_2b"

    fun userModelFile(context: Context): File = File(context.filesDir, USER_MODEL_FILE_NAME)

    /**
     * Procura o modelo em locais conhecidos. Retorna o arquivo pronto para uso
     * e uma mensagem de aviso (ou null quando o modelo e exatamente o esperado).
     */
    @Synchronized
    fun discoverModel(context: Context, savedPath: String? = null): Pair<File?, String?> {
        val candidates = candidateFiles(context, savedPath)
        var fallback: Pair<File, String>? = null

        for (candidate in candidates) {
            if (!candidate.isFile) continue
            if (!isValid(candidate)) continue

            val note = if (candidate.length() == EXPECTED_MODEL_SIZE) null
                else "Modelo GGUF com tamanho diferente do 4B Q2_0 (${candidate.length()} bytes); usando assim mesmo."

            val privateFile = userModelFile(context)
            if (candidate.absolutePath == privateFile.absolutePath) {
                return privateFile to note
            }

            if (candidate.length() == EXPECTED_MODEL_SIZE) {
                val copied = copyToPrivate(context, candidate)
                if (copied != null) return copied to null
                return candidate to "Modelo encontrado em ${candidate.absolutePath}, mas a copia interna falhou; usando o caminho original."
            }

            if (fallback == null) fallback = candidate to (note ?: "")
        }

        fallback?.let { (candidate, note) ->
            val copied = copyToPrivate(context, candidate)
            if (copied != null) return copied to note
            return candidate to "$note Copia interna falhou; usando ${candidate.absolutePath}."
        }

        val invalid = userModelFile(context)
        if (invalid.exists()) {
            Log.w(TAG, "Modelo invalido/parcial (${invalid.length()} bytes); removendo")
            invalid.delete()
        }
        return null to "Modelo Ternary Bonsai 2 4B (Q2_0) nao encontrado. Importe $RECOMMENDED_FILE_NAME nas configuracoes."
    }

    /** Compatibilidade com chamadas antigas. */
    fun resolveModel(context: Context, source: String? = null): Pair<File?, String?> =
        discoverModel(context)

    fun ensureModel(context: Context): File? = discoverModel(context).first

    private fun candidateFiles(context: Context, savedPath: String?): List<File> {
        val result = ArrayList<File>()
        val seen = HashSet<String>()
        fun add(file: File?) {
            if (file == null) return
            if (seen.add(file.absolutePath)) result.add(file)
        }

        add(savedPath?.takeIf { it.isNotBlank() }?.let { File(it) })
        add(userModelFile(context))
        add(File(context.filesDir, RECOMMENDED_FILE_NAME))
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dir ->
            add(File(dir, RECOMMENDED_FILE_NAME))
            add(File(dir, USER_MODEL_FILE_NAME))
        }
        add(File("/sdcard/Download", RECOMMENDED_FILE_NAME))
        add(File("/sdcard/Documents", RECOMMENDED_FILE_NAME))
        add(File("/storage/emulated/0/Download", RECOMMENDED_FILE_NAME))
        return result
    }

    private fun copyToPrivate(context: Context, source: File): File? {
        val target = userModelFile(context)
        val part = File(context.filesDir, "$USER_MODEL_FILE_NAME.part")
        if (part.exists()) part.delete()
        return try {
            source.inputStream().use { input ->
                part.outputStream().use { output ->
                    input.copyTo(output, 1024 * 1024)
                }
            }
            if (!isValid(part)) {
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
            Log.i(TAG, "Modelo copiado para ${target.absolutePath} (${target.length()} bytes)")
            target
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao copiar modelo para armazenamento interno: ${e.message}")
            part.delete()
            null
        }
    }

    fun isValid(file: File, expectedSize: Long = -1L): Boolean {
        if (!file.isFile) return false
        val len = file.length()
        if (len < 1024L || len > MAX_MODEL_BYTES) return false
        if (expectedSize > 0 && len != expectedSize) return false
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 &&
                    magic[0] == 'G'.code.toByte() &&
                    magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isUsableSize(file: File): Boolean = file.isFile && file.length() in 1024L..MAX_MODEL_BYTES
}
