package com.localaisearch.data.engine

import com.localaisearch.data.model.GGUFModel

/**
 * Central backend policy. It deliberately refuses to claim acceleration that
 * is not present in the installed native libraries.
 */
object InferenceBackendRouter {
    fun choose(model: GGUFModel, requested: InferenceBackend = InferenceBackend.AUTO): InferenceBackend {
        if (requested != InferenceBackend.AUTO) return requested
        // GGUF is the app's primary interchange format and therefore always
        // starts on llama.cpp. Optional runtimes can take over only after a
        // future native adapter reports model compatibility.
        return if (looksLikeGguf(model)) InferenceBackend.LLAMA_CPP else InferenceBackend.CACTUS
    }

    fun capabilities(mnnNativeAvailable: Boolean = false, cactusNativeAvailable: Boolean = false): List<BackendCapability> = listOf(
        BackendCapability(
            backend = InferenceBackend.LLAMA_CPP,
            available = true,
            supportsGguf = true,
            supportsGpu = false,
            supportsNpu = false,
            reason = "Compatibility-first GGUF runtime"
        ),
        BackendCapability(
            backend = InferenceBackend.MNN,
            available = mnnNativeAvailable,
            supportsGguf = false,
            supportsGpu = mnnNativeAvailable,
            supportsNpu = mnnNativeAvailable,
            reason = if (mnnNativeAvailable) null else "Optional native adapter is not bundled"
        ),
        BackendCapability(
            backend = InferenceBackend.CACTUS,
            available = cactusNativeAvailable,
            supportsGguf = false,
            supportsGpu = cactusNativeAvailable,
            supportsNpu = cactusNativeAvailable,
            reason = if (cactusNativeAvailable) null else "Optional native adapter is not bundled"
        )
    )

    private fun looksLikeGguf(model: GGUFModel): Boolean =
        model.path.lowercase().endsWith(".gguf") || model.name.lowercase().contains("gguf")
}
