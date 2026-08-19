package com.localaisearch.data.search

import com.localaisearch.data.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

import com.localaisearch.data.repository.NetworkClientFactory
/**
 * SearXNG search provider implementation.
 *
 * SearXNG is a privacy-respecting metasearch engine that can be self-hosted.
 * API docs: https://docs.searxng.org/admin/settings/settings.html#search
 *
 * Expected API endpoint: {apiUrl}/search?format=json&q={query}
 */
class SearXNGProvider : SearchProvider {

    override val type: SearchProviderType = SearchProviderType.SEARXNG

    private val client by lazy {
        NetworkClientFactory.builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun isConfigured(config: SearchConfig): Boolean {
        return config.apiUrl.isNotBlank()
    }

    override suspend fun search(
        query: String,
        config: SearchConfig,
        round: Int
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {

        if (!isConfigured(config)) {
            return@withContext Result.failure(
                IllegalStateException("SearXNG API URL not configured")
            )
        }

        try {
            val baseUrl = config.apiUrl.trimEnd('/')
            val urlBuilder = ("$baseUrl/search").toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("format", "json")

            if (config.searchLanguage != "auto") {
                urlBuilder.addQueryParameter("language", config.searchLanguage)
            }
            if (config.searchRegion != "global") {
                urlBuilder.addQueryParameter("region", config.searchRegion)
            }
            if (config.enableSafeSearch) {
                urlBuilder.addQueryParameter("safesearch", "1")
            }

            val requestBuilder = Request.Builder()
                .url(urlBuilder.build())
                .header("Accept", "application/json")

            if (config.apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("SearXNG API error: ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(
                    IllegalStateException("Empty response from SearXNG")
                )

            val json = JSONObject(body)
            val resultsArray = json.optJSONArray("results") ?: return@withContext Result.success(emptyList())

            val results = mutableListOf<SearchResult>()
            for (i in 0 until resultsArray.length()) {
                if (i >= config.maxResults) break
                val item = resultsArray.getJSONObject(i)
                val title = item.optString("title", "")
                if (title.isBlank()) continue
                val url = item.optString("url", "")
                if (url.isBlank()) continue
                val snippet = item.optString("content", "")
                val publishedDate = item.optString("publishedDate").takeIf { it.isNotBlank() }

                results.add(
                    SearchResult(
                        title = title,
                        url = url,
                        snippet = snippet,
                        publishedDate = publishedDate,
                        score = 1f - (i.toFloat() / resultsArray.length().coerceAtLeast(1)),
                        searchRound = round
                    )
                )
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchContent(
        url: String,
        config: SearchConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/html, text/plain")
                .header("User-Agent", "LocalAISearch/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Fetch failed: ${response.code}")
                )
            }

            val contentType = response.header("Content-Type") ?: ""
            val body = response.body?.string() ?: ""

            // Basic HTML to text conversion
            val text = if (contentType.contains("text/html")) {
                body
                    .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            } else {
                body
            }

            // Limit to reasonable size to avoid context overflow
            val truncated = if (text.length > 8000) text.substring(0, 8000) else text
            Result.success(truncated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
