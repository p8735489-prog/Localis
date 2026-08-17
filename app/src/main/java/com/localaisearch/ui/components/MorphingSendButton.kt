package com.localaisearch.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.localaisearch.ui.animation.SpringSpecs

/**
 * Send button state for morphing animation.
 */
enum class SendButtonState {
    IDLE,       // Send icon
    SEARCHING,  // Search icon, pulsing
    DONE        // Check icon, spring bounce
}

/**
 * Morphing send button.
 *
 * Transitions: Send -> Searching -> Done
 * Uses spring physics and shape morphing for a fluid, elastic feel.
 */
@Composable
fun MorphingSendButton(
    state: SendButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme

    // Corner radius animates between circle (idle) and squircle (searching)
    val cornerRadius = animateFloatAsState(
        targetValue = when (state) {
            SendButtonState.IDLE -> 1f     // fully circular
            SendButtonState.SEARCHING -> 0.7f  // squircle
            SendButtonState.DONE -> 1f    // back to circular
        },
        animationSpec = SpringSpecs.morph,
        label = "cornerRadius"
    )

    // Scale animation for bounce effects
    val scaleAnim = remember { Animatable(1f) }

    LaunchedEffect(state) {
        when (state) {
            SendButtonState.SEARCHING -> {
                // Subtle pulse while searching
                scaleAnim.animateTo(0.92f, SpringSpecs.morph)
            }
            SendButtonState.DONE -> {
                // Spring bounce on completion
                scaleAnim.animateTo(1.15f, SpringSpecs.completion)
                scaleAnim.animateTo(1f, SpringSpecs.bouncy)
            }
            SendButtonState.IDLE -> {
                scaleAnim.animateTo(1f, SpringSpecs.gentle)
            }
        }
    }

    val backgroundColor = when (state) {
        SendButtonState.IDLE -> if (enabled) colorScheme.primary else colorScheme.surfaceVariant
        SendButtonState.SEARCHING -> colorScheme.tertiary
        SendButtonState.DONE -> colorScheme.primary
    }

    val contentColor = when (state) {
        SendButtonState.IDLE -> if (enabled) colorScheme.onPrimary else colorScheme.onSurfaceVariant
        SendButtonState.SEARCHING -> colorScheme.onTertiary
        SendButtonState.DONE -> colorScheme.onPrimary
    }

    // Pulsing glow for searching state
    val pulseScale = if (state == SendButtonState.SEARCHING) {
        animateFloatAsState(
            targetValue = 1.05f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = 200f
            ),
            label = "pulse"
        ).value
    } else 1f

    Surface(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scaleAnim.value * pulseScale
                scaleY = scaleAnim.value * pulseScale
            },
        shape = RoundedCornerShape(percent = (cornerRadius.value * 50).toInt()),
        color = backgroundColor,
        contentColor = contentColor,
        tonalElevation = if (enabled) 4.dp else 0.dp,
        shadowElevation = if (enabled) 8.dp else 0.dp,
        onClick = if (enabled && state == SendButtonState.IDLE) onClick else ({})
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp)
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (scaleIn(SpringSpecs.bouncy) + fadeIn(tween(200))) togetherWith
                            (scaleOut(SpringSpecs.gentle) + fadeOut(tween(150)))
                },
                label = "iconTransition"
            ) { buttonState ->
                val icon = when (buttonState) {
                    SendButtonState.IDLE -> Icons.AutoMirrored.Filled.Send
                    SendButtonState.SEARCHING -> Icons.Filled.Search
                    SendButtonState.DONE -> Icons.Filled.Check
                }
                Icon(
                    imageVector = icon,
                    contentDescription = when (buttonState) {
                        SendButtonState.IDLE -> "Send"
                        SendButtonState.SEARCHING -> "Searching"
                        SendButtonState.DONE -> "Done"
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
