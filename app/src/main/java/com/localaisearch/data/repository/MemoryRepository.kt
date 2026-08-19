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
import kotlin.math.ln

private val Context.memoriesDataStore: DataStore<Preferences> by preferencesDataStore(name = "memories")

/** Lightweight presets for fast, predictable memory retrieval. */
enum class MemorySearchPreset {
    ALL, PREFERENCES, PROJECTS, DEVICE, RECENT
}

@Serializable
data class MemoryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val topic: String = "general",
    val sourceConversationId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,
    val importance: Float = 0.6f,
    val pinned: Boolean = false,
    val tags: List<String> = emptyList()
)

/**
 * Long-term memory store with lightweight relevance ranking.
 * The ranking is intentionally local and deterministic: exact phrase matches,
 * token overlap, topic/tag matches, recency, importance and access frequency
 * are combined without sending memory contents to a remote service.
 */
class MemoryRepository(private val context: Context) {
    private val dataStore = context.memoriesDataStore
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private val MEMORIES_JSON = stringPreferencesKey("memories_json")
        private const val MAX_MEMORY_LENGTH = 500
        private const val MAX_MEMORY_COUNT = 2000
        private val MEMORY_KEYWORDS = listOf(
            "记住", "重要", "我的", "我喜欢", "我讨厌", "我是", "我在", "以后", "偏好",
            "remember", "important", "my ", "i like", "i hate", "i am ", "i live",
            "always", "never", "usually", "prefer", "name is", "called"
        )
    }

    private fun loadMemories(prefs: Preferences): List<MemoryEntry> {
        val raw = prefs[MEMORIES_JSON] ?: return emptyList()
        return runCatching { json.decodeFromString<List<MemoryEntry>>(raw) }.getOrDefault(emptyList())
    }

    private fun normalize(text: String): String = text.trim().lowercase()

    /** Tokenizer that works for both CJK text and whitespace-delimited languages. */
    private fun tokenize(text: String): Set<String> {
        val normalized = normalize(text)
        val words = normalized.split(Regex("[\\s,;.!?，。！？、:：/\\\\|()\\[\\]{}]+"))
            .filter { it.length >= 2 }
            .toMutableSet()
        // CJK bigrams make short Chinese queries useful without an embedding model.
        val cjk = normalized.filter { it.code in 0x4E00..0x9FFF }
        for (i in 0 until (cjk.length - 1).coerceAtLeast(0)) words += cjk.substring(i, i + 2)
        return words
    }

    private fun matchesPreset(entry: MemoryEntry, preset: MemorySearchPreset): Boolean = when (preset) {
        MemorySearchPreset.ALL -> true
        MemorySearchPreset.PREFERENCES -> entry.topic in setOf("preferences", "preference") ||
            entry.tags.any { it in setOf("preference", "preferences", "偏好") }
        MemorySearchPreset.PROJECTS -> entry.topic in setOf("project", "projects", "work", "项目") ||
            entry.tags.any { it in setOf("project", "projects", "项目", "work") }
        MemorySearchPreset.DEVICE -> entry.topic in setOf("device", "devices", "设备", "technology", "tech") ||
            entry.tags.any { it in setOf("device", "devices", "设备", "tech") }
        MemorySearchPreset.RECENT -> entry.createdAt >= System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    }

    private fun relevance(entry: MemoryEntry, query: String, now: Long): Float {
        if (query.isBlank()) return entry.importance + if (entry.pinned) 2f else 0f
        val q = normalize(query)
        val content = normalize(entry.content)
        val topic = normalize(entry.topic)
        val tags = entry.tags.map(::normalize)
        val tokens = tokenize(query)
        val overlap = if (tokens.isEmpty()) 0f else tokens.count { token ->
            content.contains(token) || topic.contains(token) || tags.any { it.contains(token) }
        }.toFloat() / tokens.size
        val exact = if (content.contains(q)) 1f else 0f
        val phraseStart = if (content.startsWith(q)) 0.35f else 0f
        val recencyDays = ((now - entry.lastAccessedAt).coerceAtLeast(0L) / 86_400_000f)
        val recency = 1f / (1f + recencyDays / 14f)
        val accessBoost = (ln((entry.accessCount + 1).toDouble()) / 5.0).toFloat().coerceIn(0f, 0.7f)
        val importance = entry.importance.coerceIn(0f, 1f)
        return exact * 2.4f + phraseStart + overlap * 1.8f + recency * 0.8f + accessBoost + importance * 0.7f + if (entry.pinned) 2f else 0f
    }

    suspend fun addMemory(content: String, topic: String, sourceConversationId: String): Result<MemoryEntry> = try {
        val trimmed = content.trim().take(MAX_MEMORY_LENGTH)
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Memory content cannot be blank"))
        val now = System.currentTimeMillis()
        var result: MemoryEntry? = null
        dataStore.edit { prefs ->
            val current = loadMemories(prefs).toMutableList()
            val index = current.indexOfFirst { normalize(it.content) == normalize(trimmed) }
            if (index >= 0) {
                val old = current[index]
                result = old.copy(
                    topic = topic.ifBlank { old.topic },
                    lastAccessedAt = now,
                    lastUpdatedAt = now,
                    accessCount = old.accessCount + 1,
                    importance = maxOf(old.importance, 0.7f)
                )
                current[index] = result!!
            } else {
                result = MemoryEntry(content = trimmed, topic = topic.ifBlank { "general" }, sourceConversationId = sourceConversationId, createdAt = now, lastAccessedAt = now, lastUpdatedAt = now)
                current.add(result!!)
            }
            prefs[MEMORIES_JSON] = json.encodeToString(current.takeLast(MAX_MEMORY_COUNT))
        }
        Result.success(result!!)
    } catch (e: Exception) { Result.failure(e) }

    fun getMemories(): Flow<List<MemoryEntry>> = dataStore.data.map { prefs -> loadMemories(prefs).sortedWith(compareByDescending<MemoryEntry> { it.pinned }.thenByDescending { it.createdAt }) }

    /** Fast local search. Results are ranked like a memory retriever rather than simple substring filtering. */
    suspend fun searchMemories(query: String, preset: MemorySearchPreset = MemorySearchPreset.ALL, maxResults: Int = 50): List<MemoryEntry> {
        val now = System.currentTimeMillis()
        val snapshot = dataStore.data.map { prefs -> loadMemories(prefs) }.first()
        val ranked = snapshot.asSequence()
            .filter { matchesPreset(it, preset) }
            .map { it to relevance(it, query, now) }
            .filter { query.isBlank() || it.second > 0.0f }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
            .toList()
        if (ranked.isNotEmpty()) {
            val ids = ranked.take(8).map { it.id }.toSet()
            dataStore.edit { prefs ->
                val current = loadMemories(prefs)
                prefs[MEMORIES_JSON] = json.encodeToString(current.map { if (it.id in ids) it.copy(lastAccessedAt = now, accessCount = it.accessCount + 1) else it })
            }
        }
        return ranked
    }

    suspend fun getRelevantMemories(query: String, maxResults: Int = 5): List<MemoryEntry> =
        searchMemories(query, MemorySearchPreset.ALL, maxResults)

    suspend fun updateMemory(id: String, newContent: String): Result<Unit> = try {
        val trimmed = newContent.trim().take(MAX_MEMORY_LENGTH)
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Memory content cannot be blank"))
        dataStore.edit { prefs ->
            val current = loadMemories(prefs).toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index >= 0) {
                val old = current[index]
                current[index] = old.copy(content = trimmed, lastUpdatedAt = System.currentTimeMillis())
                prefs[MEMORIES_JSON] = json.encodeToString(current)
            }
        }
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun setPinned(id: String, pinned: Boolean): Result<Unit> = try {
        dataStore.edit { prefs ->
            prefs[MEMORIES_JSON] = json.encodeToString(loadMemories(prefs).map { if (it.id == id) it.copy(pinned = pinned, lastUpdatedAt = System.currentTimeMillis()) else it })
        }
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun deleteMemory(id: String): Result<Unit> = try {
        dataStore.edit { prefs -> prefs[MEMORIES_JSON] = json.encodeToString(loadMemories(prefs).filter { it.id != id }) }
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun clearAllMemories(): Result<Unit> = try {
        dataStore.edit { prefs -> prefs[MEMORIES_JSON] = json.encodeToString(emptyList<MemoryEntry>()) }
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    fun extractMemoriesFromConversation(conversation: Conversation): List<MemoryEntry> {
        val result = mutableListOf<MemoryEntry>()
        for (message in conversation.messages.filter { it.role == MessageRole.USER }) {
            for (sentence in splitIntoSentences(message.content)) {
                val trimmed = sentence.trim()
                if (trimmed.length < 5) continue
                val lower = trimmed.lowercase()
                val hasKeyword = MEMORY_KEYWORDS.any { lower.contains(it.lowercase()) }
                if (!hasKeyword && !containsPersonalInfo(trimmed)) continue
                val topic = when {
                    lower.contains("like") || lower.contains("喜欢") || lower.contains("prefer") || lower.contains("偏好") -> "preferences"
                    lower.contains("project") || lower.contains("项目") || lower.contains("work") || lower.contains("工作") -> "projects"
                    lower.contains("phone") || lower.contains("手机") || lower.contains("computer") || lower.contains("设备") -> "device"
                    lower.contains("name") || lower.contains("叫") || lower.contains("live") || lower.contains("住在") -> "personal_info"
                    else -> "general"
                }
                val tags = buildList {
                    if (topic == "preferences") add("preference")
                    if (topic == "projects") add("project")
                    if (topic == "device") add("device")
                }
                result += MemoryEntry(content = trimmed.take(MAX_MEMORY_LENGTH), topic = topic, sourceConversationId = conversation.id, importance = if (lower.contains("记住") || lower.contains("remember")) 0.9f else 0.65f, tags = tags)
            }
        }
        return result.distinctBy { normalize(it.content) }
    }

    private fun splitIntoSentences(text: String): List<String> = text.split(Regex("(?<=[。！？.!?])\\s*")).map { it.trim() }.filter { it.isNotBlank() }

    private fun containsPersonalInfo(text: String): Boolean {
        val lower = text.lowercase()
        return listOf(
            Regex("my name is "), Regex("i am a "), Regex("i work at "), Regex("i live in "),
            Regex("i'm from "), Regex("\\b\\d{1,2} years old\\b"), Regex("我叫"), Regex("我是"), Regex("我住在"), Regex("我在.*工作")
        ).any { it.containsMatchIn(lower) }
    }
}
