package com.localaisearch.data.llm

import android.content.Context
import android.util.Log
import com.localaisearch.data.error.GlobalErrorHandler
import com.localaisearch.data.model.InferenceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GGUF-specific LLM engine implementation using llama.cpp via JNI.
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

        init {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
                GlobalErrorHandler.emitJni(throwable.message ?: "Uncaught native exception")
            }
        }
    }

    override val providerName: String = "Local GGUF"
    override val providerType: LLMProviderType = LLMProviderType.LOCAL_GGUF
    override val isAvailable: Boolean = LlamaBridge.initialize()

    private val lock = Any()

    @Volatile
    private var modelHandle: Long = 0L

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
                    nativeAvailable = LlamaBridge.initialize()
                }
                if (!nativeAvailable) {
                    val message = "Local GGUF engine is unavailable: libllama_bridge.so was not loaded for this device ABI. Rebuild/install the version with native llama.cpp enabled."
                    Log.e(TAG, message)
                    GlobalErrorHandler.emitModel(message)
                    return@withContext Result.failure(IllegalStateException(message))
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
                        val nativeReason = runCatching { LlamaBridge.nativeGetLastError() }.getOrNull().orEmpty()
                        val message = nativeReason.ifBlank {
                            "GGUF model could not be loaded by the bundled llama.cpp engine. Check GGUF metadata, model architecture support, file access, and available RAM. File: $filePath"
                        }
                        GlobalErrorHandler.emitModel(message)
                        return@withContext Result.failure(IllegalStateException(message))
                    }
                    modelHandle = handle
                    currentModelPath = filePath
                    currentConfig = config
                    Log.i(TAG, "Model loaded: ${filePath.substringAfterLast('/')}")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Model loading failed", e)
                    GlobalErrorHandler.emitModel(e.message ?: "Unknown")
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
        if (modelHandle > 0L) {
            if (nativeAvailable) {
                try {
                    LlamaBridge.nativeUnloadModel(modelHandle)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unload model", e)
                }
            }
            modelHandle = 0L
        }
        currentModelPath = null
        currentConfig = null
    }

    override fun generateStream(prompt: String, config: InferenceConfig): Flow<String> = callbackFlow {
        if (!isLoaded) {
            close(IllegalStateException("No model loaded"))
            return@callbackFlow
        }

        isGenerating.set(true)
        stopRequested.set(false)
        val generationJob = launch(Dispatchers.IO) {
            try {
                synchronized(lock) {
                    if (modelHandle <= 0L) throw IllegalStateException("Model was unloaded before generation started")
                    // Thinking depth controls the available generation budget. This keeps the
                // setting model-agnostic while giving deeper reasoning-capable models more room.
                val thinkingMultiplier = when (config.thinkingDepth.coerceIn(1, 4)) {
                    1 -> 1.0f
                    2 -> 1.5f
                    3 -> 2.0f
                    else -> 3.0f
                }
                val effectiveMaxTokens = (config.maxTokens * thinkingMultiplier)
                    .toInt()
                    .coerceAtMost(config.contextLength.coerceAtLeast(128))
                val ok = LlamaBridge.nativeGenerateStream(
                    modelHandle, prompt, config.temperature, effectiveMaxTokens,
                    config.topK, config.topP, config.repeatPenalty, config.frequencyPenalty, config.presencePenalty,
                    LlamaBridge.TokenCallback { token ->
                        if (!stopRequested.get()) trySend(token)
                    }
                )
                    if (!ok && !stopRequested.get()) {
                        val nativeReason = runCatching { LlamaBridge.nativeGetLastError() }.getOrNull().orEmpty()
                        close(IllegalStateException(nativeReason.ifBlank { "Native generation failed" }))
                    }
                }
            } catch (e: Exception) {
                if (!stopRequested.get()) close(e)
            } finally {
                isGenerating.set(false)
                stopRequested.set(false)
                close()
            }
        }

        awaitClose {
            if (isGenerating.get()) {
                stopRequested.set(true)
                runCatching { LlamaBridge.nativeStopGeneration() }
            }
            generationJob.cancel()
        }
    }

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
    ): Flow<String> = flow {
        val prompt = withContext(Dispatchers.IO) {
            runCatching {
                val roles = messages.map { it.first }.toTypedArray()
                val contents = messages.map { it.second }.toTypedArray()
                LlamaBridge.nativeFormatChat(modelHandle, roles, contents)
            }.getOrElse { buildFallbackPrompt(messages) }
        }
        emitAll(generateStream(prompt, config))
    }.flowOn(Dispatchers.IO)

    override suspend fun stopGeneration() {
        stopGenerationInternal()
    }

    private fun stopGenerationInternal() {
        if (isGenerating.get()) {
            stopRequested.set(true)
            if (nativeAvailable) {
                try {
                    LlamaBridge.nativeStopGeneration()
                } catch (_: Exception) { }
            }
        }
    }

    override fun getMemoryUsage(): Long? {
        if (!nativeAvailable || modelHandle <= 0L) return null
        return try {
            LlamaBridge.nativeGetMemoryUsage(modelHandle).takeIf { it > 0L }
        } catch (_: Exception) {
            null
        }
    }

    override fun isGpuAvailable(): Boolean {
        return nativeAvailable && runCatching { LlamaBridge.nativeSupportsGpu() }.getOrDefault(false)
    }

    override fun release() {
        synchronized(lock) {
            unloadModelInternal()
        }
    }

    private fun buildFallbackPrompt(messages: List<Pair<String, String>>): String = buildString {
        for ((role, content) in messages) {
            append("<$role>\n")
            append(content)
            append("\n")
        }
        append("<assistant>\n")
    }
}
