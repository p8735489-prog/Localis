package com.localaisearch.data.llm

import android.content.Context

/** App-scoped provider factory. Local GGUF inference must be shared across ViewModels. */
object LLMProviderFactory {
    @Volatile private var localEngine: GGUFEngine? = null
    private val lock = Any()

    fun createEngine(context: Context): LLMEngine = getLocalEngine(context)

    fun createProvider(type: LLMProviderType, context: Context): LLMEngine = when (type) {
        LLMProviderType.LOCAL_GGUF -> getLocalEngine(context)
        LLMProviderType.OPENAI_COMPATIBLE -> OpenAILLMProvider(getApiUrlFromSettings(context))
        // Stub is explicit test-only behavior; it is never selected implicitly.
        LLMProviderType.STUB -> StubLLMProvider()
    }

    fun getLocalEngine(context: Context): GGUFEngine {
        localEngine?.let { return it }
        return synchronized(lock) {
            localEngine ?: GGUFEngine(context.applicationContext).also { localEngine = it }
        }
    }

    /** Release the app-scoped local engine when the process is intentionally shutting down. */
    fun releaseAll() {
        synchronized(lock) {
            localEngine?.release()
            localEngine = null
        }
    }

    private fun getApiUrlFromSettings(context: Context): String = ""
}
