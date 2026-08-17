package com.localaisearch.data.model

import kotlinx.serialization.Serializable

/**
 * Basic inference configuration with core generation parameters.
 *
 * @property temperature Sampling temperature (0.0 - 2.0).
 * @property topP Nucleus sampling parameter (0.0 - 1.0).
 * @property topK Top-k sampling parameter.
 * @property contextLength Maximum context length in tokens.
 * @property maxTokens Maximum number of tokens to generate.
 * @property repeatPenalty Penalty for repeating tokens.
 * @property useGpu Whether to use GPU acceleration.
 * @property gpuLayers Number of layers to offload to GPU.
 * @property threads Number of CPU threads to use.
 * @property seed Random seed for reproducibility (-1 for random).
 */
@Serializable
data class InferenceConfig(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val contextLength: Int = 4096,
    val maxTokens: Int = 1024,
    val repeatPenalty: Float = 1.1f,
    val useGpu: Boolean = false,
    val gpuLayers: Int = 0,
    val threads: Int = 4,
    val seed: Int = -1
)

/**
 * Advanced inference configuration extending the basic [InferenceConfig] pattern
 * with additional fields for memory management, backend selection, and caching.
 *
 * @property batchSize Batch size for inference (-1 means Auto).
 * @property useMmap Whether to use memory-mapped files for model loading.
 * @property useMemoryLock Whether to lock memory pages to prevent swapping.
 * @property kvCacheType KV cache quantization type ("auto", "fp16", "q8", "q4").
 * @property flashAttention Flash attention setting ("auto", "on", "off").
 * @property enableModelCache Whether to cache loaded models in memory.
 * @property maxCachedModels Maximum number of models to keep cached simultaneously.
 * @property autoUnloadIdle Whether to automatically unload idle models.
 * @property idleUnloadMinutes Minutes of inactivity before unloading a model.
 * @property backend The [HardwareBackend] to use for inference.
 * @property modelWarmup Whether to perform a warmup run after loading a model.
 */
@Serializable
data class AdvancedInferenceConfig(
    // Fields from InferenceConfig
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val contextLength: Int = 4096,
    val maxTokens: Int = 1024,
    val repeatPenalty: Float = 1.1f,
    val useGpu: Boolean = false,
    val gpuLayers: Int = 0,
    val threads: Int = 4,
    val seed: Int = -1,
    // Advanced fields
    val batchSize: Int = -1,
    val useMmap: Boolean = true,
    val useMemoryLock: Boolean = false,
    val kvCacheType: String = "auto",
    val flashAttention: String = "auto",
    val enableModelCache: Boolean = true,
    val maxCachedModels: Int = 2,
    val autoUnloadIdle: Boolean = true,
    val idleUnloadMinutes: Int = 15,
    val backend: HardwareBackend = HardwareBackend.AUTO,
    val modelWarmup: Boolean = false
) {
    /**
     * Converts this advanced config to a basic [InferenceConfig], dropping
     * all advanced-only fields.
     *
     * @return A basic [InferenceConfig] with matching core parameters.
     */
    fun toInferenceConfig(): InferenceConfig {
        return InferenceConfig(
            temperature = temperature,
            topP = topP,
            topK = topK,
            contextLength = contextLength,
            maxTokens = maxTokens,
            repeatPenalty = repeatPenalty,
            useGpu = useGpu,
            gpuLayers = gpuLayers,
            threads = threads,
            seed = seed
        )
    }

    /**
     * Converts this config to a [PerformanceProfile] in [PerformanceMode.CUSTOM].
     *
     * @return A [PerformanceProfile] populated from this config's values.
     */
    fun toPerformanceProfile(): PerformanceProfile {
        return PerformanceProfile(
            mode = PerformanceMode.CUSTOM,
            backend = backend,
            contextLength = contextLength,
            temperature = temperature,
            topP = topP,
            topK = topK,
            repeatPenalty = repeatPenalty,
            maxTokens = maxTokens,
            batchSize = batchSize,
            useMmap = useMmap,
            useMemoryLock = useMemoryLock,
            kvCacheType = kvCacheType,
            flashAttention = flashAttention,
            cpuThreads = threads,
            gpuLayers = gpuLayers,
            enableModelCache = enableModelCache,
            maxCachedModels = maxCachedModels,
            autoUnloadIdle = autoUnloadIdle,
            idleUnloadMinutes = idleUnloadMinutes
        )
    }

    companion object {
        /**
         * Default advanced inference configuration with sensible defaults.
         */
        val Default = AdvancedInferenceConfig()

        /**
         * Creates an [AdvancedInferenceConfig] from a [PerformanceProfile].
         *
         * @param profile The performance profile to convert.
         * @return An [AdvancedInferenceConfig] matching the profile settings.
         */
        fun fromPerformanceProfile(profile: PerformanceProfile): AdvancedInferenceConfig {
            return AdvancedInferenceConfig(
                temperature = profile.temperature,
                topP = profile.topP,
                topK = profile.topK,
                contextLength = profile.contextLength,
                maxTokens = profile.maxTokens,
                repeatPenalty = profile.repeatPenalty,
                useGpu = profile.backend == HardwareBackend.GPU || profile.backend == HardwareBackend.AUTO,
                gpuLayers = profile.gpuLayers,
                threads = profile.cpuThreads,
                seed = -1,
                batchSize = profile.batchSize,
                useMmap = profile.useMmap,
                useMemoryLock = profile.useMemoryLock,
                kvCacheType = profile.kvCacheType,
                flashAttention = profile.flashAttention,
                enableModelCache = profile.enableModelCache,
                maxCachedModels = profile.maxCachedModels,
                autoUnloadIdle = profile.autoUnloadIdle,
                idleUnloadMinutes = profile.idleUnloadMinutes,
                backend = profile.backend,
                modelWarmup = false
            )
        }
    }
}
