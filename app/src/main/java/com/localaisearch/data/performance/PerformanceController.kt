package com.localaisearch.data.performance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enumeration of preset performance modes.
 */
enum class PerformanceMode {
    ECO,
    BALANCED,
    PERFORMANCE,
    CUSTOM
}

/**
 * Data class representing a performance profile with tunable inference parameters.
 */
data class PerformanceProfile(
    val mode: PerformanceMode,
    val threads: Int,
    val contextLength: Int,
    val batchSize: Int,
    val useGpu: Boolean,
    val useNpu: Boolean,
    val useMmap: Boolean,
    val quantization: String,
    val kvCacheQuality: Float,
    val priority: String // "latency" or "throughput"
)

/**
 * Sealed class representing a performance profile configuration.
 */
sealed class PerformanceProfileConfig {
    /**
     * A preset profile tied to a specific [PerformanceMode].
     */
    data class Preset(val mode: PerformanceMode, val profile: PerformanceProfile) : PerformanceProfileConfig()

    /**
     * A fully custom user-defined profile.
     */
    data class Custom(val profile: PerformanceProfile) : PerformanceProfileConfig()
}

/**
 * Manages performance modes and applies profiles for on-device inference.
 *
 * @param hardwareDetector The [HardwareDetector] used to query device capabilities.
 * @param memoryManager The [MemoryManager] used to query current memory status.
 */
