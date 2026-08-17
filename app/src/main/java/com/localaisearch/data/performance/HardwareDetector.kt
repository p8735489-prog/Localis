package com.localaisearch.data.performance

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Data class representing detected hardware capabilities of the device.
 */
data class HardwareInfo(
    val totalRamBytes: Long,
    val cpuCores: Int,
    val cpuMaxFrequencyKHz: Int,
    val hasGpu: Boolean,
    val gpuVendor: String?,
    val hasNpu: Boolean,
    val npuVendor: String?,
    val supportsVulkan: Boolean,
    val supportsOpenGLES31: Boolean,
    val buildManufacturer: String = Build.MANUFACTURER,
    val buildHardware: String = Build.HARDWARE,
    val buildBoard: String = Build.BOARD
)

/**
 * Enumeration of available hardware backends for inference.
 */
enum class HardwareBackend {
    CPU,
    GPU,
    NPU
}

/**
 * Singleton object that detects device hardware capabilities.
 *
 * This object safely queries the device for RAM, CPU, GPU, and NPU information,
 * handling failures gracefully and returning sensible defaults.
 */
object HardwareDetector {

    private val GPU_VENDORS = listOf(
        "qualcomm", "qcom", "arm", "mali", "powervr", "adreno", "nvidia", "intel",
        "broadcom", "imagination", "vivante"
    )

    private val NPU_VENDORS = listOf(
        "qcom", "qualcomm", "mediatek", "unisoc", "samsung", "exynos", "google", "tensor"
    )

    /**
     * Analyzes device capabilities and returns a [HardwareInfo] snapshot.
     *
     * @param context Android [Context] used to access system services.
     * @return A [HardwareInfo] containing detected hardware details.
     */
    fun detectHardware(context: Context): HardwareInfo {
        val totalRam = detectTotalRam(context)
        val cpuCores = detectCpuCores()
        val cpuMaxFreq = detectCpuMaxFrequency()
        val gpuVendor = detectGpuVendor(context)
        val hasGpu = gpuVendor != null
        val npuVendor = detectNpuVendor()
        val hasNpu = npuVendor != null
        val supportsVulkan = checkVulkanSupport(context)
        val supportsOpenGLES31 = checkOpenGLES31Support(context)

        return HardwareInfo(
            totalRamBytes = totalRam,
            cpuCores = cpuCores,
            cpuMaxFrequencyKHz = cpuMaxFreq,
            hasGpu = hasGpu,
            gpuVendor = gpuVendor,
            hasNpu = hasNpu,
            npuVendor = npuVendor,
            supportsVulkan = supportsVulkan,
            supportsOpenGLES31 = supportsOpenGLES31
        )
    }

    /**
     * Determines the optimal backend based on detected hardware.
     *
     * Priority: NPU > GPU > CPU
     *
     * @param hardwareInfo The detected hardware capabilities.
     * @return The best available [HardwareBackend].
     */
    fun detectOptimalBackend(hardwareInfo: HardwareInfo): HardwareBackend {
        return when {
            hardwareInfo.hasNpu -> HardwareBackend.NPU
            hardwareInfo.hasGpu -> HardwareBackend.GPU
            else -> HardwareBackend.CPU
        }
    }

    /**
     * Checks whether the requested [backend] is supported given the detected [hardwareInfo].
     *
     * @param backend The backend to verify.
     * @param hardwareInfo The detected hardware capabilities.
     * @return true if the backend is available on this device.
     */
    fun isBackendSupported(backend: HardwareBackend, hardwareInfo: HardwareInfo): Boolean {
        return when (backend) {
            HardwareBackend.NPU -> hardwareInfo.hasNpu
            HardwareBackend.GPU -> hardwareInfo.hasGpu
            HardwareBackend.CPU -> true
        }
    }

