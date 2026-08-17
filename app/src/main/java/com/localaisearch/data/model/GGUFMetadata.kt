package com.localaisearch.data.model

import kotlinx.serialization.Serializable

/**
 * Metadata extracted from a GGUF file header.
 * Used to determine model capabilities, architecture, and component type.
 */
@Serializable
data class GGUFMetadata(
    val filePath: String,
    val architecture: String = "unknown",
    val parameterCount: Long = 0,
    val contextLength: Int = 4096,
    val embeddingLength: Int = 0,
    val vocabSize: Int = 0,
    val quantizationVersion: String = "unknown",
    val hasVision: Boolean = false,
    val hasClipVision: Boolean = false,
    val modelFamily: String = "unknown",
    val modelName: String = "unknown",
    val isProjector: Boolean = false,
    val rawMetadata: Map<String, String> = emptyMap()
) {
    val displayParameterCount: String
        get() = when {
            parameterCount >= 1_000_000_000 -> "${parameterCount / 1_000_000_000}B"
            parameterCount >= 1_000_000 -> "${parameterCount / 1_000_000}M"
            else -> parameterCount.toString()
        }
    
    val detectedCapabilities: List<ModelCapability>
        get() = buildList {
            add(ModelCapability.TEXT)
            if (hasVision || hasClipVision || isProjector) add(ModelCapability.VISION)
            if (embeddingLength > 0) add(ModelCapability.EMBEDDING)
        }
}
