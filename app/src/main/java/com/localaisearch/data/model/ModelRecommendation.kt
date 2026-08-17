package com.localaisearch.data.model

import kotlinx.serialization.Serializable

/**
 * Star rating levels for model-device compatibility.
 *
 * - RECOMMENDED (5 stars): Model runs comfortably with excellent performance.
 * - RUNNABLE (4 stars): Model runs well with good performance.
 * - MARGINAL (3 stars): Model can run but may be slow or use significant RAM.
 * - NOT_RECOMMENDED: Model is too large or incompatible for this device.
 */
@Serializable
enum class ModelRating {
    /** 5 stars - runs comfortably, excellent performance */
    RECOMMENDED,
    /** 4 stars - runs well, good performance */
    RUNNABLE,
    /** 3 stars - can run but may be slow or memory-heavy */
    MARGINAL,
    /** Not recommended - too large or incompatible */
    NOT_RECOMMENDED;

    /** Display string with star characters. */
    val stars: String
        get() = when (this) {
            RECOMMENDED -> "\u2605\u2605\u2605\u2605\u2605"
            RUNNABLE -> "\u2605\u2605\u2605\u2605\u2606"
            MARGINAL -> "\u2605\u2605\u2605\u2606\u2606"
            NOT_RECOMMENDED -> "\u274C"
        }

    /** Short label for the rating. */
    val label: String
        get() = when (this) {
            RECOMMENDED -> "Recommended"
            RUNNABLE -> "Runnable"
            MARGINAL -> "Marginal"
            NOT_RECOMMENDED -> "Not Recommended"
        }
}

/**
 * Detailed model recommendation result including rating, reasoning,
 * and estimated performance metrics for a specific device.
 *
 * @property rating The star rating level.
 * @property stars Display string (e.g., "\u2605\u2605\u2605\u2605\u2605").
 * @property label Short label (e.g., "Recommended").
 * @property reason Human-readable explanation.
 * @property estimatedRamMB Estimated RAM usage in megabytes.
 * @property estimatedTokensPerSecond Estimated inference speed.
 * @property fitsInRam Whether the model fits within available RAM.
 * @property hasAccelerator Whether GPU/NPU acceleration is available.
 */
@Serializable
data class DeviceModelRating(
    val rating: ModelRating,
    val stars: String,
    val label: String,
    val reason: String,
    val estimatedRamMB: Int,
    val estimatedTokensPerSecond: Int,
    val fitsInRam: Boolean,
    val hasAccelerator: Boolean
)

/**
 * Data class representing a model recommendation for a specific device configuration.
 *
 * @property quantization The recommended quantization format (e.g., "Q4_K_M").
 * @property reason Human-readable explanation for why this quantization was recommended.
 * @property estimatedRamMB Estimated RAM usage in megabytes.
 * @property estimatedTokensPerSecond Estimated inference speed in tokens per second.
 * @property recommendedForDevice Description of the target device class (e.g., "Flagship Phone", "Mid-range").
 */
@Serializable
data class ModelRecommendation(
    val quantization: String,
    val reason: String,
    val estimatedRamMB: Int,
    val estimatedTokensPerSecond: Int,
    val recommendedForDevice: String
)

/**
 * Data class holding metadata about a specific quantization format.
 *
 * @property name The quantization identifier (e.g., "Q4_K_M").
 * @property description Human-readable description of the quantization method.
 * @property ramMultiplier Approximate multiplier applied to the base model size to estimate RAM usage.
 * @property qualityScore Quality score from 1 (lowest) to 10 (highest).
 * @property speedScore Speed score from 1 (slowest) to 10 (fastest).
 * @property recommendedMinRamMB Minimum recommended device RAM (in MB) to use this quantization comfortably.
 */
@Serializable
data class QuantizationInfo(
    val name: String,
    val description: String,
    val ramMultiplier: Float,
    val qualityScore: Int,
    val speedScore: Int,
    val recommendedMinRamMB: Int
)

/**
 * Singleton object providing model quantization recommendations based on device capabilities.
 *
 * Provides two levels of recommendation:
 * 1. [getRecommendedQuantizations] - Lists suitable quantization formats for the device.
 * 2. [rateModelForDevice] - Produces a star rating for a specific model + device combo.
 */
object ModelRecommender {

