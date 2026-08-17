package com.localaisearch.data.llm

/**
 * JNI bridge to llama.cpp native library.
 *
 * This object provides the native method declarations that link to
 * the C++ implementation in cpp/llama_bridge.cpp.
 *
 * The actual llama.cpp library must be built and linked via CMake.
 * See README.md for build instructions.
 *
 * Thread-safety: All native calls are synchronized at the GGUFEngine level.
 */
object LlamaBridge {

    // -- Model lifecycle --

    /** Load a GGUF model from the given file path. Returns a model handle ( > 0 on success). */
    external fun nativeLoadModel(
        filePath: String,
        contextLength: Int,
        threads: Int,
        useGpu: Boolean,
        gpuLayers: Int
    ): Long

    /** Free the model and all associated resources. */
    external fun nativeFreeModel(modelHandle: Long)

    // -- Inference --

    /**
     * Initialize a generation context.
     * Returns a context handle ( > 0 on success).
     */
    external fun nativeInitContext(
        modelHandle: Long,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        maxTokens: Int,
        seed: Int
    ): Long

    /** Feed a prompt into the context and prepare for generation. */
    external fun nativePrompt(contextHandle: Long, prompt: String): Boolean

    /**
     * Generate the next token. Returns the token string, or null if generation is complete.
     * Called repeatedly to achieve streaming.
     */
    external fun nativeGenerateNext(contextHandle: Long): String?

    /** Stop ongoing generation. */
    external fun nativeStopGeneration(contextHandle: Long)

    /** Free a generation context. */
    external fun nativeFreeContext(contextHandle: Long)

    // -- Utilities --

    /** Get current memory usage in bytes. */
    external fun nativeGetMemoryUsage(modelHandle: Long): Long

    /** Check if GPU (Vulkan/OpenCL) is available. */
    external fun nativeIsGpuAvailable(): Boolean

    /** Get the llama.cpp version string. */
    external fun nativeGetVersion(): String

    /** Check if the native library is loaded. */
    val isLoaded: Boolean
        get() = try {
            nativeGetVersion()
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }

    private const val LIBRARY_NAME = "llama_bridge"

    private var initialized = false

    /**
     * Load the native library. Called once at app startup.
     * Returns true if the library was loaded successfully.
     */
    fun initialize(): Boolean {
        if (initialized) return true
        return try {
            System.loadLibrary(LIBRARY_NAME)
            initialized = true
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }
}
