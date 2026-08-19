package com.localaisearch.data.llm

import android.net.Uri
import java.io.File
import com.localaisearch.data.model.AdvancedInferenceConfig
import com.localaisearch.data.model.ImageInput
import com.localaisearch.data.model.ModelBundle
import com.localaisearch.data.model.HardwareBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Abstract interface for multimodal (vision + text) model adapters.
 *
 * Each adapter handles a specific vision model architecture:
 * - QwenVisionAdapter for Qwen-VL models
 * - LlavaVisionAdapter for LLaVA models
 * - GemmaVisionAdapter for PaliGemma/Gemma models
 * - GenericVisionAdapter as fallback
 *
 * The adapter manages:
 * - Loading/unloading LLM and vision projector components
 * - Processing images into model-specific format
 * - Generating text with optional image input
 * - Backend selection (CPU/GPU/NPU)
 */
interface MultimodalAdapter {

    /** The model bundle this adapter operates on */
    val modelBundle: ModelBundle

    /** The underlying LLM engine */
    val llmEngine: LLMEngine

    /** Whether the adapter is fully loaded (both LLM and projector) */
    val isLoaded: Boolean
        get() = llmEngine.isLoaded

    /** Whether this bundle supports vision tasks */
    val supportsVision: Boolean
        get() = modelBundle.hasVision && modelBundle.projectorComponent != null

    /** Current hardware backend */
    val backend: HardwareBackend
        get() = if (modelBundle.backend.isNotBlank()) {
            try {
                HardwareBackend.valueOf(modelBundle.backend)
            } catch (_: Exception) {
                HardwareBackend.CPU
            }
        } else HardwareBackend.CPU

    /**
     * Load the LLM component from the model bundle.
     */
    suspend fun load(config: AdvancedInferenceConfig): Result<Unit>

    /**
     * Load the vision projector component.
     * Only applicable for vision-capable bundles.
     */
    suspend fun loadProjector(config: AdvancedInferenceConfig): Result<Unit>

    /**
     * Process an image into model-specific representation.
     *
     * @param imageUri URI of the input image
     * @return Result containing processed image data or error
     */
    suspend fun processImage(imageUri: Uri): Result<String>

    /**
     * Generate text with optional image input.
     *
     * @param prompt Text prompt
     * @param imageInput Optional processed image
     * @param config Inference configuration
     * @return Flow of generated tokens
     */
    fun generate(
        prompt: String,
        imageInput: ImageInput? = null,
        config: AdvancedInferenceConfig
    ): Flow<String>

    /**
     * Unload all components (LLM + projector).
     */
    suspend fun unload(): Result<Unit>
}

/**
 * Factory for creating the appropriate multimodal adapter.
 */
object MultimodalAdapterFactory {

    /**
     * Create an adapter for the given model bundle.
     * Selects the correct adapter based on model architecture/family.
     */
    fun create(bundle: ModelBundle, llmEngine: LLMEngine): MultimodalAdapter {
        val family = bundle.architecture.lowercase()
        return when {
            family.contains("qwen") -> QwenVisionAdapter(bundle, llmEngine)
            family.contains("llava") || family.contains("yi-vl") -> LlavaVisionAdapter(bundle, llmEngine)
            family.contains("gemma") || family.contains("paligemma") -> GemmaVisionAdapter(bundle, llmEngine)
            family.contains("internvl") -> GenericVisionAdapter(bundle, llmEngine, "InternVL")
            family.contains("cogvlm") -> GenericVisionAdapter(bundle, llmEngine, "CogVLM")
            else -> GenericVisionAdapter(bundle, llmEngine, "Generic")
        }
    }

    /**
     * Check if a model bundle supports multimodal (vision) tasks.
     */
    fun supportsMultimodal(bundle: ModelBundle): Boolean {
        return bundle.hasVision && bundle.projectorComponent != null
    }
}

/**
 * Generic vision adapter implementation that works with any model architecture.
 * Used as fallback when no specific adapter is available.
 */
open class GenericVisionAdapter(
    override val modelBundle: ModelBundle,
    override val llmEngine: LLMEngine,
    private val architectureName: String = "Generic"
) : MultimodalAdapter {

    override suspend fun load(config: AdvancedInferenceConfig): Result<Unit> {
        return try {
            val llmComp = modelBundle.llmComponent
                ?: return Result.failure(IllegalStateException("No LLM component in bundle"))
            llmEngine.loadModel(llmComp.filePath, config.toInferenceConfig())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadProjector(config: AdvancedInferenceConfig): Result<Unit> {
        val projector = modelBundle.projectorComponent
            ?: return Result.failure(IllegalStateException("No matching mmproj/vision projector was found"))
        val engine = llmEngine as? GGUFEngine
            ?: return Result.failure(IllegalStateException("The active engine does not expose the llama.cpp mtmd bridge"))
        return engine.loadVisionProjector(projector.filePath, config.toInferenceConfig())
    }

    override suspend fun processImage(imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            imageUri.toString()
        }
    }

    override fun generate(
        prompt: String,
        imageInput: ImageInput?,
        config: AdvancedInferenceConfig
    ): Flow<String> = flow {
        if (imageInput == null) {
            llmEngine.generateStream(prompt, config.toInferenceConfig()).collect { emit(it) }
            return@flow
        }
        val engine = llmEngine as? GGUFEngine
            ?: throw UnsupportedOperationException("The active engine does not expose llama.cpp mtmd")
        if (!engine.hasVisionRuntime()) {
            throw IllegalStateException("${architectureName} requires a matching mmproj projector. Download and load it before sending the image.")
        }
        val imageFile = File(imageInput.uri)
        if (!imageFile.isFile) throw IllegalStateException("Processed image file is not accessible: ${imageInput.uri}")
        val imageBytes = withContext(Dispatchers.IO) { imageFile.readBytes() }
        engine.generateMultimodalStream("<__media__>\n$prompt", imageBytes, config.toInferenceConfig()).collect { emit(it) }
    }

    override suspend fun unload(): Result<Unit> {
        return try {
            llmEngine.unloadModel()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Vision adapter for Qwen-VL model family.
 */
class QwenVisionAdapter(
    bundle: ModelBundle,
    engine: LLMEngine
) : GenericVisionAdapter(bundle, engine, "Qwen-VL")

/**
 * Vision adapter for LLaVA model family.
 */
class LlavaVisionAdapter(
    bundle: ModelBundle,
    engine: LLMEngine
) : GenericVisionAdapter(bundle, engine, "LLaVA")

/**
 * Vision adapter for PaliGemma/Gemma model family.
 */
class GemmaVisionAdapter(
    bundle: ModelBundle,
    engine: LLMEngine
) : GenericVisionAdapter(bundle, engine, "Gemma")
