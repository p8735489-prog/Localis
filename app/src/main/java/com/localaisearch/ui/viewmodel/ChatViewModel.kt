package com.localaisearch.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localaisearch.data.agent.AgentCallback
import com.localaisearch.data.agent.AgentEngine
import com.localaisearch.data.llm.LLMProviderFactory
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
import com.localaisearch.data.performance.AutoModeConfig
import com.localaisearch.data.performance.AutoModeConfigDefault
import com.localaisearch.data.performance.AutoModeEngine
import com.localaisearch.data.performance.ContextSummarizer
import com.localaisearch.data.repository.ConversationRepository
import com.localaisearch.data.repository.MemoryRepository
import com.localaisearch.data.repository.ModelRepository
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
    private val llmEngine: LLMEngine = LLMProviderFactory.createEngine(application)
    private val searchRepo = SearchRepository()
    val modelRepo = ModelRepository(application, llmEngine)

    // -- Integrated Repositories & Managers --
    private val conversationRepo = ConversationRepository(application)
    private val memoryRepo = MemoryRepository(application)
    val privacyManager = PrivacyManager(settingsRepo)
    private val contextSummarizer = ContextSummarizer(llmEngine)
    private val autoModeEngine = AutoModeEngine()

    private var agentEngine: AgentEngine? = null
    private var currentJob: Job? = null
    private var lastModelActivityTime: Long = System.currentTimeMillis()
    private var _lastSaveTime: Long = 0L
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
        viewModelScope.launch {
            _internetSearchEnabled.value = settingsRepo.internetSearchEnabled.first()
            _defaultSystemPrompt.value = settingsRepo.defaultSystemPrompt.first()
        }
        // Model loading is performed by the model screen's ViewModel, but the engine is shared.
        // Poll only this tiny state so Home reflects load/unload without recreating the engine.
        viewModelScope.launch {
            while (true) {
                _modelLoaded.value = llmEngine.isLoaded
                _loadedModelName.value = llmEngine.loadedModelName ?: ""
                kotlinx.coroutines.delay(400)
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

    fun sendQuery(query: String) {
        if (query.isBlank() || _isProcessing.value) return
        if (!llmEngine.isLoaded) {
            _error.value = "No model loaded. Please import and load a GGUF model first."
            return
        }

        currentJob?.cancel()
        _error.value = null
        _currentAnswer.value = ""
        _citations.value = emptyList()
        _searchResults.value = emptyList()
        _isProcessing.value = true
        _privacySessionEnded.value = false
        lastModelActivityTime = System.currentTimeMillis()

        // Add user message
        val userMessage = ChatMessage(role = MessageRole.USER, content = query)
        _conversation.value = _conversation.value.addMessage(userMessage)

        // Add placeholder assistant message for streaming
        val assistantMessage = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        _conversation.value = _conversation.value.addMessage(assistantMessage)

        currentJob = viewModelScope.launch {
            try {
                val inferenceConfig = settingsRepo.inferenceConfig.first()
                val searchConfig = settingsRepo.searchConfig.first()
                searchRepo.updateConfig(searchConfig)

                agentEngine = AgentEngine(llmEngine, searchConfig)

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

                val callback = object : AgentCallback {
                    override fun onStateChanged(status: AgentStatus) {
                        _agentStatus.value = status
                        updateLastMessage { it.copy(agentStatus = status) }
                    }

                    override fun onSearchRound(round: SearchRound) {
                        _searchResults.value = _searchResults.value + round.results
                    }

                    override fun onSearchResults(results: List<SearchResult>) {
                        _searchResults.value = results
                    }

                    override fun onToken(token: String) {
                        _currentAnswer.value += token
                        updateLastMessage { it.copy(content = _currentAnswer.value) }
                    }

                    override fun onAnswer(
                        answer: String,
                        citations: List<Citation>,
                        session: SearchSession
                    ) {
                        _citations.value = citations
                        updateLastMessage {
                            it.copy(
                                content = if (answer.isNotBlank()) answer else _currentAnswer.value,
                                citations = citations,
                                isStreaming = false,
                                searchSession = session
                            )
                        }
                    }

                    override fun onError(message: String) {
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

                agentEngine?.execute(
                    query = query,
                    config = inferenceConfig,
                    enableSearch = enableSearch,
                    conversationHistory = history,
                    systemPrompt = getSystemPromptText()
                )?.collect { token ->
                    // Tokens are handled by callback
                }

                // -- Post-completion: auto-save and memory extraction --
                lastModelActivityTime = System.currentTimeMillis()
                autoSaveAndExtractMemories(query)

            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
                updateLastMessage {
                    it.copy(
                        content = it.content.ifBlank { "Error: ${e.message}" },
                        isStreaming = false
                    )
                }
            } finally {
                _isProcessing.value = false
                _agentStatus.value = AgentStatusIdle
                currentJob = null
            }
        }
    }

    /**
     * Build memory context string from relevant memories.
     * Returns empty string if privacy mode is on or memory is disabled.
     */
    private suspend fun buildMemoryContext(query: String): String {
        if (!privacyManager.canReadMemory()) return ""

        val relevantMemories = memoryRepo.getRelevantMemories(query, maxResults = 5)
        if (relevantMemories.isEmpty()) return ""

        return relevantMemories.joinToString("\n") { memory ->
            "- ${memory.content}"
        }.let { "[Relevant Memories]\n$it" }
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

        // Auto-save conversation
        conversationRepo.saveConversation(currentConv)

        // Extract and save memories if memory system is enabled
        if (privacyManager.canWriteMemory()) {
            val extracted = memoryRepo.extractMemoriesFromConversation(currentConv)
            extracted.forEach { memory ->
                memoryRepo.addMemory(
                    content = memory.content,
                    topic = memory.topic,
                    sourceConversationId = currentConv.id
                )
            }
        }
    }

    /**
     * Cancel ongoing processing.
     */
    fun cancel() {
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
