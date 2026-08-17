package com.localaisearch.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localaisearch.data.repository.MemoryEntry
import com.localaisearch.data.repository.MemoryRepository
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTopic = MutableStateFlow("all")
    val selectedTopic: StateFlow<String> = _selectedTopic.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getMemories().collect { list ->
                _memories.value = list
            }
        }
    }

    /**
     * Update the search query for filtering memories.
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Set the topic filter. Use "all" to show all topics.
     */
    fun setSelectedTopic(topic: String) {
        _selectedTopic.value = topic
    }

    /**
     * Add a new memory entry.
     *
     * @param content The memory content.
     * @param topic The topic/category.
     */
    fun addMemory(content: String, topic: String) {
        viewModelScope.launch {
            repository.addMemory(
                content = content,
                topic = topic,
                sourceConversationId = "manual"
            )
        }
    }

    /**
     * Update the content of an existing memory.
     */
    fun updateMemory(id: String, newContent: String) {
        viewModelScope.launch {
            repository.updateMemory(id, newContent)
        }
    }

    /**
     * Delete a memory entry by ID.
     */
    fun deleteMemory(id: String) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    /**
     * Clear all stored memories.
     */
    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
        }
    }
}
