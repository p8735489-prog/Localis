package com.localaisearch.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localaisearch.data.model.AgentState
import com.localaisearch.data.model.AgentStatus
import com.localaisearch.ui.animation.InfiniteAnimations
import com.localaisearch.ui.animation.SpringSpecs

/**
 * Displays the current agent state as an animated progress indicator.
 *
 * Shows: Thinking -> Searching -> Reading -> Analyzing -> Validating -> Answering
 * Each step has a spring-animated indicator. No rotation loaders.
 */
@Composable
fun AgentProgressBar(
    status: AgentStatus,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val steps = AgentState.entries.filter { it.isActive && it != AgentState.ERROR }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status message
        AnimatedContent(
            targetState = status.message.ifBlank { status.state.name.lowercase().replaceFirstChar { it.uppercase() } },
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "statusMessage"
        ) { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }

        // Step indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            steps.forEach { step ->
                val isActive = status.state == step
                val isCompleted = status.state.progress > step.progress

                val dotScale by animateFloatAsState(
                    targetValue = when {
                        isActive -> 1.3f
                        isCompleted -> 1f
                        else -> 0.6f
                    },
                    animationSpec = SpringSpecs.bouncy,
                    label = "dotScale_${step.name}"
                )

                val dotColor = when {
                    isActive -> colorScheme.primary
                    isCompleted -> colorScheme.primary.copy(alpha = 0.5f)
                    else -> colorScheme.surfaceVariant
                }

                // Pulsing glow for active step
                val pulseAlpha = if (isActive) {
                    InfiniteAnimations.breathingPulse(min = 0.4f, max = 0.8f, durationMs = 1500)
                } else 0f

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(scaleX = dotScale, scaleY = dotScale),
                    contentAlignment = Alignment.Center
                ) {
                    // Glow
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = dotColor.copy(alpha = pulseAlpha * 0.3f),
                            modifier = Modifier.size(16.dp)
                        ) {}
                    }
                    // Dot
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = dotColor,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }

                // Arrow between dots
                if (step != steps.last()) {
                    Spacer(modifier = Modifier.width(2.dp))
                }
            }
        }

        // Round indicator
        if (status.currentRound > 0) {
            Text(
                text = "Search round ${status.currentRound}/${status.maxRounds}",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
