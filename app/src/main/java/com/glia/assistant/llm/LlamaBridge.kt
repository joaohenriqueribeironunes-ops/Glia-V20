package com.glia.assistant.llm

class LlamaBridge {
    companion object { init { System.loadLibrary("glia_native") } }

    external fun nativeLoadModel(modelPath: String, nCtx: Int, nThreads: Int): Int
    external fun nativeUnloadModel()
    external fun nativeIsLoaded(): Boolean
    external fun nativeGenerate(
        prompt: String,
        callback: TokenCallback,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        presencePenalty: Float,
        repetitionPenalty: Float,
        maxTokens: Int
    ): Boolean
    external fun nativeStopGeneration()
    external fun nativeIsGenerating(): Boolean
    external fun nativeGetLastError(): String?

    fun loadModel(path: String, contextSize: Int = 1024, threads: Int = 6): Boolean = nativeLoadModel(path, contextSize, threads) == 0
    fun unload() = nativeUnloadModel()
    fun isLoaded() = nativeIsLoaded()
    fun generate(
        prompt: String,
        callback: TokenCallback,
        params: GenerationParams = GenerationParams()
    ): Boolean = nativeGenerate(
        prompt,
        callback,
        params.temperature,
        params.topP,
        params.topK,
        params.minP,
        params.presencePenalty,
        params.repetitionPenalty,
        params.maxTokens
    )
    fun stop() = nativeStopGeneration()
    fun isGenerating() = nativeIsGenerating()
    fun lastError(): String = nativeGetLastError() ?: ""

    data class GenerationParams(
        val temperature: Float = 0.7f,
        val topP: Float = 0.80f,
        val topK: Int = 20,
        val minP: Float = 0.0f,
        val presencePenalty: Float = 1.5f,
        val repetitionPenalty: Float = 1.0f,
        val maxTokens: Int = 256
    )
}
