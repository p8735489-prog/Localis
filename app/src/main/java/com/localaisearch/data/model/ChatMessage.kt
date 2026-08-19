package com.localaisearch.data.model

import kotlinx.serialization.Serializable

/**
 * Role for chat messages.
 */
@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * A chat message in the conversation history.
 */
@Serializable
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val citations: List<Citation> = emptyList(),
    val agentStatus: AgentStatus? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val searchSession: SearchSession? = null
)

/** Whether this message has any citations. */
val ChatMessage.hasCitations: Boolean get() = citations.isNotEmpty()
/** Whether this message originated from a search with active rounds. */
val ChatMessage.isFromSearch: Boolean get() = searchSession != null && searchSession.totalRounds > 0

/**
 * A conversation session containing all messages.
 */
@Serializable
data class Conversation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "New Conversation",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun addMessage(message: ChatMessage): Conversation {
        val newMessages = messages + message
        val newTitle = if (messages.isEmpty() && message.role == MessageRole.USER) {
            message.content.take(40)
        } else title
        return copy(
            messages = newMessages,
            title = newTitle,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun updateLastMessage(message: ChatMessage): Conversation {
        if (messages.isEmpty()) return this
        val newMessages = messages.dropLast(1) + message
        return copy(messages = newMessages, updatedAt = System.currentTimeMillis())
    }
}
