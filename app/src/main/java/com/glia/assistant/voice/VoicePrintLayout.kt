package com.glia.assistant.voice

import java.io.File
import java.security.MessageDigest

/** Constantes e validações puras do modelo de impressão de voz (3D-Speaker CAM++). */
object VoicePrintLayout {
    const val ASSET_NAME = "glia-voiceprint.zip"
    const val MODEL_NAME = "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx"
    const val ZIP_SHA256 = "68666cccffcd26c7cc0d58fa1e2e1330850eab57394ce51aa0e28af3e5e4625e"
    const val MODEL_SHA256 = "53cab20ba8f2eb2f6e2d89ecc18e88ef3222169b61b4419cbc13666744a4574b"
    const val MODEL_BYTES = 28_281_165L
    const val SAMPLE_RATE = 16_000
    const val DIR_NAME = "glia-voiceprint"
    const val PROFILE_FILE = "voiceprint.bin"

    val TRAINING_PHRASES = listOf(
        "Glia, esta é a minha voz.",
        "Glia, que horas são hoje?",
        "Glia, abra a câmera, por favor."
    )

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun isValidModelFile(file: File): Boolean =
        file.isFile && file.length() == MODEL_BYTES && sha256(file) == MODEL_SHA256
}
