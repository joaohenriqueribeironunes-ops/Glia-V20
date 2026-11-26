package com.glia.assistant.core

import android.app.ActivityManager
import android.content.Context
import java.io.File

/** Faca Android do calculo puro em [MemoryBudget]. */
object GliaMemory {
    const val SYSTEM_HEADROOM_BYTES = MemoryBudget.SYSTEM_HEADROOM_BYTES
    const val RUNTIME_RESERVE_BYTES = MemoryBudget.RUNTIME_RESERVE_BYTES
    const val MIN_AVAILABLE_FOR_MODEL = MemoryBudget.SYSTEM_HEADROOM_BYTES
    const val MIN_CONTEXT_TOKENS = MemoryBudget.MIN_CONTEXT_TOKENS
    const val MAX_CONTEXT_TOKENS = MemoryBudget.MAX_CONTEXT_TOKENS
    const val ESTIMATED_BYTES_PER_TOKEN = MemoryBudget.ESTIMATED_BYTES_PER_TOKEN

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

    fun plan(context: Context, modelSizeBytes: Long, requestedContext: Int): MemoryBudget.Plan =
        MemoryBudget.planBytes(availableBytes(context), modelSizeBytes, requestedContext)

    fun check(context: Context, modelSizeBytes: Long): Pair<Boolean, String> {
        val result = plan(context, modelSizeBytes, 1024)
        return result.ok to if (result.ok) "" else result.message
    }
}
