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
}

/**
 * Formats a byte count into a human-readable string (e.g. "1.5 GB").
 */
fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
