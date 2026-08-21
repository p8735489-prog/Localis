package com.localaisearch.ui.viewmodel

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localaisearch.data.agent.AgentCallback
import com.localaisearch.data.agent.AgentEngine
import com.localaisearch.data.llm.GGUFEngine
import com.localaisearch.data.llm.LlamaBridge
import com.localaisearch.data.llm.LLMEngine
import com.localaisearch.data.model.AgentStatus
import com.localaisearch.data.model.AgentStatusIdle
import com.localaisearch.data.model.ChatMessage
import com.localaisearch.data.model.Conversation
import com.localaisearch.data.model.Citation
import com.localaisearch.data.model.InferenceConfig
import com.localaisearch.data.model.MessageRole
import com.localaisearch.data.model.SearchRound
import com.localaisearch.data.model.SearchResult
import com.localaisearch.data.model.SearchSession
import com.localaisearch.ui.components.ReasoningMode
import com.localaisearch.data.model.GGUFMetadataReader
import com.localaisearch.data.performance.AutoModeConfig
import com.localaisearch.data.performance.AutoModeConfigDefault
import com.localaisearch.data.performance.AutoModeEngine
import com.localaisearch.data.performance.ContextSummarizer
import com.localaisearch.data.repository.ConversationRepository
import com.localaisearch.data.repository.MemoryRepository
import com.localaisearch.data.repository.ModelRepository
import com.localaisearch.data.repository.AppModelRepository
import com.localaisearch.data.repository.PrivacyManager
import com.localaisearch.data.repository.SearchRepository
import com.localaisearch.data.repository.SettingsRepository
import com.localaisearch.data.search.SearchConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * ViewModel for the chat/search screen.
 *
 * Orchestrates the AgentEngine and manages conversation state.
 * Integrates ConversationRepository for auto-save, MemoryRepository
 * for context injection, PrivacyManager for privacy sessions,
 * ContextSummarizer for long conversation optimization, and
 * AutoModeEngine for automatic capability selection.
 *
 * Privacy session has the highest priority: when active, no data
 * is persisted, no memory is read/written, and no summaries are
 * generated.
 */
class ChatViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val searchRepo = SearchRepository()
    val modelRepo = AppModelRepository.get(application)
    // Chat and model-center must use the exact same process-scoped engine. Creating a
    // second JNI engine here was a major source of stale model state and native races.
    private val llmEngine: LLMEngine = modelRepo.engine

    // -- Integrated Repositories & Managers --
    private val conversationRepo = ConversationRepository(application)
    private val memoryRepo = MemoryRepository(application)
    val privacyManager = PrivacyManager(settingsRepo)
    private val contextSummarizer = ContextSummarizer(llmEngine)
    private val autoModeEngine = AutoModeEngine()

    private var agentEngine: AgentEngine? = null
    private var currentJob: Job? = null
    /** Monotonically increasing request id used to invalidate stale callbacks. */
    private val requestGeneration = AtomicLong(0L)
    private var lastModelActivityTime: Long = System.currentTimeMillis()
    private var _lastSaveTime: Long = 0L
    private val answerBuffer = StringBuilder()
    private var lastAnswerUiUpdate = 0L
    private var answerFlushJob: Job? = null
    private var _lastSavedContentHash: Int = 0

    // -- UI State --

    private val _conversation = MutableStateFlow(Conversation())
    val conversation: StateFlow<Conversation> = _conversation.asStateFlow()

    private val _agentStatus = MutableStateFlow(AgentStatusIdle)
    val agentStatus: StateFlow<AgentStatus> = _agentStatus.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _currentAnswer = MutableStateFlow("")
    val currentAnswer: StateFlow<String> = _currentAnswer.asStateFlow()

    private val _citations = MutableStateFlow<List<Citation>>(emptyList())
    val citations: StateFlow<List<Citation>> = _citations.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _modelLoaded = MutableStateFlow(llmEngine.isLoaded)
    val modelLoaded: StateFlow<Boolean> = _modelLoaded.asStateFlow()

    private val _loadedModelName = MutableStateFlow(llmEngine.loadedModelName ?: "")
    val loadedModelName: StateFlow<String> = _loadedModelName.asStateFlow()

    // Image input is available only when the active model is vision-capable and a
    // matching vision projector (mmproj) is present. The chat composer uses this
    // single capability state so UI, model selection and image processing do not
    // drift apart.
    private val _imageInputAvailable = MutableStateFlow(false)
    val imageInputAvailable: StateFlow<Boolean> = _imageInputAvailable.asStateFlow()

    private val _reasoningMode = MutableStateFlow(ReasoningMode.THINKING)
    val reasoningMode: StateFlow<ReasoningMode> = _reasoningMode.asStateFlow()

    private val _defaultSystemPrompt = MutableStateFlow("general")
    val defaultSystemPrompt: StateFlow<String> = _defaultSystemPrompt.asStateFlow()

    private val _internetSearchEnabled = MutableStateFlow(false)
    val internetSearchEnabled: StateFlow<Boolean> = _internetSearchEnabled.asStateFlow()

    // -- Privacy & Auto Mode State --

    val isPrivacyMode: StateFlow<Boolean> = privacyManager.isPrivacyMode

    private val _isAutoModeEnabled = MutableStateFlow(false)
    val isAutoModeEnabled: StateFlow<Boolean> = _isAutoModeEnabled.asStateFlow()

    private val _privacySessionEnded = MutableStateFlow(false)
    val privacySessionEnded: StateFlow<Boolean> = _privacySessionEnded.asStateFlow()

    // -- Context optimization state --

    private val _contextSummary = MutableStateFlow<String?>(null)
    val contextSummary: StateFlow<String?> = _contextSummary.asStateFlow()

    init {
        // Observe the shared repository rather than polling an independent engine.
        // This makes the home header update immediately after returning from Model Center.
        viewModelScope.launch {
            modelRepo.activeModel.collect { active ->
                _modelLoaded.value = active != null && llmEngine.isLoaded
                _loadedModelName.value = active?.name ?: llmEngine.loadedModelName.orEmpty()
            }
        }
        viewModelScope.launch {
            _internetSearchEnabled.value = settingsRepo.internetSearchEnabled.first()
            _defaultSystemPrompt.value = settingsRepo.defaultSystemPrompt.first()
            _reasoningMode.value = when (settingsRepo.reasoningMode.first().lowercase()) {
                "off" -> ReasoningMode.OFF
                else -> ReasoningMode.THINKING
            }
        }
        // Model loading is performed by the model screen's ViewModel, but the engine is shared.
        // Poll only this tiny state so Home reflects load/unload without recreating the engine.
        viewModelScope.launch {
            var lastLoaded = false
            var lastName = ""
            while (true) {
                val loaded = llmEngine.isLoaded
                val name = llmEngine.loadedModelName ?: ""
                if (loaded != lastLoaded || name != lastName) {
                    _modelLoaded.value = loaded
                    _loadedModelName.value = name
                    lastLoaded = loaded
                    lastName = name
                }
                _imageInputAvailable.value = if (loaded) detectVisionInputReady() else false
                kotlinx.coroutines.delay(350)
            }
        }
    }

    /**
     * Send a query through the agent pipeline.
     *
     * Integrates:
     * - Privacy check: skip all persistence if privacy mode is on
     * - Memory injection: load relevant memories into context
     * - Context optimization: summarize old messages if near token limit
     * - Auto-save: persist conversation after completion (non-privacy)
     */
    private fun findVisionProjectorPath(): String? {
        val loadedPath = (llmEngine as? GGUFEngine)?.loadedModelPath() ?: return null
        return runCatching {
            val metadata = GGUFMetadataReader.readMetadata(loadedPath)
            if (!metadata.hasVision) return null
            val dir = java.io.File(loadedPath).parentFile ?: return null
            val stem = java.io.File(loadedPath).nameWithoutExtension.lowercase()
            dir.listFiles { file ->
                file.isFile && file.extension.equals("gguf", ignoreCase = true) &&
                    file.name.lowercase().contains("mmproj") &&
                    visionProjectorMatches(file.name, stem)
            }?.sortedByDescending { it.length() }?.firstOrNull()?.absolutePath
        }.getOrNull()
    }

    private fun visionProjectorMatches(fileName: String, modelStem: String): Boolean {
        val name = fileName.lowercase().removePrefix("mmproj-").removePrefix("mmproj_")
        val stem = modelStem.lowercase()
        return name.contains(stem) || stem.contains(name.substringBefore("-f16")) ||
            stem.contains(name.substringBefore("-f32")) || name.length < 16
    }

    private fun detectVisionInputReady(): Boolean = findVisionProjectorPath() != null

    fun setDefaultSystemPrompt(promptId: String) {
        _defaultSystemPrompt.value = promptId
        viewModelScope.launch { settingsRepo.setDefaultSystemPrompt(promptId) }
    }

    fun getSystemPromptText(): String = when (_defaultSystemPrompt.value) {
        "concise" -> "你是一名简洁高效的 AI 助手。优先直接回答问题，避免无关铺垫；信息不足时明确说明。"
        "precise" -> "你是一名严谨的 AI 助手。区分事实、推测和不确定信息；不要编造不存在的内容。"
        "coding" -> "你是一名专业编程助手。优先给出可执行、可靠的解决方案；代码应清晰、完整，并解释关键修改。"
        "chinese" -> "你是一名中文 AI 助手。默认使用自然、准确、易懂的简体中文回答；遇到专业术语时保留必要英文。"
        else -> "你是一名友好、可靠、通用的 AI 助手。请准确理解用户意图，并给出有帮助、清晰的回答。"
    }

    private val _pendingImageUri = MutableStateFlow<android.net.Uri?>(null)
    val pendingImageUri: StateFlow<android.net.Uri?> = _pendingImageUri.asStateFlow()

    fun setPendingImage(uri: android.net.Uri) {
        if (_imageInputAvailable.value) _pendingImageUri.value = uri
    }

    /**
     * Optional dedicated vision fallback. If the chat GGUF cannot see images, a
     * separately configured vision GGUF can temporarily take over the native engine,
     * describe the image, then the original chat model is restored before answering.
     * This is deliberately opt-in because two GGUF models cannot safely coexist in
     * the current Android native bridge and loading both would multiply RAM usage.
     */
    private suspend fun runDedicatedVisionFallback(
        imageUri: android.net.Uri,
        userPrompt: String,
        config: InferenceConfig,
        requestId: Long
    ): String {
        val local = llmEngine as? GGUFEngine
            ?: throw IllegalStateException("当前引擎不支持专用视觉识别")
        val visionPath = settingsRepo.visionFallbackModelPath.first().trim()
        if (visionPath.isBlank()) {
            throw IllegalStateException("当前对话模型没有视觉能力，请在设置 → AI 与模型 → 视觉识别中选择专用识别模型")
        }
        val primaryPath = local.loadedModelPath()
            ?: throw IllegalStateException("当前对话模型尚未加载")
        if (primaryPath == visionPath) {
            throw IllegalStateException("专用视觉模型不能与当前对话模型相同，请选择另一模型")
        }
        val primaryConfig = local.loadedModelConfig() ?: config
        if (!java.io.File(visionPath).isFile) {
            throw IllegalStateException("设置中的专用视觉模型文件不存在，请重新选择模型")
        }
        val projector = findProjectorForModel(visionPath)
            ?: throw IllegalStateException("专用视觉模型缺少匹配的 mmproj，请先下载视觉投影器")

        local.unloadModel().getOrThrow()
        var primaryFailure: Throwable? = null
        try {
            local.loadModel(visionPath, config.copy(contextLength = config.contextLength.coerceAtMost(4096), useGpu = false, gpuLayers = 0)).getOrThrow()
            local.loadVisionProjector(projector, config).getOrThrow()
            val bytes = loadImageBytesSafely(imageUri)
            require(bytes.isNotEmpty()) { "图片为空" }
            val history = listOf("user" to "<__media__>\n${userPrompt.ifBlank { "请详细描述这张图片，包括主要对象、文字、场景和关键细节。" }}")
            val formatted = LlamaBridge.nativeFormatChat(
                local.currentHandleForMultimodal(),
                history.map { it.first }.toTypedArray(),
                history.map { it.second }.toTypedArray(),
                config.thinkingEnabled
            )
            require(formatted.isNotBlank()) { "专用视觉模型没有可用的聊天模板" }
            val out = StringBuilder()
            local.generateMultimodalStream(formatted, bytes, config.copy(maxTokens = minOf(config.maxTokens, 768))).collect { token ->
                if (requestGeneration.get() == requestId) out.append(token)
            }
            return out.toString().trim().ifBlank { throw IllegalStateException("专用视觉模型没有返回识别结果") }
        } catch (t: Throwable) {
            primaryFailure = t
            throw t
        } finally {
            runCatching { local.unloadModel() }
            val restore = runCatching { local.loadModel(primaryPath, primaryConfig).getOrThrow() }
            if (restore.isFailure && primaryFailure == null) throw restore.exceptionOrNull()!!
        }
    }

    /**
     * Bound image memory before entering JNI/mtmd. Camera photos can be tens of MB;
     * reading them directly with readBytes() can kill the Android process before the
     * native layer can return a controlled error.
     */
    private suspend fun loadImageBytesSafely(uri: android.net.Uri): ByteArray =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IllegalArgumentException("无法识别图片格式或图片已损坏")
            }
            val maxDimension = 2048
            var sample = 1
            while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: throw IllegalStateException("无法读取所选图片")
            try {
                if (bitmap.width <= 0 || bitmap.height <= 0) throw IllegalArgumentException("所选图片为空")
                val output = ByteArrayOutputStream(2 * 1024 * 1024)
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                    throw IllegalStateException("无法准备图片用于本地视觉模型")
                }
                val bytes = output.toByteArray()
                if (bytes.isEmpty() || bytes.size > 12 * 1024 * 1024) {
                    throw IllegalStateException("图片过大，无法安全交给本地视觉模型。请使用较小的图片")
                }
                bytes
            } finally {
                bitmap.recycle()
            }
        }

    private fun findProjectorForModel(modelPath: String): String? {
        val file = java.io.File(modelPath)
        val dir = file.parentFile ?: return null
        val stem = file.nameWithoutExtension.lowercase()
        return dir.listFiles { candidate ->
            candidate.isFile && candidate.extension.equals("gguf", true) &&
                candidate.name.lowercase().contains("mmproj") &&
                visionProjectorMatches(candidate.name, stem)
        }?.sortedByDescending { it.length() }?.firstOrNull()?.absolutePath
    }

    /**
     * Coding is inferred from the user's actual request; it is not a separate
     * user-facing reasoning preset. This keeps the input bar simple while still
     * allowing requests such as "写代码" or "fix this Kotlin bug" to receive the
     * coding-oriented system prompt.
     */
    private fun isCodingRequest(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return false
        val direct = listOf(
            "写代码", "写个代码", "写一段代码", "帮我写代码", "帮我写个程序",
            "编写代码", "编写程序", "实现代码", "给我代码", "代码实现",
            "修复代码", "修复bug", "修复这个bug", "调试代码", "改代码",
            "写程序", "编程", "coding", "write code", "code this", "implement",
            "debug this", "fix this bug", "programming task"
        )
        if (direct.any(q::contains)) return true
        val language = listOf("kotlin", "java", "python", "javascript", "typescript", "c++", "c#", "rust", "go", "swift", "xml", "sql", "bash", "shell")
        val action = listOf("写", "编写", "实现", "修改", "修复", "调试", "生成", "创建", "转换", "implement", "fix", "debug", "create", "modify")
        return language.any(q::contains) && action.any(q::contains)
    }

    fun setReasoningMode(mode: ReasoningMode) {
        _reasoningMode.value = mode
        viewModelScope.launch {
            settingsRepo.setReasoningMode(mode.id)
            settingsRepo.setThinkingEnabled(mode != ReasoningMode.OFF)
        }
    }

    fun sendQuery(query: String, addUserMessage: Boolean = true) {
        val hasPendingImage = _pendingImageUri.value != null
        if ((query.isBlank() && !hasPendingImage) || _isProcessing.value) return
        if (!llmEngine.isLoaded) {
            _error.value = "No model loaded. Please import and load a GGUF model first."
            return
        }

        currentJob?.cancel()
        val requestId = requestGeneration.incrementAndGet()
        _error.value = null
        synchronized(answerBuffer) { answerBuffer.setLength(0) }
        lastAnswerUiUpdate = 0L
        answerFlushJob?.cancel()
        synchronized(answerBuffer) { answerBuffer.setLength(0) }
        _currentAnswer.value = ""
        _citations.value = emptyList()
        _searchResults.value = emptyList()
        _isProcessing.value = true
        _privacySessionEnded.value = false
        lastModelActivityTime = System.currentTimeMillis()

        // Add user message unless this is a regeneration of an existing user turn.
        if (addUserMessage) {
            val userMessage = ChatMessage(role = MessageRole.USER, content = query.ifBlank { "图片" })
            _conversation.value = _conversation.value.addMessage(userMessage)
        }

        // Add placeholder assistant message for streaming
        val assistantMessage = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        _conversation.value = _conversation.value.addMessage(assistantMessage)

        currentJob = viewModelScope.launch {
            try {
                val storedConfig = settingsRepo.inferenceConfig.first()
                val mode = _reasoningMode.value
                val inferenceConfig = storedConfig.copy(thinkingEnabled = mode != ReasoningMode.OFF)
                val searchConfig = settingsRepo.searchConfig.first()
                searchRepo.updateConfig(searchConfig)

                agentEngine = AgentEngine(llmEngine, searchConfig)
                val codingRequest = isCodingRequest(query)
                val activeSystemPrompt = if (codingRequest) {
                    getSystemPromptText() + "\n\nThe user is asking for a programming/code task. Analyze the request carefully, then provide correct, executable code with concise explanations. Preserve code in fenced code blocks. Do not enter coding mode unless the user's request actually asks for code, implementation, debugging, or a programming task."
                } else {
                    getSystemPromptText()
                }

                // -- Build optimized context --
                val allMessages = _conversation.value.messages
                    .dropLast(2) // Exclude current user message and placeholder

                // Inject relevant memories if allowed
                val memoryContext = buildMemoryContext(query)

                // Check if context needs summarization
                val maxTokens = inferenceConfig.contextLength
                val contextResult = contextSummarizer.buildOptimizedContext(
                    messages = allMessages,
                    maxTokens = maxTokens,
                    includeSummary = privacyManager.canGenerateSummary()
                )

                if (contextResult.summaryMessage != null) {
                    _contextSummary.value = contextResult.summaryMessage.content
                }

                // Build history with memory + summary + recent messages
                val history = buildHistoryWithMemory(
                    memoryContext = memoryContext,
                    summaryContent = contextResult.summaryMessage?.content,
                    selectedMessages = contextResult.selectedMessages
                )

                // -- Determine search behavior --
                val enableSearch = determineSearchBehavior(query, searchConfig)

                val reasoningParser = ReasoningStreamParser(enabled = inferenceConfig.thinkingEnabled)
                updateLastMessage { it.copy(reasoningContent = "", isThinking = inferenceConfig.thinkingEnabled) }

                val callback = object : AgentCallback {
                    override fun onStateChanged(status: AgentStatus) {
                        if (requestGeneration.get() != requestId) return
                        _agentStatus.value = status
                        updateLastMessage { it.copy(agentStatus = status) }
                    }

                    override fun onSearchRound(round: SearchRound) {
                        if (requestGeneration.get() != requestId) return
                        _searchResults.value = _searchResults.value + round.results
                    }

                    override fun onSearchResults(results: List<SearchResult>) {
                        if (requestGeneration.get() != requestId) return
                        _searchResults.value = results
                    }

                    override fun onToken(token: String) {
                        if (requestGeneration.get() != requestId) return
                        val events = reasoningParser.feed(token)
                        for (event in events) {
                            when (event) {
                                is ReasoningStreamParser.Event.Thinking -> {
                                    updateLastMessage { it.copy(reasoningContent = it.reasoningContent + event.text, isThinking = true) }
                                }
                                is ReasoningStreamParser.Event.Answer -> {
                                    synchronized(answerBuffer) { answerBuffer.append(event.text) }
                                    updateLastMessage { it.copy(content = it.content + event.text, isThinking = false) }
                                }
                            }
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastAnswerUiUpdate >= 50L && answerFlushJob?.isActive != true) {
                            answerFlushJob = viewModelScope.launch {
                                kotlinx.coroutines.delay(16L)
                                if (requestGeneration.get() != requestId) return@launch
                                val snapshot = synchronized(answerBuffer) { answerBuffer.toString() }
                                _currentAnswer.value = snapshot
                                lastAnswerUiUpdate = System.currentTimeMillis()
                            }
                        }
                    }

                    override fun onAnswer(
                        answer: String,
                        citations: List<Citation>,
                        session: SearchSession
                    ) {
                        if (requestGeneration.get() != requestId) return
                        answerFlushJob?.cancel()
                        reasoningParser.finish().forEach { event ->
                            when (event) {
                                is ReasoningStreamParser.Event.Thinking -> updateLastMessage { it.copy(reasoningContent = it.reasoningContent + event.text) }
                                is ReasoningStreamParser.Event.Answer -> synchronized(answerBuffer) { answerBuffer.append(event.text) }
                            }
                        }
                        val rawFinal = if (answer.isNotBlank()) answer else synchronized(answerBuffer) { answerBuffer.toString() }
                        val finalAnswer = stripReasoningMarkers(rawFinal)
                        _currentAnswer.value = finalAnswer
                        _citations.value = citations
                        updateLastMessage {
                            it.copy(
                                content = finalAnswer,
                                citations = citations,
                                isThinking = false,
                                isStreaming = false,
                                searchSession = session
                            )
                        }
                    }

                    override fun onError(message: String) {
                        if (requestGeneration.get() != requestId) return
                        _error.value = message
                        updateLastMessage {
                            it.copy(
                                content = it.content.ifBlank { "Error: $message" },
                                isStreaming = false
                            )
                        }
                    }
                }

                agentEngine?.setCallback(callback)

                val pendingImage = _pendingImageUri.value
                val localEngine = llmEngine as? GGUFEngine
                if (pendingImage != null) {
                    // Image conversations use the real llama.cpp mtmd path. We intentionally
                    // do not route them through AgentEngine's text-only search pipeline,
                    // because doing so would strip the pixels before the final answer.
                    if (localEngine == null) {
                        throw IllegalStateException("当前引擎不支持本地视觉推理")
                    }
                    val projectorPath = findVisionProjectorPath()
                    if (projectorPath == null) {
                        val visionAnswer = runDedicatedVisionFallback(
                            imageUri = pendingImage,
                            userPrompt = query,
                            config = inferenceConfig,
                            requestId = requestId
                        )
                        if (requestGeneration.get() != requestId) return@launch
                        // Feed the dedicated recognizer's text result back into the
                        // currently restored language model. The user still sees one
                        // coherent assistant response rather than a second model bubble.
                        agentEngine?.execute(
                            query = if (query.isBlank()) "请根据以下图片识别结果回答用户：\n$visionAnswer" else "$query\n\n[图片识别结果]\n$visionAnswer",
                            config = inferenceConfig,
                            enableSearch = false,
                            conversationHistory = history,
                            systemPrompt = activeSystemPrompt
                        )?.collect { }
                        _pendingImageUri.value = null
                    } else {
                    localEngine.loadVisionProjector(projectorPath, inferenceConfig).getOrThrow()

                    val imageBytes = loadImageBytesSafely(pendingImage)
                    if (imageBytes.isEmpty()) throw IllegalStateException("所选图片为空")

                    val multimodalMessages = buildList {
                        if (getSystemPromptText().isNotBlank()) add("system" to getSystemPromptText())
                        addAll(history)
                        add("user" to "<__media__>\n$query")
                    }
                    val formattedPrompt = LlamaBridge.nativeFormatChat(
                        localEngine.currentHandleForMultimodal(),
                        multimodalMessages.map { it.first }.toTypedArray(),
                        multimodalMessages.map { it.second }.toTypedArray(),
                        inferenceConfig.thinkingEnabled
                    )
                    if (formattedPrompt.isBlank()) {
                        throw IllegalStateException("当前 GGUF 模型没有可用的视觉聊天模板")
                    }
                    val visionReasoningParser = ReasoningStreamParser(enabled = inferenceConfig.thinkingEnabled)
                    localEngine.generateMultimodalStream(formattedPrompt, imageBytes, inferenceConfig).collect { token ->
                        visionReasoningParser.feed(token).forEach { event ->
                            when (event) {
                                is ReasoningStreamParser.Event.Thinking -> updateLastMessage { it.copy(reasoningContent = it.reasoningContent + event.text, isThinking = true) }
                                is ReasoningStreamParser.Event.Answer -> {
                                    synchronized(answerBuffer) { answerBuffer.append(event.text) }
                                    val snapshot = synchronized(answerBuffer) { answerBuffer.toString() }
                                    _currentAnswer.value = snapshot
                                    updateLastMessage { it.copy(content = snapshot, isThinking = false) }
                                }
                            }
                        }
                    }
                    visionReasoningParser.finish().forEach { event ->
                        when (event) {
                            is ReasoningStreamParser.Event.Thinking -> updateLastMessage { it.copy(reasoningContent = it.reasoningContent + event.text) }
                            is ReasoningStreamParser.Event.Answer -> synchronized(answerBuffer) { answerBuffer.append(event.text) }
                        }
                    }
                    val finalMultimodalAnswer = synchronized(answerBuffer) { answerBuffer.toString() }
                    updateLastMessage { it.copy(content = finalMultimodalAnswer, isStreaming = false) }
                    _pendingImageUri.value = null
                    }
                } else {
                    agentEngine?.execute(
                        query = query,
                        config = inferenceConfig,
                        enableSearch = enableSearch,
                        conversationHistory = history,
                        systemPrompt = activeSystemPrompt
                    )?.collect { token ->
                        // Tokens are handled by callback
                    }
                }

                // -- Post-completion: persistence/memory is isolated from inference.
                // A broken memory record or a slow DataStore write must never turn a
                // successful native inference into an apparent model crash.
                lastModelActivityTime = System.currentTimeMillis()
                runCatching { autoSaveAndExtractMemories(query) }
                    .onFailure { android.util.Log.w("ChatViewModel", "Post-response memory/save skipped", it) }

            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
                updateLastMessage {
                    it.copy(
                        content = it.content.ifBlank { "Error: ${e.message}" },
                        isStreaming = false
                    )
                }
            } finally {
                if (requestGeneration.get() == requestId) {
                    _isProcessing.value = false
                    _agentStatus.value = AgentStatusIdle
                    currentJob = null
                }
            }
        }
    }

    /**
     * Build memory context string from relevant memories.
     * Returns empty string if privacy mode is on or memory is disabled.
     */
    private suspend fun buildMemoryContext(query: String): String {
        if (!privacyManager.canReadMemory() || query.isBlank()) return ""
        return runCatching {
            // Memory retrieval is strictly best-effort. A malformed/very large local
            // memory store must never be allowed to take down an otherwise healthy AI run.
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                memoryRepo.getRelevantMemories(query.take(2000), maxResults = 5)
            }
        }.getOrElse {
            android.util.Log.w("ChatViewModel", "Memory retrieval skipped", it)
            emptyList()
        }.take(5).joinToString("\n") { memory ->
            "- ${memory.content.take(2000)}"
        }.let { memories ->
            if (memories.isBlank()) "" else "[Relevant Memories]\n$memories"
        }
    }

    /**
     * Build conversation history list with memory context and summary prepended.
     * Priority: current message -> recent messages -> relevant memory -> session summary
     */
    private fun buildHistoryWithMemory(
        memoryContext: String,
        summaryContent: String?,
        selectedMessages: List<ChatMessage>
    ): List<Pair<String, String>> {
        val history = mutableListOf<Pair<String, String>>()

        // Add session summary first (lowest priority among injected context)
        if (!summaryContent.isNullOrBlank()) {
            history.add("system" to summaryContent)
        }

        // Add relevant memories (higher priority than summary)
        if (memoryContext.isNotBlank()) {
            history.add("system" to memoryContext)
        }

        // Add selected recent messages (highest priority)
        history.addAll(selectedMessages.map { msg ->
            msg.role.name.lowercase() to msg.content
        })

        return history
    }

    /**
     * Determine whether to enable search for this query.
     * In Auto Mode, the AutoModeEngine decides based on task classification.
     * Otherwise, use the user's internet search toggle.
     */
    private fun determineSearchBehavior(
        query: String,
        searchConfig: SearchConfig
    ): Boolean {
        if (!searchRepo.isConfigured()) return false

        return if (_isAutoModeEnabled.value) {
            val searchMode = com.localaisearch.data.search.SearchMode.SMART
            autoModeEngine.shouldUseSearch(query, searchMode)
        } else {
            _internetSearchEnabled.value
        }
    }

    /**
     * Auto-save conversation and extract memories after a successful response.
     * Skipped entirely in privacy mode. Prevents duplicate saves within a short window.
     */
    private suspend fun autoSaveAndExtractMemories(userQuery: String) {
        if (!privacyManager.canSaveConversation()) return

        val currentConv = _conversation.value
        val now = System.currentTimeMillis()
        val minIntervalMs = 5000L // 5 seconds minimum between saves

        // Compute a simple content hash from the last message to detect duplicate saves
        val lastMessageContent = currentConv.messages.lastOrNull()?.content ?: ""
        val contentHash = lastMessageContent.hashCode()

        // Skip if saved recently with the same content
        if (now - _lastSaveTime < minIntervalMs && contentHash == _lastSavedContentHash) {
            return
        }

        // Update save tracking
        _lastSaveTime = now
        _lastSavedContentHash = contentHash

        withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { conversationRepo.saveConversation(currentConv) }
                .onFailure { android.util.Log.w("ChatViewModel", "Conversation auto-save failed", it) }

            if (privacyManager.canWriteMemory()) {
                runCatching {
                    val extracted = memoryRepo.extractMemoriesFromConversation(currentConv).take(8)
                    extracted.forEach { memory ->
                        memoryRepo.addMemory(
                            content = memory.content.take(4000),
                            topic = memory.topic,
                            sourceConversationId = currentConv.id
                        )
                    }
                }.onFailure { android.util.Log.w("ChatViewModel", "Memory extraction skipped", it) }
            }
        }
    }


    /** Regenerate the last assistant answer from the preceding user turn. */
    fun regenerateMessage(messageId: String) {
        if (_isProcessing.value) return
        val messages = _conversation.value.messages
        val index = messages.indexOfFirst { it.id == messageId }
        if (index <= 0 || messages[index].role != MessageRole.ASSISTANT) return
        val user = messages[index - 1]
        if (user.role != MessageRole.USER || user.content.isBlank()) return
        _conversation.value = _conversation.value.copy(messages = messages.take(index))
        sendQuery(user.content, addUserMessage = true)
    }

    /**
     * Cancel ongoing processing.
     */
    fun cancel() {
        requestGeneration.incrementAndGet()
        currentJob?.cancel()
        viewModelScope.launch {
            agentEngine?.cancel()
        }
        _isProcessing.value = false
        _agentStatus.value = AgentStatusIdle
        updateLastMessage { it.copy(isStreaming = false) }
    }

    /**
     * Toggle internet search.
     */
    fun toggleInternetSearch() {
        _internetSearchEnabled.value = !_internetSearchEnabled.value
        viewModelScope.launch {
            settingsRepo.setInternetSearchEnabled(_internetSearchEnabled.value)
        }
    }

    /**
     * Toggle privacy mode on/off.
     *
     * When turning OFF, clean up all temporary data and notify the user.
     * When turning ON, ensure no new data is persisted.
     */
    fun togglePrivacyMode() {
        if (privacyManager.isPrivacyMode.value) {
            // Turning OFF: clean up and notify
            privacyManager.clearPrivacySessionData()
            _privacySessionEnded.value = true
            // Reset conversation to empty (temporary data was in-memory only)
            newConversation()
        } else {
            // Turning ON: enable privacy mode
            privacyManager.enablePrivacyMode()
        }
    }

    /**
     * Dismiss the privacy session ended notification.
     */
    fun dismissPrivacySessionNotification() {
        _privacySessionEnded.value = false
    }

    /**
     * Toggle Auto Mode on/off.
     */
    fun toggleAutoMode() {
        _isAutoModeEnabled.value = !_isAutoModeEnabled.value
    }

    /**
     * Start a new conversation.
     * In privacy mode, does not save the current conversation.
     */
    fun newConversation() {
        requestGeneration.incrementAndGet()
        currentJob?.cancel()
        viewModelScope.launch {
            agentEngine?.cancel()
        }

        // Save current conversation if not in privacy mode and has messages
        val currentConv = _conversation.value
        if (privacyManager.canSaveConversation() && currentConv.messages.isNotEmpty()) {
            viewModelScope.launch {
                conversationRepo.saveConversation(currentConv)
            }
        }

        _conversation.value = Conversation()
        answerFlushJob?.cancel()
        synchronized(answerBuffer) { answerBuffer.setLength(0) }
        _currentAnswer.value = ""
        _citations.value = emptyList()
        _searchResults.value = emptyList()
        _agentStatus.value = AgentStatusIdle
        _isProcessing.value = false
        _error.value = null
        _contextSummary.value = null
    }

    /**
     * Load an existing conversation by ID to continue it.
     * Does not work in privacy mode.
     */
    fun loadConversation(conversationId: String) {
        if (privacyManager.isPrivacyMode.value) return

        viewModelScope.launch {
            val conv = conversationRepo.getConversation(conversationId)
            if (conv != null) {
                requestGeneration.incrementAndGet()
                currentJob?.cancel()
                agentEngine?.let { 
                    viewModelScope.launch { it.cancel() }
                }
                _conversation.value = conv
                _currentAnswer.value = ""
                _citations.value = emptyList()
                _searchResults.value = emptyList()
                _agentStatus.value = AgentStatusIdle
                _isProcessing.value = false
                _error.value = null
                _contextSummary.value = null
            }
        }
    }

    /**
     * Check for and perform auto-unload of idle models.
     * Called periodically by the UI layer.
     */
    fun checkAutoUnload() {
        if (!_isAutoModeEnabled.value) return
        if (_isProcessing.value) return
        if (!llmEngine.isLoaded) return

        val config = AutoModeConfigDefault
        if (autoModeEngine.shouldUnloadAfterIdle(lastModelActivityTime, config.autoUnloadMinutes)) {
            viewModelScope.launch {
                llmEngine.unloadModel()
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun updateLastMessage(transform: (ChatMessage) -> ChatMessage) {
        val messages = _conversation.value.messages
        if (messages.isEmpty()) return
        _conversation.value = _conversation.value.updateLastMessage(
            transform(messages.last())
        )
    }

    private fun stripReasoningMarkers(raw: String): String {
        if (raw.isBlank()) return raw
        val patterns = listOf(
            Regex("(?s)<think>.*?</think>"),
            Regex("(?s)<\|think\|>.*?<\|/think\|>"),
            Regex("(?s)<\|start_thinking\|>.*?<\|end_thinking\|>"),
            Regex("(?s)\[THINK\].*?\[/THINK\]")
        )
        return patterns.fold(raw) { text, regex -> text.replace(regex, "") }.trim()
    }

    /** Splits model output into a private reasoning stream and the visible answer stream. */
    private class ReasoningStreamParser(private val enabled: Boolean) {
        sealed interface Event { data class Thinking(val text: String): Event; data class Answer(val text: String): Event }
        private var thinking = enabled
        private var pending = ""
        private val starts = listOf("<think>", "<|think|>", "<|start_thinking|>", "[THINK]")
        private val ends = listOf("</think>", "<|/think|>", "<|end_thinking|>", "[/THINK]")
        fun feed(chunk: String): List<Event> {
            if (!enabled) return if (chunk.isEmpty()) emptyList() else listOf(Event.Answer(chunk))
            pending += chunk
            val out = mutableListOf<Event>()
            while (pending.isNotEmpty()) {
                val markers = if (thinking) ends else starts
                val hit = markers.mapNotNull { m -> pending.indexOf(m).takeIf { it >= 0 }?.let { it to m } }.minByOrNull { it.first }
                if (hit != null) {
                    if (hit.first > 0) out += if (thinking) Event.Thinking(pending.substring(0, hit.first)) else Event.Answer(pending.substring(0, hit.first))
                    pending = pending.substring(hit.first + hit.second.length)
                    thinking = !thinking
                    continue
                }
                val maxPrefix = markers.maxOfOrNull { m ->
                    (1..minOf(m.length - 1, pending.length)).maxOfOrNull { n -> if (pending.endsWith(m.substring(0, n))) n else 0 } ?: 0
                } ?: 0
                val safeLength = pending.length - maxPrefix
                if (safeLength <= 0) break
                val safe = pending.substring(0, safeLength)
                pending = pending.substring(safeLength)
                if (safe.isNotEmpty()) out += if (thinking) Event.Thinking(safe) else Event.Answer(safe)
            }
            return out
        }
        fun finish(): List<Event> {
            if (pending.isEmpty()) return emptyList()
            val out = listOf(if (thinking) Event.Thinking(pending) else Event.Answer(pending))
            pending = ""
            return out
        }
    }


    override fun onCleared() {
        super.onCleared()
        // Save current conversation asynchronously if not in privacy mode
        val currentConv = _conversation.value
        if (privacyManager.canSaveConversation() && currentConv.messages.isNotEmpty()) {
            viewModelScope.launch {
                conversationRepo.saveConversation(currentConv)
            }
        }
        // The local GGUF engine is app-scoped and shared with model management.
        // Do not release it from this ViewModel lifecycle.
    }
}
