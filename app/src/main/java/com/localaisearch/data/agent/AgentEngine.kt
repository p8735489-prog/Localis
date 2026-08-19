package com.localaisearch.data.agent

import com.localaisearch.data.llm.LLMEngine
import com.localaisearch.data.model.AgentState
import com.localaisearch.data.model.AgentStatus
import com.localaisearch.data.model.AgentStatusIdle
import com.localaisearch.data.model.Citation
import com.localaisearch.data.model.InferenceConfig
import com.localaisearch.data.model.SearchResult
import com.localaisearch.data.model.SearchRound
import com.localaisearch.data.model.SearchSession
import com.localaisearch.data.model.toCitation
import com.localaisearch.data.model.totalResults
import com.localaisearch.data.search.SearchConfig
import com.localaisearch.data.search.SearchConfigDefault
import com.localaisearch.data.search.SearchProvider
import com.localaisearch.data.search.SearchProviderFactory
import com.localaisearch.data.search.SearchResultProcessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Callback for receiving agent status updates during execution.
 */
interface AgentCallback {

    /** Called when agent state changes */
    fun onStateChanged(status: AgentStatus)

    /** Called when a search round completes */
    fun onSearchRound(round: SearchRound)

    /** Called when search results are processed */
    fun onSearchResults(results: List<SearchResult>)

    /** Called for each token generated in the final answer */
    fun onToken(token: String)

    /** Called when the final answer with citations is ready */
    fun onAnswer(answer: String, citations: List<Citation>, session: SearchSession)

    /** Called on error */
    fun onError(message: String)
}

/**
 * The core Agentic Search engine.
 *
 * Orchestrates the full flow:
 * 1. GGUF determines if internet search is needed
 * 2. If needed: generate keywords -> search -> analyze -> validate (max 3 rounds)
 * 3. GGUF generates final answer with real source citations
 *
 * Privacy: GGUF runs entirely locally. Search requests are only sent
 * when the user has enabled internet search AND the model determines
 * real-time information is needed.
 *
 * This engine is fully decoupled from UI - it communicates via callbacks
 * and StateFlow.
 */
