package com.glia.assistant.tts

import android.content.Context
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

/**
 * Instala a voz neural pt-BR a partir do asset do APK.
 *
 * Nada é publicado em filesDir/glia-neural-voice antes que o zip tenha hash
 * válido, estrutura segura (sem path traversal) e todos os arquivos exigidos
 * pelo motor sherpa-onnx estejam presentes.
 */
object NeuralVoiceStore {
    private const val TAG = "NeuralVoiceStore"
    private const val STAGING_SUFFIX = ".extract"
    private const val ZIP_PART_SUFFIX = ".part"

    fun targetDir(context: Context): File = File(context.filesDir, NeuralVoiceLayout.DIR_NAME)

    @Synchronized
    fun ensureModel(context: Context): File? {
        val target = targetDir(context)
        if (NeuralVoiceLayout.isValidModelDir(target)) return target

        val zipPart = File(context.filesDir, NeuralVoiceLayout.ASSET_NAME + ZIP_PART_SUFFIX)
        if (zipPart.exists() && !zipPart.delete()) return null
        return try {
            context.assets.open(NeuralVoiceLayout.ASSET_NAME).use { input ->
                zipPart.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
            }
            val result = extractFromZip(zipPart, target)
            if (result == null) logError("Falha ao validar/extrair o asset da voz neural")
            result
        } catch (e: Exception) {
            logError("Falha ao abrir o asset da voz neural", e)
            null
        } finally {
            if (zipPart.exists()) zipPart.delete()
        }
    }

    /** Log tolerante a testes JVM (android.jar stub lança exceção em android.util.Log). */
    private fun logError(message: String, error: Throwable? = null) {
        try { Log.e(TAG, message, error) } catch (_: Throwable) { }
    }

    private fun logInfo(message: String) {
        try { Log.i(TAG, message) } catch (_: Throwable) { }
    }

    /** Validação + extração pura (sem Android), usada pelos testes rigorosos. */
    @Synchronized
    fun extractFromZip(zipPart: File, target: File): File? {
        if (NeuralVoiceLayout.isValidModelDir(target)) return target
        val staging = File(target.parentFile, target.name + STAGING_SUFFIX)
        cleanup(staging)

        return try {
            if (!zipPart.isFile) return null
            if (NeuralVoiceLayout.sha256(zipPart) != NeuralVoiceLayout.ZIP_SHA256) {
                logError("Hash do asset da voz neural inválido")
                cleanup(staging)
                return null
            }
            ZipFile(zipPart).use { zip ->
                val problem = NeuralVoiceLayout.validateZip(zip)
                if (problem != null) {
                    logError("Estrutura do zip da voz neural inválida: $problem")
                    cleanup(staging)
                    return null
                }
                if (NeuralVoiceLayout.uncompressedBytes(zip) != NeuralVoiceLayout.EXPECTED_UNCOMPRESSED_BYTES) {
                    logError("Tamanho descomprimido inesperado da voz neural")
                    cleanup(staging)
                    return null
                }
                val root = staging.canonicalPath
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val relative = NeuralVoiceLayout.safeRelative(entry.name)
                        ?: error("entrada insegura: ${entry.name}")
                    if (relative.isEmpty()) continue
                    val destination = File(staging, relative)
                    if (!destination.canonicalPath.startsWith(root + File.separator)) {
                        error("entrada fora do diretório de destino: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        destination.mkdirs()
                    } else {
                        destination.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            destination.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
                        }
                    }
                }
            }
            if (!NeuralVoiceLayout.isValidModelDir(staging)) {
                logError("Voz neural extraída incompleta")
                cleanup(staging)
                return null
            }
            if (target.exists() && !target.deleteRecursively()) {
                cleanup(staging)
                return null
            }
            if (!staging.renameTo(target)) {
                copyTree(staging, target)
                staging.deleteRecursively()
            }
            if (!NeuralVoiceLayout.isValidModelDir(target)) {
                logError("Voz neural publicada incompleta")
                return null
            }
            logInfo("Voz neural instalada em ${target.absolutePath}")
            target
        } catch (e: Exception) {
            logError("Falha ao instalar a voz neural", e)
            cleanup(staging)
            null
        }
    }

    private fun cleanup(staging: File) {
        if (staging.exists()) staging.deleteRecursively()
    }

    private fun copyTree(source: File, destination: File) {
        if (source.isDirectory) {
            destination.mkdirs()
            source.listFiles()?.forEach { copyTree(it, File(destination, it.name)) }
        } else {
            destination.parentFile?.mkdirs()
            source.copyTo(destination, overwrite = true)
        }
    }
}
