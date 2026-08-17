package com.localaisearch.data.performance

import android.app.ActivityManager
import android.content.Context

/**
 * Enumeration of memory health states.
 */
enum class MemoryState {
    OK,
    WARNING,
    CRITICAL
}

/**
 * Data class representing a snapshot of the device's current memory status.
 */
data class MemoryStatus(
    val totalRam: Long,
    val availableRam: Long,
    val modelRamUsage: Long,
    val kvCacheUsage: Long,
    val systemUsage: Long,
    val percentUsed: Float,
    val state: MemoryState
)

/**
 * Stub interface representing the LLM engine used for model operations.
 * This should be replaced with the actual LLMEngine implementation in the project.
 */
interface LLMEngine {
    fun clearModelCache()
    fun unloadIdleModels()
    fun reduceKvCacheSize(targetBytes: Long)
}

/**
 * Data class representing advanced inference configuration parameters.
 */
data class AdvancedInferenceConfig(
    val contextLength: Int,
    val batchSize: Int,
    val useMmap: Boolean,
    val kvCacheQuality: Float
)

/**
 * Manages memory optimization and handles low-memory conditions for on-device inference.
 *
 * @param context Android [Context] used to access system services.
 * @param llmEngine The [LLMEngine] used to perform memory-related model operations.
 */
