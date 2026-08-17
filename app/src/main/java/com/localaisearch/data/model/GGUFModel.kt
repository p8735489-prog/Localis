package com.localaisearch.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a GGUF model file managed by the app.
 * Only GGUF format is supported - no ONNX, QNN, MLX, or other formats.
 */
@Serializable
data class GGUFModel(
    val id: String,
    val name: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val quantization: String = "unknown",
    val contextLength: Int = 4096,
    val parameterCount: String = "unknown",
    val isLoaded: Boolean = false,
    val importedAt: Long = System.currentTimeMillis()
) {
    val displaySize: String
        get() = formatFileSize(fileSizeBytes)

    companion object {
        fun formatFileSize(bytes: Long): String = when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}

/**
 * Inference parameters for GGUF model execution.
 */
@Serializable
data class InferenceConfig(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val contextLength: Int = 4096,
    val maxTokens: Int = 2048,
    val repeatPenalty: Float = 1.1f,
    val useGpu: Boolean = true,
    val gpuLayers: Int = 0,
    val threads: Int = 4,
    val seed: Int = -1
) {
    companion object {
        val Default = InferenceConfig()
    }
}
