package com.localaisearch.data.performance

import com.localaisearch.data.llm.LLMEngine
import com.localaisearch.data.model.ChatMessage
import com.localaisearch.data.model.InferenceConfig
import com.localaisearch.data.model.MessageRole

/**
 * Result of building an optimized context window.
 *
 * @property selectedMessages The messages selected for the context window.
 * @property summaryMessage An optional synthetic summary message replacing older context.
 * @property droppedCount Number of messages dropped to fit the token budget.
 * @property estimatedTokens Estimated token count of the final context.
 */
data class ContextBuildResult(
    val selectedMessages: List<ChatMessage>,
    val summaryMessage: ChatMessage?,
    val droppedCount: Int,
    val estimatedTokens: Int
)

/**
 * Intelligent context window management that estimates token usage,
 * detects overflow conditions, and builds an optimized message list
 * by summarizing older messages when necessary.
 *
 * @param llmEngine The LLM engine used to generate conversation summaries.
 */
class ContextSummarizer(private val llmEngine: LLMEngine) {

    /**
     * Heuristic token-count estimation.
     *
     * - English / Latin: ~1 token per 4 characters
     * - Chinese / CJK: ~1 token per 2 characters
     *
     * This is a coarse approximation; real tokenization (e.g., BPE)
     * would be more accurate but requires loading a tokenizer.
     *
     * @param messages The messages to estimate.
     * @return Approximate token count.
     */
    fun estimateTokenCount(messages: List<ChatMessage>): Int {
        var total = 0
        for (msg in messages) {
            val text = msg.content
            val cjkCount = text.count { isCJK(it) }
            val nonCjkCount = text.length - cjkCount
            // CJK: 1 token per 2 chars => divide by 2
            // Non-CJK: 1 token per 4 chars => divide by 4
            val msgTokens = (cjkCount / 2.0 + nonCjkCount / 4.0).toInt().coerceAtLeast(1)
            total += msgTokens
        }
        // Add overhead for message formatting (~4 tokens per message for role markers, etc.)
        total += messages.size * 4
        return total
    }

    /**
     * Check whether the message list exceeds ~80 % of the allowed token budget,
     * suggesting that summarization is needed.
     *
     * @param messages The current conversation messages.
     * @param maxTokens Maximum tokens the model can handle.
     * @return `true` when estimated tokens > maxTokens x 0.8.
     */
    fun needsSummarization(messages: List<ChatMessage>, maxTokens: Int): Boolean {
        if (maxTokens <= 0) return false
        return estimateTokenCount(messages) > (maxTokens * 0.8).toInt()
    }

    /**
     * Generate a concise summary of the provided messages using the LLM.
     *
     * The prompt asks the model to produce 3-5 sentences that preserve
     * key facts and user preferences.
     *
     * @param messages The messages to summarize.
     * @return The generated summary text, or an empty string on failure.
     */
    suspend fun summarizeMessages(messages: List<ChatMessage>): String {
        if (messages.isEmpty()) return ""

        val conversationText = messages.joinToString("\n") { msg ->
            val roleLabel = when (msg.role) {
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
                MessageRole.SYSTEM -> "System"
            }
            "$roleLabel: ${msg.content}"
        }

        val prompt = buildString {
            append("Summarize this conversation in 3-5 sentences, preserving key facts and user preferences.\n\n")
            append(conversationText)
            append("\n\nSummary:")
        }

        // Low temperature for a focused, deterministic summary
        val config = InferenceConfig(
            temperature = 0.3f,
            maxTokens = 256
        )

        return llmEngine.generate(prompt, config).getOrNull()?.trim() ?: ""
    }

