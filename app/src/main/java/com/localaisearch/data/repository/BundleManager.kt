package com.localaisearch.data.repository

import android.content.Context
import com.localaisearch.data.model.GGUFMetadata
import com.localaisearch.data.model.GGUFMetadataReader
import com.localaisearch.data.model.ModelBundle
import com.localaisearch.data.model.ModelCapability
import com.localaisearch.data.model.ModelComponent
import com.localaisearch.data.model.ModelComponentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Manages discovery, grouping, and lifecycle of [ModelBundle] instances from
 * on-device GGUF files.
 *
 * Scans a directory for `.gguf` files, reads metadata via [GGUFMetadataReader],
 * groups related files into bundles, and exposes the result as an observable
 * [StateFlow].
 *
 * @param context Android application context (used for paths and resources if needed).
 */
class BundleManager(private val context: Context) {

    /** Internal mutable backing for the public bundles flow. */
    private val _bundles = MutableStateFlow<List<ModelBundle>>(emptyList())

    /** Observable list of all discovered model bundles. */
    val bundles: StateFlow<List<ModelBundle>> = _bundles.asStateFlow()

    /** Directory that was last scanned; used by [refreshBundles]. */
    private var lastScannedDir: File? = null

    /**
     * Scans [modelsDir] for `.gguf` files, reads their metadata, groups them
     * into [ModelBundle] instances, and updates [bundles].
     *
     * Grouping logic:
     * 1. List all `.gguf` files in [modelsDir].
     * 2. For each file, read metadata via [GGUFMetadataReader.readMetadata].
     * 3. Detect projector files with [GGUFMetadataReader.isProjectorFile].
     * 4. Group files by a shared base name prefix (e.g., `qwen2-vision` for
     *    `qwen2-vision-Q4_K_M.gguf` and `mmproj-qwen2-vision-f16.gguf`).
     * 5. Build a [ModelBundle] per group:
     *    - LLM = main `.gguf` that is **not** a projector.
     *    - VISION_PROJECTOR = `.gguf` whose filename contains projector keywords.
     *    - Other components detected from metadata or filename patterns.
     * 6. Derive capabilities from metadata flags.
     * 7. Mark bundle as complete/incomplete based on required components.
     *
     * @param modelsDir Directory to scan (must exist and be readable).
     * @return List of created [ModelBundle] instances.
     */
    fun scanAndCreateBundles(modelsDir: File): List<ModelBundle> {
        if (!modelsDir.exists() || !modelsDir.isDirectory) {
            _bundles.value = emptyList()
            return emptyList()
        }

        lastScannedDir = modelsDir

        // 1. Collect all .gguf files
        val ggufFiles = modelsDir.listFiles { file ->
            file.isFile && file.extension.lowercase() == "gguf"
        } ?: emptyArray()

        if (ggufFiles.isEmpty()) {
            _bundles.value = emptyList()
            return emptyList()
        }

        // 2. Read metadata for each file
        val fileMetadata = ggufFiles.map { file ->
            file to GGUFMetadataReader.readMetadata(file.absolutePath)
        }

        // 3. Separate projectors from LLMs
        val projectors = fileMetadata.filter { it.second.isProjector }
        val llmFiles = fileMetadata.filter { !it.second.isProjector }

        // 4. Group by base name prefix
        val groups = mutableMapOf<String, MutableList<Pair<File, GGUFMetadata>>>()

        // Add LLM files to groups
        for ((file, metadata) in llmFiles) {
            val baseName = extractBaseName(file.name)
            groups.getOrPut(baseName) { mutableListOf() }.add(file to metadata)
        }

        // Try to attach projectors to existing groups by matching base name
        val unmatchedProjectors = mutableListOf<Pair<File, GGUFMetadata>>()
        for ((file, metadata) in projectors) {
            val baseName = extractBaseName(file.name)
            val group = groups.entries.find { (key, _) ->
                baseName.contains(key, ignoreCase = true) ||
                    key.contains(baseName, ignoreCase = true)
            }
            if (group != null) {
                group.value.add(file to metadata)
            } else {
                unmatchedProjectors.add(file to metadata)
            }
        }

        // Create bundles for each group
        val bundles = mutableListOf<ModelBundle>()

        for ((baseName, files) in groups) {
            val bundle = createBundleFromFiles(
                files.map { it.first },
                baseName
            )
            bundles.add(bundle)
        }

        // Unmatched projectors become their own bundles (incomplete)
        for ((file, metadata) in unmatchedProjectors) {
            val baseName = extractBaseName(file.name)
            val component = ModelComponent(
                type = ModelComponentType.VISION_PROJECTOR,
                filePath = file.absolutePath,
                fileSizeBytes = file.length(),
                isRequired = false,
                metadata = mapOf("source" to "projector")
            )
            val bundle = ModelBundle(
                id = getBundleId(baseName),
                displayName = getBundleDisplayName(metadata, baseName),
                description = "Unmatched vision projector (missing LLM)",
                components = listOf(component),
                capabilities = listOf(ModelCapability.VISION),
                architecture = metadata.architecture,
                parameterCount = metadata.displayParameterCount,
                quantization = metadata.quantizationVersion,
                isComplete = false,
                missingComponents = listOf("LLM (main model file)"),
                totalSizeBytes = file.length()
            )
            bundles.add(bundle)
        }

        _bundles.value = bundles
        return bundles
    }

