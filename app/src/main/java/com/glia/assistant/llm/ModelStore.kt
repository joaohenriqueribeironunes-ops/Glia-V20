package com.glia.assistant.llm

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * V20.1: fonte dupla de modelo.
 *
 * - Padrão: Ternary Bonsai 2 2B Q2_0 EMBUTIDO no APK (assets), extraído uma vez
 *   para filesDir/embedded-2b.gguf com cópia atômica (.part) e validação
 *   tamanho + magic GGUF. Funciona sem importar nada.
 * - Alternativa: modelo 4B (ou outro GGUF) importado pelo usuário via SAF e
 *   salvo em filesDir/user-model.gguf.
 *
 * A escolha fica em GliaPrefs.modelSource ("embedded_2b" | "user_4b").
 */
object ModelStore {
    private const val TAG = "ModelStore"

    const val ASSET_PATH = "models/Ternary-Bonsai-2-2B-Q2_0.gguf"
    const val EMBEDDED_FILE_NAME = "embedded-2b.gguf"
    const val EXPECTED_EMBEDDED_SIZE = 546_689_504L
    const val SOURCE_EMBEDDED = "embedded_2b"
    const val SOURCE_USER = "user_4b"

    const val USER_MODEL_FILE_NAME = "user-model.gguf"
    const val MAX_MODEL_BYTES = 3_000_000_000L

    fun embeddedModelFile(context: Context): File = File(context.filesDir, EMBEDDED_FILE_NAME)
    fun userModelFile(context: Context): File = File(context.filesDir, USER_MODEL_FILE_NAME)

    /** Extrai o 2B embutido. Retorna null com log claro se asset ausente/inválido. */
    @Synchronized
    fun ensureEmbeddedModel(context: Context): File? {
        val out = embeddedModelFile(context)
        if (isValid(out, EXPECTED_EMBEDDED_SIZE)) {
            Log.i(TAG, "Modelo 2B embutido já disponível: ${out.length()} bytes")
            return out
        }
        if (out.exists()) {
            Log.w(TAG, "Modelo 2B parcial/inválido (${out.length()} bytes); removendo")
            out.delete()
        }

        val part = File(context.filesDir, "$EMBEDDED_FILE_NAME.part")
        if (part.exists()) part.delete()

        return try {
            val assets = context.assets.list("")?.toSet() ?: emptySet()
            if (!assets.contains("models")) {
                Log.e(TAG, "Asset 'models' ausente no APK — modelo 2B não embutido")
                return null
            }
            val size = try {
                context.assets.openFd(ASSET_PATH).use { it.length }
            } catch (e: Exception) {
                Log.e(TAG, "Asset $ASSET_PATH ausente: ${e.message}")
                return null
            }
            if (size != EXPECTED_EMBEDDED_SIZE) {
                Log.e(TAG, "Asset 2B com tamanho inesperado: $size (esperado $EXPECTED_EMBEDDED_SIZE)")
                return null
            }

            var copied = 0L
            context.assets.open(ASSET_PATH).use { input ->
                FileOutputStream(part).use { output ->
                    val buf = ByteArray(1024 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        copied += n
                    }
                    output.flush()
                }
            }
            if (copied != EXPECTED_EMBEDDED_SIZE || !isValid(part, EXPECTED_EMBEDDED_SIZE)) {
                Log.e(TAG, "Cópia do modelo 2B falhou ($copied bytes)")
                part.delete()
                return null
            }
            if (!part.renameTo(out)) {
                Log.e(TAG, "Falha ao renomear cópia atômica do modelo 2B")
                part.delete()
                return null
            }
            Log.i(TAG, "Modelo 2B extraído do APK: $copied bytes")
            out
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao extrair modelo 2B: ${e.message}", e)
            part.delete()
            null
        }
    }

    /** Modelo efetivo conforme preferência do usuário, com fallback honesto. */
    fun resolveModel(context: Context, source: String): Pair<File?, String?> {
        if (source == SOURCE_USER) {
            val user = userModelFile(context)
            if (isValid(user)) return user to null
            val embedded = ensureEmbeddedModel(context)
            return embedded to "Modelo 4B importado não encontrado ou inválido; usando o 2B embutido."
        }
        val embedded = ensureEmbeddedModel(context)
        if (embedded != null) return embedded to null
        val user = userModelFile(context)
        return if (isValid(user)) {
            user to "Modelo 2B embutido ausente; usando o modelo importado."
        } else {
            null to "Modelo Ternary Bonsai 2 2B não encontrado no APK. Verifique o build."
        }
    }

    /** Legado usado por telas/serviços: garante ao menos um modelo válido. */
    @Synchronized
    fun ensureModel(context: Context): File? = ensureEmbeddedModel(context) ?: userModelFile(context).takeIf { isValid(it) }

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
                    magic[2] == 'G'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isUsableSize(file: File): Boolean = file.isFile && file.length() in 1024L..MAX_MODEL_BYTES
}