    /**
     * Build an optimized context window from a potentially long message list.
     *
     * Priority order:
     * 1. The **most recent** user message is always kept in full.
     * 2. **Recent messages** (tail of the list) are kept until the budget runs out.
     * 3. **Older messages** are replaced by a single synthetic summary message
     *    when [includeSummary] is `true`.
     *
     * @param messages Full conversation history.
     * @param maxTokens Maximum token budget for the context.
     * @param includeSummary Whether to synthesize a summary for dropped older messages.
     * @return A [ContextBuildResult] describing the final message list and stats.
     */
    suspend fun buildOptimizedContext(
        messages: List<ChatMessage>,
        maxTokens: Int,
        includeSummary: Boolean
    ): ContextBuildResult {
        if (messages.isEmpty()) {
            return ContextBuildResult(
                selectedMessages = emptyList(),
                summaryMessage = null,
                droppedCount = 0,
                estimatedTokens = 0
            )
        }

        if (!needsSummarization(messages, maxTokens)) {
            return ContextBuildResult(
                selectedMessages = messages,
                summaryMessage = null,
                droppedCount = 0,
                estimatedTokens = estimateTokenCount(messages)
            )
        }

        // Strategy: keep the last message (current user query) and work backwards,
        // adding messages until we approach the budget. Everything before that
        // gets summarized (if requested) or dropped.
        val budget = (maxTokens * 0.8).toInt()
        val result = mutableListOf<ChatMessage>()
        var tokenSum = 0
        var dropped = 0

        // Always include the very last message (current user input)
        val lastMessage = messages.lastOrNull() ?: return ContextBuildResult(
            selectedMessages = messages,
            summaryMessage = null,
            droppedCount = 0,
            estimatedTokens = estimateTokenCount(messages)
        )
        val lastTokens = estimateTokenCount(listOf(lastMessage))
        result.add(lastMessage)
        tokenSum += lastTokens

        // Walk backwards from second-last message
        for (i in messages.size - 2 downTo 0) {
            val msg = messages[i]
            val msgTokens = estimateTokenCount(listOf(msg))
            if (tokenSum + msgTokens <= budget) {
                result.add(0, msg) // prepend to maintain chronological order
                tokenSum += msgTokens
            } else {
                dropped = i + 1 // all messages from 0..i are dropped
                break
            }
        }

        val summaryMessage: ChatMessage? = if (includeSummary && dropped > 0) {
            val olderMessages = messages.take(dropped)
            val summaryText = summarizeMessages(olderMessages)
            if (summaryText.isNotBlank()) {
                ChatMessage(
                    role = MessageRole.SYSTEM,
                    content = "[Summary of earlier conversation]\n$summaryText"
                )
            } else null
        } else null

        // Insert summary at the front if we have one
        val finalMessages = if (summaryMessage != null) {
            listOf(summaryMessage) + result
        } else {
            result.toList()
        }

        return ContextBuildResult(
            selectedMessages = finalMessages,
            summaryMessage = summaryMessage,
            droppedCount = dropped,
            estimatedTokens = estimateTokenCount(finalMessages)
        )
    }

    /**
     * Build an optimized context window with relevant memories injected.
     *
     * Priority order (highest to lowest):
     * 1. **Current message** - the most recent user query (always kept in full)
     * 2. **Recent messages** - kept until token budget runs out
     * 3. **Relevant memories** - injected as a system message before recent messages
     * 4. **Session summary** - replaces older dropped messages
     *
     * This method combines context optimization with memory injection in a single
     * call, ensuring the LLM receives the most relevant information within the
     * token budget.
     *
     * @param messages Full conversation history.
     * @param maxTokens Maximum token budget for the context.
     * @param includeSummary Whether to synthesize a summary for dropped older messages.
     * @param relevantMemories List of memory content strings to inject.
     * @return A [ContextBuildResult] describing the final message list and stats.
     */
    suspend fun buildContextWithMemories(
        messages: List<ChatMessage>,
        maxTokens: Int,
        includeSummary: Boolean,
        relevantMemories: List<String>
    ): ContextBuildResult {
        if (messages.isEmpty() && relevantMemories.isEmpty()) {
            return ContextBuildResult(
                selectedMessages = emptyList(),
                summaryMessage = null,
                droppedCount = 0,
                estimatedTokens = 0
            )
        }

        // First, build the optimized context without memories
        val baseResult = buildOptimizedContext(messages, maxTokens, includeSummary)

        // If no memories to inject, return the base result
        if (relevantMemories.isEmpty()) {
            return baseResult
        }

        // Create memory injection message
        val memoryContent = relevantMemories.joinToString("\n") { "- $it" }
        val memoryMessage = ChatMessage(
            role = MessageRole.SYSTEM,
            content = "[Relevant Memories]\n$memoryContent"
        )

        // Check if adding memories still fits within budget
        val memoryTokens = estimateTokenCount(listOf(memoryMessage))
        val totalWithMemories = baseResult.estimatedTokens + memoryTokens

        if (totalWithMemories <= maxTokens) {
            // Memories fit - inject between summary and selected messages
            val finalMessages = buildList {
                baseResult.summaryMessage?.let { add(it) }
                add(memoryMessage)
                addAll(baseResult.selectedMessages)
            }
            return ContextBuildResult(
                selectedMessages = finalMessages,
                summaryMessage = baseResult.summaryMessage,
                droppedCount = baseResult.droppedCount,
                estimatedTokens = estimateTokenCount(finalMessages)
            )
        } else {
            // Memories don't fit - try with fewer recent messages
            // Keep: summary + memory + last message only
            val lastMessage = messages.lastOrNull() ?: ChatMessage(
                role = MessageRole.USER,
                content = ""
            )
            val finalMessages = buildList {
                baseResult.summaryMessage?.let { add(it) }
                add(memoryMessage)
                add(lastMessage)
            }
            return ContextBuildResult(
                selectedMessages = finalMessages,
                summaryMessage = baseResult.summaryMessage,
                droppedCount = messages.size - 1,
                estimatedTokens = estimateTokenCount(finalMessages)
            )
        }
    }

    /**
     * Check if a character is a CJK (Chinese, Japanese, Korean) ideograph
     * or full-width symbol.
     */
    private fun isCJK(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF ||    // CJK Unified Ideographs
            code in 0x3400..0x4DBF ||       // CJK Extension A
            code in 0xF900..0xFAFF ||       // CJK Compatibility Ideographs
            code in 0x2F800..0x2FA1F ||     // CJK Compatibility Supplement
            code in 0x3000..0x303F ||       // CJK Symbols and Punctuation
            code in 0xFF00..0xFFEF          // Full-width forms
    }
}
