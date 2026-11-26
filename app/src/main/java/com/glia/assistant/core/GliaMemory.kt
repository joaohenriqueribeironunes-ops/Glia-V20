package com.glia.assistant.core

import android.app.ActivityManager
import android.content.Context
import java.io.File

object GliaMemory {
    /** Headroom de sistema observado em mecanismos de admissao de modelos mobile (~1,25 GiB). */
    const val SYSTEM_HEADROOM_BYTES: Long = 1_342_177_280L
    /** Reserva para runtime (JNI, buffers de audio, STT/TTS) fora do KV-cache. */
    const val RUNTIME_RESERVE_BYTES: Long = 268_435_456L
    const val MIN_AVAILABLE_FOR_MODEL: Long = SYSTEM_HEADROOM_BYTES
    const val MIN_CONTEXT_TOKENS = 256
    const val MAX_CONTEXT_TOKENS = 4096
    /** Estimativa conservadora antes de o JNI medir os metadados reais do modelo. */
    const val ESTIMATED_BYTES_PER_TOKEN = 160_000L

    data class Plan(
        val availableBytes: Long,
        val budgetBytes: Long,
        val contextTokens: Int,
        val ok: Boolean,
        val message: String
    )

    fun availableBytes(context: Context): Long {
        var found = 0L
        try {
            File("/proc/meminfo").forEachLine { line ->
                if (line.startsWith("MemAvailable:")) {
                    val kb = line.substringAfter(":").trim().substringBefore(" ").toLongOrNull()
                    if (kb != null && kb > 0) found = kb * 1024L
                }
            }
            if (found > 0L) return found
        } catch (_: Exception) {
            // fallback abaixo
        }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        return if (am != null && runCatching { am.getMemoryInfo(info) }.isSuccess) info.availMem else 0L
    }

    /**
     * Calcula um plano que mantem o modelo abaixo do teto de memoria disponivel:
     * MemAvailable - headroom do sistema - reserva de runtime - tamanho do arquivo.
     * O saldo e o budget exclusivo do KV-cache; o JNI ainda reduz o contexto usando
     * os metadados reais (n_layer, n_head_kv, n_embd).
     */
    fun plan(context: Context, modelSizeBytes: Long, requestedContext: Int): Plan {
        val available = availableBytes(context)
        if (available <= 0L) {
            return Plan(0L, 0L, requestedContext.coerceIn(MIN_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS),
                true, "Memoria indisponivel para medicao; usando contexto solicitado")
        }
        val usable = available - SYSTEM_HEADROOM_BYTES - RUNTIME_RESERVE_BYTES
        val budget = usable - modelSizeBytes
        if (budget < MIN_CONTEXT_TOKENS * ESTIMATED_BYTES_PER_TOKEN) {
            return Plan(available, budget, MIN_CONTEXT_TOKENS, false,
                "Memoria livre (${available / 1024 / 1024} MB) insuficiente: restam " +
                    "${(budget / 1024 / 1024).coerceAtLeast(0)} MB para KV apos reservar " +
                    "${SYSTEM_HEADROOM_BYTES / 1024 / 1024} MB de sistema e " +
                    "${modelSizeBytes / 1024 / 1024} MB de modelo.")
        }
        val estimatedFit = (budget / ESTIMATED_BYTES_PER_TOKEN).toInt()
        val ctx = requestedContext.coerceAtMost(estimatedFit).coerceIn(MIN_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS)
        val note = if (ctx < requestedContext) " (contexto reduzido de $requestedContext para $ctx)" else ""
        return Plan(available, budget, ctx, true,
            "Budget KV ${budget / 1024 / 1024} MB; contexto $ctx$note")
    }

    fun check(context: Context, modelSizeBytes: Long): Pair<Boolean, String> {
        val result = plan(context, modelSizeBytes, 1024)
        return result.ok to if (result.ok) "" else result.message
    }
}
