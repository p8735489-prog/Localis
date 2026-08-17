package com.localaisearch.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localaisearch.data.repository.ConversationRepository
import com.localaisearch.data.repository.MemoryRepository
import com.localaisearch.data.repository.PrivacyManager
import com.localaisearch.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Storage statistics summary for the Data Security screen.
 *
 * @property conversationsCount Number of stored conversations.
 * @property memoriesCount Number of stored memory entries.
 * @property imageCacheSize Human-readable image cache size string.
 * @property formattedTotal Human-readable total estimated storage usage.
 */
data class StorageStats(
    val conversationsCount: String = "0",
    val memoriesCount: String = "0",
    val imageCacheSize: String = "0 B",
    val formattedTotal: String = "0 B"
)

/**
 * ViewModel for the Data & Security settings screen.
 *
 * Bridges the existing [PrivacyManager], [ConversationRepository],
 * [MemoryRepository], and [SettingsRepository] to expose privacy
 * toggles, storage statistics, and destructive data operations.
 */
class DataSecurityViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val privacyManager = PrivacyManager(settingsRepository)
    private val conversationRepository = ConversationRepository(application)
    private val memoryRepository = MemoryRepository(application)

    val isPrivacyMode: StateFlow<Boolean> = privacyManager.isPrivacyMode
    val isMemoryEnabled: StateFlow<Boolean> = privacyManager.isMemoryEnabled

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    init {
        refreshStorageStats()
    }

    /**
     * Toggle privacy mode on/off.
     */
    fun togglePrivacyMode() {
        privacyManager.togglePrivacyMode()
    }

    /**
     * Enable or disable the long-term memory system.
     */
    fun setMemoryEnabled(enabled: Boolean) {
        privacyManager.setMemoryEnabled(enabled)
    }

    /**
     * Delete all conversations from persistent storage.
     */
    fun deleteAllConversations() {
        viewModelScope.launch {
            conversationRepository.clearAllConversations()
            refreshStorageStats()
        }
    }

    /**
     * Delete all memories from persistent storage.
     */
    fun deleteAllMemories() {
        viewModelScope.launch {
            memoryRepository.clearAllMemories()
            refreshStorageStats()
        }
    }

    /**
     * Clear the image cache. This is a stub implementation; the actual
     * cache clearing would be implemented by an ImageCache manager.
     */
    fun clearImageCache() {
        viewModelScope.launch {
            // TODO: Integrate with actual ImageCache manager when available
            refreshStorageStats()
        }
    }

    /**
     * Nuclear option: delete ALL local data including conversations,
     * memories, settings, and cache.
     */
    fun deleteAllLocalData() {
        viewModelScope.launch {
            conversationRepository.clearAllConversations()
            memoryRepository.clearAllMemories()
            // Clear settings via DataStore edit (reset to defaults)
            settingsRepository.setPrivacyModeEnabled(false)
            settingsRepository.setMemorySystemEnabled(true)
            // TODO: Add image cache clear when ImageCache manager is available
            refreshStorageStats()
        }
    }

    /**
     * Refresh the storage statistics displayed on the screen.
     */
    private fun refreshStorageStats() {
        viewModelScope.launch {
            val convCount = conversationRepository.getConversationCount().collect { count ->
                val memCount = memoryRepository.getMemories().collect { memories ->
                    val totalItems = count + memories.size
                    _storageStats.value = StorageStats(
                        conversationsCount = count.toString(),
                        memoriesCount = memories.size.toString(),
                        imageCacheSize = "0 B", // Placeholder until ImageCache is available
                        formattedTotal = "$totalItems items"
                    )
                }
            }
        }
    }
}