    /**
     * Creates a single [ModelBundle] from a list of related [File]s.
     *
     * Determines component types, capabilities, completeness, and computes
     * aggregate fields such as total size and display name.
     *
     * @param files GGUF files that belong together (same model family).
     * @param baseName Shared base name for the bundle.
     * @return A fully populated [ModelBundle].
     */
    fun createBundleFromFiles(files: List<File>, baseName: String): ModelBundle {
        val components = mutableListOf<ModelComponent>()
        var totalSize = 0L
        var mainMetadata: GGUFMetadata? = null
        var architecture = "unknown"
        var paramCount = "unknown"
        var quantization = "unknown"
        val capabilities = mutableSetOf<ModelCapability>()
        val missing = mutableListOf<String>()

        for (file in files) {
            val metadata = GGUFMetadataReader.readMetadata(file.absolutePath)
            totalSize += file.length()

            // Track the "main" metadata from the LLM file
            if (!metadata.isProjector && mainMetadata == null) {
                mainMetadata = metadata
                architecture = metadata.architecture
                paramCount = metadata.displayParameterCount
                quantization = metadata.quantizationVersion
            }

            // Determine component type
            val type = when {
                metadata.isProjector -> ModelComponentType.VISION_PROJECTOR
                file.name.lowercase().contains("embed") -> ModelComponentType.EMBEDDING
                file.name.lowercase().contains("rerank") -> ModelComponentType.RERANKER
                else -> ModelComponentType.LLM
            }

            components.add(
                ModelComponent(
                    type = type,
                    filePath = file.absolutePath,
                    fileSizeBytes = file.length(),
                    isRequired = type == ModelComponentType.LLM,
                    metadata = mapOf(
                        "architecture" to metadata.architecture,
                        "modelFamily" to metadata.modelFamily
                    )
                )
            )

            // Aggregate capabilities from metadata
            capabilities.addAll(metadata.detectedCapabilities)
        }

        // Always ensure TEXT capability is present if we have an LLM
        if (components.any { it.type == ModelComponentType.LLM }) {
            capabilities.add(ModelCapability.TEXT)
        }

        // Check completeness
        val hasLlm = components.any { it.type == ModelComponentType.LLM }
        val hasProjector = components.any { it.type == ModelComponentType.VISION_PROJECTOR }
        val claimsVision = capabilities.contains(ModelCapability.VISION)

        if (!hasLlm) {
            missing.add("LLM (main model file)")
        }
        if (claimsVision && !hasProjector) {
            missing.add("Vision Projector")
        }

        val isComplete = hasLlm && (!claimsVision || hasProjector)

        return ModelBundle(
            id = getBundleId(baseName),
            displayName = mainMetadata?.let { getBundleDisplayName(it, baseName) }
                ?: baseName.replace("-", " ").replace("_", " ")
                    .replaceFirstChar { it.uppercase() },
            description = "",
            components = components,
            capabilities = capabilities.toList(),
            architecture = architecture,
            parameterCount = paramCount,
            quantization = quantization,
            isComplete = isComplete,
            missingComponents = missing,
            totalSizeBytes = totalSize
        )
    }

