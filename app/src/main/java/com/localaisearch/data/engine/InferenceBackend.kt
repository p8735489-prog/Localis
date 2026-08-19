package com.localaisearch.data.engine

/**
 * Runtime selection for local inference.
 *
 * llama.cpp remains the compatibility-first GGUF runtime. MNN and Cactus are
 * optional adapters: they are selected only when their native runtime is
 * actually bundled and reports that it can load the requested model.
 */
enum class InferenceBackend {
    AUTO,
    LLAMA_CPP,
    MNN,
    CACTUS
}

data class BackendCapability(
    val backend: InferenceBackend,
    val available: Boolean,
    val supportsGguf: Boolean,
    val supportsGpu: Boolean,
    val supportsNpu: Boolean,
    val reason: String? = null
)
