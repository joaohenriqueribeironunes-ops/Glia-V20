package com.glia.assistant.core

/**
 * Logica pura (sem Android) do teto de memoria do modelo 4B.
 *
 * As mesmas formulas existem em `cpp/memory_budget.h` (usado pelo JNI) e sao
 * verificadas por vetores compartilhados nos testes JVM e nativos.
 */
object MemoryBudget {
    const val SYSTEM_HEADROOM_BYTES: Long = 1_342_177_280L
    const val RUNTIME_RESERVE_BYTES: Long = 268_435_456L
    const val MIN_CONTEXT_TOKENS: Int = 256
    const val MAX_CONTEXT_TOKENS: Int = 4096
    const val ESTIMATED_BYTES_PER_TOKEN: Long = 160_000L
    const val Q8_0_BYTES_PER_ELEMENT: Double = 1.0625
    const val F16_BYTES_PER_ELEMENT: Double = 2.0
    const val EXTRA_BYTES_PER_TOKEN: Double = 512.0
    const val MIN_LAYERS: Int = 1
    const val MIN_HEADS_KV: Int = 1

    data class Plan(
        val availableBytes: Long,
        val budgetBytes: Long,
        val contextTokens: Int,
        val ok: Boolean,
        val message: String
    )

    /** Bytes por token do KV cache (K + V), identico ao calculo nativo. */
    fun kvBytesPerToken(
        nLayer: Int,
        nHeadKv: Int,
        nEmbd: Int,
        quantized: Boolean = true
    ): Double {
        val layers = maxOf(MIN_LAYERS, nLayer).toDouble()
        val heads = maxOf(MIN_HEADS_KV, nHeadKv).toDouble()
        val headDim = maxOf(1, nEmbd / maxOf(MIN_HEADS_KV, nHeadKv))
        val perElement = if (quantized) Q8_0_BYTES_PER_ELEMENT else F16_BYTES_PER_ELEMENT
        return 2.0 * layers * heads * headDim.toDouble() * perElement + EXTRA_BYTES_PER_TOKEN
    }

    /**
     * Contexto maximo que cabe no orcamento. Nunca menor que 256 e nunca maior que 4096.
     * Retorna 256 quando o orcamento nao alcanca nem o minimo (o chamador deve checar
     * [fitsMinimum] antes de usar o valor).
     */
    fun maxContextByBudget(
        budgetBytes: Long,
        nLayer: Int,
        nHeadKv: Int,
        nEmbd: Int,
        quantized: Boolean = true
    ): Int {
        if (budgetBytes <= 0L) return MIN_CONTEXT_TOKENS
        val perToken = kvBytesPerToken(nLayer, nHeadKv, nEmbd, quantized)
        val fit = (budgetBytes / perToken).toLong()
        return when {
            fit < MIN_CONTEXT_TOKENS -> MIN_CONTEXT_TOKENS
            fit > MAX_CONTEXT_TOKENS -> MAX_CONTEXT_TOKENS
            else -> fit.toInt()
        }
    }

    fun fitsMinimum(
        budgetBytes: Long,
        nLayer: Int,
        nHeadKv: Int,
        nEmbd: Int,
        quantized: Boolean = true
    ): Boolean {
        if (budgetBytes <= 0L) return false
        return budgetBytes.toDouble() >=
            MIN_CONTEXT_TOKENS * kvBytesPerToken(nLayer, nHeadKv, nEmbd, quantized)
    }

    /**
     * Plano antes de conhecer os metadados do modelo: usa estimativa conservadora
     * de bytes/token e devolve o contexto candidato. O JNI refina com n_layer real.
     */
    fun planBytes(
        availableBytes: Long,
        modelSizeBytes: Long,
        requestedContext: Int
    ): Plan {
        if (availableBytes <= 0L) {
            return Plan(
                0L, 0L,
                requestedContext.coerceIn(MIN_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS),
                true,
                "Memoria indisponivel para medicao; usando contexto solicitado"
            )
        }
        val usable = availableBytes - SYSTEM_HEADROOM_BYTES - RUNTIME_RESERVE_BYTES
        val budget = usable - modelSizeBytes
        val minimum = MIN_CONTEXT_TOKENS * ESTIMATED_BYTES_PER_TOKEN
        if (budget < minimum) {
            return Plan(
                availableBytes, budget, MIN_CONTEXT_TOKENS, false,
                "Memoria livre (${availableBytes / 1024 / 1024} MB) insuficiente: restam " +
                    "${(budget / 1024 / 1024).coerceAtLeast(0)} MB para KV apos reservar " +
                    "${SYSTEM_HEADROOM_BYTES / 1024 / 1024} MB de sistema e " +
                    "${modelSizeBytes / 1024 / 1024} MB de modelo."
            )
        }
        val estimatedFit = budget / ESTIMATED_BYTES_PER_TOKEN
        val ctx = requestedContext
            .coerceAtMost(estimatedFit.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .coerceIn(MIN_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS)
        val note = if (ctx < requestedContext) " (contexto reduzido de $requestedContext para $ctx)" else ""
        return Plan(
            availableBytes, budget, ctx, true,
            "Budget KV ${budget / 1024 / 1024} MB; contexto $ctx$note"
        )
    }

    fun usableBytes(availableBytes: Long): Long =
        availableBytes - SYSTEM_HEADROOM_BYTES - RUNTIME_RESERVE_BYTES

    fun budgetBytes(availableBytes: Long, modelSizeBytes: Long): Long =
        usableBytes(availableBytes) - modelSizeBytes
}
