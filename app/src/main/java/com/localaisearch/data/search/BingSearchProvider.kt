package com.localaisearch.data.search

import com.localaisearch.data.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Bing Search API provider.
 * Uses Azure Bing Search API v7.
 * Endpoint: https://api.bing.microsoft.com/v7.0/search
 */
class BingSearchProvider : SearchProvider {

    override val type: SearchProviderType = SearchProviderType.BING

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun isConfigured(config: SearchConfig): Boolean {
        return config.apiKey.isNotBlank()
    }

    override suspend fun search(
        query: String,
        config: SearchConfig,
        round: Int
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {

        if (!isConfigured(config)) {
            return@withContext Result.failure(
                IllegalStateException("Bing Search requires API Key")
            )
        }

        try {
            val baseUrl = if (config.apiUrl.isNotBlank()) {
                config.apiUrl.trimEnd('/')
            } else {
                "https://api.bing.microsoft.com/v7.0"
            }

            val urlBuilder = ("$baseUrl/search").toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("count", config.maxResults.toString())

            if (config.searchLanguage != "auto") {
                urlBuilder.addQueryParameter("setLang", config.searchLanguage)
            }
            if (config.searchRegion != "global") {
                urlBuilder.addQueryParameter("cc", config.searchRegion)
            }
            if (config.enableSafeSearch) {
                urlBuilder.addQueryParameter("safeSearch", "Moderate")
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("Accept", "application/json")
                .header("Ocp-Apim-Subscription-Key", config.apiKey)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Bing API error: ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(IllegalStateException("Empty response"))

            val json = JSONObject(body)
            val webPages = json.optJSONObject("webPages")
                ?: return@withContext Result.success(emptyList())
            val valueArray = webPages.optJSONArray("value")
                ?: return@withContext Result.success(emptyList())

            val results = mutableListOf<SearchResult>()
            for (i in 0 until valueArray.length()) {
                val item = valueArray.getJSONObject(i)
                val title = item.optString("name", "")
                if (title.isBlank()) continue
                val url = item.optString("url", "")
                if (url.isBlank()) continue
                val snippet = item.optString("snippet", "")
                val datePublished = item.optString("datePublished", null)

                results.add(
                    SearchResult(
                        title = title,
                        url = url,
                        snippet = snippet,
                        publishedDate = datePublished,
                        score = 1f - (i.toFloat() / valueArray.length().coerceAtLeast(1)),
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
