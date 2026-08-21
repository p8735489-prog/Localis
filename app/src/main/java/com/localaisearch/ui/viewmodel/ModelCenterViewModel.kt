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
import com.localaisearch.data.repository.SettingsRepository
import com.localaisearch.data.repository.TorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for the Model Center - discovering and downloading GGUF models
 * from Hugging Face Hub and domestic Hugging Face mirror.
 */
class ModelCenterViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val hfRepository = HuggingFaceRepository()
    private val mirrorRepository = TsinghuaMirrorRepository()
    private val settingsRepository = SettingsRepository(application)

    private fun torActive(): Boolean = TorManager.status != TorManager.Status.OFF

    private val _selectedSource = MutableStateFlow(ModelRepositoryFactory.Source.TSINGHUA_MIRROR)
    val selectedSource: StateFlow<ModelRepositoryFactory.Source> = _selectedSource.asStateFlow()

    private val _searchResults = MutableStateFlow<List<HFModelInfo>>(emptyList())
    val searchResults: StateFlow<List<HFModelInfo>> = _searchResults.asStateFlow()

    private val _trendingModels = MutableStateFlow<List<HFModelInfo>>(emptyList())
    val trendingModels: StateFlow<List<HFModelInfo>> = _trendingModels.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingTrending = MutableStateFlow(true)
    val isLoadingTrending: StateFlow<Boolean> = _isLoadingTrending.asStateFlow()

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
        val llmEngine = com.localaisearch.data.llm.LLMProviderFactory.createProvider(
            com.localaisearch.data.llm.LLMProviderType.LOCAL_GGUF,
            application
        )
        _modelRepo = ModelRepository(application, llmEngine)

        _downloadManager = DownloadManager(
            context = application,
            onComplete = { model ->
                // Add to downloaded list
                _downloadedModels.value = _downloadedModels.value + model
                viewModelScope.launch {
                    _modelRepo.refreshModels()
                    _downloadedModels.value = _modelRepo.models.value
                }
            }
        )
        downloadStates = _downloadManager.downloadStates

        viewModelScope.launch {
            _selectedSource.value = if (torActive() || settingsRepository.modelSource.first() == "hugging_face") {
                ModelRepositoryFactory.Source.HUGGING_FACE
            } else {
                ModelRepositoryFactory.Source.TSINGHUA_MIRROR
            }
            loadTrending()
        }

        // Load already downloaded models
        refreshDownloadedModels()
    }

    val activeModel: StateFlow<GGUFModel?> = _modelRepo.activeModel

    private val _modelLoadState = MutableStateFlow<ModelCenterLoadState>(ModelCenterLoadState.Idle)
    val modelLoadState: StateFlow<ModelCenterLoadState> = _modelLoadState.asStateFlow()


    /**
     * Set the download source (HF or Domestic Mirror).
     */
    fun setSource(source: ModelRepositoryFactory.Source) {
        // Tor mode intentionally pins Model Center to the official Hugging Face
        // endpoint. Never silently fall back to a mirror while Tor is active.
        if (torActive() && source != ModelRepositoryFactory.Source.HUGGING_FACE) return
        _selectedSource.value = source
        viewModelScope.launch {
            settingsRepository.setModelSource(
                if (source == ModelRepositoryFactory.Source.HUGGING_FACE) "hugging_face" else "tsinghua_mirror"
            )
        }
    }

    /**
     * Search for GGUF models.
     */
    fun search(query: String) {
        _isSearching.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val normalizedQuery = normalizeModelQuery(query)
                val result = searchWithFallback(normalizedQuery, 30)
                result.onSuccess { models ->
                    _searchResults.value = models
                    if (models.isEmpty()) {
                        _error.value = if (torActive()) "Hugging Face 官方源暂时没有返回模型。Tor 模式不会切换到镜像。" else "当前来源没有返回模型，已自动尝试备用来源。"
                    }
                }.onFailure { e ->
                    _error.value = "模型接口暂时不可用：${e.message ?: "未知网络错误"}"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Search failed"
            } finally {
                _isSearching.value = false
            }
        }
    }

    private suspend fun searchWithFallback(query: String, limit: Int): Result<List<HFModelInfo>> {
        if (torActive()) return hfRepository.searchModels(query, limit)
        val primary = when (_selectedSource.value) {
            ModelRepositoryFactory.Source.HUGGING_FACE -> hfRepository.searchModels(query, limit)
            ModelRepositoryFactory.Source.TSINGHUA_MIRROR -> mirrorRepository.searchModels(query, limit)
        }
        if (primary.isSuccess && !primary.getOrNull().isNullOrEmpty()) return primary

        val secondary = when (_selectedSource.value) {
            ModelRepositoryFactory.Source.HUGGING_FACE -> mirrorRepository.searchModels(query, limit)
            ModelRepositoryFactory.Source.TSINGHUA_MIRROR -> hfRepository.searchModels(query, limit)
        }
        if (secondary.isSuccess) return secondary
        return primary
    }



    private fun normalizeModelQuery(query: String): String {
        val q = query.trim()
        if (q.isBlank()) return q
        val aliases = mapOf(
            "通义千问" to "qwen", "千问" to "qwen", "阿里" to "qwen",
            "深度求索" to "deepseek", "深度" to "deepseek",
            "羊驼" to "llama", "骆驼" to "llama",
            "微软" to "phi", "费" to "phi",
            "谷歌" to "gemma", "杰玛" to "gemma",
            "米斯特拉尔" to "mistral", "法国模型" to "mistral",
            "魔搭" to "modelscope", "视觉" to "vision", "多模态" to "vision",
            "代码" to "coder", "编程" to "coder", "推理" to "reasoning"
        )
        aliases.entries.firstOrNull { q.contains(it.key, ignoreCase = true) }?.let { return "$q ${it.value}" }
        return q
    }

    /**
     * Load trending/popular models.
     */
    private fun loadTrending(showError: Boolean = false) {
        viewModelScope.launch {
            try {
                val result = trendingWithFallback(20)
                result.onSuccess { models ->
                    _trendingModels.value = models
                    if (showError && models.isEmpty()) {
                        _error.value = "模型接口返回空列表，请更换模型中心来源后重试。"
                    }
                }.onFailure { e ->
                    if (showError) {
                        _error.value = "刷新模型失败：${e.message ?: "网络接口不可用"}"
                    }
                }
            } catch (e: Exception) {
                if (showError) {
                    _error.value = "刷新模型失败：${e.message ?: "网络接口不可用"}"
                }
            } finally {
                _isLoadingTrending.value = false
            }
        }
    }

    private suspend fun trendingWithFallback(limit: Int): Result<List<HFModelInfo>> {
        if (torActive()) return hfRepository.getTrendingModels(limit)
        val primary = when (_selectedSource.value) {
            ModelRepositoryFactory.Source.HUGGING_FACE -> hfRepository.getTrendingModels(limit)
            ModelRepositoryFactory.Source.TSINGHUA_MIRROR -> mirrorRepository.getTrendingModels(limit)
        }
        if (primary.isSuccess && !primary.getOrNull().isNullOrEmpty()) return primary
        val secondary = when (_selectedSource.value) {
            ModelRepositoryFactory.Source.HUGGING_FACE -> mirrorRepository.getTrendingModels(limit)
            ModelRepositoryFactory.Source.TSINGHUA_MIRROR -> hfRepository.getTrendingModels(limit)
        }
        if (secondary.isSuccess) return secondary
        return primary
    }


    /**
     * List GGUF files in a specific model repository.
     */
    suspend fun listModelFiles(repoId: String): Result<List<HFModelFile>> {
        if (torActive()) return hfRepository.listGgufFiles(repoId)
        val primary = when (_selectedSource.value) {
            ModelRepositoryFactory.Source.HUGGING_FACE -> hfRepository.listGgufFiles(repoId)
            ModelRepositoryFactory.Source.TSINGHUA_MIRROR -> mirrorRepository.listGgufFiles(repoId)
        }
        if (primary.isSuccess && !primary.getOrNull().isNullOrEmpty()) return primary
        val secondary = when (_selectedSource.value) {
            ModelRepositoryFactory.Source.HUGGING_FACE -> mirrorRepository.listGgufFiles(repoId)
            ModelRepositoryFactory.Source.TSINGHUA_MIRROR -> hfRepository.listGgufFiles(repoId)
        }
        if (secondary.isSuccess) return secondary
        return primary
    }

    /**
     * Start downloading a GGUF model file.
     */
    fun viewModelScopeLaunch(block: suspend () -> Unit) { viewModelScope.launch { block() } }

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
        viewModelScope.launch {
            _modelRepo.refreshModels()
            _downloadedModels.value = _modelRepo.models.value
        }
    }

    fun loadModel(model: GGUFModel) {
        if (_modelLoadState.value is ModelCenterLoadState.Loading) return
        viewModelScope.launch {
            _modelLoadState.value = ModelCenterLoadState.Loading(model.id)
            val config = settingsRepository.inferenceConfig.first()
            val result = _modelRepo.loadModel(model, config)
            result.onSuccess {
                _modelLoadState.value = ModelCenterLoadState.Loaded(model.id)
                _downloadedModels.value = _modelRepo.models.value
            }.onFailure { e ->
                _modelLoadState.value = ModelCenterLoadState.Error(e.message ?: "Model load failed")
            }
        }
    }

    fun unloadModel() {
        if (_modelLoadState.value is ModelCenterLoadState.Loading) return
        viewModelScope.launch {
            val result = _modelRepo.unloadModel()
            result.onSuccess {
                _modelLoadState.value = ModelCenterLoadState.Idle
                _downloadedModels.value = _modelRepo.models.value
            }.onFailure { e ->
                _modelLoadState.value = ModelCenterLoadState.Error(e.message ?: "Model unload failed")
            }
        }
    }

    fun clearModelLoadError() {
        if (_modelLoadState.value is ModelCenterLoadState.Error) _modelLoadState.value = ModelCenterLoadState.Idle
    }

    /**
     * Set Hugging Face token for private repos.
     */
    fun setHfToken(token: String?) {
        hfRepository.setToken(token)
    }

    fun refresh() {
        _error.value = null
        loadTrending(showError = true)
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        _downloadManager.release()
    }
}

sealed class ModelCenterLoadState {
    data object Idle : ModelCenterLoadState()
    data class Loading(val modelId: String) : ModelCenterLoadState()
    data class Loaded(val modelId: String) : ModelCenterLoadState()
    data class Error(val message: String) : ModelCenterLoadState()
}