    /**
     * Generates a user-friendly display name from [metadata] and [baseName].
     *
     * Prefers the formal model name from metadata; falls back to a prettified
     * version of the file base name.
     *
     * @param metadata Parsed GGUF metadata.
     * @param baseName File base name used as fallback.
     * @return Human-readable bundle name.
     */
    fun getBundleDisplayName(metadata: GGUFMetadata, baseName: String): String {
        val name = metadata.modelName.takeIf { it.isNotBlank() && it != "unknown" }
            ?: baseName
        val clean = name.replace("-", " ").replace("_", " ").replace(".", " ")
        return clean.replaceFirstChar { it.uppercase() }
    }

    /**
     * Generates a stable unique identifier for a bundle from its [baseName].
     *
     * Uses SHA-256 hashing so the same base name always yields the same ID
     * across rescans.
     *
     * @param baseName Shared base name of the bundle.
     * @return Unique bundle ID string.
     */
    fun getBundleId(baseName: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(baseName.toByteArray(Charsets.UTF_8))
            val hex = hash.joinToString("") { "%02x".format(it) }
            "bundle-$hex"
        } catch (e: Exception) {
            // Fallback to UUID if SHA-256 is unavailable
            "bundle-${UUID.randomUUID()}"
        }
    }

    /**
     * Adds a new [File] to an existing [ModelBundle] as an additional component.
     *
     * Recomputes capabilities, size, and completeness after insertion.
     *
     * @param bundle Existing bundle to extend.
     * @param file New GGUF file to add.
     * @return A new [ModelBundle] with the additional component.
     */
    fun addFileToBundle(bundle: ModelBundle, file: File): ModelBundle {
        if (!file.exists() || file.extension.lowercase() != "gguf") {
            return bundle
        }

        val metadata = GGUFMetadataReader.readMetadata(file.absolutePath)
        val type = when {
            metadata.isProjector -> ModelComponentType.VISION_PROJECTOR
            file.name.lowercase().contains("embed") -> ModelComponentType.EMBEDDING
            file.name.lowercase().contains("rerank") -> ModelComponentType.RERANKER
            else -> ModelComponentType.LLM
        }

        val newComponent = ModelComponent(
            type = type,
            filePath = file.absolutePath,
            fileSizeBytes = file.length(),
            isRequired = type == ModelComponentType.LLM,
            metadata = mapOf("architecture" to metadata.architecture)
        )

        val updatedComponents = bundle.components + newComponent
        val updatedCapabilities = (bundle.capabilities + metadata.detectedCapabilities).distinct()
        val updatedSize = bundle.totalSizeBytes + file.length()

        // Re-evaluate completeness
        val hasLlm = updatedComponents.any { it.type == ModelComponentType.LLM }
        val hasProjector = updatedComponents.any { it.type == ModelComponentType.VISION_PROJECTOR }
        val claimsVision = updatedCapabilities.contains(ModelCapability.VISION)
        val isComplete = hasLlm && (!claimsVision || hasProjector)

        val missing = mutableListOf<String>()
        if (!hasLlm) missing.add("LLM (main model file)")
        if (claimsVision && !hasProjector) missing.add("Vision Projector")

        return bundle.copy(
            components = updatedComponents,
            capabilities = updatedCapabilities,
            isComplete = isComplete,
            missingComponents = missing,
            totalSizeBytes = updatedSize
        )
    }

    /**
     * Removes a component of [componentType] from [bundle].
     *
     * Removes only the **first** matching component. Recomputes capabilities,
     * size, and completeness afterwards.
     *
     * @param bundle Bundle to modify.
     * @param componentType Type of component to remove.
     * @return A new [ModelBundle] without the specified component.
     */
    fun removeComponent(bundle: ModelBundle, componentType: ModelComponentType): ModelBundle {
        val toRemove = bundle.components.indexOfFirst { it.type == componentType }
        if (toRemove == -1) {
            return bundle
        }

        val removed = bundle.components[toRemove]
        val updatedComponents = bundle.components.toMutableList().apply { removeAt(toRemove) }

        // Recompute capabilities from remaining components' metadata
        val updatedCapabilities = updatedComponents.flatMap { component ->
            val meta = GGUFMetadataReader.readMetadata(component.filePath)
            meta.detectedCapabilities
        }.distinct()

        val updatedSize = bundle.totalSizeBytes - removed.fileSizeBytes

        // Re-evaluate completeness
        val hasLlm = updatedComponents.any { it.type == ModelComponentType.LLM }
        val hasProjector = updatedComponents.any { it.type == ModelComponentType.VISION_PROJECTOR }
        val claimsVision = updatedCapabilities.contains(ModelCapability.VISION)
        val isComplete = hasLlm && (!claimsVision || hasProjector)

        val missing = mutableListOf<String>()
        if (!hasLlm) missing.add("LLM (main model file)")
        if (claimsVision && !hasProjector) missing.add("Vision Projector")

        return bundle.copy(
            components = updatedComponents,
            capabilities = updatedCapabilities,
            isComplete = isComplete,
            missingComponents = missing,
            totalSizeBytes = maxOf(0L, updatedSize)
        )
    }

    /**
     * Re-scans the last directory that was passed to [scanAndCreateBundles]
     * and updates [bundles].
     *
     * If no directory has been scanned yet, this is a no-op.
     */
    fun refreshBundles() {
        val dir = lastScannedDir ?: return
        scanAndCreateBundles(dir)
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Extracts a model base name from a GGUF filename.
     *
     * Strips the `.gguf` extension and removes common quantization suffixes
     * (e.g., `-Q4_K_M`, `-f16`) and projector prefixes (e.g., `mmproj-`) so
     * that `model-Q4_K_M.gguf` and `mmproj-model-f16.gguf` both map to `model`.
     *
     * @param fileName Name of the GGUF file.
     * @return Normalized base name for grouping.
     */
    private fun extractBaseName(fileName: String): String {
        var base = fileName.lowercase()
        if (base.endsWith(".gguf")) {
            base = base.substring(0, base.length - 5)
        }

        // Remove common projector prefixes
        val projectorPrefixes = listOf("mmproj-", "vision-", "visual-", "projector-")
        for (prefix in projectorPrefixes) {
            if (base.startsWith(prefix)) {
                base = base.substring(prefix.length)
                break
            }
        }

        // Remove common quantization suffixes
        val quantSuffixes = listOf(
            "-q4_0", "-q4_1", "-q4_k", "-q4_k_m", "-q4_k_s",
            "-q5_0", "-q5_1", "-q5_k", "-q5_k_m", "-q5_k_s",
            "-q6_k", "-q8_0", "-f16", "-f32",
            "-q2_k", "-q3_k", "-q3_k_m", "-q3_k_s",
            "-iq3_xxs", "-iq3_s", "-iq4_nl", "-iq4_xs",
            "-bf16", "-fp16", "-fp32"
        )
        for (suffix in quantSuffixes) {
            if (base.endsWith(suffix)) {
                base = base.substring(0, base.length - suffix.length)
                break
            }
        }

        return base.trim('-', '_')
    }
}
