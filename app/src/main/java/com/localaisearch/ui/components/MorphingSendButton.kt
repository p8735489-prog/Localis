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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.localaisearch.R
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
 * Independent circular send button with purple-blue gradient.
 *
 * Features:
 * - Circular shape
 * - Purple-blue gradient background
 * - White send icon
 * - No excessive glow effects
 */
@Composable
fun MorphingSendButton(
    state: SendButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Scale animation for bounce effects
    val scaleAnim = remember { Animatable(1f) }
    val hapticView = LocalView.current

    LaunchedEffect(state) {
        when (state) {
            SendButtonState.SEARCHING -> {
                scaleAnim.animateTo(0.92f, SpringSpecs.morph)
            }
            SendButtonState.DONE -> {
                scaleAnim.animateTo(1.15f, SpringSpecs.completion)
                scaleAnim.animateTo(1f, SpringSpecs.bouncy)
            }
            SendButtonState.IDLE -> {
                scaleAnim.animateTo(1f, SpringSpecs.gentle)
            }
        }
    }

    // Gradient colors: purple to blue
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF8B7FD4),  // soft purple
            Color(0xFF7FB8D4)   // soft blue
        )
    )

    val iconTint = Color.White
    val isClickable = enabled && state == SendButtonState.IDLE

    Surface(
        modifier = modifier
            .scale(scaleAnim.value),
        shape = CircleShape,
        color = Color.Transparent,
        shadowElevation = if (isClickable) 2.dp else 0.dp,
        onClick = if (isClickable) { { AppHaptics.confirm(hapticView); onClick() } } else ({ })
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) gradientBrush else Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f)))
                )
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
                    SendButtonState.IDLE -> Icons.AutoMirrored.Rounded.Send
                    SendButtonState.SEARCHING -> Icons.Rounded.Search
                    SendButtonState.DONE -> Icons.Rounded.Check
                }
                Icon(
                    imageVector = icon,
                    contentDescription = when (buttonState) {
                        SendButtonState.IDLE -> stringResource(R.string.send)
                        SendButtonState.SEARCHING -> stringResource(R.string.searching)
                        SendButtonState.DONE -> stringResource(R.string.done)
                    },
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
