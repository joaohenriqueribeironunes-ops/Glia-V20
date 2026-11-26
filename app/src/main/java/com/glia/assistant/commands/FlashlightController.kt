package com.glia.assistant.commands

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/** Controla a lanterna pelo CameraManager, sem manter camera aberta. */
class FlashlightController(context: Context) {
    private val manager = context.applicationContext
        .getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var cameraId: String? = null
    private var lastKnownOn = false

    fun set(on: Boolean): Boolean {
        val cm = manager ?: return false
        val id = cameraId ?: findCamera(cm) ?: return false
        cameraId = id
        return try {
            cm.setTorchMode(id, on)
            lastKnownOn = on
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Retorna o novo estado, ou null quando o aparelho nao permite controlar a lanterna. */
    fun toggle(): Boolean? {
        val target = !lastKnownOn
        return if (set(target)) target else null
    }

    private fun findCamera(cm: CameraManager): String? {
        val ids = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        val withFlash = ids.filter { id ->
            runCatching {
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }.getOrDefault(false)
        }
        if (withFlash.isEmpty()) return null
        return withFlash.firstOrNull { id ->
            runCatching {
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            }.getOrDefault(false)
        } ?: withFlash.first()
    }
}
