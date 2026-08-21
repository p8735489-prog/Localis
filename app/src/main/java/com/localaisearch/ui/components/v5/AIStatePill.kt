package com.localaisearch.ui.components.v5

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.localaisearch.ui.state.AIState

@Composable
fun AIStatePill(
    state: AIState,
    modifier: Modifier = Modifier
) {
    AssistChip(
        modifier = modifier,
        onClick = {},
        label = {
            Text(
                when (state) {
                    AIState.Idle -> "Ready"
                    AIState.LoadingModel -> "Loading model"
                    AIState.PreparingContext -> "Preparing"
                    AIState.Thinking -> "Thinking"
                    is AIState.Generating -> "Generating ${state.tokens}"
                    AIState.Completed -> "Completed"
                    is AIState.Error -> "Error"
                }
            )
        }
    )
}
