package com.localaisearch.data.model

import kotlinx.serialization.Serializable

/**
 * The state machine for the Agentic Search flow.
 * Each state drives both the agent logic and the AI Orb animation.
 *
 * Flow: Idle -> Thinking -> (Searching -> Reading -> Analyzing -> Validating)* -> Answering -> Done
 * Max 3 search rounds. If no internet needed, goes directly to Answering.
 */
@Serializable
enum class AgentState {
    /** Idle / standby - Orb shows breathing animation */
    IDLE,

    /** GGUF is thinking about whether internet search is needed */
    THINKING,

    /** Generating search keywords and initiating search */
    SEARCHING,

    /** Reading and ingesting search results */
    READING,

    /** Analyzing gathered information */
    ANALYZING,

    /** Validating whether gathered info is sufficient */
    VALIDATING,

    /** Generating the final comprehensive answer */
    ANSWERING,

    /** Process complete - Orb springs back */
    DONE,

    /** Error occurred */
    ERROR;

    val isSearching: Boolean
        get() = this in setOf(SEARCHING, READING, ANALYZING, VALIDATING)

    val isActive: Boolean
        get() = this != IDLE && this != DONE && this != ERROR

    val progress: Float
        get() = when (this) {
            IDLE -> 0f
            THINKING -> 0.1f
            SEARCHING -> 0.3f
            READING -> 0.5f
            ANALYZING -> 0.7f
            VALIDATING -> 0.85f
            ANSWERING -> 0.95f
            DONE -> 1f
            ERROR -> 0f
        }
}

/**
 * Real-time status update during agent execution.
 */
@Serializable
data class AgentStatus(
    val state: AgentState = AgentState.IDLE,
    val message: String = "",
    val currentRound: Int = 0,
    val maxRounds: Int = 3,
    val searchQuery: String = "",
    val resultsFound: Int = 0,
    val tokensGenerated: Int = 0,
    val errorMessage: String? = null
) {
    companion object {
        val Idle = AgentStatus()
        fun error(message: String) = AgentStatus(state = AgentState.ERROR, errorMessage = message)
    }
}
