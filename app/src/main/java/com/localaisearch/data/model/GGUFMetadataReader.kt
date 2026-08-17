package com.localaisearch.data.model

import java.io.File
import java.io.RandomAccessFile

/**
 * Utility for reading GGUF file metadata without loading the full model.
 *
 * Reads the GGUF header by scanning the first 64KB of the file for known
 * metadata key-value pairs. If the file is not a valid GGUF or cannot be read,
 * returns safe default metadata with [isProjector] inferred from the filename.
 *
 * Reference: [GGUF v3 spec](https://github.com/ggerganov/ggml/blob/master/docs/gguf.md)
 */
object GGUFMetadataReader {

    /** Number of bytes to scan from the start of the file. */
    private const val SCAN_SIZE = 64 * 1024 // 64KB

    /** GGUF magic bytes: "GGUF" in ASCII. */
    private val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46) // "GGUF"

    /** Keywords that indicate a projector/vision component file. */
    private val PROJECTOR_KEYWORDS = listOf("mmproj", "vision", "projector", "visual")

    // ------------------------------------------------------------------
    // Known metadata key strings (UTF-8 byte arrays for raw scanning)
    // ------------------------------------------------------------------

    private val KEY_ARCHITECTURE = "general.architecture".toByteArray(Charsets.UTF_8)
    private val KEY_NAME = "general.name".toByteArray(Charsets.UTF_8)
    private val KEY_PARAMETER_COUNT = "general.parameter_count".toByteArray(Charsets.UTF_8)
    private val KEY_PARAMETERS = "general.parameters".toByteArray(Charsets.UTF_8)
    private val KEY_CONTEXT_LENGTH_LLAMA = "llama.context_length".toByteArray(Charsets.UTF_8)
    private val KEY_CONTEXT_LENGTH_GENERAL = "general.context_length".toByteArray(Charsets.UTF_8)
    private val KEY_EMBEDDING_LENGTH_LLAMA = "llama.embedding_length".toByteArray(Charsets.UTF_8)
    private val KEY_EMBEDDING_LENGTH_GENERAL = "general.embedding_length".toByteArray(Charsets.UTF_8)
    private val KEY_VOCAB_SIZE_TOKENIZER = "tokenizer.ggml.vocab_size".toByteArray(Charsets.UTF_8)
    private val KEY_VOCAB_SIZE_GENERAL = "general.vocab_size".toByteArray(Charsets.UTF_8)
    private val KEY_VISION_PROJECTOR = "vision.projector".toByteArray(Charsets.UTF_8)
    private val KEY_CLIP_VISION = "clip.has_vision_encoder".toByteArray(Charsets.UTF_8)
    private val KEY_QUANTIZATION_VERSION = "general.quantization_version".toByteArray(Charsets.UTF_8)

    // ------------------------------------------------------------------
    // GGUF value type constants (uint32 enum values from the spec)
    // ------------------------------------------------------------------

    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    /**
     * Reads metadata from a GGUF file without loading the full model.
     *
     * Scans the first 64KB of the file looking for known metadata keys and
     * their associated values. If the file does not exist, is not a valid
     * GGUF file (does not start with "GGUF" magic), or any error occurs
     * during reading, returns default metadata with [GGUFMetadata.isProjector]
     * determined from the filename only.
     *
     * @param filePath Absolute path to the GGUF file.
     * @return Parsed [GGUFMetadata] or safe defaults on failure.
     */
    fun readMetadata(filePath: String): GGUFMetadata {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return defaultMetadata(filePath)
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val fileSize = file.length()
                val scanSize = minOf(SCAN_SIZE.toLong(), fileSize).toInt()
                if (scanSize < 8) {
                    return defaultMetadata(filePath)
                }

                val buffer = ByteArray(scanSize)
                raf.readFully(buffer)

                // Verify GGUF magic
                if (!buffer.startsWith(GGUF_MAGIC)) {
                    return defaultMetadata(filePath)
                }

                val isProj = isProjectorFile(filePath)

                // Extract fields by scanning for keys in the byte buffer
                val architecture = findStringValue(buffer, KEY_ARCHITECTURE)
                val modelName = findStringValue(buffer, KEY_NAME)
                val parameterCount = findLongValue(buffer, KEY_PARAMETER_COUNT)
                    ?: findLongValue(buffer, KEY_PARAMETERS)
                val contextLength = findIntValue(buffer, KEY_CONTEXT_LENGTH_LLAMA)
                    ?: findIntValue(buffer, KEY_CONTEXT_LENGTH_GENERAL)
                val embeddingLength = findIntValue(buffer, KEY_EMBEDDING_LENGTH_LLAMA)
                    ?: findIntValue(buffer, KEY_EMBEDDING_LENGTH_GENERAL)
                val vocabSize = findIntValue(buffer, KEY_VOCAB_SIZE_TOKENIZER)
                    ?: findIntValue(buffer, KEY_VOCAB_SIZE_GENERAL)
                val hasVision = findBoolValue(buffer, KEY_VISION_PROJECTOR) == true
                val hasClipVision = findBoolValue(buffer, KEY_CLIP_VISION) == true
                val quantizationVersion = findIntValue(buffer, KEY_QUANTIZATION_VERSION)?.toString()
                    ?: findStringValue(buffer, KEY_QUANTIZATION_VERSION)
                    ?: "unknown"

                val detectedFamily = detectModelFamily(architecture ?: "")

                GGUFMetadata(
                    filePath = filePath,
                    architecture = architecture ?: "unknown",
                    modelName = modelName ?: "unknown",
                    parameterCount = parameterCount ?: 0L,
                    contextLength = contextLength ?: 4096,
                    embeddingLength = embeddingLength ?: 0,
                    vocabSize = vocabSize ?: 0,
                    quantizationVersion = quantizationVersion,
                    hasVision = hasVision,
                    hasClipVision = hasClipVision,
                    modelFamily = detectedFamily,
                    isProjector = isProj,
                    rawMetadata = emptyMap()
                )
            }
        } catch (e: Exception) {
            // Graceful fallback: never crash
            defaultMetadata(filePath)
        }
    }

    /**
     * Checks whether the filename indicates this is a projector/vision component file.
     *
     * Looks for the keywords "mmproj", "vision", "projector", or "visual" (case-insensitive)
     * in the file's base name.
     *
     * @param filePath Absolute or relative path to the file.
     * @return `true` if the filename contains any projector keyword.
     */
    fun isProjectorFile(filePath: String): Boolean {
        val lowerName = File(filePath).name.lowercase()
        return PROJECTOR_KEYWORDS.any { lowerName.contains(it) }
    }

    /**
     * Maps a GGUF architecture string to a high-level model family.
     *
     * @param architecture Raw architecture string from GGUF metadata (e.g., "llama", "qwen2").
     * @return One of: "llama", "qwen", "phi", "gemma", "mamba", "falcon", "gpt", or "unknown".
     */
    fun detectModelFamily(architecture: String): String {
        val lower = architecture.lowercase()
        return when {
            lower.isBlank() || lower == "unknown" -> "unknown"
            lower.contains("llama") -> "llama"
            lower.contains("mixtral") -> "llama" // Mixtral is LLaMA-based
            lower.contains("qwen") -> "qwen"
            lower.contains("phi") -> "phi"
            lower.contains("gemma") -> "gemma"
            lower.contains("mamba") -> "mamba"
            lower.contains("falcon") -> "falcon"
            lower.contains("gpt") -> "gpt"
            else -> "unknown"
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** Creates safe default metadata when a file cannot be parsed. */
    private fun defaultMetadata(filePath: String): GGUFMetadata =
        GGUFMetadata(
            filePath = filePath,
            architecture = "unknown",
            parameterCount = 0L,
            contextLength = 4096,
            embeddingLength = 0,
            vocabSize = 0,
            quantizationVersion = "unknown",
            hasVision = false,
            hasClipVision = false,
            modelFamily = "unknown",
            modelName = "unknown",
            isProjector = isProjectorFile(filePath),
            rawMetadata = emptyMap()
        )

    /**
     * Scans [buffer] for occurrences of [key] and attempts to read a String value after it.
     *
     * GGUF string values are encoded as: uint64 length + UTF-8 bytes.
     * Numeric values are also accepted and converted to string.
     */
    private fun findStringValue(buffer: ByteArray, key: ByteArray): String? {
        val positions = findAllOccurrences(buffer, key)
        for (pos in positions) {
            val valuePos = pos + key.size
            if (valuePos + 5 >= buffer.size) continue

            val type = readUint32(buffer, valuePos)
            val dataPos = valuePos + 4

            when (type) {
                TYPE_STRING -> {
                    if (dataPos + 8 > buffer.size) continue
                    val strLen = readUint64(buffer, dataPos).toInt()
                    if (strLen <= 0 || strLen > 4096 || dataPos + 8 + strLen > buffer.size) continue
                    return String(buffer, dataPos + 8, strLen, Charsets.UTF_8)
                }
                TYPE_UINT32 -> {
                    if (dataPos + 4 > buffer.size) continue
                    return readUint32(buffer, dataPos).toString()
                }
                TYPE_UINT64 -> {
                    if (dataPos + 8 > buffer.size) continue
                    return readUint64(buffer, dataPos).toString()
                }
                TYPE_INT32 -> {
                    if (dataPos + 4 > buffer.size) continue
                    return readInt32(buffer, dataPos).toString()
                }
                TYPE_INT64 -> {
                    if (dataPos + 8 > buffer.size) continue
                    return readInt64(buffer, dataPos).toString()
                }
            }
        }
        return null
    }

    /** Scans [buffer] for occurrences of [key] and attempts to read an integer value after it. */
    private fun findIntValue(buffer: ByteArray, key: ByteArray): Int? {
        val positions = findAllOccurrences(buffer, key)
        for (pos in positions) {
            val valuePos = pos + key.size
            if (valuePos + 5 >= buffer.size) continue

            val type = readUint32(buffer, valuePos)
            val dataPos = valuePos + 4

            when (type) {
                TYPE_UINT32 -> {
                    if (dataPos + 4 > buffer.size) continue
                    return readUint32(buffer, dataPos).toInt()
                }
                TYPE_INT32 -> {
                    if (dataPos + 4 > buffer.size) continue
                    return readInt32(buffer, dataPos)
                }
                TYPE_UINT64 -> {
                    if (dataPos + 8 > buffer.size) continue
                    return readUint64(buffer, dataPos).toInt()
                }
                TYPE_INT64 -> {
                    if (dataPos + 8 > buffer.size) continue
                    return readInt64(buffer, dataPos).toInt()
                }
                TYPE_BOOL -> {
                    if (dataPos + 1 > buffer.size) continue
                    return if (buffer[dataPos] != 0.toByte()) 1 else 0
                }
            }
        }
        return null
    }

    /** Scans [buffer] for occurrences of [key] and attempts to read a long value after it. */
    private fun findLongValue(buffer: ByteArray, key: ByteArray): Long? {
        val positions = findAllOccurrences(buffer, key)
        for (pos in positions) {
            val valuePos = pos + key.size
            if (valuePos + 5 >= buffer.size) continue

            val type = readUint32(buffer, valuePos)
            val dataPos = valuePos + 4

            when (type) {
                TYPE_UINT32 -> {
                    if (dataPos + 4 > buffer.size) continue
                    return readUint32(buffer, dataPos).toLong()
                }
                TYPE_INT32 -> {
                    if (dataPos + 4 > buffer.size) continue
                    return readInt32(buffer, dataPos).toLong()
                }
                TYPE_UINT64 -> {
                    if (dataPos + 8 > buffer.size) continue
                    return readUint64(buffer, dataPos)
                }
                TYPE_INT64 -> {
                    if (dataPos + 8 > buffer.size) continue
                    return readInt64(buffer, dataPos)
                }
            }
        }
        return null
    }

    /** Scans [buffer] for occurrences of [key] and attempts to read a boolean value after it. */
    private fun findBoolValue(buffer: ByteArray, key: ByteArray): Boolean? {
        val positions = findAllOccurrences(buffer, key)
        for (pos in positions) {
            val valuePos = pos + key.size
            if (valuePos + 5 >= buffer.size) continue

            val type = readUint32(buffer, valuePos)
            val dataPos = valuePos + 4

            when (type) {
                TYPE_BOOL -> {
                    if (dataPos + 1 > buffer.size) continue
                    return buffer[dataPos] != 0.toByte()
                }
                TYPE_UINT32 -> {
                    if (dataPos + 4 > buffer.size) continue
                    return readUint32(buffer, dataPos) != 0u
                }
                TYPE_UINT64 -> {
                    if (dataPos + 8 > buffer.size) continue
                    return readUint64(buffer, dataPos) != 0L
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------
    // Low-level byte reading (little-endian per GGUF spec)
    // ------------------------------------------------------------------

    /** Reads a little-endian uint32 from [buffer] at [offset]. */
    private fun readUint32(buffer: ByteArray, offset: Int): UInt {
        return ((buffer[offset].toInt() and 0xFF)).toUInt() or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8).toUInt() or
            ((buffer[offset + 2].toInt() and 0xFF) shl 16).toUInt() or
            ((buffer[offset + 3].toInt() and 0xFF) shl 24).toUInt()
    }

    /** Reads a little-endian int32 from [buffer] at [offset]. */
    private fun readInt32(buffer: ByteArray, offset: Int): Int {
        return (buffer[offset].toInt() and 0xFF) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 3].toInt() and 0xFF) shl 24)
    }

    /** Reads a little-endian uint64 from [buffer] at [offset]. */
    private fun readUint64(buffer: ByteArray, offset: Int): Long {
        val low = readUint32(buffer, offset).toLong() and 0xFFFFFFFFL
        val high = readUint32(buffer, offset + 4).toLong() and 0xFFFFFFFFL
        return low or (high shl 32)
    }

    /** Reads a little-endian int64 from [buffer] at [offset]. */
    private fun readInt64(buffer: ByteArray, offset: Int): Long {
        return readUint64(buffer, offset)
    }

    // ------------------------------------------------------------------
    // Pattern search helpers
    // ------------------------------------------------------------------

    /** Checks whether [this] ByteArray starts with [prefix] at index 0. */
    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    /**
     * Finds all start positions of [pattern] within [buffer] using a naive scan.
     * Fast enough for a 64KB buffer.
     */
    private fun findAllOccurrences(buffer: ByteArray, pattern: ByteArray): List<Int> {
        if (pattern.isEmpty() || buffer.size < pattern.size) return emptyList()
        val results = mutableListOf<Int>()
        val maxStart = buffer.size - pattern.size
        for (i in 0..maxStart) {
            var match = true
            for (j in pattern.indices) {
                if (buffer[i + j] != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) results.add(i)
        }
        return results
    }
}
