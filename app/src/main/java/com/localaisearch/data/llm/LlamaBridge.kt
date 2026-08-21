package com.localaisearch.data.llm

/** Thin JNI bridge to the app-scoped llama.cpp engine. */
object LlamaBridge {
    fun interface TokenCallback { fun onToken(token: String) }

    external fun nativeInitialize(): Boolean
    external fun nativeShutdown(): Boolean
    external fun nativeGetLastError(): String
    external fun nativeSupportsGpu(): Boolean
    external fun nativeLoadModel(filePath: String, contextLength: Int, threads: Int, useGpu: Boolean, gpuLayers: Int): Long
    external fun nativeUnloadModel(handle: Long): Boolean

    /** Load the llama.cpp libmtmd/mmproj vision runtime for an already-loaded GGUF model. */
    external fun nativeLoadVisionProjector(handle: Long, mmprojPath: String, threads: Int, useGpu: Boolean): Boolean

    /** True when libmtmd has been attached to the loaded model. */
    external fun nativeHasVision(handle: Long): Boolean

    /** Stream a response after encoding an image through mtmd + mmproj. */
    external fun nativeGenerateMultimodalStream(
        handle: Long, promptWithMediaMarker: String, imageBytes: ByteArray,
        temperature: Float, maxTokens: Int, topK: Int, topP: Float,
        repeatPenalty: Float, frequencyPenalty: Float, presencePenalty: Float,
        callback: TokenCallback
    ): Boolean

    /** True token streaming. The native side invokes [callback] once per decoded piece. */
    external fun nativeGenerateStream(
        handle: Long, prompt: String, temperature: Float, maxTokens: Int, topK: Int, topP: Float, repeatPenalty: Float, frequencyPenalty: Float, presencePenalty: Float, callback: TokenCallback
    ): Boolean

    /** Format chat messages using the model's GGUF chat-template metadata. */
    external fun nativeFormatChat(handle: Long, roles: Array<String>, contents: Array<String>, enableThinking: Boolean = true): String

    external fun nativeStopGeneration(): Boolean
    external fun nativeTokenize(handle: Long, text: String, tokens: IntArray): Long
    external fun nativeDetokenize(handle: Long, tokens: IntArray): String
    external fun nativeGetMemoryUsage(handle: Long): Long
    external fun nativeGetModelContextSize(handle: Long): Long
    external fun nativeGetModelInfo(handle: Long): String
    external fun nativeGetVersion(): String

    val isLoaded: Boolean
        get() = initialized

    private const val LIBRARY_NAME = "llama_bridge"
    @Volatile private var initialized = false

    @Synchronized
    fun initialize(): Boolean {
        if (initialized) return true
        return try {
            System.loadLibrary(LIBRARY_NAME)
            val ok = nativeInitialize()
            initialized = ok
            ok
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("LlamaBridge", "Unable to load lib$LIBRARY_NAME.so. Check APK jniLibs and ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}", e)
            initialized = false
            false
        } catch (e: Throwable) {
            android.util.Log.e("LlamaBridge", "Native llama.cpp initialization failed", e)
            initialized = false
            false
        }
    }
}
