package com.glia.assistant.llm

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Faca Android da descoberta pura em [ModelDiscovery].
 *
 * V20.5+: o assistente localiza o Ternary Bonsai 2 4B automaticamente no caminho
 * salvo, no armazenamento privado, no Download do app e nas pastas publicas, e
 * copia o arquivo validado para filesDir/user-model.gguf.
 */
object ModelStore {
    private const val TAG = "ModelStore"

    const val USER_MODEL_FILE_NAME = ModelDiscovery.USER_MODEL_FILE_NAME
    const val RECOMMENDED_FILE_NAME = ModelDiscovery.RECOMMENDED_FILE_NAME
    const val EXPECTED_MODEL_SIZE = ModelDiscovery.EXPECTED_MODEL_SIZE
    const val EXPECTED_SHA256 = "64a5183b44921bdb5ac4a6f5f92007821d6ebe5c66997d3c147fd214ab9433d4"
    const val MAX_MODEL_BYTES = ModelDiscovery.MAX_MODEL_BYTES

    /** Constante mantida para compatibilidade com preferencias antigas. */
    const val SOURCE_USER = "user_4b"
    const val SOURCE_EMBEDDED = "embedded_2b"

    fun userModelFile(context: Context): File =
        ModelDiscovery.privateModelFile(context.filesDir)

    fun directories(context: Context, savedPath: String?): ModelDirectories = ModelDirectories(
        savedPath = savedPath,
        privateDir = context.filesDir,
        appDownloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        publicDirs = listOf(
            File("/sdcard/Download"),
            File("/sdcard/Documents"),
            File("/storage/emulated/0/Download")
        )
    )

    @Synchronized
    fun discoverModel(context: Context, savedPath: String? = null): Pair<File?, String?> {
        val result = ModelDiscovery.discover(directories(context, savedPath))
        if (result.file == null) {
            val invalid = userModelFile(context)
            if (invalid.exists()) {
                Log.w(TAG, "Modelo invalido/parcial (${invalid.length()} bytes); removendo")
                invalid.delete()
            }
        }
        if (result.copiedToPrivate) {
            Log.i(TAG, "Modelo copiado para ${result.file?.absolutePath}")
        }
        return result.file to result.note
    }

    /** Compatibilidade com chamadas antigas. */
    fun resolveModel(context: Context, source: String? = null): Pair<File?, String?> =
        discoverModel(context)

    fun ensureModel(context: Context): File? = discoverModel(context).first

    fun isValid(file: File, expectedSize: Long = -1L): Boolean =
        ModelDiscovery.isValidGguf(file, expectedSize)

    fun isUsableSize(file: File): Boolean =
        file.isFile && file.length() in ModelDiscovery.MIN_MODEL_BYTES..MAX_MODEL_BYTES
}
