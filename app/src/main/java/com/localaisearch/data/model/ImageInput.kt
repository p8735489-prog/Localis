package com.localaisearch.data.model

import kotlinx.serialization.Serializable

// ── Constants ────────────────────────────────────────────────────────────────

/** Maximum input dimension (width or height) for auto-scaling images. */
const val MAX_INPUT_DIMENSION = 2048

/** Supported MIME type for JPEG images. */
const val MIME_TYPE_JPEG = "image/jpeg"

/** Supported MIME type for PNG images. */
const val MIME_TYPE_PNG = "image/png"

/** Supported MIME type for WebP images. */
const val MIME_TYPE_WEBP = "image/webp"

/** Set of all supported image MIME types. */
val SUPPORTED_IMAGE_MIME_TYPES: Set<String> = setOf(MIME_TYPE_JPEG, MIME_TYPE_PNG, MIME_TYPE_WEBP)

// ── Data classes ─────────────────────────────────────────────────────────────

/**
 * Represents an image selected or captured for model input.
 *
 * @property uri The content URI or file path of the image.
 * @property width The original width of the image in pixels.
 * @property height The original height of the image in pixels.
 * @property fileSizeBytes The file size on disk in bytes.
 * @property mimeType The MIME type of the image (e.g., "image/jpeg").
 */
@Serializable
data class ImageInput(
    val uri: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val mimeType: String
) {
    /**
     * Whether this image uses a supported MIME type.
     */
    val isSupportedType: Boolean
        get() = mimeType in SUPPORTED_IMAGE_MIME_TYPES

    /**
     * Whether the image exceeds the maximum input dimension and needs scaling.
     */
    val needsScaling: Boolean
        get() = width > MAX_INPUT_DIMENSION || height > MAX_INPUT_DIMENSION

    /**
     * The display dimensions after applying [MAX_INPUT_DIMENSION] scaling.
     */
    val scaledDimensions: Pair<Int, Int>
        get() {
            if (!needsScaling) return width to height
            val scale = MAX_INPUT_DIMENSION.toFloat() / kotlin.math.max(width, height)
            return (width * scale).toInt() to (height * scale).toInt()
        }
}

// ── Sealed class: ImageProcessResult ─────────────────────────────────────────

/**
 * Sealed class representing the outcome of processing an [ImageInput].
 */
@Serializable
sealed class ImageProcessResult {

    /**
     * Image was successfully processed.
     *
     * @property thumbnailUri URI to the generated thumbnail.
     * @property processedUri URI to the processed (e.g., scaled/converted) image.
     * @property description Optional generated or extracted description of the image.
     * @property originalUri URI of the original unprocessed image.
     * @property width Final processed image width in pixels.
     * @property height Final processed image height in pixels.
     * @property fileSizeBytes Size of the processed image file in bytes.
     */
    @Serializable
    data class Success(
        val thumbnailUri: String,
        val processedUri: String,
        val description: String? = null,
        val originalUri: String = "",
        val width: Int = 0,
        val height: Int = 0,
        val fileSizeBytes: Long = 0L
    ) : ImageProcessResult()

    /**
     * Processing failed with an error message.
     *
     * @property message Human-readable error description.
     */
    @Serializable
    data class Error(
        val message: String
    ) : ImageProcessResult()

    /**
     * The image type is not supported and cannot be processed.
     */
    @Serializable
    data object Unsupported : ImageProcessResult()
}
