package com.localaisearch.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3-inspired expressive loading indicator.
 * Uses the app's MaterialTheme colors and morphing rounded shapes instead of
 * a legacy spinner, so loading states stay visually consistent with M3 UI.
 */
@Composable
fun Material3LoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "m3-loading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "m3-loading-phase"
    )
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        val blob = size * 0.30f
        Box(
            Modifier
                .size(blob)
                .scale(0.78f + phase * 0.22f)
                .rotate(phase * 38f)
                .graphicsLayer { alpha = 0.95f }
                .then(Modifier)
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(blob)) {
                drawRoundRect(
                    color = color,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        x = blob.toPx() * (0.18f + phase * 0.28f),
                        y = blob.toPx() * (0.18f + phase * 0.28f)
                    )
                )
            }
        }
        Box(
            Modifier
                .size(blob * 0.82f)
                .scale(0.72f + (1f - phase) * 0.28f)
                .rotate(-phase * 52f)
                .graphicsLayer { alpha = 0.9f }
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(blob * 0.82f)) {
                drawCircle(secondary)
            }
        }
        Box(
            Modifier
                .size(blob * 0.62f)
                .scale(0.78f + phase * 0.22f)
                .rotate(phase * 70f)
                .graphicsLayer { alpha = 0.9f }
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(blob * 0.62f)) {
                drawRoundRect(
                    color = tertiary,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        x = blob.toPx() * 0.18f,
                        y = blob.toPx() * 0.18f
                    )
                )
            }
        }
    }
}
