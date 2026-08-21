package com.localaisearch.data.repository

import android.content.Context
import android.app.ActivityManager
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.localaisearch.data.llm.GGUFEngine
import com.localaisearch.data.llm.LLMEngine
import com.localaisearch.data.model.GGUFModel
import com.localaisearch.data.model.InferenceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

/**
 * Repository for managing GGUF model files.
 *
 * Handles:
 * - Importing GGUF files from SAF (Storage Access Framework)
 * - Deleting model files
 * - Switching active model
 * - Loading/unloading model into GGUFEngine
 *
 * Only GGUF format is supported. File extension validation enforced.
 */
class ModelRepository(
    private val context: Context,
    val engine: LLMEngine
) {
    private val modelsDir: File by lazy {
        File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
    }

    private val _models = MutableStateFlow<List<GGUFModel>>(emptyList())
    val models: StateFlow<List<GGUFModel>> = _models.asStateFlow()

    private val _activeModel = MutableStateFlow<GGUFModel?>(null)
    val activeModel: StateFlow<GGUFModel?> = _activeModel.asStateFlow()
    private val modelOperationMutex = Mutex()
    private var activeConfig: InferenceConfig? = null


    /**
     * Validate that a file is a valid GGUF file by checking the magic number.
     * The file header must start with "GGUF" (bytes 0x47 0x47 0x55 0x46).
     */
    fun validateGGUFFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                if (input.read(header) != 4) return false
                header[0] == 0x47.toByte() &&
                        header[1] == 0x47.toByte() &&
                        header[2] == 0x55.toByte() &&
                        header[3] == 0x46.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Scan the models directory and update the model list.
     */
    suspend fun refreshModels() = withContext(Dispatchers.IO) {
        val modelFiles = modelsDir.listFiles { file ->
            file.isFile && file.extension.equals("gguf", ignoreCase = true)
        } ?: emptyArray()

        val activeId = _activeModel.value?.id
        val models = modelFiles.map { file ->
            val metadata = runCatching { com.localaisearch.data.model.GGUFMetadataReader.readMetadata(file.absolutePath) }.getOrNull()
            GGUFModel(
                id = file.absolutePath,
                name = file.nameWithoutExtension,
                filePath = file.absolutePath,
                fileSizeBytes = file.length(),
                quantization = metadata?.quantizationVersion ?: "unknown",
                contextLength = metadata?.contextLength ?: 4096,
                parameterCount = metadata?.parameterCount?.takeIf { it > 0 }?.let { formatParameterCount(it) } ?: "unknown",
                isLoaded = activeId == file.absolutePath
            )
        }.sortedBy { it.name }

        // Publish only after the full directory scan has completed off the main thread.
        _models.value = models
    }

    private fun formatParameterCount(parameters: Long): String {
        val billions = parameters / 1_000_000_000.0
        return when {
            billions >= 1.0 -> "%.1fB".format(billions)
            parameters >= 1_000_000L -> "%.1fM".format(parameters / 1_000_000.0)
            else -> parameters.toString()
        }
    }

    /**
     * Import a GGUF model from a content URI (SAF).
     * Copies the file to the app's internal storage.
     *
     * @throws IllegalArgumentException if the file is not a GGUF file
     */
    suspend fun importModel(uri: Uri): Result<GGUFModel> = withContext(Dispatchers.IO) {
        try {
            val documentFile = DocumentFile.fromSingleUri(context, uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid file URI"))

            val fileName = documentFile.name ?: "unknown.gguf"
            if (!fileName.endsWith(".gguf", ignoreCase = true)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Only GGUF files are supported. Got: $fileName")
                )
            }

            val targetFile = File(modelsDir, fileName)
            if (targetFile.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("A model with this name already exists: $fileName")
                )
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(
                IllegalArgumentException("Cannot read file from URI")
            )

            // Validate GGUF magic number after copy
            if (!validateGGUFFile(targetFile)) {
                targetFile.delete()
                return@withContext Result.failure(
                    IllegalStateException("Invalid GGUF file: magic number mismatch")
                )
            }

            val model = GGUFModel(
                id = targetFile.absolutePath,
                name = targetFile.nameWithoutExtension,
                filePath = targetFile.absolutePath,
                fileSizeBytes = targetFile.length()
            )

            refreshModels()
            Result.success(model)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a model file and unload it if active.
     */
    suspend fun deleteModel(model: GGUFModel): Result<Unit> = withContext(Dispatchers.IO) {
        modelOperationMutex.withLock {
        try {
            if (_activeModel.value?.id == model.id) {
                engine.unloadModel()
                _activeModel.value = null
                activeConfig = null
            }

            val file = File(model.filePath)
            if (file.exists()) {
                file.delete()
            }

            refreshModels()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
        }
    }

    /** Build a conservative mobile-safe inference config before entering native llama.cpp. */
    private fun safeConfigForModel(file: File, requested: InferenceConfig): InferenceConfig {
        val metadata = runCatching { com.localaisearch.data.model.GGUFMetadataReader.readMetadata(file.absolutePath) }.getOrNull()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        val available = info.availMem
        val trainContext = metadata?.contextLength?.takeIf { it > 0 } ?: Int.MAX_VALUE
        val fileBytes = file.length()
        val memoryBasedContext = if (available > 0L && fileBytes > 0L) {
            val reserve = (available - (fileBytes * 1.30f).toLong() - 256L * 1024L * 1024L).coerceAtLeast(256L * 1024L * 1024L)
            (reserve / (96L * 1024L)).toInt().coerceAtLeast(512)
        } else 8192
        val contextLength = requested.contextLength
            .coerceIn(512, 32768)
            .coerceAtMost(trainContext.coerceAtMost(32768))
            .coerceAtMost(memoryBasedContext.coerceIn(512, 32768))
        val maxThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(8).coerceAtLeast(1)
        val resolvedThreads = if (requested.threads <= 0) maxThreads else requested.threads.coerceIn(1, maxThreads)
        // GPU is opt-in only when the actual native llama.cpp backend reports it.
        // A stale preference can never force unsupported offload.
        val gpuAvailable = requested.useGpu && engine.isGpuAvailable()
        return requested.copy(
            contextLength = contextLength,
            threads = resolvedThreads,
            useGpu = gpuAvailable,
            gpuLayers = if (gpuAvailable) requested.gpuLayers.coerceAtLeast(1) else 0,
            backend = if (gpuAvailable) com.localaisearch.data.model.HardwareBackend.GPU else com.localaisearch.data.model.HardwareBackend.CPU
        )
    }

    /**
     * Load a model into the engine and set it as active.
     */
    suspend fun loadModel(model: GGUFModel, config: InferenceConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            modelOperationMutex.withLock {
                val file = File(model.filePath)
                if (!file.isFile || !validateGGUFFile(file)) {
                    return@withLock Result.failure(IllegalArgumentException("Invalid or missing GGUF file: ${model.filePath}"))
                }

                val previousModel = _activeModel.value
                val previousConfig = activeConfig
                if (previousModel?.id == model.id && engine.isLoaded) {
                    _activeModel.value = model.copy(isLoaded = true)
                    return@withLock Result.success(Unit)
                }
                val safeConfig = safeConfigForModel(file, config)
                // Try the requested accelerated path first. If the device advertises
                // Vulkan but the particular model cannot be offloaded safely, immediately
                // retry the same model on CPU before declaring the load failed.
                val firstResult = engine.loadModel(model.filePath, safeConfig)
                val result = if (firstResult.isFailure && safeConfig.useGpu) {
                    val cpuConfig = safeConfig.copy(
                        useGpu = false,
                        gpuLayers = 0,
                        backend = com.localaisearch.data.model.HardwareBackend.CPU
                    )
                    engine.loadModel(model.filePath, cpuConfig)
                } else firstResult
                val activeConfigToStore = if (result.isSuccess && firstResult.isFailure && safeConfig.useGpu) {
                    safeConfig.copy(useGpu = false, gpuLayers = 0, backend = com.localaisearch.data.model.HardwareBackend.CPU)
                } else safeConfig
                if (result.isSuccess) {
                    _activeModel.value = model.copy(isLoaded = true)
                    activeConfig = activeConfigToStore
                    refreshModels()
                    return@withLock result
                }

                _activeModel.value = null
                activeConfig = null
                if (previousModel != null && previousConfig != null) {
                    val rollback = engine.loadModel(previousModel.filePath, previousConfig)
                    if (rollback.isSuccess) {
                        _activeModel.value = previousModel.copy(isLoaded = true)
                        activeConfig = previousConfig
                    }
                }
                refreshModels()
                result
            }
        }

    /**
     * Unload the current model.
     */
    suspend fun unloadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        modelOperationMutex.withLock {
            try {
                engine.unloadModel()
                _activeModel.value = null
                activeConfig = null
                refreshModels()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Switch to a different model (unload current, load new).
     */
    suspend fun switchModel(model: GGUFModel, config: InferenceConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            modelOperationMutex.withLock {
                try {
                    val file = File(model.filePath)
                    if (!file.isFile || !validateGGUFFile(file)) {
                        return@withLock Result.failure(IllegalArgumentException("Invalid or missing GGUF file: ${model.filePath}"))
                    }
                    val previousModel = _activeModel.value
                    val previousConfig = activeConfig
                    if (previousModel?.id == model.id && engine.isLoaded) {
                        _activeModel.value = model.copy(isLoaded = true)
                        return@withLock Result.success(Unit)
                    }
                    val safeConfig = safeConfigForModel(file, config)
                    engine.unloadModel()
                    _activeModel.value = null
                    activeConfig = null
                    val firstResult = engine.loadModel(model.filePath, safeConfig)
                    val result = if (firstResult.isFailure && safeConfig.useGpu) {
                        engine.loadModel(model.filePath, safeConfig.copy(useGpu = false, gpuLayers = 0, backend = com.localaisearch.data.model.HardwareBackend.CPU))
                    } else firstResult
                    if (result.isSuccess) {
                        _activeModel.value = model.copy(isLoaded = true)
                        activeConfig = if (firstResult.isFailure && safeConfig.useGpu) safeConfig.copy(useGpu = false, gpuLayers = 0, backend = com.localaisearch.data.model.HardwareBackend.CPU) else safeConfig
                        refreshModels()
                        return@withLock result
                    }
                    // Failed model switches automatically restore the previous model when
                    // its file and configuration are still available.
                    if (previousModel != null && previousConfig != null && File(previousModel.filePath).isFile) {
                        val rollback = engine.loadModel(previousModel.filePath, previousConfig)
                        if (rollback.isSuccess) {
                            _activeModel.value = previousModel.copy(isLoaded = true)
                            activeConfig = previousConfig
                        }
                    }
                    refreshModels()
                    result
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

    /**
     * Get total storage used by models.
     */
    fun getTotalStorageUsed(): Long {
        return _models.value.sumOf { it.fileSizeBytes }
    }
}