    /**
     * Catalog of supported quantization formats with their characteristics.
     */
    val quantizationCatalog: List<QuantizationInfo> = listOf(
        QuantizationInfo(
            name = "Q4_K_M",
            description = "4-bit K-quant with medium mixture. Good balance of size, quality, and speed.",
            ramMultiplier = 1.15f,
            qualityScore = 7,
            speedScore = 8,
            recommendedMinRamMB = 3_072
        ),
        QuantizationInfo(
            name = "Q5_K_M",
            description = "5-bit K-quant with medium mixture. Slightly larger than Q4 with noticeably better quality.",
            ramMultiplier = 1.30f,
            qualityScore = 8,
            speedScore = 7,
            recommendedMinRamMB = 4_096
        ),
        QuantizationInfo(
            name = "Q6_K",
            description = "6-bit K-quant. High quality with moderate size increase over Q5.",
            ramMultiplier = 1.45f,
            qualityScore = 9,
            speedScore = 6,
            recommendedMinRamMB = 6_144
        ),
        QuantizationInfo(
            name = "Q8_0",
            description = "8-bit quantization. Near-original quality, but significantly larger and slower.",
            ramMultiplier = 1.75f,
            qualityScore = 10,
            speedScore = 4,
            recommendedMinRamMB = 8_192
        ),
        QuantizationInfo(
            name = "Q4_0",
            description = "Legacy 4-bit quantization. Smallest size, lowest quality. Use only on very constrained devices.",
            ramMultiplier = 1.05f,
            qualityScore = 5,
            speedScore = 9,
            recommendedMinRamMB = 2_048
        ),
        QuantizationInfo(
            name = "Q5_0",
            description = "Legacy 5-bit quantization. Better quality than Q4_0 but less optimized than K-quants.",
            ramMultiplier = 1.20f,
            qualityScore = 6,
            speedScore = 7,
            recommendedMinRamMB = 3_072
        )
    )

    /**
     * Returns a list of [QuantizationInfo] entries recommended for the given device,
     * ordered from most to least suitable.
     *
     * @param totalRamMB Total device RAM in megabytes.
     * @param hasGpu Whether the device has a GPU.
     * @param hasNpu Whether the device has an NPU.
     * @return Filtered and ordered list of suitable quantization options.
     */
    fun getRecommendedQuantizations(
        totalRamMB: Int,
        hasGpu: Boolean,
        hasNpu: Boolean
    ): List<QuantizationInfo> {
        val hasAccelerator = hasGpu || hasNpu
        val deviceCapability = determineDeviceCapability(
            totalRamBytes = totalRamMB * 1024L * 1024L,
            cpuMaxFreqMHz = 2000, // Default assumption
            cpuCores = 4           // Default assumption
        )

        val suitable = quantizationCatalog.filter { it.recommendedMinRamMB <= totalRamMB }

        return suitable.sortedWith(
            compareByDescending<QuantizationInfo> { info ->
                when (deviceCapability) {
                    DeviceCapability.FLAGSHIP -> info.qualityScore
                    DeviceCapability.HIGH -> info.qualityScore * 2 + info.speedScore
                    DeviceCapability.MEDIUM -> info.speedScore * 2 + info.qualityScore
                    DeviceCapability.LOW -> info.speedScore
                }
            }.thenByDescending { it.qualityScore }
        )
    }

    /**
     * Roughly estimates RAM usage (in MB) for a model file given its size and quantization.
     *
     * @param modelFileSizeGB The model file size in gigabytes.
     * @param quantization The quantization format name.
     * @return Estimated RAM usage in megabytes, or 0 if quantization is unknown.
     */
    fun estimateRamUsage(modelFileSizeGB: Float, quantization: String): Int {
        val info = quantizationCatalog.find { it.name == quantization }
        val multiplier = info?.ramMultiplier ?: 1.0f
        val fileSizeMB = modelFileSizeGB * 1024f
        val overhead = 1.1f // 10% runtime overhead
        return (fileSizeMB * multiplier * overhead).toInt()
    }