class PerformanceController(
    private val hardwareDetector: HardwareDetector,
    private val memoryManager: MemoryManager
) {

    private val _currentProfile = MutableStateFlow(defaultBalancedProfile())

    /**
     * A [StateFlow] exposing the currently active [PerformanceProfile].
     */
    val currentProfile: StateFlow<PerformanceProfile> = _currentProfile.asStateFlow()

    private var currentMode: PerformanceMode = PerformanceMode.BALANCED

    /**
     * Applies a preset [PerformanceMode] profile.
     *
     * @param mode The desired [PerformanceMode].
     */
    fun applyMode(mode: PerformanceMode) {
        currentMode = mode
        val profile = when (mode) {
            PerformanceMode.ECO -> ecoProfile()
            PerformanceMode.BALANCED -> defaultBalancedProfile()
            PerformanceMode.PERFORMANCE -> performanceProfile()
            PerformanceMode.CUSTOM -> return // Custom must be applied via applyCustomProfile
        }
        _currentProfile.value = profile
    }

    /**
     * Applies a fully custom [PerformanceProfile].
     *
     * @param profile The custom profile to apply.
     */
    fun applyCustomProfile(profile: PerformanceProfile) {
        currentMode = PerformanceMode.CUSTOM
        _currentProfile.value = profile
    }

    /**
     * Automatically selects the best preset profile based on detected hardware.
     *
     * If the device has an NPU, a performance profile is selected.
     * If it has a GPU but limited RAM, a balanced profile is selected.
     * Otherwise, an eco profile is selected.
     *
     * @param hardwareInfo The detected hardware capabilities.
     * @return The selected [PerformanceProfile].
     */
    fun getOptimalProfileForHardware(hardwareInfo: HardwareInfo): PerformanceProfile {
        return when {
            hardwareInfo.hasNpu -> performanceProfile().copy(useNpu = false) // llama.cpp bridge is CPU-first; accelerator detection is exposed separately until a native APU/TPU backend is linked
            hardwareInfo.hasGpu && hardwareInfo.totalRamBytes > 4L * 1024 * 1024 * 1024 ->
                defaultBalancedProfile().copy(useGpu = true)
            hardwareInfo.totalRamBytes > 6L * 1024 * 1024 * 1024 ->
                defaultBalancedProfile()
            else -> ecoProfile()
        }
    }

    /**
     * Automatically tunes the current profile based on real-time hardware and memory conditions.
     *
     * @param hardwareInfo The detected hardware capabilities.
     * @param memoryStatus The current memory status.
     */
    fun autoTune(hardwareInfo: HardwareInfo, memoryStatus: MemoryStatus) {
        val baseProfile = when (currentMode) {
            PerformanceMode.ECO -> ecoProfile()
            PerformanceMode.BALANCED -> defaultBalancedProfile()
            PerformanceMode.PERFORMANCE -> performanceProfile()
            PerformanceMode.CUSTOM -> currentProfile.value
        }

        var tunedProfile = baseProfile

        // Reduce threads if CPU is weak
        if (hardwareInfo.cpuCores <= 4) {
            tunedProfile = tunedProfile.copy(threads = (tunedProfile.threads / 2).coerceAtLeast(1))
        }

        // Adjust for memory conditions
        tunedProfile = when (memoryStatus.state) {
            MemoryState.CRITICAL -> {
                tunedProfile.copy(
                    contextLength = (tunedProfile.contextLength / 4).coerceAtLeast(512),
                    batchSize = 1,
                    useMmap = true,
                    useGpu = false,
                    useNpu = false,
                    kvCacheQuality = 0.25f
                )
            }
            MemoryState.WARNING -> {
                tunedProfile.copy(
                    contextLength = (tunedProfile.contextLength / 2).coerceAtLeast(1024),
                    batchSize = (tunedProfile.batchSize / 2).coerceAtLeast(1),
                    useMmap = true,
                    kvCacheQuality = (tunedProfile.kvCacheQuality * 0.5f).coerceAtLeast(0.25f)
                )
            }
            MemoryState.OK -> tunedProfile
        }

        // Disable GPU/NPU if not supported
        if (!hardwareInfo.hasGpu) {
            tunedProfile = tunedProfile.copy(useGpu = false)
        }
        if (!hardwareInfo.hasNpu) {
            tunedProfile = tunedProfile.copy(useNpu = false)
        } else {
            // Keep the NPU/TPU capability visible without routing llama.cpp to an unsupported backend.
            tunedProfile = tunedProfile.copy(useNpu = false)
        }

        _currentProfile.value = tunedProfile
    }

    /**
     * Returns true if the current mode is [PerformanceMode.CUSTOM].
     *
     * @return true if a custom profile is active.
     */
    fun isCustomMode(): Boolean {
        return currentMode == PerformanceMode.CUSTOM
    }

    /**
     * Resets the profile to the default [PerformanceMode.BALANCED] preset.
     */
    fun resetToDefault() {
        applyMode(PerformanceMode.BALANCED)
    }

    /**
     * Builds the ECO preset profile.
     */
    private fun ecoProfile(): PerformanceProfile {
        return PerformanceProfile(
            mode = PerformanceMode.ECO,
            threads = 2,
            contextLength = 1024,
            batchSize = 1,
            useGpu = false,
            useNpu = false,
            useMmap = true,
            quantization = "q4_0",
            kvCacheQuality = 0.25f,
            priority = "latency"
        )
    }

    /**
     * Builds the default BALANCED preset profile.
     */
    private fun defaultBalancedProfile(): PerformanceProfile {
        return PerformanceProfile(
            mode = PerformanceMode.BALANCED,
            threads = 4,
            contextLength = 4096,
            batchSize = 2,
            useGpu = true,
            useNpu = false,
            useMmap = true,
            quantization = "q8_0",
            kvCacheQuality = 0.75f,
            priority = "throughput"
        )
    }

    /**
     * Builds the PERFORMANCE preset profile.
     */
    private fun performanceProfile(): PerformanceProfile {
        return PerformanceProfile(
            mode = PerformanceMode.PERFORMANCE,
            threads = 8,
            contextLength = 8192,
            batchSize = 4,
            useGpu = true,
            useNpu = true,
            useMmap = false,
            quantization = "fp16",
            kvCacheQuality = 1.0f,
            priority = "throughput"
        )
    }
}
