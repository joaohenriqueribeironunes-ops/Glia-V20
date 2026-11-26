package com.glia.assistant.core

import android.content.Context
import android.content.SharedPreferences

object GliaPrefs {
    private const val NAME = "glia"

    const val KEY_TILE_ACTIVE = "tile_active"
    const val KEY_MODEL_PATH = "model_path"
    const val KEY_MODEL_SOURCE = "model_source"
    const val KEY_CONTEXT_SIZE = "context_size"
    const val KEY_THREADS = "threads"
    const val KEY_TEMPERATURE = "temperature"
    const val KEY_TOP_P = "top_p"
    const val KEY_TOP_K = "top_k"
    const val KEY_MIN_P = "min_p"
    const val KEY_PRESENCE_PENALTY = "presence_penalty"
    const val KEY_REPEAT_PENALTY = "repeat_penalty"
    const val KEY_MAX_TOKENS = "max_tokens"
    const val KEY_TILE_SUBTITLE = "tile_subtitle"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isTileActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TILE_ACTIVE, false)

    fun setTileActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_TILE_ACTIVE, active).apply()
    }

    fun modelPath(context: Context): String? = prefs(context).getString(KEY_MODEL_PATH, null)

    fun setModelPath(context: Context, path: String?) {
        prefs(context).edit().putString(KEY_MODEL_PATH, path?.takeIf { it.isNotBlank() })?.apply()
    }

    fun modelSource(context: Context): String =
        prefs(context).getString(KEY_MODEL_SOURCE, "embedded_2b") ?: "embedded_2b"

    fun setModelSource(context: Context, source: String) {
        val value = if (source == "user_4b") "user_4b" else "embedded_2b"
        prefs(context).edit().putString(KEY_MODEL_SOURCE, value).apply()
    }

    fun contextSize(context: Context): Int = prefs(context).getInt(KEY_CONTEXT_SIZE, 1024).coerceIn(512, 4096)
    fun setContextSize(context: Context, v: Int) = prefs(context).edit().putInt(KEY_CONTEXT_SIZE, v.coerceIn(512, 4096)).apply()

    fun threads(context: Context): Int = prefs(context).getInt(KEY_THREADS, 6).coerceIn(2, 8)
    fun setThreads(context: Context, v: Int) = prefs(context).edit().putInt(KEY_THREADS, v.coerceIn(2, 8)).apply()

    fun temperature(context: Context): Float = prefs(context).getFloat(KEY_TEMPERATURE, 0.7f).coerceIn(0f, 1.5f)
    fun setTemperature(context: Context, v: Float) = prefs(context).edit().putFloat(KEY_TEMPERATURE, v.coerceIn(0f, 1.5f)).apply()

    fun topP(context: Context): Float = prefs(context).getFloat(KEY_TOP_P, 0.80f).coerceIn(0.05f, 0.98f)
    fun setTopP(context: Context, v: Float) = prefs(context).edit().putFloat(KEY_TOP_P, v.coerceIn(0.05f, 0.98f)).apply()

    fun topK(context: Context): Int = prefs(context).getInt(KEY_TOP_K, 20).coerceIn(1, 100)
    fun setTopK(context: Context, v: Int) = prefs(context).edit().putInt(KEY_TOP_K, v.coerceIn(1, 100)).apply()

    fun minP(context: Context): Float = prefs(context).getFloat(KEY_MIN_P, 0.0f).coerceIn(0f, 0.95f)
    fun setMinP(context: Context, v: Float) = prefs(context).edit().putFloat(KEY_MIN_P, v.coerceIn(0f, 0.95f)).apply()

    fun presencePenalty(context: Context): Float = prefs(context).getFloat(KEY_PRESENCE_PENALTY, 1.5f).coerceIn(-2f, 2f)
    fun setPresencePenalty(context: Context, v: Float) = prefs(context).edit().putFloat(KEY_PRESENCE_PENALTY, v.coerceIn(-2f, 2f)).apply()

    fun repeatPenalty(context: Context): Float = prefs(context).getFloat(KEY_REPEAT_PENALTY, 1.0f).coerceIn(0.8f, 1.5f)
    fun setRepeatPenalty(context: Context, v: Float) = prefs(context).edit().putFloat(KEY_REPEAT_PENALTY, v.coerceIn(0.8f, 1.5f)).apply()

    fun maxTokens(context: Context): Int = prefs(context).getInt(KEY_MAX_TOKENS, 256).coerceIn(64, 1024)
    fun setMaxTokens(context: Context, v: Int) = prefs(context).edit().putInt(KEY_MAX_TOKENS, v.coerceIn(64, 1024)).apply()

    fun tileSubtitle(context: Context): String =
        prefs(context).getString(KEY_TILE_SUBTITLE, "desligada") ?: "desligada"

    fun setTileSubtitle(context: Context, subtitle: String) {
        prefs(context).edit().putString(KEY_TILE_SUBTITLE, subtitle).apply()
    }
}
