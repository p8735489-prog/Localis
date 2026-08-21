package com.localaisearch.data.repository

import android.app.Application
import com.localaisearch.data.llm.LLMProviderFactory
import com.localaisearch.data.llm.LLMProviderType

/**
 * Process-scoped model repository.  The llama engine is already process-scoped;
 * keeping its repository state process-scoped as well prevents Home, Model Center
 * and Settings ViewModels from disagreeing about which model is active.
 */
object AppModelRepository {
    @Volatile private var instance: ModelRepository? = null
    private val lock = Any()

    fun get(application: Application): ModelRepository {
        instance?.let { return it }
        return synchronized(lock) {
            instance ?: ModelRepository(
                application.applicationContext,
                LLMProviderFactory.createProvider(LLMProviderType.LOCAL_GGUF, application)
            ).also { instance = it }
        }
    }
}
