package com.localaisearch.data.llm

import android.content.Context
import android.util.Log
import com.localaisearch.data.model.InferenceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GGUF-specific LLM engine implementation using llama.cpp via JNI.
 *
 * This is the ONLY supported model format. No ONNX, QNN, MLX, or other formats.
 *
 * Features:
 * - GGUF model loading/unloading
 * - Token streaming generation
 * - Configurable: Context, Temperature, Top-P, Top-K, Repeat Penalty
 * - CPU/GPU acceleration (via llama.cpp Vulkan/OpenCL backend)
 * - Thread control for performance tuning
 *
 * Thread-safety: All operations are synchronized to prevent concurrent native access.
 */
class GGUFEngine(context: Context? = null) : LLMEngine {

    companion object {
        private const val TAG = "GGUFEngine"
    }

    override val providerName: String = "Local GGUF"
    override val providerType: LLMProviderType = LLMProviderType.LOCAL_GGUF
    override val isAvailable: Boolean = LlamaBridge.initialize()

    private val lock = Any()

    @Volatile
    private var modelHandle: Long = 0L

    @Volatile
    private var contextHandle: Long = 0L

    @Volatile
    private var currentConfig: InferenceConfig? = null

    private val isGenerating = AtomicBoolean(false)

    private val stopRequested = AtomicBoolean(false)

    private var nativeAvailable: Boolean = false

    init {
        nativeAvailable = LlamaBridge.initialize()
        if (nativeAvailable) {
            Log.i(TAG, "llama.cpp ${LlamaBridge.nativeGetVersion()} loaded successfully")
        } else {
            Log.w(TAG, "Native library not available - running in stub mode. " +
                    "Build llama.cpp and link via CMake to enable local inference.")
        }
    }

    override val isLoaded: Boolean
        get() = synchronized(lock) { modelHandle > 0L }

    override val loadedModelName: String?
        get() = synchronized(lock) {
            if (modelHandle > 0L) currentModelPath?.substringAfterLast('/') else null
        }

    private var currentModelPath: String? = null

    override suspend fun loadModel(filePath: String, config: InferenceConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (modelHandle > 0L) {
                    unloadModelInternal()
                }

                if (!nativeAvailable) {
                    Log.w(TAG, "Native library not loaded. Model loading skipped (stub mode).")
                    currentModelPath = filePath
                    currentConfig = config
                    return@withContext Result.success(Unit)
                }

                try {
                    val handle = LlamaBridge.nativeLoadModel(
                        filePath,
                        config.contextLength,
                        config.threads,
                        config.useGpu,
                        config.gpuLayers
                    )
                    if (handle <= 0L) {
                        return@withContext Result.failure(
                            IllegalStateException("Failed to load GGUF model: $filePath")
                        )
                    }
                    modelHandle = handle
                    currentModelPath = filePath
                    currentConfig = config
                    Log.i(TAG, "Model loaded: ${filePath.substringAfterLast('/')}")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Model loading failed", e)
                    Result.failure(e)
                }
            }
        }

    override suspend fun unloadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            unloadModelInternal()
            Result.success(Unit)
        }
    }

    private fun unloadModelInternal() {
        stopGenerationInternal()
        if (contextHandle > 0L) {
            if (nativeAvailable) LlamaBridge.nativeFreeContext(contextHandle)
            contextHandle = 0L
        }
        if (modelHandle > 0L) {
            if (nativeAvailable) LlamaBridge.nativeFreeModel(modelHandle)
            modelHandle = 0L
        }
        currentModelPath = null
        currentConfig = null
    }

    override fun generateStream(prompt: String, config: InferenceConfig): Flow<String> = flow {
        if (!isLoaded) {
            throw IllegalStateException("No model loaded")
        }

        isGenerating.set(true)
        stopRequested.set(false)

        try {
            if (nativeAvailable) {
                synchronized(lock) {
                    if (contextHandle > 0L) {
                        LlamaBridge.nativeFreeContext(contextHandle)
                    }
                    contextHandle = LlamaBridge.nativeInitContext(
                        modelHandle,
                        config.temperature,
                        config.topP,
                        config.topK,
                        config.repeatPenalty,
                        config.maxTokens,
                        config.seed
                    )
                    LlamaBridge.nativePrompt(contextHandle, prompt)
                }

                while (!stopRequested.get()) {
                    val token: String? = synchronized(lock) {
                        if (contextHandle > 0L) LlamaBridge.nativeGenerateNext(contextHandle) else null
                    }
                    if (token == null) break
                    emit(token)
                }
            } else {
                // Stub mode: emit a placeholder response
                emit("[Native library not available. Build llama.cpp to enable local GGUF inference.]\n")
                emit("Prompt received: ${prompt.take(100)}...\n")
                emit("Config: temp=${config.temperature}, topP=${config.topP}, topK=${config.topK}")
            }
        } finally {
            isGenerating.set(false)
            stopRequested.set(false)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generate(prompt: String, config: InferenceConfig): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val sb = StringBuilder()
                generateStream(prompt, config).collect { token ->
                    sb.append(token)
                    ensureActive()
                }
                Result.success(sb.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                Result.failure(e)
            }
        }

    override fun chatStream(
        messages: List<Pair<String, String>>,
        config: InferenceConfig
    ): Flow<String> {
        val prompt = buildChatPrompt(messages)
        return generateStream(prompt, config)
    }

    override suspend fun stopGeneration() {
        stopGenerationInternal()
    }

    private fun stopGenerationInternal() {
        if (isGenerating.get()) {
            stopRequested.set(true)
            if (nativeAvailable && contextHandle > 0L) {
                try {
                    LlamaBridge.nativeStopGeneration(contextHandle)
                } catch (_: Exception) { }
            }
        }
    }

    override fun getMemoryUsage(): Long? {
        if (!nativeAvailable || modelHandle <= 0L) return null
        return try {
            LlamaBridge.nativeGetMemoryUsage(modelHandle)
        } catch (_: Exception) {
            null
        }
    }

    override fun isGpuAvailable(): Boolean {
        if (!nativeAvailable) return false
        return try {
            LlamaBridge.nativeIsGpuAvailable()
        } catch (_: Exception) {
            false
        }
    }

    override fun release() {
        synchronized(lock) {
            unloadModelInternal()
        }
    }

    /**
     * Build a chat-formatted prompt from message list.
     * Uses a simple chat template compatible with most GGUF models.
     */
    private fun buildChatPrompt(messages: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        for ((role, content) in messages) {
            when (role.lowercase()) {
                "system" -> sb.append("<|system|>\n$content<|end|>\n")
                "user" -> sb.append("<|user|>\n$content<|end|>\n")
                "assistant" -> sb.append("<|assistant|>\n$content<|end|>\n")
            }
        }
        sb.append("<|assistant|>\n")
        return sb.toString()
    }
}
