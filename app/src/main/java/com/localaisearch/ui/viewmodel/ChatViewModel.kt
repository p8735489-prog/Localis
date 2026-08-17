package com.localaisearch.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localaisearch.data.agent.AgentCallback
import com.localaisearch.data.agent.AgentEngine
import com.localaisearch.data.llm.GGUFEngine
import com.localaisearch.data.llm.LLMEngine
import com.localaisearch.data.model.AgentStatus
import com.localaisearch.data.model.ChatMessage
import com.localaisearch.data.model.Conversation
import com.localaisearch.data.model.Citation
import com.localaisearch.data.model.InferenceConfig
import com.localaisearch.data.model.MessageRole
import com.localaisearch.data.model.SearchRound
import com.localaisearch.data.model.SearchResult
import com.localaisearch.data.model.SearchSession
import com.localaisearch.data.performance.AutoModeConfig
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
    private val llmEngine: LLMEngine = GGUFEngine()
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

    // -- UI State --

    private val _conversation = MutableStateFlow(Conversation())
    val conversation: StateFlow<Conversation> = _conversation.asStateFlow()

    private val _agentStatus = MutableStateFlow(AgentStatus.Idle)
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

        viewModelScope.launch {
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

                agentEngine!!.setCallback(callback)

                agentEngine!!.execute(
                    query = query,
                    config = inferenceConfig,
                    enableSearch = enableSearch,
                    conversationHistory = history
                ).collect { token ->
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
                _agentStatus.value = AgentStatus.Idle
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
     * Skipped entirely in privacy mode.
     */
    private suspend fun autoSaveAndExtractMemories(userQuery: String) {
        if (!privacyManager.canSaveConversation()) return

        // Auto-save conversation
        val currentConv = _conversation.value
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
        agentEngine?.cancel()
        _isProcessing.value = false
        _agentStatus.value = AgentStatus.Idle
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
        agentEngine?.cancel()

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
        _agentStatus.value = AgentStatus.Idle
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
                agentEngine?.cancel()
                _conversation.value = conv
                _currentAnswer.value = ""
                _citations.value = emptyList()
                _searchResults.value = emptyList()
                _agentStatus.value = AgentStatus.Idle
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

        val config = AutoModeConfig.Default
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
        _conversation.value = _conversation.value.updateLastMessage(
            transform(_conversation.value.messages.last())
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Save current conversation if not in privacy mode
        val currentConv = _conversation.value
        if (privacyManager.canSaveConversation() && currentConv.messages.isNotEmpty()) {
            // Use a blocking scope to ensure save completes
            kotlinx.coroutines.runBlocking {
                conversationRepo.saveConversation(currentConv)
            }
        }
        llmEngine.release()
    }
}
