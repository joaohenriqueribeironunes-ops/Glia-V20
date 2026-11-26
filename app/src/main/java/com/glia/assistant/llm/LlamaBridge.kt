package com.glia.assistant.llm

object LlamaBridge {
    data class GenerationParams(
        val temperature: Float = 0.7f,
        val topP: Float = 0.8f,
        val topK: Int = 20,
        val minP: Float = 0.0f,
        val presencePenalty: Float = 1.5f,
        val repetitionPenalty: Float = 1.0f,
        val maxTokens: Int = 256,
    )

    init {
        System.loadLibrary("glia_native")
    }

    @JvmStatic external fun nativeLoadModel(
        modelPath: String,
        nCtx: Int,
        nThreads: Int,
        budgetBytes: Long,
    ): Int
    @JvmStatic external fun nativeUnloadModel()
    @JvmStatic external fun nativeIsLoaded(): Boolean
    @JvmStatic external fun nativeGenerate(
        prompt: String,
        callback: TokenCallback,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        presencePenalty: Float,
        repetitionPenalty: Float,
        maxTokens: Int,
    ): Boolean
    @JvmStatic external fun nativeStopGeneration()
    @JvmStatic external fun nativeIsGenerating(): Boolean
    @JvmStatic external fun nativeGetLastError(): String?
    @JvmStatic external fun nativeLastGenerationStats(): LongArray
    @JvmStatic external fun nativeCountTokens(text: String): Int
    @JvmStatic external fun nativeContextSize(): Int
    @JvmStatic external fun nativeUsesQuantizedKv(): Boolean

    fun loadModel(
        modelPath: String,
        contextSize: Int = 1024,
        threads: Int = 6,
        budgetBytes: Long = -1L,
    ): Boolean = nativeLoadModel(modelPath, contextSize, threads, budgetBytes) == 0

    fun unload() = nativeUnloadModel()

    fun isLoaded(): Boolean = nativeIsLoaded()

    fun generate(prompt: String, callback: TokenCallback, params: GenerationParams): Boolean =
        nativeGenerate(
            prompt,
            callback,
            params.temperature,
            params.topP,
            params.topK,
            params.minP,
            params.presencePenalty,
            params.repetitionPenalty,
            params.maxTokens,
        )

    fun stop() = nativeStopGeneration()

    fun isGenerating(): Boolean = nativeIsGenerating()

    fun lastError(): String? = nativeGetLastError()

    /** [promptTokens, generatedTokens, prefillMs, decodeMs, tpsX100, reusedPrefixTokens] */
    fun lastGenerationStats(): LongArray = nativeLastGenerationStats()

    fun countTokens(text: String): Int = nativeCountTokens(text)

    fun contextSize(): Int = nativeContextSize()

    fun usesQuantizedKv(): Boolean = nativeUsesQuantizedKv()
}
