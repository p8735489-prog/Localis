package com.localaisearch.data.model

import kotlinx.serialization.Serializable

/**
 * Model capabilities detected from GGUF metadata.
 */
@Serializable
enum class ModelCapability(val displayName: String, val icon: String) {
    TEXT("Text", "text"),
    VISION("Vision", "image"),
    EMBEDDING("Embedding", "vector"),
    RERANKER("Reranker", "sort")
}
