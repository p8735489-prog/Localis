package com.localaisearch.data.llm

import android.content.Context

/**
 * Factory for creating LLM engine instances.
 * Manages provider selection and lifecycle.
 */
object LLMProviderFactory {
    
    /**
     * Create the appropriate LLM engine based on availability:
     * 1. Try GGUFEngine (llama.cpp local inference)
     * 2. Try OpenAILLMProvider (if configured)
     * 3. Fall back to StubLLMProvider
     */
    fun createEngine(context: Context): LLMEngine {
        // Try local GGUF first
        val ggufEngine = GGUFEngine(context)
        if (ggufEngine.isAvailable) {
            return ggufEngine
        }
        
        // Try API provider (if configured in settings)
        val apiUrl = getApiUrlFromSettings(context)
        if (apiUrl.isNotBlank()) {
            val apiProvider = OpenAILLMProvider(apiUrl)
            if (apiProvider.isAvailable) {
                return apiProvider
            }
        }
        
        // Fallback to stub
        return StubLLMProvider()
    }
    
    /**
     * Create a specific provider by type
     */
    fun createProvider(type: LLMProviderType, context: Context): LLMEngine {
        return when (type) {
            LLMProviderType.LOCAL_GGUF -> GGUFEngine(context)
            LLMProviderType.OPENAI_COMPATIBLE -> {
                val apiUrl = getApiUrlFromSettings(context)
                OpenAILLMProvider(apiUrl)
            }
            LLMProviderType.STUB -> StubLLMProvider()
        }
    }
    
    private fun getApiUrlFromSettings(context: Context): String {
        // TODO: Read from SettingsRepository when API provider config is added
        return ""
    }
}
