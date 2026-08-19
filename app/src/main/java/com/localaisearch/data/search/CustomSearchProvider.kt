package com.localaisearch.data.search

import com.localaisearch.data.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

import com.localaisearch.data.repository.NetworkClientFactory
/**
 * Custom search provider for user-defined API endpoints.
 *
 * Supports two response formats:
 * 1. SearXNG-compatible JSON ({ "results": [...] })
 * 2. Raw JSON array ([...])
 *
 * Each result item should have: title, url, content/snippet (optional)
 */
class CustomSearchProvider : SearchProvider {

    override val type: SearchProviderType = SearchProviderType.CUSTOM

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
                IllegalStateException("Custom API URL not configured")
            )
        }

        try {
            val baseUrl = config.apiUrl.trimEnd('/')
            val urlBuilder = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("format", "json")
                .addQueryParameter("count", config.maxResults.toString())

            if (config.searchLanguage != "auto") {
                urlBuilder.addQueryParameter("lang", config.searchLanguage)
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
                    IllegalStateException("Custom API error: ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(IllegalStateException("Empty response"))

            val results = parseResults(body, config.maxResults, round)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseResults(body: String, maxResults: Int, round: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        try {
            val json = JSONObject(body)
            val resultsArray: JSONArray = if (json.has("results")) {
                json.getJSONArray("results")
            } else if (json.has("web") && json.getJSONObject("web").has("results")) {
                json.getJSONObject("web").getJSONArray("results")
            } else if (json.has("value")) {
                json.getJSONArray("value")
            } else {
                JSONArray(body)
            }

            for (i in 0 until resultsArray.length()) {
                if (i >= maxResults) break
                val item = resultsArray.getJSONObject(i)
                val title = item.optString("title", item.optString("name", ""))
                if (title.isBlank()) continue
                val url = item.optString("url", item.optString("link", ""))
                if (url.isBlank()) continue
                val snippet = item.optString("content", item.optString("snippet", item.optString("description", "")))
                val publishedDate = item.optString("publishedDate", item.optString("datePublished")).takeIf { it.isNotBlank() }

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
        } catch (_: Exception) {
            // Try parsing as raw array
            try {
                val array = JSONArray(body)
                for (i in 0 until array.length()) {
                    if (i >= maxResults) break
                    val item = array.getJSONObject(i)
                    val title = item.optString("title", "")
                    if (title.isBlank()) continue
                    val url = item.optString("url", "")
                    if (url.isBlank()) continue
                    results.add(
                        SearchResult(
                            title = title,
                            url = url,
                            snippet = item.optString("content", item.optString("snippet", "")),
                            score = 1f - (i.toFloat() / array.length().coerceAtLeast(1)),
                            searchRound = round
                        )
                    )
                }
            } catch (_: Exception) {
                return emptyList()
            }
        }

        return results
    }

    override suspend fun fetchContent(
        url: String,
        config: SearchConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LocalAISearch/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Fetch failed: ${response.code}"))
            }

            val body = response.body?.string() ?: ""
            val text = body
                .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val truncated = if (text.length > 8000) text.substring(0, 8000) else text
            Result.success(truncated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
