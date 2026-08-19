package com.localaisearch.data.llm

import com.localaisearch.data.model.InferenceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible API provider (supports Ollama, vLLM, LM Studio, etc.)
 *
 * Usage: Set apiUrl to "http://localhost:11434/v1/chat/completions" for Ollama
 *        or "https://api.openai.com/v1/chat/completions" for OpenAI
 */
class OpenAILLMProvider(
    private var apiUrl: String = "",
    private var apiKey: String = "",
    private var modelName: String = ""
) : LLMEngine {
    override val providerName: String = "OpenAI-Compatible"
    override val providerType: LLMProviderType = LLMProviderType.OPENAI_COMPATIBLE
    override val isAvailable: Boolean get() = apiUrl.isNotBlank()
    override var isLoaded: Boolean = false
        private set
    override var loadedModelName: String? = null
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private var stopRequested = false

    fun configure(url: String, key: String = "", model: String = "") {
        apiUrl = url
        apiKey = key
        modelName = model
    }

    override suspend fun loadModel(filePath: String, config: InferenceConfig): Result<Unit> {
        return if (apiUrl.isNotBlank()) {
            loadedModelName = modelName.ifBlank { "OpenAI-Compatible" }
            isLoaded = true
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("API URL not configured"))
        }
    }

    override suspend fun unloadModel(): Result<Unit> {
        isLoaded = false
        loadedModelName = null
        return Result.success(Unit)
    }

    override fun generateStream(prompt: String, config: InferenceConfig): Flow<String> = flow {
        val messages = listOf("user" to prompt)
        emitAll(chatStream(messages, config))
    }.flowOn(Dispatchers.IO)

    override suspend fun generate(prompt: String, config: InferenceConfig): Result<String> {
        return try {
            val messages = listOf("user" to prompt)
            val response = chatCompletion(messages, config, stream = false)
            Result.success(response ?: "[No response]")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun chatStream(
        messages: List<Pair<String, String>>,
        config: InferenceConfig
    ): Flow<String> = flow {
        try {
            val reader = chatCompletionStream(messages, config)
            var buffer = ""
            reader?.use {
                while (true) {
                    if (stopRequested) break
                    val line = it.readLine() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        if (data == "[DONE]") break
                        val chunk = parseStreamChunk(data)
                        if (chunk.isNotEmpty()) {
                            buffer += chunk
                            // Emit word-by-word for smoother UI
                            val words = buffer.split(" ")
                            if (words.size > 1) {
                                for (i in 0 until words.size - 1) {
                                    emit(words[i] + " ")
                                }
                                buffer = words.last()
                            }
                        }
                    }
                }
                if (buffer.isNotEmpty()) emit(buffer)
            }
        } catch (e: Exception) {
            emit("[Error: ${e.message}]")
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun stopGeneration() {
        stopRequested = true
    }

    override fun getMemoryUsage(): Long? = null
    override fun isGpuAvailable(): Boolean = false
    override fun release() {}

    private fun chatCompletion(
        messages: List<Pair<String, String>>,
        config: InferenceConfig,
        stream: Boolean
    ): String? {
        val body = buildRequestBody(messages, config, stream)
        val request = Request.Builder()
            .url(apiUrl)
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.body?.string()}")
            }
            val jsonResponse = response.body?.string() ?: return null
            val jsonObject = json.parseToJsonElement(jsonResponse).jsonObject
            val choices = jsonObject["choices"]?.jsonArray ?: return null
            val firstChoice = choices.firstOrNull()?.jsonObject ?: return null
            return firstChoice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: firstChoice["delta"]?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: firstChoice["text"]?.jsonPrimitive?.content
        }
    }

    private fun chatCompletionStream(
        messages: List<Pair<String, String>>,
        config: InferenceConfig
    ): java.io.BufferedReader? {
        val body = buildRequestBody(messages, config, stream = true)
        val request = Request.Builder()
            .url(apiUrl)
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${response.body?.string()}")
        }
        return response.body?.byteStream()?.bufferedReader()
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun buildRequestBody(
        messages: List<Pair<String, String>>,
        config: InferenceConfig,
        stream: Boolean
    ): String {
        val messagesArray = messages.map { (role, content) ->
            buildJsonObject {
                put("role", role)
                put("content", content)
            }
        }
        val requestJson = buildJsonObject {
            put("model", modelName.ifBlank { "default" })
            putJsonArray("messages") { addAll(messagesArray) }
            put("temperature", config.temperature)
            put("top_p", config.topP)
            put("max_tokens", config.maxTokens)
            put("stream", stream)
        }
        return requestJson.toString()
    }

    private fun parseStreamChunk(data: String): String {
        return try {
            val jsonObject = json.parseToJsonElement(data).jsonObject
            val choices = jsonObject["choices"]?.jsonArray
            val first = choices?.firstOrNull()?.jsonObject
            val delta = first?.get("delta")?.jsonObject
            delta?.get("content")?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
