package com.glia.assistant.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.glia.assistant.core.GliaPrefs
import com.glia.assistant.ui.QuickOverlayActivity

class GliaTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(isActive())
    }

    override fun onClick() {
        super.onClick()
        if (isActive()) {
            // Desativa
            stopService(Intent(this, GliaVoiceService::class.java))
            setActive(false)
            updateTileState(false)
        } else {
            // Ativa escuta imediata (bypass wake word) - apenas com tile ligado
            setActive(true)
            updateTileState(true)
            // Inicia foreground service
            val svc = Intent(this, GliaVoiceService::class.java).apply {
                action = GliaVoiceService.ACTION_START_LISTENING
            }
            startForegroundService(svc)
            // Overlay transparente para garantir acesso ao mic (MIUI)
            val overlay = Intent(this, QuickOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivityAndCollapse(overlay)
        }
    }

    private fun isActive(): Boolean = GliaPrefs.isTileActive(this)

    private fun setActive(v: Boolean) = GliaPrefs.setTileActive(this, v)

    private fun updateTileState(active: Boolean) {
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Glia"
            updateTile()
        }
    }
}