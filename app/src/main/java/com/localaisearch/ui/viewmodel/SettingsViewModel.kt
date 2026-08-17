package com.localaisearch.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localaisearch.data.model.InferenceConfig
import com.localaisearch.data.model.InferenceConfigDefault
import com.localaisearch.data.search.SearchConfig
import com.localaisearch.data.search.SearchConfigDefault
import com.localaisearch.data.search.SearchProviderType
import com.localaisearch.data.repository.SettingsRepository
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

    private val _internetSearchEnabled = MutableStateFlow(false)
    val internetSearchEnabled: StateFlow<Boolean> = _internetSearchEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            _searchConfig.value = settingsRepo.searchConfig.first()
            _inferenceConfig.value = settingsRepo.inferenceConfig.first()
            _darkMode.value = settingsRepo.darkMode.first()
            _dynamicColor.value = settingsRepo.dynamicColorEnabled.first()
            _internetSearchEnabled.value = settingsRepo.internetSearchEnabled.first()
        }
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
        _inferenceConfig.value = _inferenceConfig.value.copy(useGpu = enabled)
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

    // -- UI settings --

    fun updateDarkMode(mode: String) {
        _darkMode.value = mode
        viewModelScope.launch { settingsRepo.setDarkMode(mode) }
    }

    fun updateDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        viewModelScope.launch { settingsRepo.setDynamicColorEnabled(enabled) }
    }

    fun updateInternetSearch(enabled: Boolean) {
        _internetSearchEnabled.value = enabled
        viewModelScope.launch { settingsRepo.setInternetSearchEnabled(enabled) }
    }

    private fun saveSearchConfig() {
        viewModelScope.launch { settingsRepo.updateSearchConfig(_searchConfig.value) }
    }

    private fun saveInferenceConfig() {
        viewModelScope.launch { settingsRepo.updateInferenceConfig(_inferenceConfig.value) }
    }
}
