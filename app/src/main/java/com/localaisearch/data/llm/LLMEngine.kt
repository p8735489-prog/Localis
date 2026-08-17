package com.localaisearch.data.llm

import com.localaisearch.data.model.InferenceConfig
import kotlinx.coroutines.flow.Flow

/**
 * Abstract LLM engine interface.
 * Decoupled from specific implementations (GGUF, etc.).
 *
 * This interface defines the contract for:
 * - Loading/unloading models
 * - Streaming token generation
 * - Configuration of inference parameters
 * - Status monitoring
 */
interface LLMEngine {

    /** Whether a model is currently loaded and ready for inference */
    val isLoaded: Boolean

    /** Display name of the currently loaded model, or null if none */
    val loadedModelName: String?

    /** Load a model from the given file path with the specified config */
    suspend fun loadModel(filePath: String, config: InferenceConfig): Result<Unit>

    /** Unload the currently loaded model and free memory */
    suspend fun unloadModel(): Result<Unit>

    /**
     * Generate text with streaming token output.
     * @param prompt The input prompt
     * @param config Inference configuration (temperature, top-p, top-k, etc.)
     * @return A Flow that emits tokens one at a time
     */
    fun generateStream(prompt: String, config: InferenceConfig): Flow<String>

    /**
     * Generate a complete response (non-streaming).
     * Collects all tokens and returns the full text.
     */
    suspend fun generate(prompt: String, config: InferenceConfig): Result<String>

    /**
     * Generate with a chat-formatted prompt.
     * @param messages List of (role, content) pairs
     * @param config Inference configuration
     * @return Streaming token flow
     */
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
