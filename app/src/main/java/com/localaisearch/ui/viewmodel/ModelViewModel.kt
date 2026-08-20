package com.localaisearch.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localaisearch.data.model.GGUFModel
import com.localaisearch.data.model.InferenceConfig
import com.localaisearch.data.repository.ModelRepository
import com.localaisearch.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for model management (import, delete, switch, load/unload).
 */
class ModelViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val _llmEngine = com.localaisearch.data.llm.LLMProviderFactory.createProvider(
        com.localaisearch.data.llm.LLMProviderType.LOCAL_GGUF,
        application
    )
    val modelRepo = ModelRepository(application, _llmEngine)

    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus.asStateFlow()

    private val _loadStatus = MutableStateFlow<LoadStatus>(LoadStatus.Idle)
    val loadStatus: StateFlow<LoadStatus> = _loadStatus.asStateFlow()

    val models = modelRepo.models
    val activeModel = modelRepo.activeModel

    init {
        viewModelScope.launch { modelRepo.refreshModels() }
    }

    /**
     * Import a GGUF model from a content URI.
     */
    fun importModel(uri: Uri) {
        viewModelScope.launch {
            modelRepo.refreshModels()
            _importStatus.value = ImportStatus.Importing
            val result = modelRepo.importModel(uri)
            result.onSuccess {
                _importStatus.value = ImportStatus.Success(it)
            }.onFailure { e ->
                _importStatus.value = ImportStatus.Error(e.message ?: "Import failed")
            }
        }
    }

    /**
     * Delete a model.
     */
    fun deleteModel(model: GGUFModel) {
        viewModelScope.launch {
            modelRepo.deleteModel(model)
        }
    }

    /**
     * Load a model with current inference config.
     */
    fun loadModel(model: GGUFModel) {
        viewModelScope.launch {
            _loadStatus.value = LoadStatus.Loading(model)
            val config = settingsRepo.inferenceConfig.first()
            val result = modelRepo.loadModel(model, config)
            result.onSuccess {
                _loadStatus.value = LoadStatus.Loaded(model)
            }.onFailure { e ->
                _loadStatus.value = LoadStatus.Error(e.message ?: "Load failed")
            }
        }
    }

    /**
     * Unload the current model.
     */
    fun unloadModel() {
        viewModelScope.launch {
            modelRepo.unloadModel()
            _loadStatus.value = LoadStatus.Idle
        }
    }

    /**
     * Switch to a different model.
     */
    fun switchModel(model: GGUFModel) {
        viewModelScope.launch {
            _loadStatus.value = LoadStatus.Loading(model)
            val config = settingsRepo.inferenceConfig.first()
            val result = modelRepo.switchModel(model, config)
            result.onSuccess {
                _loadStatus.value = LoadStatus.Loaded(model)
            }.onFailure { e ->
                _loadStatus.value = LoadStatus.Error(e.message ?: "Switch failed")
            }
        }
    }

    fun resetImportStatus() {
        _importStatus.value = ImportStatus.Idle
    }

    override fun onCleared() {
        // The GGUF engine is app-scoped and shared with ChatViewModel/ModelCenterViewModel.
        // Releasing it here used to make the chat lose the loaded model when this VM left scope.
        super.onCleared()
    }
}

sealed class ImportStatus {
    object Idle : ImportStatus()
    object Importing : ImportStatus()
    data class Success(val model: GGUFModel) : ImportStatus()
    data class Error(val message: String) : ImportStatus()
}

sealed class LoadStatus {
    object Idle : LoadStatus()
    data class Loading(val model: GGUFModel) : LoadStatus()
    data class Loaded(val model: GGUFModel) : LoadStatus()
    data class Error(val message: String) : LoadStatus()
}