    /**
     * Queries total RAM via [ActivityManager.MemoryInfo].
     *
     * @param context Android [Context].
     * @return Total RAM in bytes, or 0L if detection fails.
     */
    private fun detectTotalRam(context: Context): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Queries the number of available CPU cores.
     *
     * @return Number of CPU cores, or 1 if detection fails.
     */
    private fun detectCpuCores(): Int {
        return try {
            Runtime.getRuntime().availableProcessors()
        } catch (e: Exception) {
            1
        }
    }

    /**
     * Reads the maximum CPU frequency from sysfs.
     *
     * @return Max frequency in kHz, or 0 if unavailable.
     */
    private fun detectCpuMaxFrequency(): Int {
        return try {
            val freqFile = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (freqFile.exists() && freqFile.canRead()) {
                freqFile.readText().trim().toIntOrNull() ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Detects GPU vendor by checking manufacturer string and common GPU system properties.
     *
     * @param context Android [Context].
     * @return GPU vendor string if detected, null otherwise.
     */
    private fun detectGpuVendor(context: Context): String? {
        val manufacturerLower = Build.MANUFACTURER.lowercase()

        // Check known GPU vendors in manufacturer string
        GPU_VENDORS.forEach { vendor ->
            if (manufacturerLower.contains(vendor)) {
                return vendor
            }
        }

        // Check common GPU system properties
        val gpuProperties = listOf(
            "ro.hardware.vulkan",
            "ro.hardware.egl",
            "ro.opengles.version",
            "ro.product.board",
            "ro.board.platform"
        )

        gpuProperties.forEach { prop ->
            val value = readSystemProperty(prop)?.lowercase() ?: return@forEach
            GPU_VENDORS.forEach { vendor ->
                if (value.contains(vendor)) {
                    return vendor
                }
            }
        }

        // Fallback: try PackageManager feature detection for OpenGL ES
        return try {
            val pm = context.packageManager
            val hasGLES = pm.hasSystemFeature(PackageManager.FEATURE_OPENGLES_EXTENSION_PACK)
            if (hasGLES) "generic_gpu" else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Detects NPU vendor by inspecting [Build.HARDWARE], [Build.BOARD], and system properties.
     *
     * @return NPU vendor string if detected, null otherwise.
     */
    private fun detectNpuVendor(): String? {
        val hardwareLower = Build.HARDWARE.lowercase()
        val boardLower = Build.BOARD.lowercase()

        // Check Build.HARDWARE and Build.BOARD for known NPU vendors
        NPU_VENDORS.forEach { vendor ->
            if (hardwareLower.contains(vendor) || boardLower.contains(vendor)) {
                return vendor
            }
        }

        // Check system properties for NPU indicators
        val npuProperties = listOf(
            "ro.hardware.npu",
            "ro.vendor.npu",
            "ro.ai.processor",
            "ro.vendor.ai.engine",
            "ro.hardware.apu",
            "ro.hardware.dsp"
        )

        npuProperties.forEach { prop ->
            val value = readSystemProperty(prop)?.lowercase() ?: return@forEach
            NPU_VENDORS.forEach { vendor ->
                if (value.contains(vendor)) {
                    return vendor
                }
            }
        }

        return null
    }

    /**
     * Checks Vulkan support via PackageManager.
     *
     * @param context Android [Context].
     * @return true if the device declares Vulkan support.
     */
    private fun checkVulkanSupport(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x401000) // Vulkan 1.1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks OpenGL ES 3.1+ support via PackageManager.
     *
     * @param context Android [Context].
     * @return true if the device declares OpenGL ES 3.1 support.
     */
    private fun checkOpenGLES31Support(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            pm.hasSystemFeature(PackageManager.FEATURE_OPENGLES_EXTENSION_PACK)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reads a system property by spawning a process.
     *
     * @param key The property name.
     * @return The property value, or null if unavailable.
     */
    private fun readSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            process.inputStream.bufferedReader().use { it.readText().trim() }.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}
