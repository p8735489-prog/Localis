package com.localaisearch.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localaisearch.data.model.InferenceConfig
import com.localaisearch.data.model.InferenceConfigDefault
import com.localaisearch.data.model.HardwareBackend
import com.localaisearch.data.llm.LlamaBridge
import kotlin.math.max
import kotlin.math.min
import com.localaisearch.data.search.SearchConfig
import com.localaisearch.data.search.SearchConfigDefault
import com.localaisearch.data.search.SearchProviderType
import com.localaisearch.data.repository.LanguageManager
import com.localaisearch.data.repository.SettingsRepository
import com.localaisearch.data.repository.TorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for app settings.
 */
class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)

    private val _searchConfig = MutableStateFlow(SearchConfigDefault)
    val searchConfig: StateFlow<SearchConfig> = _searchConfig.asStateFlow()

    private val _inferenceConfig = MutableStateFlow(InferenceConfigDefault)
    val inferenceConfig: StateFlow<InferenceConfig> = _inferenceConfig.asStateFlow()

    private val _darkMode = MutableStateFlow("system")
    val darkMode: StateFlow<String> = _darkMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _themePreset = MutableStateFlow("blue")
    val themePreset: StateFlow<String> = _themePreset.asStateFlow()

    private val _internetSearchEnabled = MutableStateFlow(false)
    val internetSearchEnabled: StateFlow<Boolean> = _internetSearchEnabled.asStateFlow()

    private val _language = MutableStateFlow(LanguageManager.SYSTEM_DEFAULT)
    val language: StateFlow<String> = _language.asStateFlow()

    private val _animationLevel = MutableStateFlow("standard")
    val animationLevel: StateFlow<String> = _animationLevel.asStateFlow()

    private val _fontMode = MutableStateFlow("system")
    val fontMode: StateFlow<String> = _fontMode.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    // Prevent the onboarding screen from flashing for one frame while DataStore is loading.
    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

    private val _modelSource = MutableStateFlow("tsinghua_mirror")
    val modelSource: StateFlow<String> = _modelSource.asStateFlow()

    private val _privacyMode = MutableStateFlow(false)
    private val _proxyConfig = MutableStateFlow(com.localaisearch.data.repository.ProxyConfig())
    private val _torEnabled = MutableStateFlow(false)
    val torEnabled: StateFlow<Boolean> = _torEnabled.asStateFlow()
    private val _torBridges = MutableStateFlow("")
    val torBridges: StateFlow<String> = _torBridges.asStateFlow()
    private val _torExitCountry = MutableStateFlow("")
    val torExitCountry: StateFlow<String> = _torExitCountry.asStateFlow()
    private val _torCustomConfig = MutableStateFlow("")
    val torCustomConfig: StateFlow<String> = _torCustomConfig.asStateFlow()
    private var torJob: kotlinx.coroutines.Job? = null
    val torStatus: StateFlow<TorManager.Status> = TorManager.statusFlow
    val proxyConfig: StateFlow<com.localaisearch.data.repository.ProxyConfig> = _proxyConfig.asStateFlow()
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()
    private val _visionFallbackModelPath = MutableStateFlow("")
    val visionFallbackModelPath: StateFlow<String> = _visionFallbackModelPath.asStateFlow()

    init {
        viewModelScope.launch {
            _searchConfig.value = settingsRepo.searchConfig.first()
            _inferenceConfig.value = settingsRepo.inferenceConfig.first()
            _darkMode.value = settingsRepo.darkMode.first()
            _dynamicColor.value = settingsRepo.dynamicColorEnabled.first()
            _themePreset.value = settingsRepo.themePreset.first()
            _internetSearchEnabled.value = settingsRepo.internetSearchEnabled.first()
            _language.value = settingsRepo.appLanguage.first()
            _animationLevel.value = settingsRepo.animationLevel.first()
            _fontMode.value = settingsRepo.fontMode.first()
            _onboardingCompleted.value = settingsRepo.onboardingCompleted.first()
            _modelSource.value = settingsRepo.modelSource.first()
            _privacyMode.value = settingsRepo.privacyModeEnabled.first()
            _visionFallbackModelPath.value = settingsRepo.visionFallbackModelPath.first()
            _proxyConfig.value = settingsRepo.proxyConfig.first()
            _torEnabled.value = settingsRepo.torEnabled.first()
            _torBridges.value = settingsRepo.torBridges.first()
            _torExitCountry.value = settingsRepo.torExitCountry.first()
            _torCustomConfig.value = settingsRepo.torCustomConfig.first()
            if (_torEnabled.value) {
                // A persisted toggle is not proof that Tor is connected. Reconnect and let
                // TorManager report STARTING/ON/ERROR. Never restore a fake ON state.
                val result = TorManager.start(
                bridgeLines = _torBridges.value,
                exitCountry = _torExitCountry.value,
                customConfig = _torCustomConfig.value
            )
                if (result.isFailure) {
                    _torEnabled.value = false
                    settingsRepo.setTorEnabled(false)
                }
            } else {
                com.localaisearch.data.repository.NetworkClientFactory.updateProxy(_proxyConfig.value)
            }
            _settingsLoaded.value = true
        }
    }


    fun setVisionFallbackModelPath(path: String) {
        _visionFallbackModelPath.value = path
        viewModelScope.launch { settingsRepo.setVisionFallbackModelPath(path) }
    }

    // -- Search config updates --

    fun updateSearchProvider(type: SearchProviderType) {
        _searchConfig.value = _searchConfig.value.copy(providerType = type)
        saveSearchConfig()
    }

    fun updateApiUrl(url: String) {
        _searchConfig.value = _searchConfig.value.copy(apiUrl = url)
        saveSearchConfig()
    }

    fun updateApiKey(key: String) {
        _searchConfig.value = _searchConfig.value.copy(apiKey = key)
        saveSearchConfig()
    }

    fun updateSearchLanguage(lang: String) {
        _searchConfig.value = _searchConfig.value.copy(searchLanguage = lang)
        saveSearchConfig()
    }

    fun updateSearchRegion(region: String) {
        _searchConfig.value = _searchConfig.value.copy(searchRegion = region)
        saveSearchConfig()
    }

    fun updateMaxResults(count: Int) {
        _searchConfig.value = _searchConfig.value.copy(maxResults = count)
        saveSearchConfig()
    }

    fun updateMaxRounds(rounds: Int) {
        _searchConfig.value = _searchConfig.value.copy(maxSearchRounds = rounds.coerceIn(1, 3))
        saveSearchConfig()
    }

    fun updateSafeSearch(enabled: Boolean) {
        _searchConfig.value = _searchConfig.value.copy(enableSafeSearch = enabled)
        saveSearchConfig()
    }

    // -- Inference config updates --

    fun updateTemperature(value: Float) {
        _inferenceConfig.value = _inferenceConfig.value.copy(temperature = value)
        saveInferenceConfig()
    }

    fun updateTopP(value: Float) {
        _inferenceConfig.value = _inferenceConfig.value.copy(topP = value)
        saveInferenceConfig()
    }

    fun updateTopK(value: Int) {
        _inferenceConfig.value = _inferenceConfig.value.copy(topK = value)
        saveInferenceConfig()
    }

    fun updateContextLength(value: Int) {
        _inferenceConfig.value = _inferenceConfig.value.copy(contextLength = value)
        saveInferenceConfig()
    }

    fun updateMaxTokens(value: Int) {
        _inferenceConfig.value = _inferenceConfig.value.copy(maxTokens = value)
        saveInferenceConfig()
    }

    fun updateUseGpu(enabled: Boolean) {
        if (enabled) {
            enableRecommendedAcceleration()
        } else {
            _inferenceConfig.value = _inferenceConfig.value.copy(useGpu = false, backend = HardwareBackend.CPU, gpuLayers = 0)
            saveInferenceConfig()
        }
    }

    /** Fast-start preset: use the compiled GPU backend when the native library really supports it. */
    fun enableRecommendedAcceleration() {
        val gpuAvailable = runCatching { LlamaBridge.initialize() && LlamaBridge.nativeSupportsGpu() }.getOrDefault(false)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val threads = min(8, max(2, cores - 1))
        _inferenceConfig.value = _inferenceConfig.value.copy(
            useGpu = gpuAvailable,
            backend = if (gpuAvailable) HardwareBackend.GPU else HardwareBackend.CPU,
            gpuLayers = if (gpuAvailable) 99 else 0,
            threads = threads,
            contextLength = 4096,
            maxTokens = 1024,
            thinkingDepth = 1
        )
        saveInferenceConfig()
    }

    fun updateGpuLayers(value: Int) {
        _inferenceConfig.value = _inferenceConfig.value.copy(gpuLayers = value)
        saveInferenceConfig()
    }

    fun updateThreads(value: Int) {
        _inferenceConfig.value = _inferenceConfig.value.copy(threads = value)
        saveInferenceConfig()
    }

    fun updateFrequencyPenalty(value: Float) {
        _inferenceConfig.value = _inferenceConfig.value.copy(frequencyPenalty = value.coerceIn(-2f, 2f))
        saveInferenceConfig()
    }

    fun updatePresencePenalty(value: Float) {
        _inferenceConfig.value = _inferenceConfig.value.copy(presencePenalty = value.coerceIn(-2f, 2f))
        saveInferenceConfig()
    }

    fun updateThinkingDepth(value: Int) {
        _inferenceConfig.value = _inferenceConfig.value.copy(thinkingDepth = value.coerceIn(1, 4))
        saveInferenceConfig()
    }

    fun updateBackend(value: HardwareBackend) {
        val gpu = value == HardwareBackend.GPU && runCatching { LlamaBridge.initialize() && LlamaBridge.nativeSupportsGpu() }.getOrDefault(false)
        _inferenceConfig.value = _inferenceConfig.value.copy(backend = if (gpu) HardwareBackend.GPU else HardwareBackend.CPU, useGpu = gpu, gpuLayers = if (gpu) 99 else 0)
        saveInferenceConfig()
    }

    // -- UI settings --

    fun updateDarkMode(mode: String) {
        _darkMode.value = mode
        viewModelScope.launch { settingsRepo.setDarkMode(mode) }
    }

    fun updateDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        viewModelScope.launch { settingsRepo.setDynamicColorEnabled(enabled) }
    }

    fun updateThemePreset(preset: String) {
        _themePreset.value = preset
        viewModelScope.launch { settingsRepo.setThemePreset(preset) }
    }

    fun updateInternetSearch(enabled: Boolean) {
        _internetSearchEnabled.value = enabled
        viewModelScope.launch { settingsRepo.setInternetSearchEnabled(enabled) }
    }

    fun updateLanguage(languageCode: String) {
        _language.value = languageCode
        viewModelScope.launch { settingsRepo.setAppLanguage(languageCode) }
    }

    fun updateAnimationLevel(level: String) {
        _animationLevel.value = level
        viewModelScope.launch { settingsRepo.setAnimationLevel(level) }
    }

    fun updateFontMode(mode: String) {
        _fontMode.value = mode
        viewModelScope.launch { settingsRepo.setFontMode(mode) }
    }

    fun completeOnboarding() {
        _onboardingCompleted.value = true
        viewModelScope.launch { settingsRepo.setOnboardingCompleted(true) }
    }

    fun updateModelSource(source: String) {
        // Tor mode pins Model Center to the official Hugging Face source.
        if (TorManager.status == TorManager.Status.ON || TorManager.status == TorManager.Status.STARTING) {
            _modelSource.value = "hugging_face"
            return
        }
        _modelSource.value = source
        viewModelScope.launch { settingsRepo.setModelSource(source) }
    }

    fun updatePrivacyMode(enabled: Boolean) {
        _privacyMode.value = enabled
        viewModelScope.launch { settingsRepo.setPrivacyModeEnabled(enabled) }
    }


    fun updateTorBridges(value: String) {
        _torBridges.value = value
        viewModelScope.launch { settingsRepo.setTorBridges(value) }
    }

    fun updateTorExitCountry(value: String) {
        val normalized = value.trim().lowercase().filter(Char::isLetter).take(2)
        _torExitCountry.value = normalized
        viewModelScope.launch { settingsRepo.setTorExitCountry(normalized) }
    }

    fun updateTorCustomConfig(value: String) {
        _torCustomConfig.value = value.take(8192)
        viewModelScope.launch { settingsRepo.setTorCustomConfig(_torCustomConfig.value) }
    }

    fun setTorEnabled(enabled: Boolean) {
        torJob?.cancel()
        if (!enabled) {
            _torEnabled.value = false
            torJob = viewModelScope.launch {
                settingsRepo.setTorEnabled(false)
                TorManager.stop()
                com.localaisearch.data.repository.NetworkClientFactory.updateProxy(_proxyConfig.value)
            }
            return
        }

        _torEnabled.value = false
        torJob = viewModelScope.launch {
            val result = TorManager.start(
                bridgeLines = _torBridges.value,
                exitCountry = _torExitCountry.value,
                customConfig = _torCustomConfig.value
            )
            if (result.isSuccess && TorManager.status == TorManager.Status.ON) {
                _torEnabled.value = true
                settingsRepo.setTorEnabled(true)
            } else {
                _torEnabled.value = false
                settingsRepo.setTorEnabled(false)
            }
        }
    }

    fun retryTor() {
        torJob?.cancel()
        torJob = viewModelScope.launch {
            _torEnabled.value = false
            TorManager.stop()
            kotlinx.coroutines.delay(350)
            val result = TorManager.start(
                bridgeLines = _torBridges.value,
                exitCountry = _torExitCountry.value,
                customConfig = _torCustomConfig.value
            )
            val connected = result.isSuccess && TorManager.status == TorManager.Status.ON
            _torEnabled.value = connected
            settingsRepo.setTorEnabled(connected)
        }
    }

    fun updateProxy(config: com.localaisearch.data.repository.ProxyConfig) {
        _proxyConfig.value = config
        com.localaisearch.data.repository.NetworkClientFactory.updateProxy(config)
        viewModelScope.launch { settingsRepo.setProxyConfig(config) }
    }

    private fun saveSearchConfig() {
        viewModelScope.launch { settingsRepo.updateSearchConfig(_searchConfig.value) }
    }

    private fun saveInferenceConfig() {
        viewModelScope.launch { settingsRepo.updateInferenceConfig(_inferenceConfig.value) }
    }
    val chatAutoScroll = settingsRepo.chatAutoScroll
    val chatMarkdown = settingsRepo.chatMarkdown
    val chatCodeHighlight = settingsRepo.chatCodeHighlight
    val chatEnterSend = settingsRepo.chatEnterSend
    val chatAutoCopy = settingsRepo.chatAutoCopy
    val perfMemoryOptimization = settingsRepo.perfMemoryOptimization
    val perfBackgroundInference = settingsRepo.perfBackgroundInference
    val perfTemperatureProtection = settingsRepo.perfTemperatureProtection

    fun updateChatAutoScroll(v: Boolean) = viewModelScope.launch { settingsRepo.setChatAutoScroll(v) }
    fun updateChatMarkdown(v: Boolean) = viewModelScope.launch { settingsRepo.setChatMarkdown(v) }
    fun updateChatCodeHighlight(v: Boolean) = viewModelScope.launch { settingsRepo.setChatCodeHighlight(v) }
    fun updateChatEnterSend(v: Boolean) = viewModelScope.launch { settingsRepo.setChatEnterSend(v) }
    fun updateChatAutoCopy(v: Boolean) = viewModelScope.launch { settingsRepo.setChatAutoCopy(v) }
    fun updatePerfMemoryOptimization(v: Boolean) = viewModelScope.launch { settingsRepo.setPerfMemoryOptimization(v) }
    fun updatePerfBackgroundInference(v: Boolean) = viewModelScope.launch { settingsRepo.setPerfBackgroundInference(v) }
    fun updatePerfTemperatureProtection(v: Boolean) = viewModelScope.launch { settingsRepo.setPerfTemperatureProtection(v) }

}
