package com.localaisearch.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.localaisearch.data.model.Conversation
import com.localaisearch.data.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.memoriesDataStore: DataStore<Preferences> by preferencesDataStore(name = "memories")

/**
 * A single long-term memory entry extracted from conversations.
 *
 * @property id Unique identifier for this memory entry.
 * @property content The fact or preference being remembered.
 * @property topic Optional topic/category for grouping (e.g., "preferences", "personal_info").
 * @property sourceConversationId The ID of the conversation this memory was extracted from.
 * @property createdAt Timestamp when the memory was first extracted.
 * @property lastAccessedAt Timestamp when the memory was last read or referenced.
 */
@Serializable
data class MemoryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val topic: String = "general",
    val sourceConversationId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)

/**
 * Repository for managing long-term memory entries extracted from conversations.
 *
 * Memory entries are stored as a JSON-serialized list under a single DataStore
 * string key. They represent facts, preferences, and important information the
 * user has shared that the AI should remember across sessions.
 */
class MemoryRepository(private val context: Context) {

    private val dataStore = context.memoriesDataStore
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val MEMORIES_JSON = stringPreferencesKey("memories_json")

        /** Keywords that indicate a sentence contains something worth remembering. */
        private val MEMORY_KEYWORDS = listOf(
            "记住", "重要", "我的", "我喜欢", "我讨厌", "我是", "我在",
            "remember", "important", "my ", "i like", "i hate", "i am ", "i live",
            "always", "never", "usually", "prefer", "name is", "called"
        )

