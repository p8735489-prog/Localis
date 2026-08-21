package com.localaisearch.data.llm

import android.content.Context
import android.util.Log
import android.app.ActivityManager
import java.io.File
import com.localaisearch.data.error.GlobalErrorHandler
import com.localaisearch.data.model.InferenceConfig
import com.localaisearch.data.model.GGUFMetadataReader
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
class GGUFEngine(private val appContext: Context? = null) : LLMEngine {

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

    /** Absolute path of the model currently owned by the shared native engine. */
    fun loadedModelPath(): String? = synchronized(lock) {
        if (modelHandle > 0L) currentModelPath else null
    }

    fun loadedModelConfig(): InferenceConfig? = synchronized(lock) {
        currentConfig
    }

    private var currentModelPath: String? = null
    private var currentVisionProjectorPath: String? = null

    override suspend fun loadModel(filePath: String, config: InferenceConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (isGenerating.get()) {
                    return@withContext Result.failure(IllegalStateException("请先停止当前 AI 生成，再加载或切换模型"))
                }
                if (!nativeAvailable) nativeAvailable = LlamaBridge.initialize()
                if (!nativeAvailable) {
                    val message = "Local GGUF engine is unavailable: libllama_bridge.so was not loaded for this device ABI."
                    GlobalErrorHandler.emitModel(message)
                    return@withContext Result.failure(IllegalStateException(message))
                }
                try {
                    // Validate the replacement before unloading the healthy current model.
                    val modelFile = File(filePath)
                    if (!modelFile.isFile || modelFile.length() < 8L) {
                        val message = "The GGUF file is missing, empty, or incomplete."
                        GlobalErrorHandler.emitModel(message)
                        return@withContext Result.failure(IllegalArgumentException(message))
                    }
                    val metadata = GGUFMetadataReader.readMetadata(filePath)
                    if (metadata.isProjector) {
                        val message = "This is a vision projector/mmproj file, not a language-model GGUF."
                        GlobalErrorHandler.emitModel(message)
                        return@withContext Result.failure(IllegalArgumentException(message))
                    }
                    val am = appContext?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    val safeThreads = config.threads.coerceIn(1, Runtime.getRuntime().availableProcessors().coerceAtMost(8).coerceAtLeast(1))
                    var requestedContext = config.contextLength.coerceIn(512, 32768)
                    val memInfo = ActivityManager.MemoryInfo()
                    am?.getMemoryInfo(memInfo)
                    val available = memInfo.availMem
                    val fileBytes = modelFile.length()
                    if (available > 0L && fileBytes > 0L) {
                        val contextBudget = (available - (fileBytes * 1.30f).toLong() - 256L * 1024L * 1024L).coerceAtLeast(256L * 1024L * 1024L)
                        val memoryContextCap = (contextBudget / (96L * 1024L)).toInt().coerceIn(512, 32768)
                        requestedContext = minOf(requestedContext, memoryContextCap)
                    }
                    val kvReserve = requestedContext.toLong() * 96L * 1024L
                    val requiredFloor = maxOf((fileBytes * 1.35f).toLong(), fileBytes + kvReserve + 256L * 1024L * 1024L)
                    if (available > 0L && available < requiredFloor) {
                        val message = "Not enough free memory to load this model safely (${formatMemory(available)} free, about ${formatMemory(requiredFloor)} recommended). Try a smaller quantization or lower context length."
                        GlobalErrorHandler.emitModel(message)
                        return@withContext Result.failure(IllegalStateException(message))
                    }
                    if (modelHandle > 0L) unloadModelInternal()
                    val handle = LlamaBridge.nativeLoadModel(filePath, requestedContext, safeThreads, false, 0)
                    if (handle <= 0L) {
                        val nativeReason = runCatching { LlamaBridge.nativeGetLastError() }.getOrNull().orEmpty()
                        val message = nativeReason.ifBlank { "GGUF model could not be loaded by the bundled llama.cpp engine. Check architecture, file integrity and available RAM." }
                        GlobalErrorHandler.emitModel(message)
                        return@withContext Result.failure(IllegalStateException(message))
                    }
                    modelHandle = handle
                    currentModelPath = filePath
                    currentConfig = config.copy(contextLength = requestedContext, threads = safeThreads, useGpu = false, gpuLayers = 0)
                    Result.success(Unit)
                } catch (e: OutOfMemoryError) {
                    unloadModelInternal()
                    Result.failure(IllegalStateException("The model needs more memory than is currently available. Try a smaller quantization.", e))
                } catch (e: Exception) {
                    unloadModelInternal()
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
        currentVisionProjectorPath = null
        currentConfig = null
    }

    override fun generateStream(prompt: String, config: InferenceConfig): Flow<String> = callbackFlow {
        if (!isLoaded) {
            close(IllegalStateException("No model loaded"))
            return@callbackFlow
        }

        if (!isGenerating.compareAndSet(false, true)) {
            close(IllegalStateException("A model response is already being generated"))
            return@callbackFlow
        }
        stopRequested.set(false)
        val generationHandle = synchronized(lock) { modelHandle }
        if (generationHandle <= 0L) {
            isGenerating.set(false)
            close(IllegalStateException("Model was unloaded before generation started"))
            return@callbackFlow
        }
        val generationJob = launch(Dispatchers.IO) {
            try {
                val thinkingMultiplier = when (config.thinkingDepth.coerceIn(1, 4)) {
                    1 -> 1.0f
                    2 -> 1.5f
                    3 -> 2.0f
                    else -> 3.0f
                }
                val effectiveMaxTokens = (config.maxTokens * thinkingMultiplier).toInt().coerceAtMost(config.contextLength.coerceAtLeast(128))
                val ok = LlamaBridge.nativeGenerateStream(
                    generationHandle, prompt, config.temperature, effectiveMaxTokens,
                    config.topK, config.topP, config.repeatPenalty, config.frequencyPenalty, config.presencePenalty,
                    LlamaBridge.TokenCallback { token -> if (!stopRequested.get()) trySend(token) }
                )
                if (!ok && !stopRequested.get()) {
                    val nativeReason = runCatching { LlamaBridge.nativeGetLastError() }.getOrNull().orEmpty()
                    close(IllegalStateException(nativeReason.ifBlank { "Native generation failed" }))
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
                // Snapshot the handle under the same lock every other native
                // call uses. Without this, chatStream was the one code path
                // that read modelHandle unsynchronized and never checked
                // isLoaded first — a concurrent unloadModel() could hand
                // nativeFormatChat a stale/zero handle and crash the native
                // side instead of failing as a Kotlin exception like every
                // other entry point in this class does.
                val handle = synchronized(lock) { modelHandle }
                if (handle <= 0L) {
                    throw IllegalStateException("No model loaded")
                }
                val roles = messages.map { it.first }.toTypedArray()
                val contents = messages.map { it.second }.toTypedArray()
                val formatted = LlamaBridge.nativeFormatChat(handle, roles, contents)
                if (formatted.isBlank()) {
                    val reason = runCatching { LlamaBridge.nativeGetLastError() }.getOrNull().orEmpty()
                    throw IllegalStateException(
                        reason.ifBlank { "This GGUF model does not expose a usable llama.cpp chat template." }
                    )
                }
                formatted
            }.getOrElse { throw it }
        }
        emitAll(generateStream(prompt, config))
    }.flowOn(Dispatchers.IO)

    /** Attach a matching llama.cpp mmproj/libmtmd vision runtime to the loaded model. */
    suspend fun loadVisionProjector(mmprojPath: String, config: InferenceConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (modelHandle <= 0L) return@synchronized Result.failure(IllegalStateException("No language model is loaded"))
                val projector = File(mmprojPath)
                if (!projector.isFile || projector.length() < 8L) {
                    return@synchronized Result.failure(IllegalArgumentException("视觉投影器文件不存在或已损坏"))
                }
                if (currentVisionProjectorPath == projector.absolutePath && hasVisionRuntime()) {
                    return@synchronized Result.success(Unit)
                }
                val ok = runCatching {
                    LlamaBridge.nativeLoadVisionProjector(modelHandle, projector.absolutePath, config.threads.coerceIn(1, 8), false)
                }.getOrElse { false }
                if (ok) {
                    currentVisionProjectorPath = projector.absolutePath
                    Result.success(Unit)
                } else {
                    val reason = runCatching { LlamaBridge.nativeGetLastError() }.getOrNull().orEmpty()
                    Result.failure(IllegalStateException(reason.ifBlank { "Failed to load llama.cpp vision projector" }))
                }
            }
        }

    internal fun currentHandleForMultimodal(): Long = synchronized(lock) { modelHandle }

    fun hasVisionRuntime(): Boolean = synchronized(lock) {
        modelHandle > 0L && runCatching { LlamaBridge.nativeHasVision(modelHandle) }.getOrDefault(false)
    }

    /**
     * Generate with real pixels. The caller must pass a prompt containing
     * llama.cpp's default <__media__> marker. Pixels are decoded and projected
     * by libmtmd/mmproj in native code before the language model samples tokens.
     */
    fun generateMultimodalStream(
        promptWithMediaMarker: String,
        imageBytes: ByteArray,
        config: InferenceConfig
    ): Flow<String> = callbackFlow {
        if (!isLoaded || !hasVisionRuntime()) {
            close(IllegalStateException("Vision runtime is not loaded. Download and attach a matching mmproj model first."))
            return@callbackFlow
        }
        if (!isGenerating.compareAndSet(false, true)) {
            close(IllegalStateException("A model response is already being generated"))
            return@callbackFlow
        }
        stopRequested.set(false)
        val generationHandle = synchronized(lock) { modelHandle }
        if (generationHandle <= 0L) {
            isGenerating.set(false)
            close(IllegalStateException("Model was unloaded before multimodal generation started"))
            return@callbackFlow
        }
        val generationJob = launch(Dispatchers.IO) {
            try {
                val thinkingMultiplier = when (config.thinkingDepth.coerceIn(1, 4)) {
                    1 -> 1.0f
                    2 -> 1.5f
                    3 -> 2.0f
                    else -> 3.0f
                }
                val effectiveMaxTokens = (config.maxTokens * thinkingMultiplier).toInt().coerceAtMost(config.contextLength.coerceAtLeast(128))
                val ok = LlamaBridge.nativeGenerateMultimodalStream(
                    generationHandle, promptWithMediaMarker, imageBytes, config.temperature,
                    effectiveMaxTokens, config.topK, config.topP, config.repeatPenalty,
                    config.frequencyPenalty, config.presencePenalty,
                    LlamaBridge.TokenCallback { token -> if (!stopRequested.get()) trySend(token) }
                )
                if (!ok && !stopRequested.get()) {
                    val reason = runCatching { LlamaBridge.nativeGetLastError() }.getOrNull().orEmpty()
                    close(IllegalStateException(reason.ifBlank { "Multimodal inference failed" }))
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

    private fun formatMemory(bytes: Long): String {
        if (bytes <= 0L) return "unknown"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) "%.1f GB".format(gb) else "%.0f MB".format(bytes / (1024.0 * 1024.0))
    }

    override fun release() {
        synchronized(lock) {
            unloadModelInternal()
        }
    }

}
