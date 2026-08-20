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
import com.localaisearch.data.repository.ProxyConfig
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
 * - UI preferences (dark mode, dynamic color, language, animation level)
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
        val LLM_THINKING_DEPTH = intPreferencesKey("llm_thinking_depth")
        val LLM_BACKEND = stringPreferencesKey("llm_backend")
        val LLM_FREQUENCY_PENALTY = stringPreferencesKey("llm_frequency_penalty")
        val LLM_PRESENCE_PENALTY = stringPreferencesKey("llm_presence_penalty")

        // UI
        val DARK_MODE = stringPreferencesKey("dark_mode") // "system", "light", "dark"
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val ANIMATION_LEVEL = stringPreferencesKey("animation_level") // "off", "low", "standard", "high"
        val FONT_MODE = stringPreferencesKey("font_mode") // "system" or "google_sans"
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val MODEL_SOURCE = stringPreferencesKey("model_source") // "hugging_face" or "tsinghua_mirror"

        // Privacy
        val INTERNET_SEARCH_ENABLED = booleanPreferencesKey("internet_search_enabled")
        val PRIVACY_MODE_ENABLED = booleanPreferencesKey("privacy_mode_enabled")
        val MEMORY_SYSTEM_ENABLED = booleanPreferencesKey("memory_system_enabled")
        val CHAT_AUTO_SCROLL = booleanPreferencesKey("chat_auto_scroll")
        val CHAT_MARKDOWN = booleanPreferencesKey("chat_markdown")
        val CHAT_CODE_HIGHLIGHT = booleanPreferencesKey("chat_code_highlight")
        val CHAT_ENTER_SEND = booleanPreferencesKey("chat_enter_send")
        val CHAT_AUTO_COPY = booleanPreferencesKey("chat_auto_copy")
        val PERF_MEMORY_OPT = booleanPreferencesKey("perf_memory_opt")
        val PERF_BACKGROUND = booleanPreferencesKey("perf_background")
        val PERF_TEMP_PROTECTION = booleanPreferencesKey("perf_temp_protection")
        val DEFAULT_SYSTEM_PROMPT = stringPreferencesKey("default_system_prompt")

        // Network proxy
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_TYPE = stringPreferencesKey("proxy_type")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_USERNAME = stringPreferencesKey("proxy_username")
        val PROXY_PASSWORD = stringPreferencesKey("proxy_password")
        val TOR_ENABLED = booleanPreferencesKey("tor_enabled")
        val TOR_BRIDGES = stringPreferencesKey("tor_bridges")
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
            threads = prefs[Keys.LLM_THREADS] ?: 4,
            thinkingDepth = prefs[Keys.LLM_THINKING_DEPTH] ?: 2,
            backend = runCatching { com.localaisearch.data.model.HardwareBackend.valueOf(prefs[Keys.LLM_BACKEND] ?: "CPU") }.getOrDefault(com.localaisearch.data.model.HardwareBackend.CPU),
            frequencyPenalty = prefs[Keys.LLM_FREQUENCY_PENALTY]?.toFloatOrNull() ?: 0f,
            presencePenalty = prefs[Keys.LLM_PRESENCE_PENALTY]?.toFloatOrNull() ?: 0f
        )
    }

    val darkMode: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: "system"
    }

    val dynamicColorEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR] ?: true
    }

    val themePreset: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.THEME_PRESET] ?: "blue"
    }

    val appLanguage: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.APP_LANGUAGE] ?: LanguageManager.SYSTEM_DEFAULT
    }

    val animationLevel: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.ANIMATION_LEVEL] ?: "standard"
    }

    val fontMode: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.FONT_MODE] ?: "system"
    }

    val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    val modelSource: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.MODEL_SOURCE] ?: "tsinghua_mirror"
    }

    val internetSearchEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.INTERNET_SEARCH_ENABLED] ?: false
    }

    val defaultSystemPrompt: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_SYSTEM_PROMPT] ?: "general"
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
            prefs[Keys.LLM_THINKING_DEPTH] = config.thinkingDepth.coerceIn(1, 4)
            prefs[Keys.LLM_BACKEND] = config.backend.name
            prefs[Keys.LLM_FREQUENCY_PENALTY] = config.frequencyPenalty.toString()
            prefs[Keys.LLM_PRESENCE_PENALTY] = config.presencePenalty.toString()
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETED] = completed }
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

    suspend fun setThemePreset(preset: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME_PRESET] = preset
        }
    }

    suspend fun setAppLanguage(language: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = language
        }
    }

    suspend fun setAnimationLevel(level: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ANIMATION_LEVEL] = level
        }
    }

    suspend fun setFontMode(mode: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.FONT_MODE] = mode
        }
    }

    suspend fun setModelSource(source: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.MODEL_SOURCE] = source
        }
    }

    suspend fun setInternetSearchEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.INTERNET_SEARCH_ENABLED] = enabled
        }
    }

    suspend fun setDefaultSystemPrompt(promptId: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_SYSTEM_PROMPT] = promptId
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

    val proxyConfig: Flow<ProxyConfig> = context.settingsDataStore.data.map { prefs ->
        ProxyConfig(
            enabled = prefs[Keys.PROXY_ENABLED] ?: false,
            type = prefs[Keys.PROXY_TYPE] ?: "HTTP",
            host = prefs[Keys.PROXY_HOST] ?: "",
            port = prefs[Keys.PROXY_PORT] ?: 0,
            username = prefs[Keys.PROXY_USERNAME] ?: "",
            password = prefs[Keys.PROXY_PASSWORD] ?: ""
        )
    }

    val torEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.TOR_ENABLED] ?: false
    }

    val torBridges: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.TOR_BRIDGES] ?: ""
    }


    suspend fun setTorEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.TOR_ENABLED] = enabled }
    }

    suspend fun setTorBridges(bridges: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.TOR_BRIDGES] = bridges }
    }


    suspend fun setProxyConfig(config: ProxyConfig) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.PROXY_ENABLED] = config.enabled
            prefs[Keys.PROXY_TYPE] = config.type
            prefs[Keys.PROXY_HOST] = config.host
            prefs[Keys.PROXY_PORT] = config.port.coerceIn(0, 65535)
            prefs[Keys.PROXY_USERNAME] = config.username
            prefs[Keys.PROXY_PASSWORD] = config.password
        }
    }
    val chatAutoScroll: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.CHAT_AUTO_SCROLL] ?: true }
    val chatMarkdown: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.CHAT_MARKDOWN] ?: true }
    val chatCodeHighlight: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.CHAT_CODE_HIGHLIGHT] ?: true }
    val chatEnterSend: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.CHAT_ENTER_SEND] ?: true }
    val chatAutoCopy: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.CHAT_AUTO_COPY] ?: false }
    val perfMemoryOptimization: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.PERF_MEMORY_OPT] ?: true }
    val perfBackgroundInference: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.PERF_BACKGROUND] ?: false }
    val perfTemperatureProtection: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.PERF_TEMP_PROTECTION] ?: true }

    suspend fun setChatAutoScroll(v: Boolean) = setBoolean(Keys.CHAT_AUTO_SCROLL, v)
    suspend fun setChatMarkdown(v: Boolean) = setBoolean(Keys.CHAT_MARKDOWN, v)
    suspend fun setChatCodeHighlight(v: Boolean) = setBoolean(Keys.CHAT_CODE_HIGHLIGHT, v)
    suspend fun setChatEnterSend(v: Boolean) = setBoolean(Keys.CHAT_ENTER_SEND, v)
    suspend fun setChatAutoCopy(v: Boolean) = setBoolean(Keys.CHAT_AUTO_COPY, v)
    suspend fun setPerfMemoryOptimization(v: Boolean) = setBoolean(Keys.PERF_MEMORY_OPT, v)
    suspend fun setPerfBackgroundInference(v: Boolean) = setBoolean(Keys.PERF_BACKGROUND, v)
    suspend fun setPerfTemperatureProtection(v: Boolean) = setBoolean(Keys.PERF_TEMP_PROTECTION, v)

    private suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }

    suspend fun clearAllSettings() {
        context.settingsDataStore.edit { it.clear() }
    }

}
