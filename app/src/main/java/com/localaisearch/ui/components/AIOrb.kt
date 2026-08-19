package com.localaisearch.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.localaisearch.data.model.AgentState
import kotlin.math.cos
import kotlin.math.sin

enum class ModelState { NO_MODEL, LOADING, LOADED, ERROR }

/**
 * Material 3 inspired ambient light field. It deliberately is not a sphere/card:
 * particles and glow extend beyond the visual core, making the home state feel alive.
 */
@Composable
fun AIOrb(
    state: AgentState,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    animationLevel: String = "standard",
    modelState: ModelState = ModelState.LOADED,
    modelName: String = "",
    tokensPerSecond: Float = 0f,
    loadProgress: Float = 0f,
    onSelectModel: (() -> Unit)? = null,
    onReloadModel: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "m3ExpressiveLight")
    val phase by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(7200, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "phase")
    val pulse by transition.animateFloat(0.90f, 1.08f, infiniteRepeatable(tween(if (modelState == ModelState.LOADING) 900 else 2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val drift by transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(if (modelState == ModelState.LOADING) 1200 else 4200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    val motion = when (animationLevel) { "off" -> 0f; "low" -> .35f; "high" -> 1.35f; else -> 1f }
    val active = modelState == ModelState.LOADING || state != AgentState.IDLE

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val base = minOf(size.width, size.height) * .28f
            val dynamicScale = pulse * (if (active) 1.12f else 1f)
            val center = Offset(cx + drift * base * .12f * motion, cy - drift * base * .08f * motion)

            // Broad ambient glow; no enclosing card or hard sphere.
            drawCircle(
                brush = Brush.radialGradient(
                    0f to scheme.primary.copy(alpha = if (active) .34f else .20f),
                    .38f to scheme.tertiary.copy(alpha = if (active) .18f else .10f),
                    1f to Color.Transparent
                ),
                radius = base * 2.35f * dynamicScale,
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = if (active) .28f else .16f),
                    .34f to scheme.primary.copy(alpha = .18f),
                    1f to Color.Transparent
                ),
                radius = base * .95f * dynamicScale,
                center = center
            )

            // Free particles escape the core so it reads as light, not plastic.
            val colors = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primary.copy(alpha = .72f))
            repeat(16) { i ->
                val a = (i * 22.5f + phase * (if (i % 2 == 0) 1f else -.72f)) * Math.PI / 180.0
                val orbit = base * (1.05f + (i % 4) * .24f)
                val wobble = sin((phase * .017 + i).toDouble()).toFloat() * base * .10f * motion
                val x = cx + cos(a).toFloat() * (orbit + wobble)
                val y = cy + sin(a).toFloat() * (orbit * .72f + wobble)
                val r = base * (.025f + (i % 3) * .009f) * (if (active) 1.25f else 1f)
                drawCircle(colors[i % colors.size].copy(alpha = if (active) .72f else .48f), r, Offset(x, y))
            }

            // Thin expressive arcs give the loading state a visible sense of motion.
            if (modelState == ModelState.LOADING) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primary)),
                    startAngle = phase,
                    sweepAngle = 225f,
                    useCenter = false,
                    topLeft = Offset(cx - base * 1.15f, cy - base * 1.15f),
                    size = androidx.compose.ui.geometry.Size(base * 2.3f, base * 2.3f),
                    style = Stroke(width = base * .055f)
                )
            }
        }
        if (modelState == ModelState.ERROR) {
            Text("!", style = MaterialTheme.typography.titleLarge, color = scheme.error)
        }
    }
}
