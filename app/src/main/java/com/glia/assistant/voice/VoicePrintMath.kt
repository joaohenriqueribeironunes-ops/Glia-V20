package com.glia.assistant.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Matemática pura da impressão de voz: fácil de testar sem Android nem modelo. */
object VoicePrintMath {
    const val DEFAULT_THRESHOLD = 0.55f
    const val MIN_THRESHOLD = 0.35f
    const val MAX_THRESHOLD = 0.75f
    const val MAX_SAMPLES = 8

    private const val MAGIC = 0x56503130 // "VP10"
    private const val HEADER_BYTES = 12
    private const val MAX_DIM = 2048

    fun clampThreshold(value: Float): Float = value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)

    /** Média dos embeddings do treino; null quando não há amostras válidas. */
    fun average(embeddings: List<FloatArray>): FloatArray? {
        if (embeddings.isEmpty()) return null
        val dim = embeddings.first().size
        if (dim == 0 || embeddings.any { it.size != dim || !it.isValid() }) return null
        val result = FloatArray(dim)
        for (embedding in embeddings) {
            for (i in 0 until dim) result[i] += embedding[i]
        }
        for (i in 0 until dim) result[i] /= embeddings.size.toFloat()
        return result
    }

    /** Similaridade de cosseno; 1 é a mesma voz, 0 é ortogonal. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size || !a.isValid() || !b.isValid()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA <= 0f || normB <= 0f) return 0f
        return (dot / kotlin.math.sqrt(normA * normB)).coerceIn(-1f, 1f)
    }

    /** Só aceita a fala quando há treino e a similaridade passa do limite. */
    fun isOwner(reference: FloatArray?, sample: FloatArray?, threshold: Float): Boolean {
        if (reference == null || sample == null) return false
        return cosine(reference, sample) >= clampThreshold(threshold)
    }

    /** Serializa amostras em binário compacto: magic, dim, count, floats little-endian. */
    fun encode(embeddings: List<FloatArray>): ByteArray {
        if (embeddings.isEmpty()) return ByteArray(0)
        val dim = embeddings.first().size
        require(dim in 1..MAX_DIM)
        require(embeddings.size in 1..MAX_SAMPLES)
        require(embeddings.all { it.size == dim && it.isValid() })
        val buffer = ByteBuffer.allocate(HEADER_BYTES + dim * embeddings.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(MAGIC).putInt(dim).putInt(embeddings.size)
        embeddings.forEach { embedding ->
            embedding.forEach { buffer.putFloat(it) }
        }
        return buffer.array()
    }

    /** Decodifica com validação rígida; retorna vazio para qualquer arquivo inválido. */
    fun decode(bytes: ByteArray): List<FloatArray> {
        if (bytes.size < HEADER_BYTES) return emptyList()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.int != MAGIC) return emptyList()
        val dim = buffer.int
        val count = buffer.int
        if (dim !in 1..MAX_DIM || count !in 1..MAX_SAMPLES) return emptyList()
        if (bytes.size != HEADER_BYTES + dim * count * 4) return emptyList()
        val result = ArrayList<FloatArray>(count)
        repeat(count) {
            val embedding = FloatArray(dim)
            repeat(dim) { i -> embedding[i] = buffer.float }
            if (!embedding.isValid()) return emptyList()
            result.add(embedding)
        }
        return result
    }

    private fun FloatArray.isValid(): Boolean = all { !it.isNaN() && !it.isInfinite() }
}
