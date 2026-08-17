package com.localaisearch.data.repository

import com.localaisearch.data.model.SearchResult
import com.localaisearch.data.search.SearchConfig
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
    private var currentConfig: SearchConfig = SearchConfig.Default

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

        val result = provider.search(query, currentConfig, round)
        result.onSuccess { rawResults ->
            // Process: deduplicate, filter, rank
            val processed = SearchResultProcessor.process(rawResults, query, currentConfig.maxResults)
        }
        result
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