    /**
     * Rate a model for the current device based on model metadata and hardware capabilities.
     *
     * Evaluation criteria:
     * - Model size vs. available RAM
     * - Quantization suitability for device capability
     * - Context length requirements
     * - GPU/NPU acceleration availability
     * - Vision projector requirements (if applicable)
     *
     * @param modelSizeGB The model file size in gigabytes.
     * @param quantization The quantization format name (e.g., "Q4_K_M").
     * @param contextLength Required context length in tokens.
     * @param hasProjector Whether the model includes a vision projector.
     * @param hardwareInfo The detected hardware capabilities of the device.
     * @return A [DeviceModelRating] with star rating and details.
     */
    fun rateModelForDevice(
        modelSizeGB: Float,
        quantization: String,
        contextLength: Int,
        hasProjector: Boolean,
        hardwareInfo: HardwareInfo
    ): DeviceModelRating {
        val totalRamMB = (hardwareInfo.totalRamBytes / (1024L * 1024L)).toInt()
        val availableRamMB = (hardwareInfo.availableRamBytes / (1024L * 1024L)).toInt()
        val estimatedRamMB = estimateRamUsage(modelSizeGB, quantization)
        val hasAccelerator = hardwareInfo.hasGpu || hardwareInfo.hasNpu
        val quantInfo = quantizationCatalog.find { it.name == quantization }

        // Determine device capability
        val deviceCapability = determineDeviceCapability(
            totalRamBytes = hardwareInfo.totalRamBytes,
            cpuMaxFreqMHz = hardwareInfo.cpuMaxFreqMHz,
            cpuCores = hardwareInfo.cpuCores
        )

        // Check if model fits in available RAM (with 20% safety margin)
        val ramBudget = (availableRamMB * 0.8).toInt()
        val fitsInRam = estimatedRamMB <= ramBudget
        val fitsInTotalRam = estimatedRamMB <= totalRamMB

        // Estimate tokens per second based on device capability and quantization
        val baseTps = when (deviceCapability) {
            DeviceCapability.FLAGSHIP -> 25
            DeviceCapability.HIGH -> 15
            DeviceCapability.MEDIUM -> 8
            DeviceCapability.LOW -> 4
        }
        val accelMultiplier = if (hasAccelerator) 1.5f else 1.0f
        val quantSpeedFactor = (quantInfo?.speedScore ?: 5) / 10.0f
        val estimatedTps = (baseTps * accelMultiplier * quantSpeedFactor).toInt()

        // Determine rating
        val rating: ModelRating
        val reason: String

        when {
            !fitsInTotalRam -> {
                rating = ModelRating.NOT_RECOMMENDED
                reason = "Model requires ~${estimatedRamMB}MB RAM but device only has " +
                    "${totalRamMB}MB total. Model cannot be loaded."
            }

            !fitsInRam -> {
                rating = ModelRating.NOT_RECOMMENDED
                reason = "Model requires ~${estimatedRamMB}MB RAM but only " +
                    "${availableRamMB}MB available. Loading may cause OOM crashes."
            }

            estimatedRamMB <= ramBudget * 0.5 && hasAccelerator && deviceCapability >= DeviceCapability.HIGH -> {
                rating = ModelRating.RECOMMENDED
                reason = "Model fits comfortably in RAM (~${estimatedRamMB}MB / " +
                    "${availableRamMB}MB available). ${if (hasAccelerator) "Hardware acceleration available. " else ""}" +
                    "Expected ~${estimatedTps} tokens/sec."
            }

            estimatedRamMB <= ramBudget * 0.7 && deviceCapability >= DeviceCapability.MEDIUM -> {
                rating = ModelRating.RUNNABLE
                reason = "Model runs well (~${estimatedRamMB}MB / " +
                    "${availableRamMB}MB available). Expected ~${estimatedTps} tokens/sec."
            }

            fitsInRam -> {
                rating = ModelRating.MARGINAL
                reason = "Model can run but uses ${estimatedRamMB}MB of " +
                    "${availableRamMB}MB available RAM. Performance may be limited " +
                    "(~${estimatedTps} tokens/sec). Consider a smaller quantization."
            }

            else -> {
                rating = ModelRating.NOT_RECOMMENDED
                reason = "Model is too large for this device."
            }
        }

        // Adjust for context length
        val contextMB = (contextLength * 4) / (1024 * 1024) // ~4 bytes per token
        val totalWithCtx = estimatedRamMB + contextMB
        if (totalWithCtx > availableRamMB && rating == ModelRating.RECOMMENDED) {
            // Downgrade if context pushes it over budget
            return DeviceModelRating(
                rating = ModelRating.RUNNABLE,
                stars = ModelRating.RUNNABLE.stars,
                label = ModelRating.RUNNABLE.label,
                reason = "$reason Note: Large context (${contextLength} tokens) increases memory usage.",
                estimatedRamMB = totalWithCtx,
                estimatedTokensPerSecond = estimatedTps,
                fitsInRam = false,
                hasAccelerator = hasAccelerator
            )
        }

        return DeviceModelRating(
            rating = rating,
            stars = rating.stars,
            label = rating.label,
            reason = reason,
            estimatedRamMB = estimatedRamMB,
            estimatedTokensPerSecond = estimatedTps,
            fitsInRam = fitsInRam,
            hasAccelerator = hasAccelerator
        )
    }
}
