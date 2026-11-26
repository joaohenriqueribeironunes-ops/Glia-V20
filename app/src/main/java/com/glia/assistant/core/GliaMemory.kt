package com.glia.assistant.core

import android.app.ActivityManager
import android.content.Context

object GliaMemory {
    const val MIN_AVAILABLE_FOR_MODEL: Long = 1_800_000_000L

    fun check(context: Context, modelSizeBytes: Long): Pair<Boolean, String> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        val hasInfo = am?.getMemoryInfo(mi) ?: false
        if (hasInfo == false) return true to ""
        val needed = modelSizeBytes + 512_000_000L + 256_000_000L
        val okFree = mi.availMem >= needed
        return okFree to if (okFree) "" else "Memoria livre (${mi.availMem / 1024 / 1024} MB) abaixo do teto recomendado para este modelo."
    }
}
