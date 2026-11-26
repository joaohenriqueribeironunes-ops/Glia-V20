package com.glia.assistant.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Único modo ativo: LISTENING. IDLE existe somente com o tile desligado. */
enum class GliaMode { IDLE, LISTENING }

object GliaState {
    private val _mode = MutableStateFlow(GliaMode.IDLE)
    val mode: StateFlow<GliaMode> = _mode.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _responseText = MutableStateFlow("")
    val responseText: StateFlow<String> = _responseText.asStateFlow()

    private val _assistantStatus = MutableStateFlow("desligada")
    val assistantStatus: StateFlow<String> = _assistantStatus.asStateFlow()

    private val _ttsStatus = MutableStateFlow("inicializando")
    val ttsStatus: StateFlow<String> = _ttsStatus.asStateFlow()

    private val _sttStatus = MutableStateFlow("inicializando")
    val sttStatus: StateFlow<String> = _sttStatus.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun setMode(mode: GliaMode) {
        _mode.value = mode
    }

    fun setAmplitude(value: Float) {
        _amplitude.value = value.coerceIn(0f, 1f)
    }

    fun setPartial(text: String) {
        _partialText.value = text
    }

    fun setResponse(text: String) {
        _responseText.value = text
    }

    fun setAssistantStatus(text: String) {
        _assistantStatus.value = text
    }

    fun setTtsStatus(text: String) {
        _ttsStatus.value = text
    }

    fun setSttStatus(text: String) {
        _sttStatus.value = text
    }

    fun setLastError(message: String?) {
        _lastError.value = message
    }

    fun resetForCommand() {
        _partialText.value = ""
        _responseText.value = ""
        _lastError.value = null
    }
}
