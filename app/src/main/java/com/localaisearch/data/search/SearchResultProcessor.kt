package com.localaisearch.data.search

import com.localaisearch.data.model.SearchResult
import com.localaisearch.data.model.SearchSession

/**
 * Processes search results: deduplication, filtering, ranking.
 *
 * Prevents large amounts of irrelevant content from consuming context.
 */
object SearchResultProcessor {

    /**
     * Deduplicate results by URL (case-insensitive, trailing slash normalized).
     */
    fun deduplicate(results: List<SearchResult>): List<SearchResult> {
        val seen = mutableSetOf<String>()
        return results.filter { result ->
            val key = normalizeUrl(result.url)
            if (seen.contains(key)) {
                false
            } else {
                seen.add(key)
                true
            }
        }
    }

    /**
     * Filter out low-quality or irrelevant results.
     */
    fun filter(results: List<SearchResult>): List<SearchResult> {
        return results.filter { result ->
            // Filter out empty titles or snippets
            if (result.title.isBlank()) return@filter false
            if (result.url.isBlank()) return@filter false

            // Filter out known low-quality domains
            val domain = result.source.lowercase()
            if (isLowQualityDomain(domain)) return@filter false

            // Filter out results with extremely short snippets (likely not useful)
            if (result.snippet.length < 20) return@filter false

            true
        }
    }

    /**
     * Rank results by relevance score combining:
     * - Original search position score
     * - Snippet length (longer = more info)
     * - Domain authority heuristic
     */
    fun rank(results: List<SearchResult>, query: String): List<SearchResult> {
        val queryTerms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

        return results.map { result ->
            val baseScore = result.score.coerceIn(0f, 1f)

            // Query term match in title
            val titleLower = result.title.lowercase()
            val titleMatchScore = queryTerms.count { term -> titleLower.contains(term) }
                .toFloat() / queryTerms.size.coerceAtLeast(1)

            // Query term match in snippet
            val snippetLower = result.snippet.lowercase()
            val snippetMatchScore = queryTerms.count { term -> snippetLower.contains(term) }
                .toFloat() / queryTerms.size.coerceAtLeast(1)

            // Snippet length score (normalized, capped at 300 chars)
            val lengthScore = (result.snippet.length.toFloat() / 300f).coerceIn(0f, 1f)

            // Domain authority boost
            val domainBoost = if (isAuthoritativeDomain(result.source.lowercase())) 0.15f else 0f

            // Earlier rounds get slight boost
            val roundScore = if (result.searchRound == 0) 0.1f else 0f

            val combinedScore = baseScore * 0.3f +
                    titleMatchScore * 0.3f +
                    snippetMatchScore * 0.2f +
                    lengthScore * 0.1f +
                    domainBoost +
                    roundScore

            result.copy(score = combinedScore)
        }.sortedByDescending { it.score }
    }

    /**
     * Full pipeline: deduplicate -> filter -> rank -> limit.
     */
    fun process(results: List<SearchResult>, query: String, maxCount: Int = 10): List<SearchResult> {
        return rank(filter(deduplicate(results)), query).take(maxCount)
    }

    /**
     * Process an entire search session.
     */
    fun processSession(session: SearchSession, query: String, maxCount: Int = 10): List<SearchResult> {
        return process(session.allResults, query, maxCount)
    }

    /**
     * Build context text from search results for the LLM.
     * Limits total size to avoid context overflow.
     */
    fun buildContextText(results: List<SearchResult>, maxChars: Int = 6000): String {
        val sb = StringBuilder()
        for ((index, result) in results.withIndex()) {
            val entry = buildString {
                append("[${index + 1}] ${result.title}\n")
                append("URL: ${result.url}\n")
                append("Source: ${result.source}\n")
                if (result.snippet.isNotBlank()) {
                    val snippet = if (result.snippet.length > 500) {
                        result.snippet.substring(0, 500) + "..."
                    } else {
                        result.snippet
                    }
                    append("Content: $snippet\n")
                }
                append("\n")
            }

            if (sb.length + entry.length > maxChars) break
            sb.append(entry)
        }
        return sb.toString()
    }

    private fun normalizeUrl(url: String): String {
        return url.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
            .substringBefore('#')
    }

    private val lowQualityDomains = setOf(
        "pinterest.com", "facebook.com", "instagram.com",
        "twitter.com", "x.com", "tiktok.com",
        "reddit.com", "quora.com"
    )

    private fun isLowQualityDomain(domain: String): Boolean {
        return lowQualityDomains.any { domain.contains(it) }
    }

    private val authoritativeDomains = setOf(
        "wikipedia.org", "github.com", "stackoverflow.com",
        "arxiv.org", "nature.com", "sciencedirect.com",
        "ieee.org", "acm.org", "gov", "edu",
        "mozilla.org", "w3.org", "apple.com",
        "developer.android.com", "kotlinlang.org"
    )

    private fun isAuthoritativeDomain(domain: String): Boolean {
        return authoritativeDomains.any { domain.contains(it) }
    }
}