class MemoryManager(
    private val context: Context,
    private val llmEngine: LLMEngine
) {

    private val activityManager: ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /** Total physical RAM of the device in bytes. */
    val totalRam: Long
        get() = queryMemoryInfo().totalMem

    /** Currently available RAM in bytes. */
    val availableRam: Long
        get() = queryMemoryInfo().availMem

    /** Estimated RAM currently used by loaded models. */
    var modelRamUsage: Long = 0L
        private set

    /** Estimated RAM currently used by the key-value cache. */
    var kvCacheRamUsage: Long = 0L
        private set

    private val memoryPressureCallbacks = mutableListOf<() -> Unit>()

    /**
     * Registers a callback to be invoked when the system is under memory pressure.
     *
     * @param callback A no-argument lambda invoked on low memory.
     */
    fun registerMemoryPressureCallback(callback: () -> Unit) {
        memoryPressureCallbacks.add(callback)
    }

    /**
     * Returns a [MemoryStatus] snapshot containing all current memory metrics.
     *
     * @return A [MemoryStatus] data class with the latest values.
     */
    fun getMemoryStatus(): MemoryStatus {
        val memoryInfo = queryMemoryInfo()
        val systemUsage = memoryInfo.totalMem - memoryInfo.availMem
        val percentUsed = if (memoryInfo.totalMem > 0) {
            (systemUsage.toFloat() / memoryInfo.totalMem.toFloat()) * 100f
        } else {
            0f
        }

        val state = when {
            isCriticalMemory() -> MemoryState.CRITICAL
            isLowMemory() -> MemoryState.WARNING
            else -> MemoryState.OK
        }

        return MemoryStatus(
            totalRam = memoryInfo.totalMem,
            availableRam = memoryInfo.availMem,
            modelRamUsage = modelRamUsage,
            kvCacheUsage = kvCacheRamUsage,
            systemUsage = systemUsage,
            percentUsed = percentUsed,
            state = state
        )
    }

    /**
     * Performs aggressive memory cleanup to free resources.
     *
     * Actions include clearing model cache, unloading idle models,
     * reducing kv cache size, and requesting garbage collection.
     */
    fun requestMemoryOptimization() {
        llmEngine.clearModelCache()
        llmEngine.unloadIdleModels()

        // Reduce kv cache to half of current usage, but not below zero
        val targetKvCache = (kvCacheRamUsage / 2).coerceAtLeast(0L)
        llmEngine.reduceKvCacheSize(targetKvCache)
        kvCacheRamUsage = targetKvCache

        // Request garbage collection
        System.gc()

        // Notify registered callbacks if memory is still low after cleanup
        if (isLowMemory()) {
            memoryPressureCallbacks.forEach { it.invoke() }
        }
    }

    /**
     * Determines whether a model of the given size can be safely loaded.
     *
     * Uses a safety margin of 1.5x the model size.
     *
     * @param modelSizeBytes The size of the model in bytes.
     * @return true if enough RAM is available.
     */
    fun canLoadModel(modelSizeBytes: Long): Boolean {
        val requiredBytes = (modelSizeBytes * 1.5).toLong()
        return availableRam >= requiredBytes
    }

    /**
     * Estimates the RAM required to run a model with the given parameters.
     *
     * @param modelSizeBytes The on-disk size of the model.
     * @param quantization The quantization type (e.g., "q4_0", "q8_0", "fp16").
     * @param useMmap Whether the model is memory-mapped rather than fully loaded.
     * @return Estimated RAM usage in bytes.
     */
    fun estimateModelRamUsage(
        modelSizeBytes: Long,
        quantization: String,
        useMmap: Boolean
    ): Long {
        val quantizationMultiplier = when (quantization.lowercase()) {
            "q4_0", "q4_k_m", "q4_k_s" -> 0.5
            "q5_0", "q5_k_m", "q5_k_s" -> 0.625
            "q8_0" -> 1.0
            "fp16" -> 2.0
            "fp32" -> 4.0
            else -> 1.0
        }

        val baseRam = (modelSizeBytes * quantizationMultiplier).toLong()

        return if (useMmap) {
            // Memory-mapped models only need working memory, estimate ~10% of base
            (baseRam * 0.1).toLong()
        } else {
            baseRam
        }
    }

    /**
     * Checks whether the device is in a low-memory state.
     *
     * @return true if available RAM is less than 10% of total RAM.
     */
    fun isLowMemory(): Boolean {
        val memoryInfo = queryMemoryInfo()
        return if (memoryInfo.totalMem > 0) {
            memoryInfo.availMem < (memoryInfo.totalMem * 0.10).toLong()
        } else {
            false
        }
    }

    /**
     * Checks whether the device is in a critically low-memory state.
     *
     * @return true if available RAM is less than 5% of total RAM.
     */
    fun isCriticalMemory(): Boolean {
        val memoryInfo = queryMemoryInfo()
        return if (memoryInfo.totalMem > 0) {
            memoryInfo.availMem < (memoryInfo.totalMem * 0.05).toLong()
        } else {
            false
        }
    }

    /**
     * Automatically adjusts inference configuration to reduce memory pressure.
     *
     * Adjustments include reducing context length, batch size, enabling mmap,
     * and reducing kv cache quality when memory is low.
     *
     * @param advancedConfig The current [AdvancedInferenceConfig].
     * @return A modified [AdvancedInferenceConfig] optimized for current memory conditions.
     */
    fun autoAdjustForMemory(advancedConfig: AdvancedInferenceConfig): AdvancedInferenceConfig {
        val memoryInfo = queryMemoryInfo()
        val availPercent = if (memoryInfo.totalMem > 0) {
            memoryInfo.availMem.toFloat() / memoryInfo.totalMem.toFloat()
        } else {
            1f
        }

        return when {
            availPercent < 0.05f -> {
                // Critical: minimize everything
                advancedConfig.copy(
                    contextLength = (advancedConfig.contextLength / 4).coerceAtLeast(512),
                    batchSize = 1,
                    useMmap = true,
                    kvCacheQuality = 0.25f
                )
            }
            availPercent < 0.10f -> {
                // Warning: moderate reductions
                advancedConfig.copy(
                    contextLength = (advancedConfig.contextLength / 2).coerceAtLeast(1024),
                    batchSize = (advancedConfig.batchSize / 2).coerceAtLeast(1),
                    useMmap = true,
                    kvCacheQuality = (advancedConfig.kvCacheQuality * 0.5f).coerceAtLeast(0.25f)
                )
            }
            availPercent < 0.20f -> {
                // Caution: mild reductions
                advancedConfig.copy(
                    contextLength = (advancedConfig.contextLength * 0.75).toInt().coerceAtLeast(1024),
                    batchSize = (advancedConfig.batchSize * 0.75).toInt().coerceAtLeast(1),
                    useMmap = true,
                    kvCacheQuality = (advancedConfig.kvCacheQuality * 0.75f).coerceAtLeast(0.5f)
                )
            }
            else -> {
                // OK: keep current settings
                advancedConfig
            }
        }
    }

    /**
     * Updates the tracked model RAM usage.
     *
     * @param bytes The current model RAM usage in bytes.
     */
    fun updateModelRamUsage(bytes: Long) {
        modelRamUsage = bytes.coerceAtLeast(0L)
    }

    /**
     * Updates the tracked KV cache RAM usage.
     *
     * @param bytes The current KV cache RAM usage in bytes.
     */
    fun updateKvCacheRamUsage(bytes: Long) {
        kvCacheRamUsage = bytes.coerceAtLeast(0L)
    }

    /**
     * Queries the current memory info from [ActivityManager].
     *
     * @return An [ActivityManager.MemoryInfo] object with current values.
     */
    private fun queryMemoryInfo(): ActivityManager.MemoryInfo {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo
    }
}