        /** Maximum length of a single memory entry in characters. */
        private const val MAX_MEMORY_LENGTH = 500
    }

    /**
     * Load the list of memory entries from the current preferences snapshot.
     */
    private fun loadMemories(prefs: Preferences): List<MemoryEntry> {
        val jsonStr = prefs[MEMORIES_JSON] ?: return emptyList()
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Persist the given list of memory entries to DataStore.
     */
    private suspend fun saveMemories(list: List<MemoryEntry>) {
        dataStore.edit { prefs ->
            prefs[MEMORIES_JSON] = json.encodeToString(list)
        }
    }

    /**
     * Add a new memory entry.
     *
     * @param content The fact or preference to remember.
     * @param topic Topic/category for grouping.
     * @param sourceConversationId The originating conversation ID.
     * @return [Result.success] with the created [MemoryEntry], or [Result.failure] on error.
     */
    suspend fun addMemory(
        content: String,
        topic: String,
        sourceConversationId: String
    ): Result<MemoryEntry> {
        return try {
            val trimmed = content.trim().take(MAX_MEMORY_LENGTH)
            if (trimmed.isBlank()) {
                return Result.failure(IllegalArgumentException("Memory content cannot be blank"))
            }
            val entry = MemoryEntry(
                content = trimmed,
                topic = topic,
                sourceConversationId = sourceConversationId
            )
            val current = dataStore.data.map { loadMemories(it) }.first().toMutableList()
            current.add(entry)
            saveMemories(current)
            Result.success(entry)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all memory entries as a Flow, ordered by creation time descending.
     */
    fun getMemories(): Flow<List<MemoryEntry>> {
        return dataStore.data.map { prefs ->
            loadMemories(prefs).sortedByDescending { it.createdAt }
        }
    }

    /**
     * Simple text search across memory contents and topics.
     *
     * @param query Case-insensitive search string.
     * @return Matching entries ordered by recency.
     */
    suspend fun searchMemories(query: String): List<MemoryEntry> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return emptyList()
        return dataStore.data.map { prefs ->
            loadMemories(prefs).filter { entry ->
                entry.content.lowercase().contains(normalized) ||
                    entry.topic.lowercase().contains(normalized)
            }.sortedByDescending { it.createdAt }
        }.first()
    }

    /**
     * Get memories that share keywords with the given query.
     *
     * Splits the query into words and returns entries whose content contains
     * at least one of those words.
     *
     * @param query The user query to match against.
     * @param maxResults Maximum number of entries to return.
     * @return Relevant memory entries.
     */
    suspend fun getRelevantMemories(
        query: String,
        maxResults: Int = 5
    ): List<MemoryEntry> {
        val words = query.trim().lowercase()
            .split(Regex("[\\s,;.!?，。！？]"))
            .filter { it.length >= 2 }
            .toSet()
        if (words.isEmpty()) return emptyList()

        return dataStore.data.map { prefs ->
            loadMemories(prefs)
                .filter { entry ->
                    val contentLower = entry.content.lowercase()
                    words.any { contentLower.contains(it) }
                }
                .sortedByDescending { it.lastAccessedAt }
                .take(maxResults)
        }.first()
    }

    /**
     * Update the content of an existing memory entry.
     *
     * @param id The memory entry UUID.
     * @param newContent The updated content.
     * @return [Result.success] if found and updated, [Result.failure] on error.
     */
    suspend fun updateMemory(id: String, newContent: String): Result<Unit> {
        return try {
            val trimmed = newContent.trim().take(MAX_MEMORY_LENGTH)
            if (trimmed.isBlank()) {
                return Result.failure(IllegalArgumentException("Memory content cannot be blank"))
            }
            dataStore.edit { prefs ->
                val current = loadMemories(prefs).toMutableList()
                val index = current.indexOfFirst { it.id == id }
                if (index >= 0) {
                    val entry = current[index]
                    current[index] = entry.copy(
                        content = trimmed,
                        lastAccessedAt = System.currentTimeMillis()
                    )
                    prefs[MEMORIES_JSON] = json.encodeToString(current)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a single memory entry by ID.
     *
     * @param id The memory entry UUID.
     * @return [Result.success] on success, [Result.failure] on error.
     */
    suspend fun deleteMemory(id: String): Result<Unit> {
        return try {
            dataStore.edit { prefs ->
                val current = loadMemories(prefs).filter { it.id != id }
                prefs[MEMORIES_JSON] = json.encodeToString(current)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove all memory entries.
     *
     * @return [Result.success] on success, [Result.failure] on error.
     */
    suspend fun clearAllMemories(): Result<Unit> {
        return try {
            dataStore.edit { prefs ->
                prefs[MEMORIES_JSON] = json.encodeToString(emptyList<MemoryEntry>())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete all memories associated with a specific conversation.
     *
     * @param conversationId The conversation UUID.
     * @return [Result.success] on success, [Result.failure] on error.
     */
    suspend fun deleteMemoriesByConversation(conversationId: String): Result<Unit> {
        return try {
            dataStore.edit { prefs ->
                val current = loadMemories(prefs).filter { it.sourceConversationId != conversationId }
                prefs[MEMORIES_JSON] = json.encodeToString(current)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extract potential memory entries from a conversation using simple heuristics.
     *
     * Looks for sentences containing memory-indicating keywords (e.g., "记住",
     * "important", "my name") or user messages that appear to contain personal
     * information.
     *
     * @param conversation The conversation to analyze.
     * @return A list of newly extracted [MemoryEntry] objects (not yet persisted).
     */
    fun extractMemoriesFromConversation(conversation: Conversation): List<MemoryEntry> {
        val memories = mutableListOf<MemoryEntry>()
        val userMessages = conversation.messages.filter { it.role == MessageRole.USER }

        for (message in userMessages) {
            val sentences = splitIntoSentences(message.content)
            for (sentence in sentences) {
                val trimmed = sentence.trim()
                if (trimmed.length < 5) continue

                // Heuristic 1: contains explicit memory keywords
                val hasKeyword = MEMORY_KEYWORDS.any { keyword ->
                    trimmed.lowercase().contains(keyword.lowercase())
                }

                // Heuristic 2: looks like personal info (simple patterns)
                val looksLikePersonalInfo = containsPersonalInfo(trimmed)

                if (hasKeyword || looksLikePersonalInfo) {
                    val topic = when {
                        trimmed.lowercase().contains("name") -> "personal_info"
                        trimmed.lowercase().contains("like") || trimmed.lowercase().contains("喜欢") -> "preferences"
                        trimmed.lowercase().contains("hate") || trimmed.lowercase().contains("讨厌") -> "preferences"
                        trimmed.lowercase().contains("live") || trimmed.lowercase().contains("住在") -> "personal_info"
                        trimmed.lowercase().contains("work") || trimmed.lowercase().contains("工作") -> "personal_info"
                        else -> "general"
                    }

                    memories.add(
                        MemoryEntry(
                            content = trimmed.take(MAX_MEMORY_LENGTH),
                            topic = topic,
                            sourceConversationId = conversation.id
                        )
                    )
                }
            }
        }

        // Deduplicate by content similarity (exact match)
        return memories.distinctBy { it.content.lowercase() }
    }

    // --- Private helpers ---

    /**
     * Split text into sentences using common delimiters.
     */
    private fun splitIntoSentences(text: String): List<String> {
        return text.split(Regex("(?<=[。！？.!?])\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Simple heuristic to detect if a sentence likely contains personal information.
     */
    private fun containsPersonalInfo(text: String): Boolean {
        val lower = text.lowercase()
        val patterns = listOf(
            Regex("my name is "),
            Regex("i am a "),
            Regex("i work at "),
            Regex("i live in "),
            Regex("i'm from "),
            Regex("\b\d{1,2} years old\b"),
            Regex("我叫"),
            Regex("我是"),
            Regex("我住在"),
            Regex("我在.*工作")
        )
        return patterns.any { it.containsMatchIn(lower) }
    }
}
