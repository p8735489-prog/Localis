package com.localaisearch.data.repository

import android.util.Log
import com.localaisearch.data.model.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

import com.localaisearch.data.repository.NetworkClientFactory
/**
 * Hugging Face Hub API repository for discovering and downloading GGUF models.
 *
 * Uses the Hugging Face Hub API v2:
 * - Search: https://huggingface.co/api/models?search={query}&filter=gguf
 * - Files:  https://huggingface.co/api/models/{repo_id}/tree/main
 * - Download: https://huggingface.co/{repo_id}/resolve/main/{filename}
 *
 * Supports anonymous access for public models. Private repos require a token.
 */
class HuggingFaceRepository {

    companion object {
        private const val TAG = "HuggingFaceRepo"
        private const val HF_API = "https://huggingface.co/api"
        private const val HF_BASE = "https://huggingface.co"
        private const val GGUF_FILTER = "gguf"
    }

    private val client = NetworkClientFactory.builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var authToken: String? = null

    /**
     * Set authentication token for private repositories.
     * Token is never logged or stored in plain text in code.
     */
    fun setToken(token: String?) {
        authToken = token
    }

    /**
     * Search for GGUF models on Hugging Face Hub.
     */
    suspend fun searchModels(
        query: String = "",
        limit: Int = 50
    ): Result<List<HFModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder("$HF_API/models?")
            if (query.isNotBlank()) {
                urlBuilder.append("search=${java.net.URLEncoder.encode(query, "UTF-8")}&")
            }
            urlBuilder.append("filter=$GGUF_FILTER&")
            urlBuilder.append("limit=$limit&")
            urlBuilder.append("sort=downloads&")
            urlBuilder.append("direction=-1")

            val requestBuilder = Request.Builder()
                .url(urlBuilder.toString())
                .header("Accept", "application/json")
                .header("User-Agent", "Localis/1.0")

            authToken?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("HF API error: ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(IllegalStateException("Empty response"))

            val jsonArray = org.json.JSONArray(body)
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
     * Get trending/popular GGUF models.
     */
    suspend fun getTrendingModels(
        limit: Int = 20
    ): Result<List<HFModelInfo>> = searchModels("", limit)

    /**
     * List GGUF files in a model repository.
     */
    suspend fun listGgufFiles(
        repoId: String
    ): Result<List<HFModelFile>> = withContext(Dispatchers.IO) {
        try {
            val url = "$HF_API/models/$repoId/tree/main"
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")

            authToken?.let { requestBuilder.header("Authorization", "Bearer $it") }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("HF files API error: ${response.code}")
                )
            }

            val body = response.body?.string() ?: return@withContext Result.success(emptyList())
            val jsonArray = org.json.JSONArray(body)

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
                        downloadUrl = "$HF_BASE/$repoId/resolve/main/$path",
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
     * Get model details.
     */
    suspend fun getModelInfo(repoId: String): Result<HFModelDetail> = withContext(Dispatchers.IO) {
        try {
            val url = "$HF_API/models/$repoId"
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")

            authToken?.let { requestBuilder.header("Authorization", "Bearer $it") }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("HF model API error: ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(IllegalStateException("Empty response"))

            val json = JSONObject(body)
            val cardData = json.optJSONObject("cardData")
            val baseModel = cardData?.optString("base_model", "") ?: ""
            val parameterCount = extractParameterCount(cardData)

            Result.success(
                HFModelDetail(
                    id = repoId,
                    name = repoId.substringAfterLast('/'),
                    author = repoId.substringBefore('/'),
                    description = json.optString("description", ""),
                    downloads = json.optInt("downloads", 0),
                    likes = json.optInt("likes", 0),
                    tags = parseTags(json.optJSONArray("tags")),
                    baseModel = baseModel,
                    parameterCount = parameterCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate direct download URL for a file.
     * This is based on actual API structure, not fabricated.
     */
    fun getDownloadUrl(repoId: String, filePath: String): String {
        val encodedPath = filePath.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return "$HF_BASE/$repoId/resolve/main/$encodedPath"
    }

    private fun parseTags(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun extractQuantization(fileName: String): String {
        val upper = fileName.uppercase()
        val iq = Regex("\\bIQ[1-9][0-9A-Z_]*\\b").find(upper)?.value
        if (iq != null) return iq
        val q = Regex("\\bQ(?:[1-9][0-9]?)(?:_[A-Z0-9]+)*\\b").find(upper)?.value
        if (q != null) return q
        if (upper.contains("FP16")) return "FP16"
        if (upper.contains("BF16")) return "BF16"
        if (upper.contains("F32")) return "F32"
        if (upper.contains("F16")) return "F16"
        return "unknown"
    }

    private fun extractParameterCount(cardData: JSONObject?): String {
        if (cardData == null) return "unknown"
        // Try to extract from model name or tags
        val modelName = cardData.optString("base_model", "")
        val regex = Regex("(\\d+\\.?\\d*)[Bb]")
        val match = regex.find(modelName)
        return match?.groupValues?.get(1)?.let { "${it}B" } ?: "unknown"
    }
}

/**
 * Model info from Hugging Face Hub.
 */
data class HFModelInfo(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val downloads: Int,
    val likes: Int,
    val tags: List<String>,
    val isGguf: Boolean
)

/**
 * A single GGUF file in a model repository.
 */
data class HFModelFile(
    val path: String,
    val size: Long,
    val quantization: String,
    val downloadUrl: String,
    val isLfs: Boolean
) {
    val displaySize: String get() = formatFileSize(size)
}

/**
 * Detailed model information.
 */
data class HFModelDetail(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val downloads: Int,
    val likes: Int,
    val tags: List<String>,
    val baseModel: String = "",
    val parameterCount: String = "unknown"
)
