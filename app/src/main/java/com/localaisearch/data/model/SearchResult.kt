package com.localaisearch.data.model

import kotlinx.serialization.Serializable

/**
 * A single search result returned by a SearchProvider.
 */
@Serializable
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String = extractDomain(url),
    val publishedDate: String? = null,
    val score: Float = 0f,
    val searchRound: Int = 0
) {
    companion object {
        fun extractDomain(url: String): String {
            return try {
                val uri = java.net.URI(url)
                uri.host?.removePrefix("www.") ?: url
            } catch (_: Exception) {
                url
            }
        }
    }
}

/**
 * Aggregated search results from a single search round.
 */
@Serializable
data class SearchRound(
    val round: Int,
    val query: String,
    val results: List<SearchResult>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * All search rounds accumulated during an agentic search session.
 */
@Serializable
data class SearchSession(
    val rounds: List<SearchRound> = emptyList(),
    val allResults: List<SearchResult> = emptyList()
) {
    val totalResults: Int get() = allResults.size
    val totalRounds: Int get() = rounds.size

    fun addRound(round: SearchRound): SearchSession {
        val newRounds = rounds + round
        val deduped = deduplicate(newRounds.flatMap { it.results })
        return copy(rounds = newRounds, allResults = deduped)
    }

    companion object {
        fun deduplicate(results: List<SearchResult>): List<SearchResult> {
            val seen = mutableSetOf<String>()
            return results.filter { result ->
                val key = result.url.lowercase().trimEnd('/')
                seen.add(key)
            }
        }
    }
}
