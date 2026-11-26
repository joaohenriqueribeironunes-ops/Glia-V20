package com.glia.assistant.core

import android.content.Context
import android.content.SharedPreferences

/** Preferencias enxutas da V20.7: apenas ativacao do tile e subtitulo. */
object GliaPrefs {
    private const val NAME = "glia"

    const val KEY_TILE_ACTIVE = "tile_active"
    const val KEY_TILE_SUBTITLE = "tile_subtitle"
    const val KEY_MIC_GAIN = "mic_gain"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isTileActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TILE_ACTIVE, false)

    fun setTileActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_TILE_ACTIVE, active).apply()
    }

    fun tileSubtitle(context: Context): String =
        prefs(context).getString(KEY_TILE_SUBTITLE, "Ola Glia") ?: "Ola Glia"

    fun setTileSubtitle(context: Context, subtitle: String) {
        prefs(context).edit().putString(KEY_TILE_SUBTITLE, subtitle).apply()
    }

    /** Ganho do microfone em 1x..4x; 2x é o padrão. */
    fun micGain(context: Context): Float =
        prefs(context).getFloat(KEY_MIC_GAIN, com.glia.assistant.stt.MicGain.DEFAULT)

    fun setMicGain(context: Context, gain: Float) {
        val value = com.glia.assistant.stt.MicGain.clamp(gain)
        prefs(context).edit().putFloat(KEY_MIC_GAIN, value).apply()
    }
}
