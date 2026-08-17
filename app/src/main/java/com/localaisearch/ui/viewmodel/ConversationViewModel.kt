package com.localaisearch.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localaisearch.data.repository.ConversationRepository
import com.localaisearch.data.repository.StoredConversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the conversation history screen.
 *
 * Exposes the list of stored conversations (including pinned state) as a
 * StateFlow, supports real-time search filtering, and provides actions for
 * pin, rename, delete, export, and clear-all operations via the existing
 * [ConversationRepository].
 */
class ConversationViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = ConversationRepository(application)

    private val _conversations = MutableStateFlow<List<StoredConversation>>(emptyList())
    val conversations: StateFlow<List<StoredConversation>> = _conversations.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllStoredConversations().collect { list ->
                _conversations.value = list
            }
        }
    }

    /**
     * Update the current search query. When non-empty, the repository's
     * [searchStoredConversations] flow is used instead.
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            if (query.isBlank()) {
                repository.getAllStoredConversations().collect { list ->
                    _conversations.value = list
                }
            } else {
                repository.searchStoredConversations(query).collect { list ->
                    _conversations.value = list
                }
            }
        }
    }

    /**
     * Toggle the pinned state of a conversation.
     */
    fun togglePin(id: String) {
        viewModelScope.launch {
            val current = _conversations.value.find { it.conversation.id == id }
            val newPinned = !(current?.pinned ?: false)
            repository.pinConversation(id, newPinned)
        }
    }

    /**
     * Rename a conversation.
     */
    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameConversation(id, newTitle)
        }
    }

    /**
     * Delete a conversation.
     */
    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
        }
    }

    /**
     * Export a conversation to JSON string and make it available via [exportResult].
     *
     * @param id The conversation ID to export.
     */
    fun exportConversation(id: String) {
        viewModelScope.launch {
            val json = repository.exportConversation(id)
            _exportResult.value = json
        }
    }

    /**
     * Clear the export result after the UI has consumed it.
     */
    fun clearExportResult() {
        _exportResult.value = null
    }

    /**
     * Clear all conversation history.
     */
    fun clearAllConversations() {
        viewModelScope.launch {
            repository.clearAllConversations()
        }
    }
}
