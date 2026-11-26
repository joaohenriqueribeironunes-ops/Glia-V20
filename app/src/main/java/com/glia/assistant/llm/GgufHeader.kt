package com.glia.assistant.llm

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** Leitor minimalista e robusto do cabecalho GGUF (little endian). */
data class GgufHeader(
    val version: Int,
    val tensorCount: Long,
    val metadataCount: Long,
    val metadata: Map<String, Any>,
    val headerBytes: Long
) {
    val architecture: String? get() = metadata["general.architecture"] as? String

    fun long(key: String): Long? = when (val v = metadata[key]) {
        is Long -> v
        is Int -> v.toLong()
        else -> null
    }

    fun string(key: String): String? = metadata[key] as? String

    val contextLength: Long?
        get() = long("${architecture ?: "llama"}.context_length") ?: long("general.context_length")

    val blockCount: Long? get() = long("${architecture ?: "llama"}.block_count")

    val embeddingLength: Long? get() = long("${architecture ?: "llama"}.embedding_length")

    val headCountKv: Long? get() = long("${architecture ?: "llama"}.attention.head_count_kv")

    val fileType: Long? get() = long("general.file_type")

    companion object {
        const val MAGIC = "GGUF"
        const val SUPPORTED_VERSION_MIN = 2
        const val SUPPORTED_VERSION_MAX = 3
        private const val MAX_STRING_BYTES = 1 * 1024 * 1024
        private const val MAX_ARRAY_ITEMS = 1_000_000

        fun read(file: File): GgufHeader = FileInputStream(file).use { read(it) }

        fun read(input: InputStream): GgufHeader {
            val stream = DataInputStream(input)
            val magic = ByteArray(4)
            stream.readFully(magic)
            if (!ModelDiscovery.isGgufMagic(magic)) {
                throw IllegalArgumentException("magic GGUF invalido")
            }
            val version = readU32(stream).toInt()
            if (version !in SUPPORTED_VERSION_MIN..SUPPORTED_VERSION_MAX) {
                throw IllegalArgumentException("versao GGUF nao suportada: $version")
            }
            val tensorCount = readU64(stream)
            val metadataCount = readU64(stream)
            if (metadataCount > MAX_ARRAY_ITEMS) {
                throw IllegalArgumentException("quantidade de metadados suspeita: $metadataCount")
            }
            val metadata = LinkedHashMap<String, Any>(metadataCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            repeat(metadataCount.toInt()) {
                val key = readString(stream)
                val type = readU32(stream).toInt()
                metadata[key] = readValue(stream, type, 0)
            }
            return GgufHeader(
                version,
                tensorCount,
                metadataCount,
                metadata,
                -1L // tamanho do cabecalho nao e necessario para validacao
            )
        }

        private fun readU32(stream: DataInputStream): Long {
            val b = ByteArray(4)
            stream.readFully(b)
            return (b[0].toLong() and 0xFF) or
                ((b[1].toLong() and 0xFF) shl 8) or
                ((b[2].toLong() and 0xFF) shl 16) or
                ((b[3].toLong() and 0xFF) shl 24)
        }

        private fun readU64(stream: DataInputStream): Long {
            val b = ByteArray(8)
            stream.readFully(b)
            var value = 0L
            for (i in 7 downTo 0) {
                value = (value shl 8) or (b[i].toLong() and 0xFF)
            }
            return value
        }

        private fun readString(stream: DataInputStream): String {
            val len = readU64(stream)
            if (len < 0L || len > MAX_STRING_BYTES) {
                throw IllegalArgumentException("string GGUF suspeita: $len bytes")
            }
            val bytes = ByteArray(len.toInt())
            stream.readFully(bytes)
            return String(bytes, Charsets.UTF_8)
        }

        private fun readValue(stream: DataInputStream, type: Int, depth: Int): Any {
            if (depth > 4) throw IllegalArgumentException("GGUF aninhado demais")
            return when (type) {
                0 -> readU32(stream) and 0xFF
                1 -> (readU32(stream) and 0xFF).toByte()
                2 -> readU32(stream) and 0xFFFF
                3 -> (readU32(stream) and 0xFFFF).toShort()
                4 -> readU32(stream)
                5 -> readU32(stream).toInt()
                6 -> java.lang.Float.intBitsToFloat(readU32(stream).toInt()).toDouble()
                7 -> (readU32(stream) and 0xFF) != 0L
                8 -> readString(stream)
                9 -> {
                    val count = readU64(stream)
                    val elementType = readU32(stream).toInt()
                    if (count < 0L || count > MAX_ARRAY_ITEMS) {
                        throw IllegalArgumentException("array GGUF suspeito: $count itens")
                    }
                    val list = ArrayList<Any>(count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    repeat(count.toInt()) { list.add(readValue(stream, elementType, depth + 1)) }
                    list
                }
                10 -> readU64(stream)
                11 -> readU64(stream)
                12 -> {
                    val b = ByteArray(8)
                    stream.readFully(b)
                    java.lang.Double.longBitsToDouble(
                        b.foldIndexed(0L) { i, acc, byte -> acc or ((byte.toLong() and 0xFF) shl (8 * i)) }
                    )
                }
                else -> throw IllegalArgumentException("tipo GGUF desconhecido: $type")
            }
        }
    }
}

class GgufFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
