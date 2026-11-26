package com.glia.assistant.llm

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream

/**
 * V20.3: apenas o modelo local 4B do usuario.
 *
 * O arquivo e obtido pelo proprio Loci/ADB e copiado para
 * filesDir/user-model.gguf (ou importado via SAF nas configuracoes).
 * Validamos magic GGUF, tamanho esperado do Ternary Bonsai 2 4B Q2_0
 * e mantemos mensagem clara quando ausente. Nenhum asset 2B e embutido.
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

    /** Sempre resolve para o 4B importado. Nao ha mais fallback 2B. */
    fun resolveModel(context: Context, source: String? = null): Pair<File?, String?> {
        val file = userModelFile(context)
        if (isValid(file, EXPECTED_MODEL_SIZE)) return file to null
        if (isValid(file)) {
            return file to "Modelo GGUF diferente do 4B Q2_0 recomendado (${file.length()} bytes); usando assim mesmo."
        }
        if (file.exists()) {
            Log.w(TAG, "Modelo invalido/parcial (${file.length()} bytes); removendo")
            file.delete()
        }
        return null to "Modelo Ternary Bonsai 2 4B (Q2_0) nao encontrado. Importe $RECOMMENDED_FILE_NAME nas configuracoes."
    }

    fun ensureModel(context: Context): File? = resolveModel(context).first

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
