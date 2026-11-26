package com.glia.assistant.core

import android.app.ActivityManager
import android.content.Context

object GliaMemory {
    /** Headroom de sistema observado em mecanismos de admissao de modelos mobile (~1,25 GiB). */
    const val SYSTEM_HEADROOM_BYTES: Long = 1_342_177_280L
    const val MIN_AVAILABLE_FOR_MODEL: Long = SYSTEM_HEADROOM_BYTES

    fun check(context: Context, modelSizeBytes: Long): Pair<Boolean, String> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        val hasInfo = am?.getMemoryInfo(mi) ?: false
        if (hasInfo == false) return true to ""
        val needed = modelSizeBytes + SYSTEM_HEADROOM_BYTES
        val okFree = mi.availMem >= needed
        return okFree to if (okFree) "" else "Memoria livre (${mi.availMem / 1024 / 1024} MB) abaixo do teto de ${needed / 1024 / 1024} MB para este modelo."
    }
}
