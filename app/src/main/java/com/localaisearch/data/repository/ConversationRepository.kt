package com.localaisearch.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.localaisearch.data.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.conversationsDataStore: DataStore<Preferences> by preferencesDataStore(name = "conversations")

/**
 * Wrapper around [Conversation] that adds repository-level metadata
 * without modifying the original data class.
 *
 * @property conversation The core conversation data.
 * @property pinned Whether this conversation is pinned to the top of the list.
 * @property modelUsed The ID of the model that was used for this conversation.
 */
@Serializable
data class StoredConversation(
    val conversation: Conversation,
    val pinned: Boolean = false,
    val modelUsed: String? = null
)

/**
 * Repository for managing persistent conversation history using DataStore.
 *
 * Conversations are stored as a JSON-serialized list of [StoredConversation]
 * objects under a single DataStore string key. This keeps the original
 * [Conversation] model unchanged while adding `pinned` and `modelUsed`
 * metadata at the repository layer.
 */
class ConversationRepository(private val context: Context) {

    private val dataStore = context.conversationsDataStore
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val CONVERSATIONS_JSON = stringPreferencesKey("conversations_json")
    }

    /**
     * Load the list of stored conversations from the current preferences snapshot.
     */
    private fun loadConversations(prefs: Preferences): List<StoredConversation> {
        val jsonStr = prefs[CONVERSATIONS_JSON] ?: return emptyList()
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Persist the given list of stored conversations to DataStore.
     */
    private suspend fun saveConversations(list: List<StoredConversation>) {
        dataStore.edit { prefs ->
            prefs[CONVERSATIONS_JSON] = json.encodeToString(list)
        }
    }

    /**
     * Save a new conversation or update an existing one (matched by ID).
     *
     * @param conversation The conversation to persist.
     * @return [Result.success] on success, [Result.failure] on error.
     */
    suspend fun saveConversation(conversation: Conversation): Result<Unit> {
        return try {
            val list = dataStore.data.map { loadConversations(it) }.first().toMutableList()
            val index = list.indexOfFirst { it.conversation.id == conversation.id }
            if (index >= 0) {
                val existing = list[index]
                list[index] = existing.copy(
                    conversation = conversation.copy(updatedAt = System.currentTimeMillis())
                )
            } else {
                list.add(
                    StoredConversation(
                        conversation = conversation.copy(updatedAt = System.currentTimeMillis())
                    )
                )
            }
            saveConversations(list.sortedByDescending { it.conversation.updatedAt })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retrieve a single conversation by its ID.
     *
     * @param id The conversation UUID.
     * @return The [Conversation] if found, or `null`.
     */
    suspend fun getConversation(id: String): Conversation? {
        return try {
            dataStore.data.map { prefs ->
                loadConversations(prefs).find { it.conversation.id == id }?.conversation
            }.first()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get all conversations sorted by `updatedAt` descending (most recent first).
     * Pinned conversations always appear before unpinned ones.
     */
    fun getAllConversations(): Flow<List<Conversation>> {
        return dataStore.data.map { prefs ->
            loadConversations(prefs)
                .sortedWith(
                    compareByDescending<StoredConversation> { it.pinned }
                        .thenByDescending { it.conversation.updatedAt }
                )
                .map { it.conversation }
        }
    }

    /**
     * Get all stored conversations (including pinned state) sorted by
     * `updatedAt` descending. Pinned conversations always appear first.
     *
     * Unlike [getAllConversations], this preserves the [StoredConversation]
     * wrapper so callers can access the `pinned` and `modelUsed` fields.
     */
    fun getAllStoredConversations(): Flow<List<StoredConversation>> {
        return dataStore.data.map { prefs ->
            loadConversations(prefs)
                .sortedWith(
                    compareByDescending<StoredConversation> { it.pinned }
                        .thenByDescending { it.conversation.updatedAt }
                )
        }
    }

    /**
     * Search conversations by matching [query] against titles and message contents.
     *
     * @param query Case-insensitive search string.
     * @return Flow of matching conversations, ordered by relevance (title match first,
     *         then recency).
     */
    fun searchConversations(query: String): Flow<List<Conversation>> {
        val normalized = query.trim().lowercase()
        return dataStore.data.map { prefs ->
            if (normalized.isBlank()) {
                return@map emptyList<Conversation>()
            }
            loadConversations(prefs)
                .filter { stored ->
                    val conv = stored.conversation
                    conv.title.lowercase().contains(normalized) ||
                        conv.messages.any { msg ->
                            msg.content.lowercase().contains(normalized)
                        }
                }
                .sortedWith(
                    compareByDescending<StoredConversation> {
                        it.conversation.title.lowercase().contains(normalized)
                    }.thenByDescending { it.conversation.updatedAt }
                )
                .map { it.conversation }
        }
    }

    /**
     * Search stored conversations (including pinned state) by matching [query]
     * against titles and message contents.
     *
     * @param query Case-insensitive search string.
     * @return Flow of matching [StoredConversation] entries, ordered by relevance.
     */
    fun searchStoredConversations(query: String): Flow<List<StoredConversation>> {
        val normalized = query.trim().lowercase()
        return dataStore.data.map { prefs ->
            if (normalized.isBlank()) {
                return@map emptyList<StoredConversation>()
            }
            loadConversations(prefs)
                .filter { stored ->
                    val conv = stored.conversation
                    conv.title.lowercase().contains(normalized) ||
                        conv.messages.any { msg ->
                            msg.content.lowercase().contains(normalized)
                        }
                }
                .sortedWith(
                    compareByDescending<StoredConversation> {
                        it.conversation.title.lowercase().contains(normalized)
                    }.thenByDescending { it.conversation.updatedAt }
                )
        }
    }

    /**
     * Delete a conversation by ID.
     *
     * @param id The conversation UUID to remove.
     * @return [Result.success] if the ID existed (or no-op), [Result.failure] on error.
     */
    suspend fun deleteConversation(id: String): Result<Unit> {
        return try {
            dataStore.edit { prefs ->
                val current = loadConversations(prefs).filter { it.conversation.id != id }
                prefs[CONVERSATIONS_JSON] = json.encodeToString(current)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rename a conversation.
     *
     * @param id The conversation UUID.
     * @param newTitle The new title to assign.
     * @return [Result.success] if found and updated, [Result.failure] on error.
     */
    suspend fun renameConversation(id: String, newTitle: String): Result<Unit> {
        return try {
            dataStore.edit { prefs ->
                val current = loadConversations(prefs).toMutableList()
                val index = current.indexOfFirst { it.conversation.id == id }
                if (index >= 0) {
                    val stored = current[index]
                    current[index] = stored.copy(
                        conversation = stored.conversation.copy(
                            title = newTitle,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    prefs[CONVERSATIONS_JSON] = json.encodeToString(current)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pin or unpin a conversation.
     *
     * @param id The conversation UUID.
     * @param isPinned `true` to pin, `false` to unpin.
     * @return [Result.success] if found and updated, [Result.failure] on error.
     */
    suspend fun pinConversation(id: String, isPinned: Boolean): Result<Unit> {
        return try {
            dataStore.edit { prefs ->
                val current = loadConversations(prefs).toMutableList()
                val index = current.indexOfFirst { it.conversation.id == id }
                if (index >= 0) {
                    val stored = current[index]
                    current[index] = stored.copy(
                        pinned = isPinned,
                        conversation = stored.conversation.copy(
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    prefs[CONVERSATIONS_JSON] = json.encodeToString(current)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove all conversations from persistent storage.
     *
     * @return [Result.success] on success, [Result.failure] on error.
     */
    suspend fun clearAllConversations(): Result<Unit> {
        return try {
            dataStore.edit { prefs ->
                prefs[CONVERSATIONS_JSON] = json.encodeToString(emptyList<StoredConversation>())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export a single conversation to a JSON string.
     *
     * @param id The conversation UUID.
     * @return Pretty-printed JSON of the [Conversation], or an empty string if not found.
     */
    suspend fun exportConversation(id: String): String {
        val prettyJson = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        return try {
            val stored = dataStore.data.map { prefs ->
                loadConversations(prefs).find { it.conversation.id == id }
            }.first()
            stored?.let { prettyJson.encodeToString(it.conversation) } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Import a conversation from a JSON string.
     *
     * @param json The JSON representation of a [Conversation].
     * @return [Result] containing the parsed [Conversation], or failure if invalid.
     */
    fun importConversation(json: String): Result<Conversation> {
        return try {
            val conversation = this.json.decodeFromString<Conversation>(json)
            Result.success(conversation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    data class ConversationExport(
        val schemaVersion: Int = 1,
        val exportedAt: Long = System.currentTimeMillis(),
        val conversations: List<StoredConversation>
    )

    /** Export every conversation, including pin/model metadata. */
    suspend fun exportAllConversations(): String {
        val exportJson = Json { prettyPrint = true; encodeDefaults = true }
        return try {
            val all = dataStore.data.map { loadConversations(it) }.first()
            exportJson.encodeToString(ConversationExport(conversations = all))
        } catch (e: Exception) {
            ""
        }
    }

    /** Import an export created by this app. Returns number of conversations imported. */
    suspend fun importAllConversations(jsonText: String): Result<Int> {
        return try {
            val incoming = runCatching {
                json.decodeFromString<ConversationExport>(jsonText).conversations
            }.getOrElse {
                listOf(StoredConversation(conversation = json.decodeFromString<Conversation>(jsonText)))
            }
            dataStore.edit { prefs ->
                val current = loadConversations(prefs).toMutableList()
                val byId = current.associateBy { it.conversation.id }.toMutableMap()
                incoming.forEach { item -> byId[item.conversation.id] = item }
                val merged = byId.values.sortedWith(
                    compareByDescending<StoredConversation> { it.pinned }
                        .thenByDescending { it.conversation.updatedAt }
                )
                prefs[CONVERSATIONS_JSON] = json.encodeToString(merged)
            }
            Result.success(incoming.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe the total number of stored conversations.
     */
    fun getConversationCount(): Flow<Int> {
        return dataStore.data.map { prefs ->
            loadConversations(prefs).size
        }
    }
}
