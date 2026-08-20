package com.localaisearch

import android.app.Application
import com.localaisearch.data.llm.LlamaBridge
import com.localaisearch.data.repository.NetworkClientFactory
import com.localaisearch.data.repository.SettingsRepository
import com.localaisearch.data.repository.TorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application class - initializes native libraries on startup.
 */
class LocalAISearchApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Initialize llama.cpp native library
        LlamaBridge.initialize()
        TorManager.initialize(this)
        appScope.launch {
            val settings = SettingsRepository(this@LocalAISearchApp)
            // A persisted Tor toggle means startup must not briefly install the
            // user's clearnet proxy before SettingsViewModel can re-establish Tor.
            if (!settings.torEnabled.first()) {
                NetworkClientFactory.updateProxy(settings.proxyConfig.first())
            }
        }
    }
}
