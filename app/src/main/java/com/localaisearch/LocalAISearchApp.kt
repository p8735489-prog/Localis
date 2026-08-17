package com.localaisearch

import android.app.Application
import com.localaisearch.data.llm.LlamaBridge

/**
 * Application class - initializes native libraries on startup.
 */
class LocalAISearchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize llama.cpp native library
        LlamaBridge.initialize()
    }
}
