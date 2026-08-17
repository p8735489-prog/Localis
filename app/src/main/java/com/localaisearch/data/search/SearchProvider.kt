package com.localaisearch.data.search

import com.localaisearch.data.model.SearchResult
import kotlinx.serialization.Serializable

/**
 * Search provider type enum.
 */
@Serializable
enum class SearchProviderType(val displayName: String) {
    SEARXNG("SearXNG"),
    BRAVE("Brave Search"),
    BING("Bing Search"),
    CUSTOM("Custom API")
}

/**
 * Configuration for a search provider.
 */
@Serializable
data class SearchConfig(
    val providerType: SearchProviderType = SearchProviderType.SEARXNG,
    val apiUrl: String = "",
    val apiKey: String = "",
    val searchLanguage: String = "auto",
    val searchRegion: String = "global",
    val maxResults: Int = 10,
    val maxSearchRounds: Int = 3,
    val enableSafeSearch: Boolean = true,
    val timeoutSeconds: Int = 15
) {
    companion object {
        val Default = SearchConfig()
    }
}

/**
 * Unified search provider interface.
 * All search backends implement this interface to ensure
 * the AgentEngine can work with any provider seamlessly.
 */
interface SearchProvider {

    /** Provider type identifier */
    val type: SearchProviderType

    /** Whether this provider is properly configured and ready */
    fun isConfigured(config: SearchConfig): Boolean

    /**
     * Execute a search query.
     *
     * @param query The search keywords
     * @param config Search configuration
     * @param round Current search round (for tracking)
     * @return List of search results
     */
    suspend fun search(query: String, config: SearchConfig, round: Int = 0): Result<List<SearchResult>>

    /**
     * Fetch the full content of a search result URL.
     * Used by the AgentEngine to read source pages.
     *
     * @param url The URL to fetch
     * @param config Search configuration
     * @return The page text content, or null if unavailable
     */
    suspend fun fetchContent(url: String, config: SearchConfig): Result<String>
}

/**
 * Factory for creating search providers.
 */
object SearchProviderFactory {

    fun create(type: SearchProviderType): SearchProvider = when (type) {
        SearchProviderType.SEARXNG -> SearXNGProvider()
        SearchProviderType.BRAVE -> BraveSearchProvider()
        SearchProviderType.BING -> BingSearchProvider()
        SearchProviderType.CUSTOM -> CustomSearchProvider()
    }

    fun create(config: SearchConfig): SearchProvider = create(config.providerType)
}
