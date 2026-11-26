package com.glia.assistant.tts

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Nomes compartilhados por todos os modelos VITS/Piper embarcados. */
object OfflineVoiceShared {
    const val TOKENS_NAME = "tokens.txt"
    const val ESPEAK_DIR = "espeak-ng-data"
}

/** Modelo neural pt-BR 100% local, com contrato validável sem Android. */
data class OfflineVoice(
    val id: String,
    val label: String,
    val description: String,
    val assetName: String,
    val dirName: String,
    val modelName: String,
    val tokensName: String = OfflineVoiceShared.TOKENS_NAME,
    val espeakDir: String = OfflineVoiceShared.ESPEAK_DIR,
    val zipSha256: String,
    val uncompressedBytes: Long,
    val entryCount: Int
) {
    val requiredFiles: List<String>
        get() = listOf(
            modelName,
            "$modelName.json",
            tokensName,
            "$espeakDir/intonations",
            "$espeakDir/phondata",
            "$espeakDir/phondata-manifest",
            "$espeakDir/phonindex",
            "$espeakDir/phontab",
            "$espeakDir/pt_dict",
            "$espeakDir/lang/roa/pt",
            "$espeakDir/lang/roa/pt-BR"
        )
}

object OfflineVoices {
    const val DII_ASSET = "glia-neural-voice.zip"
    const val DII_DIR = "glia-neural-voice"
    const val DII_MODEL = "pt_BR-dii-high.onnx"
    const val DII_SHA = "813eb4e520f115bdb80337e4aa5b05d3d6999ec2e1e1541bf0137ca5ad2b7c38"
    const val DII_UNCOMPRESSED = 64_254_245L
    const val DII_ENTRY_COUNT = 11

    const val CADU_ASSET = "glia-neural-voice-cadu.zip"
    const val CADU_DIR = "glia-neural-voice-cadu"
    const val CADU_MODEL = "pt_BR-cadu-medium.onnx"
    const val CADU_SHA = "d3839a2a95ae2300105c7d28f71e9f2d1386fcb30fca648bf955345610138da7"
    const val CADU_ZIP_BYTES = 13_840_894L
    const val CADU_UNCOMPRESSED = 19_087_043L
    const val CADU_ENTRY_COUNT = 11

    val DII = OfflineVoice(
        id = "dii",
        label = "Dii · feminina",
        description = "Voz feminina pt-BR, qualidade alta.",
        assetName = DII_ASSET,
        dirName = DII_DIR,
        modelName = DII_MODEL,
        zipSha256 = DII_SHA,
        uncompressedBytes = DII_UNCOMPRESSED,
        entryCount = DII_ENTRY_COUNT
    )

    val CADU = OfflineVoice(
        id = "cadu",
        label = "Cadu · int8",
        description = "Voz pt-BR compacta (int8), menor consumo de armazenamento.",
        assetName = CADU_ASSET,
        dirName = CADU_DIR,
        modelName = CADU_MODEL,
        zipSha256 = CADU_SHA,
        uncompressedBytes = CADU_UNCOMPRESSED,
        entryCount = CADU_ENTRY_COUNT
    )

    val ALL = listOf(DII, CADU)

    fun fromId(id: String?): OfflineVoice = ALL.firstOrNull { it.id == id } ?: DII

    /** Remove o prefixo do zip e rejeita entradas inseguras. */
    fun safeRelative(voice: OfflineVoice, entryName: String): String? {
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.contains(':')) return null
        val prefix = "${voice.dirName}/"
        if (!normalized.startsWith(prefix)) return null
        val relative = normalized.removePrefix(prefix)
        if (relative.isEmpty()) return ""
        if (relative.split('/').any { it == ".." || it == "." || it.isEmpty() }) return null
        return relative
    }

    fun isValidModelDir(voice: OfflineVoice, dir: File): Boolean {
        if (!dir.isDirectory) return false
        return voice.requiredFiles.all { name ->
            val file = File(dir, name)
            file.isFile && file.length() > 0L
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun uncompressedBytes(zip: ZipFile): Long =
        zip.entries().toList().sumOf { if (it.isDirectory) 0L else it.size }

    /** Retorna null quando o zip é seguro e completo; caso contrário, o motivo. */
    fun validateZip(voice: OfflineVoice, zip: ZipFile): String? {
        val names = zip.entries().toList().map { it.name }
        if (names.size != voice.entryCount) {
            return "quantidade de entradas inesperada: ${names.size} (esperado ${voice.entryCount})"
        }
        val files = HashSet<String>()
        for (name in names) {
            val relative = safeRelative(voice, name) ?: return "entrada insegura no zip: $name"
            if (relative.isEmpty()) continue
            val entry: ZipEntry = zip.getEntry(name) ?: return "entrada ausente: $name"
            if (!entry.isDirectory) files.add(relative)
        }
        val missing = voice.requiredFiles.filter { it !in files }
        if (missing.isNotEmpty()) return "arquivos obrigatórios ausentes: ${missing.joinToString()}"
        val unknown = files.filter { it !in OfflineVoices.DII.requiredFiles && it !in voice.requiredFiles }
        if (unknown.isNotEmpty()) return "arquivos inesperados no zip: ${unknown.joinToString()}"
        return null
    }
}
