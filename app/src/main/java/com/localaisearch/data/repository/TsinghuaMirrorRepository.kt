package com.localaisearch.data.repository

import android.util.Log
import com.localaisearch.data.model.GGUFModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Tsinghua University mirror of Hugging Face.
 *
 * API endpoint: https://hf-mirror.com (replaces huggingface.co)
 * The API structure is identical to Hugging Face Hub but served from a mirror.
 */
class TsinghuaMirrorRepository {

    companion object {
        private const val TAG = "TsinghuaMirrorRepo"
        private const val MIRROR_API = "https://hf-mirror.com/api"
        private const val MIRROR_BASE = "https://hf-mirror.com"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Search for GGUF models via the Tsinghua mirror.
     */
    suspend fun searchModels(
        query: String = "",
        limit: Int = 50
    ): Result<List<HFModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder("$MIRROR_API/models?")
            if (query.isNotBlank()) {
                urlBuilder.append("search=${java.net.URLEncoder.encode(query, "UTF-8")}&")
            }
            urlBuilder.append("filter=gguf&")
            urlBuilder.append("limit=$limit&")
            urlBuilder.append("sort=downloads&")
            urlBuilder.append("direction=-1")

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("Accept", "application/json")
                .header("User-Agent", "Localis/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Mirror API error: ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(IllegalStateException("Empty response"))

            val jsonArray = JSONArray(body)
            val models = mutableListOf<HFModelInfo>()

            for (i in 0 until jsonArray.length().coerceAtMost(limit)) {
                val item = jsonArray.getJSONObject(i)
                val modelId = item.optString("id", "")
                if (modelId.isBlank()) continue

                models.add(
                    HFModelInfo(
                        id = modelId,
                        name = modelId.substringAfterLast('/'),
                        author = modelId.substringBefore('/'),
                        description = item.optString("description", ""),
                        downloads = item.optInt("downloads", 0),
                        likes = item.optInt("likes", 0),
                        tags = parseTags(item.optJSONArray("tags")),
                        isGguf = true
                    )
                )
            }

            Result.success(models)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            Result.failure(e)
        }
    }

    /**
     * Get trending/popular GGUF models from the mirror.
     */
    suspend fun getTrendingModels(limit: Int = 20): Result<List<HFModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val url = "$MIRROR_API/models?filter=gguf&limit=$limit&sort=downloads&direction=-1"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Localis/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Mirror API error: ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(IllegalStateException("Empty response"))

            val jsonArray = JSONArray(body)
            val models = mutableListOf<HFModelInfo>()

            for (i in 0 until jsonArray.length().coerceAtMost(limit)) {
                val item = jsonArray.getJSONObject(i)
                val modelId = item.optString("id", "")
                if (modelId.isBlank()) continue

                models.add(
                    HFModelInfo(
                        id = modelId,
                        name = modelId.substringAfterLast('/'),
                        author = modelId.substringBefore('/'),
                        description = item.optString("description", ""),
                        downloads = item.optInt("downloads", 0),
                        likes = item.optInt("likes", 0),
                        tags = parseTags(item.optJSONArray("tags")),
                        isGguf = true
                    )
                )
            }

            Result.success(models)
        } catch (e: Exception) {
            Log.e(TAG, "Trending models failed", e)
            Result.failure(e)
        }
    }

    /**
     * List GGUF files in a repository via the mirror.
     */
    suspend fun listGgufFiles(repoId: String): Result<List<HFModelFile>> = withContext(Dispatchers.IO) {
        try {
            val url = "$MIRROR_API/models/$repoId/tree/main"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Mirror files API error: ${response.code}")
                )
            }

            val body = response.body?.string() ?: return@withContext Result.success(emptyList())
            val jsonArray = JSONArray(body)

            val files = mutableListOf<HFModelFile>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val path = item.optString("path", "")
                if (!path.endsWith(".gguf", ignoreCase = true)) continue

                val size = item.optLong("size", 0)
                val quantization = extractQuantization(path)

                files.add(
                    HFModelFile(
                        path = path,
                        size = size,
                        quantization = quantization,
                        downloadUrl = "$MIRROR_BASE/$repoId/resolve/main/$path",
                        isLfs = item.optBoolean("lfs", true)
                    )
                )
            }

            Result.success(files.sortedByDescending { it.size })
        } catch (e: Exception) {
            Log.e(TAG, "List files failed for $repoId", e)
            Result.failure(e)
        }
    }

    /**
     * Get model details from the mirror.
     */
    suspend fun getModelInfo(repoId: String): Result<HFModelDetail> = withContext(Dispatchers.IO) {
        try {
            val url = "$MIRROR_API/models/$repoId"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Mirror model API error: ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(IllegalStateException("Empty response"))

            val json = JSONObject(body)
            val cardData = json.optJSONObject("cardData")
            val baseModel = cardData?.optString("base_model", "") ?: ""

            Result.success(
                HFModelDetail(
                    id = repoId,
                    name = repoId.substringAfterLast('/'),
                    author = repoId.substringBefore('/'),
                    description = json.optString("description", ""),
                    downloads = json.optInt("downloads", 0),
                    likes = json.optInt("likes", 0),
                    tags = parseTags(json.optJSONArray("tags")),
                    baseModel = baseModel
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate download URL using the mirror.
     */
    fun getDownloadUrl(repoId: String, filePath: String): String {
        return "$MIRROR_BASE/$repoId/resolve/main/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
    }

    private fun parseTags(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun extractQuantization(fileName: String): String {
        val patterns = listOf("Q4_K_M", "Q5_K_M", "Q6_K", "Q8_0", "Q4_0", "Q5_0", "Q2_K", "Q3_K_M", "Q4_K_S", "Q5_K_S", "IQ4_XS", "IQ3_M", "FP16")
        for (pattern in patterns) {
            if (fileName.contains(pattern, ignoreCase = true)) {
                return pattern.uppercase()
            }
        }
        return "unknown"
    }
}

/**
 * Factory to select the appropriate repository based on user preference.
 */
object ModelRepositoryFactory {
    enum class Source { HUGGING_FACE, TSINGHUA_MIRROR }

    fun create(source: Source): Any = when (source) {
        Source.HUGGING_FACE -> HuggingFaceRepository()
        Source.TSINGHUA_MIRROR -> TsinghuaMirrorRepository()
    }
}
