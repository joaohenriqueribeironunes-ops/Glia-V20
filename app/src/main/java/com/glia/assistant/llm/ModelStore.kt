package com.glia.assistant.llm

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream

/**
 * V20: gerencia o modelo local (.gguf) do assistente.
 *
 * O modelo NAO e mais embutido no APK: o usuario importa o arquivo
 * Ternary-Bonsai-2-4B-Q2_0.gguf (ou outro .gguf suportado) via SAF em
 * SettingsActivity, que o copia atomicamente para o filesDir do app
 * (user-model.gguf) e valida magic GGUF + tamanho antes de salvar o caminho.
 *
 * Aqui validamos o arquivo do usuario (tamanho entre 1KB e 3GB) e o devolve
 * para o LlamaBridge carregar via mmap.
 */
object ModelStore {
    private const val TAG = "ModelStore"
    const val USER_MODEL_FILE_NAME = "user-model.gguf"
    const val MAX_MODEL_BYTES = 3_000_000_000L

    fun userModelFile(context: Context): File = File(context.filesDir, USER_MODEL_FILE_NAME)

    @Synchronized
    fun ensureModel(context: Context): File? {
        val out = userModelFile(context)
        if (isValid(out)) {
            Log.i(TAG, "Modelo do usuario valido: ${out.length()} bytes")
            return out
        }
        if (out.exists()) {
            Log.w(TAG, "Modelo invalido/parcial (${out.length()} bytes); removendo")
            out.delete()
        }
        return null
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
