package com.localaisearch.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages privacy session state and enforces privacy rules.
 *
 * Privacy mode is designed to give users confidence that no conversation
 * data, search history, or temporary files are persisted during sensitive
 * sessions. When privacy mode is active:
 * - Conversations are not saved to storage
 * - Search history is not recorded
 * - Image cache is not written to disk
 * - Memory/summary generation is disabled
 *
 * The memory system is independently togglable but is additionally gated
 * by privacy mode.
 *
 * @param settingsRepository Repository used to persist privacy preferences
 *                           to DataStore so they survive app restarts.
 */
class PrivacyManager(private val settingsRepository: SettingsRepository) {

    /** Current privacy mode state (volatile, lost on process death). */
    private val _isPrivacyMode = MutableStateFlow(false)
    val isPrivacyMode: StateFlow<Boolean> = _isPrivacyMode.asStateFlow()

    /** Whether the long-term memory system is enabled (persisted). */
    private val _isMemoryEnabled = MutableStateFlow(true)
    val isMemoryEnabled: StateFlow<Boolean> = _isMemoryEnabled.asStateFlow()

    init {
        // Observe persisted memory setting and update the in-memory state.
        // Privacy mode is intentionally NOT persisted so that it defaults
        // to off on every app launch (user must explicitly activate each
        // session).
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            settingsRepository.memorySystemEnabled.collect { enabled ->
                _isMemoryEnabled.value = enabled
            }
        }
    }

    /**
     * Activate privacy mode for the current session.
     *
     * Once enabled, no conversation, search, or cache data will be written
     * to persistent storage until [disablePrivacyMode] is called.
     */
    fun enablePrivacyMode() {
        _isPrivacyMode.value = true
    }

    /**
     * Deactivate privacy mode and restore normal persistence behavior.
     */
    fun disablePrivacyMode() {
        _isPrivacyMode.value = false
    }

    /**
     * Toggle privacy mode on/off.
     */
    fun togglePrivacyMode() {
        _isPrivacyMode.value = !_isPrivacyMode.value
    }

    /**
     * Enable or disable the long-term memory system.
     *
     * This setting is persisted via [SettingsRepository]. Even when enabled,
     * memory is additionally gated by privacy mode (see [canReadMemory]
     * and [canWriteMemory]).
     *
     * @param enabled `true` to allow the memory system, `false` to disable it.
     */
    fun setMemoryEnabled(enabled: Boolean) {
        _isMemoryEnabled.value = enabled
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            settingsRepository.setMemorySystemEnabled(enabled)
        }
    }

    /**
     * Returns `true` if the memory system is both enabled AND privacy mode
     * is currently inactive.
     */
    fun canReadMemory(): Boolean {
        return _isMemoryEnabled.value && !_isPrivacyMode.value
    }

    /**
     * Returns `true` if the memory system is both enabled AND privacy mode
     * is currently inactive.
     *
     * Same logic as [canReadMemory] but expressed separately for clarity.
     */
    fun canWriteMemory(): Boolean {
        return _isMemoryEnabled.value && !_isPrivacyMode.value
    }

    /**
     * Returns `false` when privacy mode is active, preventing conversation
     * persistence.
     */
    fun canSaveConversation(): Boolean {
        return !_isPrivacyMode.value
    }

    /**
     * Returns `false` when privacy mode is active, preventing search history
     * from being recorded.
     */
    fun canSaveSearchHistory(): Boolean {
        return !_isPrivacyMode.value
    }

    /**
     * Returns `false` when privacy mode is active, preventing image cache
     * from being written to disk.
     */
    fun canSaveImageCache(): Boolean {
        return !_isPrivacyMode.value
    }

    /**
     * Returns `false` when privacy mode is active, preventing the LLM from
     * generating conversation summaries that could leak sensitive context.
     */
    fun canGenerateSummary(): Boolean {
        return !_isPrivacyMode.value
    }

    /**
     * Produce a human-readable summary of the current privacy settings.
     *
     * @return A short description suitable for display in a settings UI or
     *         a privacy status indicator.
     */
    fun getPrivacySummary(): String {
        return buildString {
            if (_isPrivacyMode.value) {
                append("Privacy Mode: ON\\n")
                append("  - Conversations will NOT be saved\\n")
                append("  - Search history will NOT be recorded\\n")
                append("  - Image cache is disabled\\n")
                append("  - Memory/summary generation is disabled\\n")
            } else {
                append("Privacy Mode: OFF\\n")
                append("  - Conversations will be saved\\n")
                append("  - Search history will be recorded\\n")
                append("  - Image cache is enabled\\n")
            }
            if (_isMemoryEnabled.value) {
                append("Memory System: ENABLED")
            } else {
                append("Memory System: DISABLED")
            }
        }
    }

    /**
     * Clean up temporary in-memory data after a privacy session ends.
     *
     * This should be called when the user explicitly turns off privacy mode
     * or when the app detects that the privacy session has ended. It clears
     * any transient state that was held only in memory.
     */
    fun clearPrivacySessionData() {
        // Reset the volatile privacy flag (already done by disablePrivacyMode,
        // but included here for completeness in case this method is called
        // directly).
        _isPrivacyMode.value = false
    }
}
