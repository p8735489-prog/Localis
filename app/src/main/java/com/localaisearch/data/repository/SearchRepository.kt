package com.localaisearch.data.repository

import com.localaisearch.data.model.SearchResult
import com.localaisearch.data.search.SearchConfig
import com.localaisearch.data.search.SearchConfigDefault
import com.localaisearch.data.search.SearchProvider
import com.localaisearch.data.search.SearchProviderFactory
import com.localaisearch.data.search.SearchResultProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for search operations.
 * Wraps SearchProvider and adds result processing.
 */
class SearchRepository {

    private var currentProvider: SearchProvider? = null
    private var currentConfig: SearchConfig = SearchConfigDefault

    /**
     * Update the search configuration and recreate the provider if needed.
     */
    fun updateConfig(config: SearchConfig) {
        if (currentConfig.providerType != config.providerType || currentProvider == null) {
            currentProvider = SearchProviderFactory.create(config)
        }
        currentConfig = config
    }

    /**
     * Execute a search query and process results.
     */
    suspend fun search(
        query: String,
        round: Int = 0
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        val provider = currentProvider
            ?: return@withContext Result.failure(IllegalStateException("Search provider not initialized"))

        if (!provider.isConfigured(currentConfig)) {
            return@withContext Result.failure(
                IllegalStateException("Search provider not configured. Check API settings.")
            )
        }

        val normalizedQuery = query.trim().replace(Regex("\\s+"), " ")
        if (normalizedQuery.isBlank()) return@withContext Result.success(emptyList())
        provider.search(normalizedQuery, currentConfig, round).map { rawResults ->
            SearchResultProcessor.process(rawResults, normalizedQuery, currentConfig.maxResults)
        }
    }

    /** One-call search for simple UI/search-box usage without mutating global configuration. */
    suspend fun quickSearch(query: String, limit: Int = 8): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        val provider = currentProvider
            ?: return@withContext Result.failure(IllegalStateException("Search provider not initialized"))
        val normalizedQuery = query.trim().replace(Regex("\\s+"), " ")
        if (normalizedQuery.isBlank()) return@withContext Result.success(emptyList())
        val quickConfig = currentConfig.copy(maxResults = limit.coerceIn(1, 20), maxSearchRounds = 1)
        if (!provider.isConfigured(quickConfig)) {
            return@withContext Result.failure(IllegalStateException("Search provider not configured. Check API settings."))
        }
        provider.search(normalizedQuery, quickConfig, 0).map { raw ->
            SearchResultProcessor.process(raw, normalizedQuery, quickConfig.maxResults)
        }
    }

    /**
     * Fetch full content from a URL.
     */
    suspend fun fetchContent(url: String): Result<String> = withContext(Dispatchers.IO) {
        val provider = currentProvider
            ?: return@withContext Result.failure(IllegalStateException("Search provider not initialized"))

        provider.fetchContent(url, currentConfig)
    }

    /**
     * Check if the search provider is configured and ready.
     */
    fun isConfigured(): Boolean {
        val provider = currentProvider ?: return false
        return provider.isConfigured(currentConfig)
    }

    /**
     * Get current search configuration.
     */
    fun getConfig(): SearchConfig = currentConfig
}
