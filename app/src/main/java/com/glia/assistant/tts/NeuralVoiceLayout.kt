package com.glia.assistant.tts

import java.io.File
import java.util.zip.ZipFile

/**
 * Fachada do modelo Dii, mantida por compatibilidade com os testes e com o
 * código existente. A validação genérica está em [OfflineVoices].
 */
object NeuralVoiceLayout {
    const val ASSET_NAME = OfflineVoices.DII_ASSET
    const val DIR_NAME = OfflineVoices.DII_DIR
    const val MODEL_NAME = OfflineVoices.DII_MODEL
    const val TOKENS_NAME = OfflineVoiceShared.TOKENS_NAME
    const val ESPEAK_DIR = OfflineVoiceShared.ESPEAK_DIR
    const val ZIP_SHA256 = OfflineVoices.DII_SHA
    const val EXPECTED_UNCOMPRESSED_BYTES = OfflineVoices.DII_UNCOMPRESSED
    const val EXPECTED_ENTRY_COUNT = OfflineVoices.DII_ENTRY_COUNT

    val REQUIRED_FILES: List<String> get() = OfflineVoices.DII.requiredFiles

    fun safeRelative(entryName: String): String? =
        OfflineVoices.safeRelative(OfflineVoices.DII, entryName)

    fun isValidModelDir(dir: File): Boolean =
        OfflineVoices.isValidModelDir(OfflineVoices.DII, dir)

    fun sha256(file: File): String = OfflineVoices.sha256(file)

    fun uncompressedBytes(zip: ZipFile): Long = OfflineVoices.uncompressedBytes(zip)

    fun validateZip(zip: ZipFile): String? = OfflineVoices.validateZip(OfflineVoices.DII, zip)
}
