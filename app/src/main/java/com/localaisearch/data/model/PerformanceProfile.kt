package com.localaisearch.data.model

import kotlinx.serialization.Serializable

/**
 * Enum representing different performance/quality trade-off modes.
 *
 * @property displayName Human-readable name for UI display.
 * @property description Short explanation of the mode.
 */
@Serializable
enum class PerformanceMode(val displayName: String, val description: String) {
    POWER_SAVE(
        displayName = "Power Save",
        description = "Minimizes battery usage by using CPU-only inference with low context length."
    ),
    BALANCED(
        displayName = "Balanced",
        description = "Automatically selects the best backend with moderate context length."
    ),
    PERFORMANCE(
        displayName = "Performance",
        description = "Uses GPU acceleration with high context length for faster responses."
    ),
    EXTREME(
        displayName = "Extreme",
        description = "Maximum GPU layers and the largest context length for the best quality."
    ),
    CUSTOM(
        displayName = "Custom",
        description = "User-defined settings that override all presets."
    )
}

/**
 * Data class representing a complete performance profile configuration.
 *
 * @property mode The selected [PerformanceMode].
 * @property backend The [HardwareBackend] to use.
 * @property contextLength Maximum context length in tokens.
 * @property temperature Sampling temperature.
 * @property topP Nucleus sampling parameter.
 * @property topK Top-k sampling parameter.
 * @property repeatPenalty Penalty for repeating tokens.
 * @property maxTokens Maximum number of tokens to generate.
 * @property batchSize Batch size for inference (-1 means Auto).
 * @property useMmap Whether to use memory-mapped files.
 * @property useMemoryLock Whether to lock memory pages.
 * @property kvCacheType KV cache quantization type ("auto", "fp16", "q8", "q4").
 * @property flashAttention Flash attention setting ("auto", "on", "off").
 * @property cpuThreads Number of CPU threads to use.
 * @property gpuLayers Number of layers to offload to GPU.
 * @property enableModelCache Whether to cache loaded models.
 * @property maxCachedModels Maximum number of models to keep cached.
 * @property autoUnloadIdle Whether to unload idle models automatically.
 * @property idleUnloadMinutes Minutes of inactivity before unloading a model.
 */
@Serializable
data class PerformanceProfile(
    val mode: PerformanceMode,
    val backend: HardwareBackend,
    val contextLength: Int,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val repeatPenalty: Float,
    val maxTokens: Int,
    val batchSize: Int,
    val useMmap: Boolean,
    val useMemoryLock: Boolean,
    val kvCacheType: String,
    val flashAttention: String,
    val cpuThreads: Int,
    val gpuLayers: Int,
    val enableModelCache: Boolean,
    val maxCachedModels: Int,
    val autoUnloadIdle: Boolean,
    val idleUnloadMinutes: Int
)

/**
 * Object containing predefined [PerformanceProfile] presets for each [PerformanceMode].
 */
object PerformanceProfilePresets {

    /**
     * CPU-only, low context length profile optimized for battery life.
     */
    val POWER_SAVE = PerformanceProfile(
        mode = PerformanceMode.POWER_SAVE,
        backend = HardwareBackend.CPU,
        contextLength = 2048,
        temperature = 0.7f,
        topP = 0.9f,
        topK = 40,
        repeatPenalty = 1.1f,
        maxTokens = 512,
        batchSize = -1,
        useMmap = true,
        useMemoryLock = false,
        kvCacheType = "q8",
        flashAttention = "off",
        cpuThreads = 4,
        gpuLayers = 0,
        enableModelCache = false,
        maxCachedModels = 1,
        autoUnloadIdle = true,
        idleUnloadMinutes = 5
    )

    /**
     * Balanced profile using AUTO backend with moderate settings.
     */
    val BALANCED = PerformanceProfile(
        mode = PerformanceMode.BALANCED,
        backend = HardwareBackend.AUTO,
        contextLength = 4096,
        temperature = 0.7f,
        topP = 0.9f,
        topK = 40,
        repeatPenalty = 1.1f,
        maxTokens = 1024,
        batchSize = -1,
        useMmap = true,
        useMemoryLock = false,
        kvCacheType = "auto",
        flashAttention = "auto",
        cpuThreads = 4,
        gpuLayers = -1,
        enableModelCache = true,
        maxCachedModels = 2,
        autoUnloadIdle = true,
        idleUnloadMinutes = 15
    )

    /**
     * Performance profile utilizing GPU acceleration with high context length.
     */
    val PERFORMANCE = PerformanceProfile(
        mode = PerformanceMode.PERFORMANCE,
        backend = HardwareBackend.GPU,
        contextLength = 8192,
        temperature = 0.7f,
        topP = 0.9f,
        topK = 40,
        repeatPenalty = 1.1f,
        maxTokens = 2048,
        batchSize = -1,
        useMmap = true,
        useMemoryLock = true,
        kvCacheType = "auto",
        flashAttention = "on",
        cpuThreads = 6,
        gpuLayers = 32,
        enableModelCache = true,
        maxCachedModels = 3,
        autoUnloadIdle = true,
        idleUnloadMinutes = 30
    )

    /**
     * Extreme profile with maximum GPU layers and largest context length.
     */
    val EXTREME = PerformanceProfile(
        mode = PerformanceMode.EXTREME,
        backend = HardwareBackend.GPU,
        contextLength = 16384,
        temperature = 0.7f,
        topP = 0.95f,
        topK = 50,
        repeatPenalty = 1.1f,
        maxTokens = 4096,
        batchSize = -1,
        useMmap = true,
        useMemoryLock = true,
        kvCacheType = "fp16",
        flashAttention = "on",
        cpuThreads = 8,
        gpuLayers = 99,
        enableModelCache = true,
        maxCachedModels = 5,
        autoUnloadIdle = false,
        idleUnloadMinutes = 0
    )

    /**
     * Default profile used when no specific mode is selected.
     */
    val DEFAULT = BALANCED

    /**
     * Returns the preset [PerformanceProfile] for a given [PerformanceMode].
     *
     * @param mode The desired performance mode.
     * @return The corresponding preset profile.
     */
    fun fromMode(mode: PerformanceMode): PerformanceProfile {
        return when (mode) {
            PerformanceMode.POWER_SAVE -> POWER_SAVE
            PerformanceMode.BALANCED -> BALANCED
            PerformanceMode.PERFORMANCE -> PERFORMANCE
            PerformanceMode.EXTREME -> EXTREME
            PerformanceMode.CUSTOM -> BALANCED // Fallback for custom until user modifies
        }
    }
}
