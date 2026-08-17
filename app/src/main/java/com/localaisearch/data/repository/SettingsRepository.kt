package com.localaisearch.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.localaisearch.data.model.InferenceConfig
import com.localaisearch.data.search.SearchConfig
import com.localaisearch.data.search.SearchProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Repository for managing app settings using DataStore.
 *
 * Stores:
 * - Search provider configuration (API URL, key, language, etc.)
 * - LLM inference parameters (temperature, top-p, top-k, etc.)
 * - UI preferences (dark mode, dynamic color)
 * - Privacy settings (internet search enabled)
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        // Search config
        val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        val SEARCH_API_URL = stringPreferencesKey("search_api_url")
        val SEARCH_API_KEY = stringPreferencesKey("search_api_key")
        val SEARCH_LANGUAGE = stringPreferencesKey("search_language")
        val SEARCH_REGION = stringPreferencesKey("search_region")
        val SEARCH_MAX_RESULTS = intPreferencesKey("search_max_results")
        val SEARCH_MAX_ROUNDS = intPreferencesKey("search_max_rounds")
        val SEARCH_SAFE_SEARCH = booleanPreferencesKey("search_safe_search")

        // LLM config
        val LLM_TEMPERATURE = stringPreferencesKey("llm_temperature")
        val LLM_TOP_P = stringPreferencesKey("llm_top_p")
        val LLM_TOP_K = intPreferencesKey("llm_top_k")
        val LLM_CONTEXT_LENGTH = intPreferencesKey("llm_context_length")
        val LLM_MAX_TOKENS = intPreferencesKey("llm_max_tokens")
        val LLM_USE_GPU = booleanPreferencesKey("llm_use_gpu")
        val LLM_GPU_LAYERS = intPreferencesKey("llm_gpu_layers")
        val LLM_THREADS = intPreferencesKey("llm_threads")

        // UI
        val DARK_MODE = stringPreferencesKey("dark_mode") // "system", "light", "dark"
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

        // Privacy
        val INTERNET_SEARCH_ENABLED = booleanPreferencesKey("internet_search_enabled")
        val PRIVACY_MODE_ENABLED = booleanPreferencesKey("privacy_mode_enabled")
        val MEMORY_SYSTEM_ENABLED = booleanPreferencesKey("memory_system_enabled")
    }

    val searchConfig: Flow<SearchConfig> = context.settingsDataStore.data.map { prefs ->
        SearchConfig(
            providerType = SearchProviderType.valueOf(
                prefs[Keys.SEARCH_PROVIDER] ?: SearchProviderType.SEARXNG.name
            ),
            apiUrl = prefs[Keys.SEARCH_API_URL] ?: "",
            apiKey = prefs[Keys.SEARCH_API_KEY] ?: "",
            searchLanguage = prefs[Keys.SEARCH_LANGUAGE] ?: "auto",
            searchRegion = prefs[Keys.SEARCH_REGION] ?: "global",
            maxResults = prefs[Keys.SEARCH_MAX_RESULTS] ?: 10,
            maxSearchRounds = prefs[Keys.SEARCH_MAX_ROUNDS] ?: 3,
            enableSafeSearch = prefs[Keys.SEARCH_SAFE_SEARCH] ?: true
        )
    }

    val inferenceConfig: Flow<InferenceConfig> = context.settingsDataStore.data.map { prefs ->
        InferenceConfig(
            temperature = prefs[Keys.LLM_TEMPERATURE]?.toFloatOrNull() ?: 0.7f,
            topP = prefs[Keys.LLM_TOP_P]?.toFloatOrNull() ?: 0.9f,
            topK = prefs[Keys.LLM_TOP_K] ?: 40,
            contextLength = prefs[Keys.LLM_CONTEXT_LENGTH] ?: 4096,
            maxTokens = prefs[Keys.LLM_MAX_TOKENS] ?: 2048,
            useGpu = prefs[Keys.LLM_USE_GPU] ?: true,
            gpuLayers = prefs[Keys.LLM_GPU_LAYERS] ?: 0,
            threads = prefs[Keys.LLM_THREADS] ?: 4
        )
    }

    val darkMode: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: "system"
    }

    val dynamicColorEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR] ?: true
    }

    val internetSearchEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.INTERNET_SEARCH_ENABLED] ?: false
    }

    suspend fun updateSearchConfig(config: SearchConfig) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SEARCH_PROVIDER] = config.providerType.name
            prefs[Keys.SEARCH_API_URL] = config.apiUrl
            prefs[Keys.SEARCH_API_KEY] = config.apiKey
            prefs[Keys.SEARCH_LANGUAGE] = config.searchLanguage
            prefs[Keys.SEARCH_REGION] = config.searchRegion
            prefs[Keys.SEARCH_MAX_RESULTS] = config.maxResults
            prefs[Keys.SEARCH_MAX_ROUNDS] = config.maxSearchRounds
            prefs[Keys.SEARCH_SAFE_SEARCH] = config.enableSafeSearch
        }
    }

    suspend fun updateInferenceConfig(config: InferenceConfig) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.LLM_TEMPERATURE] = config.temperature.toString()
            prefs[Keys.LLM_TOP_P] = config.topP.toString()
            prefs[Keys.LLM_TOP_K] = config.topK
            prefs[Keys.LLM_CONTEXT_LENGTH] = config.contextLength
            prefs[Keys.LLM_MAX_TOKENS] = config.maxTokens
            prefs[Keys.LLM_USE_GPU] = config.useGpu
            prefs[Keys.LLM_GPU_LAYERS] = config.gpuLayers
            prefs[Keys.LLM_THREADS] = config.threads
        }
    }

    suspend fun setDarkMode(mode: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = mode
        }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setInternetSearchEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.INTERNET_SEARCH_ENABLED] = enabled
        }
    }

    // Privacy settings
    val privacyModeEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.PRIVACY_MODE_ENABLED] ?: false
    }

    val memorySystemEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.MEMORY_SYSTEM_ENABLED] ?: true
    }

    suspend fun setPrivacyModeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.PRIVACY_MODE_ENABLED] = enabled
        }
    }

    suspend fun setMemorySystemEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.MEMORY_SYSTEM_ENABLED] = enabled
        }
    }
}
