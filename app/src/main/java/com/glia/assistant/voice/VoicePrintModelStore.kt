package com.glia.assistant.voice

import android.content.Context
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

/**
 * Instala o modelo de impressão de voz a partir do asset do APK.
 * O modelo só é publicado depois de validar hash do zip, nome da entrada e hash
 * do arquivo extraído — o mesmo padrão usado pela voz neural.
 */
object VoicePrintModelStore {
    private const val TAG = "VoicePrintModel"
    private const val ZIP_PART_SUFFIX = ".part"

    fun targetDir(context: Context): File = File(context.filesDir, VoicePrintLayout.DIR_NAME)

    @Synchronized
    fun ensureModel(context: Context): File? {
        val target = File(targetDir(context), VoicePrintLayout.MODEL_NAME)
        if (VoicePrintLayout.isValidModelFile(target)) return target

        val part = File(context.filesDir, VoicePrintLayout.ASSET_NAME + ZIP_PART_SUFFIX)
        if (part.exists() && !part.delete()) return null
        return try {
            context.assets.open(VoicePrintLayout.ASSET_NAME).use { input ->
                part.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
            }
            extract(part, target)
        } catch (e: Exception) {
            log("Falha ao abrir o asset da impressão de voz", e)
            null
        } finally {
            if (part.exists()) part.delete()
        }
    }

    /** Extração e validação puras, também usadas pelos testes JVM. */
    @Synchronized
    fun extract(zipPart: File, target: File): File? {
        if (VoicePrintLayout.isValidModelFile(target)) return target
        val staging = File(target.parentFile, target.name + ".extract")
        if (staging.exists()) staging.delete()
        return try {
            if (!zipPart.isFile) return null
            if (VoicePrintLayout.sha256(zipPart) != VoicePrintLayout.ZIP_SHA256) {
                log("Hash do asset da impressão de voz inválido")
                return null
            }
            ZipFile(zipPart).use { zip ->
                val entries = zip.entries()
                val first = if (entries.hasMoreElements()) entries.nextElement() else null
                if (first == null || first.name != VoicePrintLayout.MODEL_NAME ||
                    first.isDirectory || entries.hasMoreElements()
                ) {
                    log("Estrutura do zip da impressão de voz inválida")
                    return null
                }
                staging.parentFile?.mkdirs()
                zip.getInputStream(first).use { input ->
                    staging.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
                }
            }
            if (!VoicePrintLayout.isValidModelFile(staging)) {
                log("Modelo de impressão de voz extraído incompleto")
                staging.delete()
                return null
            }
            target.parentFile?.mkdirs()
            if (target.exists() && !target.delete()) {
                staging.delete()
                return null
            }
            if (!staging.renameTo(target)) {
                staging.copyTo(target, overwrite = true)
                staging.delete()
            }
            if (!VoicePrintLayout.isValidModelFile(target)) {
                target.delete()
                return null
            }
            target
        } catch (e: Exception) {
            log("Falha ao instalar a impressão de voz", e)
            staging.delete()
            null
        }
    }

    private fun log(message: String, error: Throwable? = null) {
        try { if (error != null) Log.e(TAG, message, error) else Log.e(TAG, message) } catch (_: Throwable) { }
    }
}
