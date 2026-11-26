package com.glia.assistant.stt

import android.content.Context
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

/**
 * Instala o modelo Vosk PT-BR a partir do asset do APK.
 *
 * A extracao e atomica do ponto de vista do app: nada e publicado em
 * filesDir/vosk-model-small-pt-0.3 ate que o zip tenha hash valido, estrutura
 * segura (sem path traversal) e todos os arquivos obrigatorios presentes.
 */
object VoskModelStore {
    private const val TAG = "VoskModelStore"
    private const val STAGING_SUFFIX = ".extract"
    private const val ZIP_PART_SUFFIX = ".part"

    fun targetDir(context: Context): File = File(context.filesDir, VoskLayout.DIR_NAME)

    @Synchronized
    fun ensureModel(context: Context): File? {
        val target = targetDir(context)
        if (VoskLayout.isValidModelDir(target)) return target

        val zipPart = File(context.filesDir, VoskLayout.ASSET_NAME + ZIP_PART_SUFFIX)
        if (zipPart.exists() && !zipPart.delete()) return null
        return try {
            context.assets.open(VoskLayout.ASSET_NAME).use { input ->
                zipPart.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
            }
            val result = extractFromZip(zipPart, target)
            if (result == null) logError("Falha ao validar/extrair o asset Vosk")
            result
        } catch (e: Exception) {
            logError("Falha ao abrir asset Vosk", e)
            null
        } finally {
            if (zipPart.exists()) zipPart.delete()
        }
    }

    /** Log tolerante a testes JVM (android.jar stub lanca excecao em android.util.Log). */
    private fun logError(message: String, error: Throwable? = null) {
        try { Log.e(TAG, message, error) } catch (_: Throwable) { }
    }

    private fun logInfo(message: String) {
        try { Log.i(TAG, message) } catch (_: Throwable) { }
    }

    /**
     * Validacao + extracao pura (sem Android), usada pelos testes rigorosos.
     * Retorna o diretorio publicado ou null, sem deixar lixo em staging.
     */
    @Synchronized
    fun extractFromZip(zipPart: File, target: File): File? {
        if (VoskLayout.isValidModelDir(target)) return target
        val staging = File(target.parentFile, target.name + STAGING_SUFFIX)
        cleanup(staging)

        return try {
            if (!zipPart.isFile) return null
            if (VoskLayout.sha256(zipPart) != VoskLayout.ZIP_SHA256) {
                logError("Hash do asset Vosk invalido")
                cleanup(staging)
                return null
            }
            ZipFile(zipPart).use { zip ->
                val problem = VoskLayout.validateZip(zip)
                if (problem != null) {
                    logError("Estrutura do zip Vosk invalida: $problem")
                    cleanup(staging)
                    return null
                }
                if (VoskLayout.uncompressedBytes(zip) != VoskLayout.EXPECTED_UNCOMPRESSED_BYTES) {
                    logError("Tamanho descomprimido inesperado do zip Vosk")
                    cleanup(staging)
                    return null
                }
                val root = staging.canonicalPath
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val relative = VoskLayout.safeRelative(entry.name) ?: error("entrada insegura: ${entry.name}")
                    if (relative.isEmpty()) continue
                    val destination = File(staging, relative)
                    if (!destination.canonicalPath.startsWith(root + File.separator)) {
                        error("entrada fora do diretorio de destino: ${entry.name}")
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
            if (!VoskLayout.isValidModelDir(staging)) {
                logError("Modelo Vosk extraido incompleto")
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
            if (!VoskLayout.isValidModelDir(target)) {
                logError("Modelo Vosk publicado incompleto")
                return null
            }
            logInfo("Modelo Vosk instalado em ${target.absolutePath}")
            target
        } catch (e: Exception) {
            logError("Falha ao instalar modelo Vosk", e)
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
