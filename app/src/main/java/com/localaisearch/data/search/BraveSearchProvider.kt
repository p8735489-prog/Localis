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
 * Brave Search API provider.
 * API docs: https://api-dashboard.search.brave.com/
 * Endpoint: https://api.search.brave.com/res/v1/web/search
 */
class BraveSearchProvider : SearchProvider {

    override val type: SearchProviderType = SearchProviderType.BRAVE

    private val client by lazy {
        NetworkClientFactory.builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun isConfigured(config: SearchConfig): Boolean {
        return config.apiKey.isNotBlank() && config.apiUrl.isNotBlank()
    }

    override suspend fun search(
        query: String,
        config: SearchConfig,
        round: Int
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {

        if (!isConfigured(config)) {
            return@withContext Result.failure(
                IllegalStateException("Brave Search requires API Key and URL")
            )
        }

        try {
            val baseUrl = config.apiUrl.trimEnd('/')
            val urlBuilder = ("$baseUrl/res/v1/web/search").toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("count", config.maxResults.toString())

            if (config.searchLanguage != "auto") {
                urlBuilder.addQueryParameter("search_lang", config.searchLanguage)
            }
            if (config.searchRegion != "global") {
                urlBuilder.addQueryParameter("country", config.searchRegion)
            }
            if (config.enableSafeSearch) {
                urlBuilder.addQueryParameter("safesearch", "moderate")
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .header("X-Subscription-Token", config.apiKey)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Brave API error: ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(IllegalStateException("Empty response"))

            val json = JSONObject(body)
            val web = json.optJSONObject("web")
                ?: return@withContext Result.success(emptyList())
            val resultsArray = web.optJSONArray("results")
                ?: return@withContext Result.success(emptyList())

            val results = mutableListOf<SearchResult>()
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val title = item.optString("title", "")
                if (title.isBlank()) continue
                val url = item.optString("url", "")
                if (url.isBlank()) continue
                val snippet = item.optString("description", "")
                val age = item.optString("age").takeIf { it.isNotBlank() }

                results.add(
                    SearchResult(
                        title = title,
                        url = url,
                        snippet = snippet,
                        publishedDate = age,
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
