package com.localaisearch.ui.state

sealed interface AIState {
    data object Idle : AIState
    data object LoadingModel : AIState
    data object PreparingContext : AIState
    data object Thinking : AIState
    data class Generating(val tokens: Int = 0) : AIState
    data object Completed : AIState
    data class Error(val message: String) : AIState
}
