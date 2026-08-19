package com.localaisearch.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localaisearch.data.repository.MemoryEntry
import com.localaisearch.data.repository.MemoryRepository
import com.localaisearch.data.repository.MemorySearchPreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Memory Center screen.
 *
 * Manages the list of memory entries, search/filter state,
 * and CRUD operations through the existing [MemoryRepository].
 */
class MemoryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = MemoryRepository(application)

    private val _memories = MutableStateFlow<List<MemoryEntry>>(emptyList())
    val memories: StateFlow<List<MemoryEntry>> = _memories.asStateFlow()

    private val _visibleMemories = MutableStateFlow<List<MemoryEntry>>(emptyList())
    val visibleMemories: StateFlow<List<MemoryEntry>> = _visibleMemories.asStateFlow()

    private val _searchPreset = MutableStateFlow(MemorySearchPreset.ALL)
    val searchPreset: StateFlow<MemorySearchPreset> = _searchPreset.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTopic = MutableStateFlow("all")
    val selectedTopic: StateFlow<String> = _selectedTopic.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var debounceJob: Job? = null

    init {
        viewModelScope.launch {
            repository.getMemories().collect { list ->
                _memories.value = list
                refreshSearch()
            }
        }
    }

    /**
     * Update the search query for filtering memories.
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(if (query.isBlank()) 0L else 120L)
            refreshSearch()
        }
    }

    fun setSearchPreset(preset: MemorySearchPreset) {
        _searchPreset.value = preset
        refreshSearch()
    }

    private fun refreshSearch() {
        viewModelScope.launch {
            _visibleMemories.value = repository.searchMemories(_searchQuery.value, _searchPreset.value, 100)
        }
    }

    /**
     * Set the topic filter. Use "all" to show all topics.
     */
    fun setSelectedTopic(topic: String) {
        if (_isLoading.value) return
        _selectedTopic.value = topic
    }

    /**
     * Add a new memory entry.
     *
     * @param content The memory content.
     * @param topic The topic/category.
     */
    fun addMemory(content: String, topic: String) {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.addMemory(
                    content = content,
                    topic = topic,
                    sourceConversationId = "manual"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Update the content of an existing memory.
     */
    fun updateMemory(id: String, newContent: String) {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.updateMemory(id, newContent)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Delete a memory entry by ID.
     */
    fun deleteMemory(id: String) {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.deleteMemory(id)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Clear all stored memories.
     */
    fun clearAllMemories() {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.clearAllMemories()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