class AgentEngine(
    private val llmEngine: LLMEngine,
    private val searchConfig: SearchConfig = SearchConfigDefault
) {

    private val _status = MutableStateFlow(AgentStatusIdle)
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    private var callback: AgentCallback? = null
    private var isCancelled = false

    /**
     * Set the callback for receiving updates.
     */
    fun setCallback(callback: AgentCallback?) {
        this.callback = callback
    }

    /**
     * Execute an agentic search query.
     *
     * @param query The user's question
     * @param config LLM inference configuration
     * @param enableSearch Whether internet search is allowed
     * @return A Flow of answer tokens for streaming
     */
    fun execute(
        query: String,
        config: InferenceConfig,
        enableSearch: Boolean,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        systemPrompt: String = ""
    ): Flow<String> = flow {
        isCancelled = false
        var searchSession = SearchSession()

        try {
            // -- Step 1: THINKING - Determine if search is needed --
            updateState(AgentState.THINKING, "Analyzing your question...")

            val needsSearch = if (enableSearch && searchConfig.apiUrl.isNotBlank()) {
                determineIfSearchNeeded(query, config)
            } else {
                false
            }

            if (!needsSearch) {
                // -- Direct local answer --
                updateState(AgentState.ANSWERING, "Generating answer locally...")

                val fullHistory = buildList {
                    if (systemPrompt.isNotBlank()) add("system" to systemPrompt)
                    addAll(conversationHistory)
                    add("user" to query)
                }
                llmEngine.chatStream(fullHistory, config).collect { token ->
                    if (isCancelled) return@collect
                    emit(token)
                    callback?.onToken(token)
                    _status.value = _status.value.copy(
                        tokensGenerated = _status.value.tokensGenerated + 1
                    )
                }

                updateState(AgentState.DONE, "Complete")
                callback?.onAnswer("", emptyList(), searchSession)
                return@flow
            }

            // -- Step 2: SEARCHING - Multi-round agentic search --
            val maxRounds = searchConfig.maxSearchRounds.coerceIn(1, 3)
            val provider = SearchProviderFactory.create(searchConfig)
            var searchQuery = query
            var sufficientInfo = false

            for (round in 0 until maxRounds) {
                if (isCancelled) return@flow

                // Generate search keywords
                updateState(
                    AgentState.SEARCHING,
                    "Generating search keywords...",
                    currentRound = round + 1,
                    maxRounds = maxRounds
                )

                searchQuery = if (round == 0) {
                    generateSearchKeywords(query, config)
                } else {
                    generateRefinedQuery(query, searchSession, config)
                }

                _status.value = _status.value.copy(searchQuery = searchQuery)

                // Execute search
                val searchResult = provider.search(searchQuery, searchConfig, round)

                val rawResults = searchResult.getOrElse { e ->
                    callback?.onError("Search failed: ${e.message}")
                    emptyList()
                }

                if (rawResults.isEmpty()) {
                    if (round == 0) {
                        // No results at all, try to answer locally
                        break
                    }
                    break
                }

                // -- Step 3: READING - Process results --
                updateState(
                    AgentState.READING,
                    "Reading ${rawResults.size} sources...",
                    currentRound = round + 1,
                    maxRounds = maxRounds,
                    resultsFound = searchSession.totalResults + rawResults.size
                )

                val roundData = SearchRound(
                    round = round + 1,
                    query = searchQuery,
                    results = rawResults
                )
                searchSession = searchSession.addRound(roundData)
                callback?.onSearchRound(roundData)

                // Process: deduplicate, filter, rank
                val processed = SearchResultProcessor.process(
                    searchSession.allResults,
                    query,
                    maxCount = searchConfig.maxResults
                )
                callback?.onSearchResults(processed)

                // -- Step 4: ANALYZING --
                updateState(
                    AgentState.ANALYZING,
                    "Analyzing search results...",
                    currentRound = round + 1,
                    maxRounds = maxRounds
                )

                // -- Step 5: VALIDATING - Check if info is sufficient --
                updateState(
                    AgentState.VALIDATING,
                    "Checking if information is sufficient...",
                    currentRound = round + 1,
                    maxRounds = maxRounds
                )

                sufficientInfo = checkInfoSufficient(query, processed, config)

                if (sufficientInfo) break
            }

            // -- Step 6: ANSWERING - Generate final answer with citations --
            updateState(AgentState.ANSWERING, "Generating comprehensive answer...")

            val finalResults = SearchResultProcessor.process(
                searchSession.allResults,
                query,
                maxCount = searchConfig.maxResults
            )

            val contextText = SearchResultProcessor.buildContextText(finalResults)
            val answerPrompt = buildAnswerPrompt(query, contextText, conversationHistory, systemPrompt)

            val answerBuilder = StringBuilder()

            // Use the model's native GGUF chat template for the final answer too.
            // Feeding a hand-built prompt here bypassed Qwen/Llama/Gemma templates
            // and could make the model echo control markers.
            llmEngine.chatStream(
                listOf(
                    "system" to (systemPrompt.ifBlank { "You are a helpful AI assistant." }),
                    "user" to answerPrompt
                ),
                config
            ).collect { token ->
                if (isCancelled) return@collect
                emit(token)
                answerBuilder.append(token)
                callback?.onToken(token)
                _status.value = _status.value.copy(
                    tokensGenerated = _status.value.tokensGenerated + 1
                )
            }

            // Build citations from real search results
            val citations = finalResults.mapIndexed { index, result ->
                result.toCitation(index + 1)
            }

            updateState(AgentState.DONE, "Complete")
            callback?.onAnswer(answerBuilder.toString(), citations, searchSession)

        } catch (e: Exception) {
            updateState(AgentState.ERROR, errorMessage = e.message ?: "Unknown error")
            callback?.onError(e.message ?: "Unknown error")
        }
    }

    /**
     * Cancel ongoing execution.
     */
    /**
     * Cancel ongoing execution.
     * Non-blocking — the caller is responsible for launching this in a
     * coroutine if needed (e.g. from the UI layer via viewModelScope).
     */
    suspend fun cancel() {
        isCancelled = true
        llmEngine.stopGeneration()
        updateState(AgentState.IDLE, "Cancelled")
    }

    /**
     * Reset to idle state.
     */
    fun reset() {
        isCancelled = false
        _status.value = AgentStatusIdle
    }

    // -- Private helper methods --

    private suspend fun determineIfSearchNeeded(
        query: String,
        config: InferenceConfig
    ): Boolean {
        val prompt = buildString {
            append("You are an AI assistant that determines if a question requires real-time internet search.")
            append(" Answer ONLY with 'YES' or 'NO'.")
            append("\n\nRules:")
            append("\n- Answer YES if the question is about: current events, latest news, recent data,")
            append(" real-time prices, weather, sports scores, recent releases, or anything that changes over time.")
            append("\n- Answer NO if the question is about: general knowledge, math, coding, explanations,")
            append(" creative writing, or anything that doesn't require up-to-date information.")
            append("\n\nQuestion: $query")
            append("\n\nAnswer (YES/NO):")
        }

        val result = llmEngine.generate(prompt, config.copy(maxTokens = 10, temperature = 0.1f))
        val response = result.getOrNull()?.trim()?.uppercase() ?: "NO"
        return response.startsWith("YES")
    }

    private suspend fun generateSearchKeywords(
        query: String,
        config: InferenceConfig
    ): String {
        val prompt = buildString {
            append("You are a search query generator. Convert the user's question into an effective search query.")
            append("\n\nRules:")
            append("\n- Output ONLY the search query, nothing else.")
            append("\n- Use the most relevant keywords.")
            append("\n- Keep it concise (under 60 characters).")
            append("\n- Do not include quotes or special formatting.")
            append("\n\nUser question: $query")
            append("\n\nSearch query:")
        }

        val result = llmEngine.generate(prompt, config.copy(maxTokens = 30, temperature = 0.3f))
        return result.getOrNull()?.trim()?.take(100) ?: query
    }

    private suspend fun generateRefinedQuery(
        originalQuery: String,
        session: SearchSession,
        config: InferenceConfig
    ): String {
        val previousQueries = session.rounds.joinToString("\n") { "- ${it.query}" }
        val prompt = buildString {
            append("You are a search query refiner. The previous searches did not provide sufficient information.")
            append("\n\nOriginal question: $originalQuery")
            append("\nPrevious search queries:\n$previousQueries")
            append("\n\nGenerate a NEW, different search query that might find additional or better information.")
            append("\nOutput ONLY the search query, nothing else.")
            append("\n\nSearch query:")
        }

        val result = llmEngine.generate(prompt, config.copy(maxTokens = 30, temperature = 0.5f))
        return result.getOrNull()?.trim()?.take(100) ?: originalQuery
    }

    private suspend fun checkInfoSufficient(
        query: String,
        results: List<SearchResult>,
        config: InferenceConfig
    ): Boolean {
        if (results.size >= 5) return true

        val contextText = SearchResultProcessor.buildContextText(results, maxChars = 2000)
        val prompt = buildString {
            append("You are evaluating if search results contain enough information to answer a question.")
            append("\n\nQuestion: $query")
            append("\n\nSearch results:\n$contextText")
            append("\n\nIs this information sufficient to answer the question? Answer ONLY 'YES' or 'NO'.")
            append("\n\nAnswer:")
        }

        val result = llmEngine.generate(prompt, config.copy(maxTokens = 10, temperature = 0.1f))
        val response = result.getOrNull()?.trim()?.uppercase() ?: "YES"
        return response.startsWith("YES")
    }

    private fun buildAnswerPrompt(
        query: String,
        contextText: String,
        history: List<Pair<String, String>>,
        systemPrompt: String
    ): String {
        return buildString {
            if (systemPrompt.isNotBlank()) append("System instructions:\n$systemPrompt\n\n")
            append("You are a knowledgeable AI assistant. Answer the user's question using the provided search results.")
            append("\n\nImportant rules:")
            append("\n1. Use [1], [2], [3], etc. to cite sources in your answer.")
            append("\n2. Only cite sources that are relevant to your answer.")
            append("\n3. Do not fabricate sources. Only use the provided search results.")
            append("\n4. Provide a comprehensive, well-structured answer.")
            append("\n5. If the search results don't fully answer the question, acknowledge the limitation.")
            append("\n\nSearch results:\n$contextText")
            append("\n\n---\n\nUser question: $query")
            append("\n\nAnswer:")
        }
    }

    private fun updateState(
        state: AgentState,
        message: String = "",
        currentRound: Int = 0,
        maxRounds: Int = 3,
        resultsFound: Int = 0,
        errorMessage: String? = null
    ) {
        val newStatus = AgentStatus(
            state = state,
            message = message,
            currentRound = currentRound,
            maxRounds = maxRounds,
            resultsFound = resultsFound,
            errorMessage = errorMessage
        )
        _status.value = newStatus
        callback?.onStateChanged(newStatus)
    }
}
