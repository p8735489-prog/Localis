package com.localaisearch.data.performance

import com.localaisearch.data.model.ModelBundle
import com.localaisearch.data.model.ModelComponentType
import com.localaisearch.data.model.ModelCapability

/**
 * Overall compatibility classification for a [ModelBundle].
 */
enum class CompatibilityStatus {
    /** All required components present, no warnings. */
    COMPATIBLE,
    /** Usable but missing optional components or has minor warnings. */
    INCOMPLETE,
    /** Cannot determine compatibility (e.g., unknown architecture). */
    UNKNOWN,
    /** Critical issues prevent usage. */
    INCOMPATIBLE
}

/**
 * Presence status of a single component type within a bundle.
 */
enum class ComponentStatus {
    /** The component is present in the bundle. */
    PRESENT,
    /** The component is required but missing. */
    MISSING,
    /** The component is optional and may be absent. */
    OPTIONAL
}

/**
 * Result of running a compatibility check on a [ModelBundle].
 *
 * @property isCompatible `true` if the bundle can be used (may still have warnings).
 * @property issues Critical problems that prevent or severely limit usage.
 * @property warnings Non-critical concerns that do not block usage.
 * @property status Overall compatibility classification.
 */
data class CompatibilityResult(
    val isCompatible: Boolean,
    val issues: List<String>,
    val warnings: List<String>,
    val status: CompatibilityStatus
)

/**
 * Singleton compatibility manager for [ModelBundle] instances.
 *
 * Provides safe, crash-free validation ensuring that required components are
 * present and that component combinations make sense (e.g., a vision model
 * should have both an LLM and a projector file).
 */
object CompatibilityManager {

    /**
     * Checks whether all components in [bundle] are compatible with each other.
     *
     * Rules evaluated:
     * 1. LLM component is always required.
     * 2. Unknown architecture yields UNKNOWN status (does not crash).
     * 3. If the bundle claims vision capability but no projector is present -> warning.
     * 4. Component combination must make sense (e.g., vision needs LLM + projector).
     * 5. Zero or unknown total size triggers a warning.
     *
     * @param bundle The model bundle to validate.
     * @return A [CompatibilityResult] describing the bundle's health.
     */
    fun checkCompatibility(bundle: ModelBundle): CompatibilityResult {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        return try {
            // Rule 1: LLM is mandatory
            if (bundle.llmComponent == null) {
                issues.add("Missing required LLM component. The bundle has no main model file.")
            }

            // Rule 2: Architecture sanity
            if (bundle.architecture.isBlank() || bundle.architecture == "unknown") {
                warnings.add("Unknown model architecture. The model may not load correctly.")
            }

            // Rule 3: Vision capability consistency
            val hasProjector = bundle.projectorComponent != null
            val claimsVision = bundle.capabilities.contains(ModelCapability.VISION)

            if (claimsVision && !hasProjector) {
                warnings.add(
                    "Bundle claims vision capability but no vision projector file was found. " +
                        "Vision features will be unavailable."
                )
            }
            if (hasProjector && !claimsVision) {
                warnings.add(
                    "Vision projector present but bundle does not declare vision capability. " +
                        "Capabilities may need updating."
                )
            }

            // Rule 4: Validate overall component combination
            val componentTypes = bundle.components.map { it.type }
            if (!validateComponentCombination(componentTypes)) {
                warnings.add("Unusual component combination detected.")
            }

            // Rule 5: Size sanity
            if (bundle.totalSizeBytes <= 0) {
                warnings.add("Bundle has zero or unknown total size.")
            }

            // Determine final status
            val status: CompatibilityStatus = when {
                issues.isNotEmpty() -> CompatibilityStatus.INCOMPATIBLE
                bundle.architecture == "unknown" -> CompatibilityStatus.UNKNOWN
                warnings.isNotEmpty() -> CompatibilityStatus.INCOMPLETE
                else -> CompatibilityStatus.COMPATIBLE
            }

            val isCompatible = status == CompatibilityStatus.COMPATIBLE ||
                status == CompatibilityStatus.INCOMPLETE

            CompatibilityResult(
                isCompatible = isCompatible,
                issues = issues,
                warnings = warnings,
                status = status
            )
        } catch (e: Exception) {
            // Never crash: return safe default
            CompatibilityResult(
                isCompatible = false,
                issues = listOf("Internal error during compatibility check: ${e.message}"),
                warnings = emptyList(),
                status = CompatibilityStatus.UNKNOWN
            )
        }
    }

    /**
     * Returns `true` only if [bundle] contains both an LLM and a VISION_PROJECTOR
     * component, indicating that vision inference can be loaded.
     *
     * @param bundle The model bundle to inspect.
     */
    fun canLoadVision(bundle: ModelBundle): Boolean {
        return try {
            bundle.llmComponent != null && bundle.projectorComponent != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Lists the human-readable descriptions of missing components that would be
     * required for full functionality.
     *
     * @param bundle The model bundle to analyze.
     * @return List of missing component descriptions.
     */
    fun getMissingComponents(bundle: ModelBundle): List<String> {
        return try {
            val missing = mutableListOf<String>()

            if (bundle.llmComponent == null) {
                missing.add("LLM (main model file) -- required for all inference")
            }

            if (bundle.capabilities.contains(ModelCapability.VISION) &&
                bundle.projectorComponent == null
            ) {
                missing.add("Vision Projector -- required for vision capability")
            }

            // Embedding is optional, so we don't list it as missing
            missing
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Validates whether a list of [ModelComponentType] values forms a sensible combination.
     *
     * Known rules:
     * - A vision model should have both LLM and VISION_PROJECTOR.
     * - LLM alone is always valid.
     * - Embedding-only bundles are unusual but allowed (returns true with warning).
     * - Projector without LLM is suspicious.
     *
     * @param components List of component types present in a bundle.
     * @return `true` if the combination is acceptable, `false` if it looks wrong.
     */
    fun validateComponentCombination(components: List<ModelComponentType>): Boolean {
        return try {
            val hasLlm = components.contains(ModelComponentType.LLM)
            val hasProjector = components.contains(ModelComponentType.VISION_PROJECTOR)

            // Projector without LLM is suspicious
            if (hasProjector && !hasLlm) {
                return false
            }

            true
        } catch (e: Exception) {
            // Safe default: assume valid
            true
        }
    }

    /**
     * Returns a map describing the presence status of each relevant component type.
     *
     * @param bundle The model bundle to inspect.
     * @return Map from [ModelComponentType] to [ComponentStatus].
     */
    fun getComponentStatus(bundle: ModelBundle): Map<ModelComponentType, ComponentStatus> {
        return try {
            val presentTypes = bundle.components.map { it.type }.toSet()

            mapOf(
                ModelComponentType.LLM to if (presentTypes.contains(ModelComponentType.LLM))
                    ComponentStatus.PRESENT else ComponentStatus.MISSING,
                ModelComponentType.VISION_PROJECTOR to if (presentTypes.contains(ModelComponentType.VISION_PROJECTOR))
                    ComponentStatus.PRESENT else ComponentStatus.OPTIONAL,
                ModelComponentType.EMBEDDING to if (presentTypes.contains(ModelComponentType.EMBEDDING))
                    ComponentStatus.PRESENT else ComponentStatus.OPTIONAL,
                ModelComponentType.RERANKER to if (presentTypes.contains(ModelComponentType.RERANKER))
                    ComponentStatus.PRESENT else ComponentStatus.OPTIONAL
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
