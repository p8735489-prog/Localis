package com.localaisearch.data.llm

import com.localaisearch.data.model.InferenceConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stub LLM Provider - provides placeholder responses when no real engine is available.
 * Used as fallback when llama.cpp is not linked.
 */
class StubLLMProvider : LLMEngine {
    override val providerName: String = "Stub"
    override val providerType: LLMProviderType = LLMProviderType.STUB
    override val isAvailable: Boolean = true
    override val isLoaded: Boolean = true
    override val loadedModelName: String? = "Stub Provider"

    private var stopRequested = false

    override suspend fun loadModel(filePath: String, config: InferenceConfig): Result<Unit> =
        Result.success(Unit)

    override suspend fun unloadModel(): Result<Unit> = Result.success(Unit)

    override fun generateStream(prompt: String, config: InferenceConfig): Flow<String> = flow {
        val response = buildResponse(prompt)
        val words = response.split(" ")
        for (word in words) {
            if (stopRequested) break
            emit("$word ")
            delay(50)
        }
        emit("\n")
    }

    override suspend fun generate(prompt: String, config: InferenceConfig): Result<String> =
        Result.success(buildResponse(prompt))

    override fun chatStream(messages: List<Pair<String, String>>, config: InferenceConfig): Flow<String> =
        generateStream(messages.lastOrNull()?.second ?: "", config)

    override suspend fun stopGeneration() {
        stopRequested = true
    }

    override fun getMemoryUsage(): Long? = null
    override fun isGpuAvailable(): Boolean = false
    override fun release() {}

    private fun buildResponse(prompt: String): String {
        return when {
            prompt.contains("news", ignoreCase = true) || prompt.contains("today", ignoreCase = true) ->
                "I don't have real-time internet access in this mode. Please enable internet search in Settings > Network & Search, or load a local GGUF model for on-device inference."
            prompt.contains("remember", ignoreCase = true) || prompt.contains("memory", ignoreCase = true) ->
                "Memory system is active. Your memories will be saved and retrieved in future conversations."
            else ->
                "[Local AI is in stub mode]\n\nNo GGUF model is loaded. To use local AI inference:\n1. Go to Models and import a GGUF model\n2. Load the model\n3. Start chatting\n\nOr enable internet search in Settings for web-based answers.\n\nYour question: \"${prompt.take(80)}${if (prompt.length > 80) "..." else ""}\""
        }
    }
}
