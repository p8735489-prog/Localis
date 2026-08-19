package com.localaisearch.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.localaisearch.data.model.ImageProcessResult
import com.localaisearch.data.model.MAX_INPUT_DIMENSION
import com.localaisearch.data.model.MIME_TYPE_JPEG
import com.localaisearch.data.model.SUPPORTED_IMAGE_MIME_TYPES
import java.io.File
import java.io.FileOutputStream

/**
 * Processes and validates image inputs for vision-capable models.
 *
 * This class handles the full image preprocessing pipeline:
 * MIME type validation, dimension inspection, downscaling for large images,
 * JPEG compression, and cache persistence. All operations gracefully handle
 * OutOfMemoryError by catching it and returning [ImageProcessResult.Error].
 *
 * @param context The Android [Context] used to resolve content URIs and access the cache directory.
 */
class ImageProcessor(
    private val context: Context
) {

    /**
     * Main processing pipeline for an image [Uri].
     *
     * Steps:
     * 1. Validate the MIME type against [SUPPORTED_IMAGE_MIME_TYPES].
     * 2. Decode image bounds (width / height) without loading the full bitmap into memory.
     * 3. If either dimension exceeds [MAX_INPUT_DIMENSION], calculate a power-of-two sample
     *    size and create a downscaled [Bitmap].
     * 4. Compress the final bitmap to JPEG at quality 85 %.
     * 5. Write the result to the app cache directory with a timestamped filename.
     * 6. Return [ImageProcessResult.Success] containing the original URI, processed URI,
     *    dimensions, and file size.
     *
     * If any step fails (including [OutOfMemoryError]), an [ImageProcessResult.Error] is returned.
     *
     * @param uri The content or file [Uri] of the image to process.
     * @return An [ImageProcessResult] describing the outcome.
     */
    fun processImage(uri: Uri): ImageProcessResult {
        return try {
            // Step 1: Validate MIME type
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType == null || mimeType !in SUPPORTED_IMAGE_MIME_TYPES) {
                return ImageProcessResult.Error(
                    message = "Unsupported image type: ${mimeType ?: "unknown"}. " +
                        "Supported types: ${SUPPORTED_IMAGE_MIME_TYPES.joinToString()}."
                )
            }

            // Step 2: Decode bounds without loading the full bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return ImageProcessResult.Error(
                    message = "Unable to decode image dimensions. The file may be corrupted or empty."
                )
            }

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            // Step 3: Calculate sample size if dimensions exceed the limit
            val sampleSize = calculateInSampleSize(originalWidth, originalHeight, MAX_INPUT_DIMENSION, MAX_INPUT_DIMENSION)

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap: Bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return ImageProcessResult.Error(
                message = "Failed to decode bitmap from URI."
            )

            // Step 4 & 5: Compress to JPEG (quality 85%) and write to cache
            val timestamp = System.currentTimeMillis()
            val outputFile = File(context.cacheDir, "processed_image_${timestamp}.jpg")
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            val processedUri = Uri.fromFile(outputFile).toString()
            val fileSizeBytes = outputFile.length()

            // Recycle the bitmap to free native memory eagerly
            bitmap.recycle()

            // Step 6: Return success result
            ImageProcessResult.Success(
                thumbnailUri = uri.toString(),
                processedUri = processedUri,
                description = "Processed ${originalWidth}x${originalHeight} image to " +
                    "${decodeOptions.outWidth}x${decodeOptions.outHeight} " +
                    "(sampleSize=$sampleSize).",
                originalUri = uri.toString(),
                width = decodeOptions.outWidth,
                height = decodeOptions.outHeight,
                fileSizeBytes = fileSizeBytes
            )
        } catch (oom: OutOfMemoryError) {
            ImageProcessResult.Error(
                message = "Out of memory while processing image. " +
                    "Try using a smaller image or freeing device memory."
            )
        } catch (e: Exception) {
            ImageProcessResult.Error(
                message = "Image processing failed: ${e.localizedMessage ?: e.message ?: "Unknown error"}"
            )
        }
    }

    /**
     * Calculates a power-of-two sample size for downscaling a bitmap so that
     * neither dimension exceeds [reqWidth] or [reqHeight].
     *
     * @param width The original image width.
     * @param height The original image height.
     * @param reqWidth The maximum allowed width.
     * @param reqHeight The maximum allowed height.
     * @return A power-of-two integer >= 1 suitable for [BitmapFactory.Options.inSampleSize].
     */
    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (
                halfHeight / inSampleSize >= reqHeight ||
                halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Heuristic check to determine whether a model name indicates vision capabilities.
     *
     * Matches against common vision model keywords (case-insensitive):
     * "vision", "vlm", "llava", "qwen-vl", "yi-vl", "internvl", "cogvlm".
     *
     * @param modelName The model identifier or display name.
     * @return `true` if the model is likely vision-capable.
     */
    fun isVisionModel(modelName: String): Boolean {
        val lower = modelName.lowercase()
        val visionKeywords = listOf(
            "vision", "vlm", "llava", "qwen-vl", "yi-vl", "internvl", "cogvlm"
        )
        return visionKeywords.any { keyword -> lower.contains(keyword) }
    }

    /**
     * Alias for [isVisionModel].
     *
     * @param modelName The model identifier or display name.
     * @return `true` if the model can process images.
     */
    fun canModelProcessImages(modelName: String): Boolean = isVisionModel(modelName)

    /**
     * Generates a simple text description of a [Bitmap] for non-vision models
     * that cannot ingest actual image data.
     *
     * The description includes the image dimensions (width x height) and
     * an approximate in-memory size.
     *
     * @param bitmap The bitmap to describe.
     * @return A human-readable string describing the image dimensions and size.
     */
    fun getImageDescription(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        val byteCount = bitmap.byteCount
        val sizeLabel = when {
            byteCount >= 1_000_000 -> "%.2f MB".format(byteCount / 1_000_000.0)
            byteCount >= 1_000 -> "%.2f KB".format(byteCount / 1_000.0)
            else -> "$byteCount B"
        }
        return "Image: ${width}x${height} pixels, approximate memory size: $sizeLabel."
    }
}
