package com.glia.assistant.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class GliaMode { IDLE, LISTENING, THINKING, SPEAKING }

object GliaState {
    private val _mode = MutableStateFlow(GliaMode.IDLE)
    val mode: StateFlow<GliaMode> = _mode
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude
    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText
    private val _responseText = MutableStateFlow("")
    val responseText: StateFlow<String> = _responseText

    private val _modelStatus = MutableStateFlow("inicializando")
    val modelStatus: StateFlow<String> = _modelStatus
    private val _ttsStatus = MutableStateFlow("inicializando")
    val ttsStatus: StateFlow<String> = _ttsStatus
    private val _sttStatus = MutableStateFlow("inicializando")
    val sttStatus: StateFlow<String> = _sttStatus
    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError
    private val _generationStats = MutableStateFlow("")
    val generationStats: StateFlow<String> = _generationStats

    fun setMode(m: GliaMode) { _mode.value = m }
    fun setAmplitude(a: Float) { _amplitude.value = a }
    fun setPartial(t: String) { _partialText.value = t }
    fun setResponse(t: String) { _responseText.value = t }
    fun appendResponse(t: String) { _responseText.value += t }
    fun setModelStatus(s: String) { _modelStatus.value = s }
    fun setTtsStatus(s: String) { _ttsStatus.value = s }
    fun setSttStatus(s: String) { _sttStatus.value = s }
    fun setLastError(s: String) { _lastError.value = s }
    fun setGenerationStats(ms: Long, tokens: Int) { _generationStats.value = "${ms}ms, $tokens tokens" }

    fun setDetailedGenerationStats(text: String) { _generationStats.value = text }

    fun resetForCommand() {
        _responseText.value = ""
        _partialText.value = ""
        _lastError.value = ""
    }
}
