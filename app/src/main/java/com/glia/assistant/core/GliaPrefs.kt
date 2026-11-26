package com.glia.assistant.core

import android.content.Context
import android.content.SharedPreferences
import com.glia.assistant.tts.OfflineVoice
import com.glia.assistant.tts.OfflineVoices

/** Preferencias enxutas da V20.7: apenas ativacao do tile e subtitulo. */
object GliaPrefs {
    private const val NAME = "glia"

    const val KEY_TILE_ACTIVE = "tile_active"
    const val KEY_TILE_SUBTITLE = "tile_subtitle"
    const val KEY_MIC_GAIN = "mic_gain"
    const val KEY_VOICE_LOCK_ENABLED = "voice_lock_enabled"
    const val KEY_VOICE_THRESHOLD = "voice_threshold"
    const val KEY_NEURAL_VOICE_ID = "neural_voice_id"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isTileActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TILE_ACTIVE, false)

    fun setTileActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_TILE_ACTIVE, active).apply()
    }

    fun tileSubtitle(context: Context): String =
        prefs(context).getString(KEY_TILE_SUBTITLE, "Ouvindo tudo") ?: "Ouvindo tudo"

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

    /** V26: responde somente à voz treinada quando ativado. */
    fun voiceLockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VOICE_LOCK_ENABLED, false)

    fun setVoiceLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOICE_LOCK_ENABLED, enabled).apply()
    }

    /** Limite de similaridade (0.35..0.75; padrão 0.55). */
    fun voiceThreshold(context: Context): Float =
        com.glia.assistant.voice.VoicePrintMath.clampThreshold(
            prefs(context).getFloat(
                KEY_VOICE_THRESHOLD,
                com.glia.assistant.voice.VoicePrintMath.DEFAULT_THRESHOLD
            )
        )

    fun setVoiceThreshold(context: Context, threshold: Float) {
        val value = com.glia.assistant.voice.VoicePrintMath.clampThreshold(threshold)
        prefs(context).edit().putFloat(KEY_VOICE_THRESHOLD, value).apply()
    }

    /** V32: modelo neural offline selecionado (Dii por padrão). */
    fun neuralVoice(context: Context): OfflineVoice =
        OfflineVoices.fromId(prefs(context).getString(KEY_NEURAL_VOICE_ID, OfflineVoices.DII.id))

    fun setNeuralVoice(context: Context, voice: OfflineVoice) {
        prefs(context).edit().putString(KEY_NEURAL_VOICE_ID, voice.id).apply()
    }
}
