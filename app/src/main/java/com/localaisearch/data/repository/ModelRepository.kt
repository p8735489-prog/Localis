package com.localaisearch.data.repository

import android.content.Context
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
    private val engine: LLMEngine
) {
    private val modelsDir: File by lazy {
        File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
    }

    private val _models = MutableStateFlow<List<GGUFModel>>(emptyList())
    val models: StateFlow<List<GGUFModel>> = _models.asStateFlow()

    private val _activeModel = MutableStateFlow<GGUFModel?>(null)
    val activeModel: StateFlow<GGUFModel?> = _activeModel.asStateFlow()
    private val modelOperationMutex = Mutex()

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
    fun refreshModels() {
        val modelFiles = modelsDir.listFiles { file ->
            file.isFile && file.extension.equals("gguf", ignoreCase = true)
        } ?: emptyArray()

        val models = modelFiles.map { file ->
            GGUFModel(
                id = file.absolutePath,
                name = file.nameWithoutExtension,
                filePath = file.absolutePath,
                fileSizeBytes = file.length(),
                isLoaded = false
            )
        }.sortedBy { it.name }

        _models.value = models
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
                val result = engine.loadModel(model.filePath, config)
                result.onSuccess {
                    _activeModel.value = model.copy(isLoaded = true)
                }
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
                    val previous = _activeModel.value
                    engine.unloadModel()
                    _activeModel.value = null
                    val result = engine.loadModel(model.filePath, config)
                    if (result.isSuccess) {
                        _activeModel.value = model.copy(isLoaded = true)
                        return@withLock result
                    }

                    // Do not leave the app with a blank model after a failed switch.
                    // Re-load the previous model when possible; this also makes rapid
                    // multi-model switching much safer under memory pressure.
                    if (previous != null && previous.id != model.id) {
                        val restore = engine.loadModel(previous.filePath, config)
                        if (restore.isSuccess) {
                            _activeModel.value = previous.copy(isLoaded = true)
                        }
                    }
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
