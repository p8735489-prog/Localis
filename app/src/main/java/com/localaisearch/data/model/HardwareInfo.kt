package com.localaisearch.data.model

import kotlinx.serialization.Serializable

/**
 * Enum representing the available hardware backends for inference.
 */
@Serializable
enum class HardwareBackend {
    CPU,
    GPU,
    NPU,
    AUTO
}

/**
 * Enum representing the overall capability level of a device.
 */
@Serializable
enum class DeviceCapability {
    LOW,
    MEDIUM,
    HIGH,
    FLAGSHIP
}

/**
 * Data class holding static hardware information about the device.
 *
 * @property deviceModel The commercial name/model of the device.
 * @property totalRamBytes Total physical RAM installed on the device.
 * @property availableRamBytes Currently available/free RAM.
 * @property cpuCores Number of logical CPU cores.
 * @property cpuMaxFreqMHz Maximum CPU frequency in MHz.
 * @property hasGpu Whether the device has a GPU suitable for compute.
 * @property gpuName Name of the GPU, or null if unavailable.
 * @property hasNpu Whether the device has an NPU (Neural Processing Unit).
 * @property npuName Name of the NPU, or null if unavailable.
 * @property isVulkanAvailable Whether the Vulkan graphics API is available.
 * @property recommendedBackend The recommended [HardwareBackend] based on detected hardware.
 */
@Serializable
data class HardwareInfo(
    val deviceModel: String,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val cpuCores: Int,
    val cpuMaxFreqMHz: Int,
    val hasGpu: Boolean,
    val gpuName: String? = null,
    val hasNpu: Boolean,
    val npuName: String? = null,
    val isVulkanAvailable: Boolean,
    val recommendedBackend: HardwareBackend
)

/**
 * Sealed class representing real-time hardware status for monitoring.
 */
@Serializable
sealed class HardwareStatus {
    /**
     * Device is idle and ready for inference.
     */
    @Serializable
    data object Idle : HardwareStatus()

    /**
     * Model is currently being loaded into memory.
     */
    @Serializable
    data object Loading : HardwareStatus()

    /**
     * Inference is actively running.
     */
    @Serializable
    data object Running : HardwareStatus()

    /**
     * Available memory has dropped below a safe threshold.
     */
    @Serializable
    data class MemoryWarning(
        val availableRamBytes: Long,
        val thresholdBytes: Long
    ) : HardwareStatus()

    /**
     * The system is out of memory; inference cannot continue safely.
     */
    @Serializable
    data class OutOfMemory(
        val requestedBytes: Long,
        val availableRamBytes: Long
    ) : HardwareStatus()
}

/**
 * Formats a byte count into a human-readable string (e.g., "1.5 GB").
 *
 * @param bytes The number of bytes.
 * @return A formatted string with appropriate unit (B, KB, MB, GB, TB).
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "-${formatBytes(-bytes)}"
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val exp = (kotlin.math.log2(bytes.toDouble()) / 10).toInt().coerceAtMost(units.size - 1)
    val value = bytes / Math.pow(1024.0, exp.toDouble())
    return String.format("%.1f %s", value, units[exp])
}

/**
 * Formats a byte count into a human-readable string (e.g., "1.5 GB").
 *
 * @param bytes The number of bytes.
 * @return A formatted string with appropriate unit (B, KB, MB, GB, TB).
 */
fun formatBytes(bytes: Int): String = formatBytes(bytes.toLong())

/**
 * Determines the [DeviceCapability] based on total RAM and CPU characteristics.
 *
 * @param totalRamBytes Total physical RAM in bytes.
 * @param cpuMaxFreqMHz Maximum CPU frequency in MHz.
 * @param cpuCores Number of CPU cores.
 * @return The estimated [DeviceCapability].
 */
fun determineDeviceCapability(
    totalRamBytes: Long,
    cpuMaxFreqMHz: Int,
    cpuCores: Int
): DeviceCapability {
    val totalRamMB = totalRamBytes / (1024L * 1024L)
    return when {
        totalRamMB >= 12_288 && cpuMaxFreqMHz >= 2800 && cpuCores >= 8 -> DeviceCapability.FLAGSHIP
        totalRamMB >= 6_144 && cpuMaxFreqMHz >= 2000 && cpuCores >= 6 -> DeviceCapability.HIGH
        totalRamMB >= 3_072 && cpuMaxFreqMHz >= 1500 && cpuCores >= 4 -> DeviceCapability.MEDIUM
        else -> DeviceCapability.LOW
    }
}
