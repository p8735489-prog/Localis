package com.localaisearch.data.llm

import com.localaisearch.data.model.InferenceConfig
import kotlinx.coroutines.flow.Flow

/**
 * Abstract LLM engine interface.
 * Decoupled from specific implementations (GGUF, API, etc.).
 */
interface LLMEngine {
    val providerName: String
    val providerType: LLMProviderType
    
    /** Whether this provider is available (native library loaded, API key set, etc.) */
    val isAvailable: Boolean

    /** Whether a model is currently loaded and ready for inference */
    val isLoaded: Boolean

    /** Display name of the currently loaded model, or null if none */
    val loadedModelName: String?

    /** Load a model from the given file path with the specified config */
    suspend fun loadModel(filePath: String, config: InferenceConfig): Result<Unit>

    /** Unload the currently loaded model and free memory */
    suspend fun unloadModel(): Result<Unit>

    /** Generate text with streaming token output */
    fun generateStream(prompt: String, config: InferenceConfig): Flow<String>

    /** Generate a complete response (non-streaming) */
    suspend fun generate(prompt: String, config: InferenceConfig): Result<String>

    /** Generate with a chat-formatted prompt */
    fun chatStream(messages: List<Pair<String, String>>, config: InferenceConfig): Flow<String>

    /** Stop ongoing generation */
    suspend fun stopGeneration()

    /** Get current memory usage in bytes, or null if unavailable */
    fun getMemoryUsage(): Long?

    /** Whether GPU acceleration is available on this device */
    fun isGpuAvailable(): Boolean

    /** Release all resources */
    fun release()
}

enum class LLMProviderType {
    LOCAL_GGUF,      // llama.cpp local inference
    OPENAI_COMPATIBLE, // OpenAI-compatible API (Ollama, vLLM, etc.)
    STUB             // Fallback stub for testing
}

/**
 * Status of the LLM engine.
 */
sealed class LLMEngineStatus {
    object Idle : LLMEngineStatus()
    data class Loading(val progress: Float) : LLMEngineStatus()
    object Ready : LLMEngineStatus()
    data class Generating(val tokensPerSecond: Float) : LLMEngineStatus()
    data class Error(val message: String) : LLMEngineStatus()
}
