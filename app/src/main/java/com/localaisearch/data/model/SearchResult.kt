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
)

/**
 * Extracts the domain name from a URL, stripping the "www." prefix.
 */
fun extractDomain(url: String): String {
    return try {
        val uri = java.net.URI(url)
        uri.host?.removePrefix("www.") ?: url
    } catch (_: Exception) {
        url
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
    fun addRound(round: SearchRound): SearchSession {
        val newRounds = rounds + round
        val deduped = deduplicateSearchResults(newRounds.flatMap { it.results })
        return copy(rounds = newRounds, allResults = deduped)
    }
}

/** Total number of results in a SearchSession. */
val SearchSession.totalResults: Int get() = allResults.size
/** Total number of rounds in a SearchSession. */
val SearchSession.totalRounds: Int get() = rounds.size

/**
 * Removes duplicate [SearchResult]s based on URL (case-insensitive, trailing slash normalized).
 */
fun deduplicateSearchResults(results: List<SearchResult>): List<SearchResult> {
    val seen = mutableSetOf<String>()
    return results.filter { result ->
        val key = result.url.lowercase().trimEnd('/')
        seen.add(key)
    }
}
