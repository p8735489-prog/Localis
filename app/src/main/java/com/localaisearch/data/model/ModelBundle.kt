package com.localaisearch.data.model

import kotlinx.serialization.Serializable

/**
 * A Model Bundle represents a complete model that may consist of multiple GGUF files.
 * 
 * For example, a vision model bundle:
 * - LLM component: model-Q4_K_M.gguf
 * - Vision Projector component: mmproj-model-f16.gguf
 * 
 * Users see "Qwen Vision" as a single model, not two separate GGUF files.
 */
@Serializable
data class ModelBundle(
    val id: String,
    val displayName: String,
    val description: String = "",
    val author: String = "",
    val components: List<ModelComponent> = emptyList(),
    val capabilities: List<ModelCapability> = emptyList(),
    val architecture: String = "unknown",
    val parameterCount: String = "unknown",
    val quantization: String = "unknown",
    val isLoaded: Boolean = false,
    val isComplete: Boolean = true,
    val missingComponents: List<String> = emptyList(),
    val totalSizeBytes: Long = 0L,
    val importedAt: Long = System.currentTimeMillis(),
    val backend: String = "CPU"
) {
    val llmComponent: ModelComponent? get() = components.find { it.type == ModelComponentType.LLM }
    val projectorComponent: ModelComponent? get() = components.find { it.type == ModelComponentType.VISION_PROJECTOR }
    val displaySize: String get() = formatFileSize(totalSizeBytes)
    val hasVision: Boolean get() = capabilities.contains(ModelCapability.VISION)
    val hasText: Boolean get() = capabilities.contains(ModelCapability.TEXT)
    val hasEmbedding: Boolean get() = capabilities.contains(ModelCapability.EMBEDDING)
    val llmSize: String get() = llmComponent?.displaySize ?: "N/A"
    val projectorSize: String get() = projectorComponent?.displaySize ?: "N/A"
}
