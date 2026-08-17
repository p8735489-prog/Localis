package com.localaisearch.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localaisearch.data.model.GGUFModel
import com.localaisearch.data.repository.DownloadManager
import com.localaisearch.data.repository.DownloadState
import com.localaisearch.data.repository.HFModelFile
import com.localaisearch.data.repository.HFModelInfo
import com.localaisearch.data.repository.HuggingFaceRepository
import com.localaisearch.data.repository.ModelRepository
import com.localaisearch.data.repository.ModelRepositoryFactory
import com.localaisearch.data.repository.TsinghuaMirrorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Model Center - discovering and downloading GGUF models
 * from Hugging Face Hub and Tsinghua University mirror.
 */
class ModelCenterViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val hfRepository = HuggingFaceRepository()
    private val mirrorRepository = TsinghuaMirrorRepository()

    private val _selectedSource = MutableStateFlow(ModelRepositoryFactory.Source.HUGGING_FACE)
    val selectedSource: StateFlow<ModelRepositoryFactory.Source> = _selectedSource.asStateFlow()

    private val _searchResults = MutableStateFlow<List<HFModelInfo>>(emptyList())
    val searchResults: StateFlow<List<HFModelInfo>> = _searchResults.asStateFlow()

    private val _trendingModels = MutableStateFlow<List<HFModelInfo>>(emptyList())
    val trendingModels: StateFlow<List<HFModelInfo>> = _trendingModels.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Download manager
    private val _downloadManager: DownloadManager
    val downloadStates: StateFlow<Map<String, DownloadState>>

    // Track downloaded models
    private val _downloadedModels = MutableStateFlow<List<GGUFModel>>(emptyList())
    val downloadedModels: StateFlow<List<GGUFModel>> = _downloadedModels.asStateFlow()

    private val _modelRepo: ModelRepository

    init {
        val llmEngine = com.localaisearch.data.llm.GGUFEngine()
        _modelRepo = ModelRepository(application, llmEngine)

        _downloadManager = DownloadManager(
            context = application,
            onComplete = { model ->
                // Add to downloaded list
                _downloadedModels.value = _downloadedModels.value + model
                _modelRepo.refreshModels()
            }
        )
        downloadStates = _downloadManager.downloadStates

        // Load trending models on init
        loadTrending()

        // Load already downloaded models
        refreshDownloadedModels()
    }

    /**
     * Set the download source (HF or Tsinghua Mirror).
     */
    fun setSource(source: ModelRepositoryFactory.Source) {
        _selectedSource.value = source
    }

    /**
     * Search for GGUF models.
     */
    fun search(query: String) {
        _isSearching.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val result = when (_selectedSource.value) {
                    ModelRepositoryFactory.Source.HUGGING_FACE -> hfRepository.searchModels(query, limit = 30)
                    ModelRepositoryFactory.Source.TSINGHUA_MIRROR -> mirrorRepository.searchModels(query, limit = 30)
                }
                result.onSuccess { models ->
                    _searchResults.value = models
                }.onFailure { e ->
                    _error.value = e.message ?: "Search failed"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Search failed"
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * Load trending/popular models.
     */
    private fun loadTrending() {
        viewModelScope.launch {
            try {
                val result = when (_selectedSource.value) {
                    ModelRepositoryFactory.Source.HUGGING_FACE -> hfRepository.getTrendingModels(limit = 20)
                    ModelRepositoryFactory.Source.TSINGHUA_MIRROR -> mirrorRepository.getTrendingModels(limit = 20)
                }
                result.onSuccess { models ->
                    _trendingModels.value = models
                }.onFailure { e ->
                    // Don't show error for initial load
                }
            } catch (e: Exception) {
                // Silent fail for initial load
            }
        }
    }

    /**
     * List GGUF files in a specific model repository.
     */
    suspend fun listModelFiles(repoId: String): Result<List<HFModelFile>> {
        return when (_selectedSource.value) {
            ModelRepositoryFactory.Source.HUGGING_FACE -> hfRepository.listGgufFiles(repoId)
            ModelRepositoryFactory.Source.TSINGHUA_MIRROR -> mirrorRepository.listGgufFiles(repoId)
        }
    }

    /**
     * Start downloading a GGUF model file.
     */
    fun startDownload(model: HFModelInfo, file: HFModelFile) {
        val modelId = model.id
        _downloadManager.startDownload(
            modelId = modelId,
            downloadUrl = file.downloadUrl,
            fileName = file.path.substringAfterLast('/'),
            totalSize = file.size,
            downloadSource = _selectedSource.value.name
        )
    }

    /**
     * Pause a download.
     */
    fun pauseDownload(modelId: String) {
        _downloadManager.pauseDownload(modelId)
    }

    /**
     * Resume a paused download.
     */
    fun resumeDownload(modelId: String, file: HFModelFile) {
        _downloadManager.resumeDownload(
            modelId = modelId,
            downloadUrl = file.downloadUrl,
            fileName = file.path.substringAfterLast('/'),
            totalSize = file.size,
            downloadSource = _selectedSource.value.name
        )
    }

    /**
     * Cancel and delete a download.
     */
    fun cancelDownload(modelId: String, fileName: String) {
        _downloadManager.cancelDownload(modelId, fileName)
    }

    /**
     * Delete a downloaded model file.
     */
    fun deleteDownloadedModel(model: GGUFModel) {
        viewModelScope.launch {
            _modelRepo.deleteModel(model)
            refreshDownloadedModels()
        }
    }

    /**
     * Refresh the list of downloaded models.
     */
    fun refreshDownloadedModels() {
        _modelRepo.refreshModels()
        _downloadedModels.value = _modelRepo.models.value
    }

    /**
     * Set Hugging Face token for private repos.
     */
    fun setHfToken(token: String?) {
        hfRepository.setToken(token)
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        _downloadManager.release()
    }
}
