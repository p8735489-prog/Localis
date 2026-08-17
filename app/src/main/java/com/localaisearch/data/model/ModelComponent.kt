package com.localaisearch.data.model

import kotlinx.serialization.Serializable

/**
 * Component types within a Model Bundle.
 */
@Serializable
enum class ModelComponentType(val displayName: String, val filePattern: List<String>) {
    LLM("LLM", listOf(".gguf")),
    VISION_PROJECTOR("Vision Projector", listOf("mmproj", "vision", "visual", "projector")),
    EMBEDDING("Embedding", listOf("embed", "embedding")),
    RERANKER("Reranker", listOf("rerank", "rank")),
    OTHER("Other", listOf())
}

/**
 * A single component file within a Model Bundle.
 */
@Serializable
data class ModelComponent(
    val type: ModelComponentType,
    val filePath: String,
    val fileSizeBytes: Long,
    val isRequired: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
) {
    val displaySize: String get() = formatFileSize(fileSizeBytes)
}
