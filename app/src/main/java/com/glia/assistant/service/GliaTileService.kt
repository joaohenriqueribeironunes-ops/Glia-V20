package com.glia.assistant.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.glia.assistant.core.GliaPrefs

/** Liga/desliga a escuta direta de comandos. */
class GliaTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(isActive())
    }

    override fun onClick() {
        super.onClick()
        val active = !isActive()
        setActive(active)
        updateTileState(active)
        if (active) {
            // Sem action: o servico entra no loop de comandos.
            startForegroundService(Intent(this, GliaVoiceService::class.java))
        } else {
            stopService(Intent(this, GliaVoiceService::class.java))
        }
    }

    private fun isActive(): Boolean = GliaPrefs.isTileActive(this)

    private fun setActive(value: Boolean) {
        GliaPrefs.setTileActive(this, value)
        GliaPrefs.setTileSubtitle(this, if (value) "Ouvindo tudo" else "Desligada")
    }

    private fun updateTileState(active: Boolean) {
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Glia"
            subtitle = if (active) "Ouvindo tudo" else "Desligada"
            updateTile()
        }
    }
}
