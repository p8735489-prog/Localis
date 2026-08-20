package com.localaisearch.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.localaisearch.data.model.AgentState
import kotlin.math.cos
import kotlin.math.sin

enum class ModelState { NO_MODEL, LOADING, LOADED, ERROR }

/**
 * Material 3 Expressive ambient "light" field. It is deliberately not a
 * sphere/card: the visible boundary is a soft liquid-glass blob whose edge
 * keeps shifting, and particles/glow extend past that boundary.
 *
 * Animation-continuity contract: every animated quantity (rotation, blob
 * wobble, pulse, particle drift) runs on ONE continuous infinite clock
 * ([phase]) that never restarts and never changes its own period. State
 * changes (idle -> loading -> loaded/error) only ever retarget a handful of
 * smoothly-interpolated *intensity* values via [animateFloatAsState] (
 * [loadIntensity], [activeIntensity]). Those intensities scale the same
 * continuous waveforms rather than swapping to a different animation, so a
 * transition never resets phase or snaps — the shape simply "spins up" or
 * "relaxes" into the next state, like the same liquid glass reshaping
 * itself rather than one animation cutting to another.
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

    // Single continuous clock. Its own period never changes with state, so
    // retargeting never causes InfiniteTransition to restart mid-cycle.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    // Slower secondary clock for blob wobble (kept independent of `phase`
    // so the boundary reads as liquid rather than perfectly periodic).
    val wobblePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "wobblePhase"
    )
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    val motion = when (animationLevel) { "off" -> 0f; "low" -> .35f; "high" -> 1.35f; else -> 1f }
    val stateActive = modelState == ModelState.LOADING || state != AgentState.IDLE

    // Smoothly-interpolated intensities: the ONLY thing that changes across
    // model/agent state transitions. Everything downstream keeps using the
    // same continuous `phase`/`wobblePhase`/`breathe` clocks, just scaled by
    // these, so before/after always line up on the same waveform.
    val loadIntensity by animateFloatAsState(
        targetValue = if (modelState == ModelState.LOADING) 1f else 0f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "loadIntensity"
    )
    val activeIntensity by animateFloatAsState(
        targetValue = if (stateActive) 1f else 0f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "activeIntensity"
    )
    val errorIntensity by animateFloatAsState(
        targetValue = if (modelState == ModelState.ERROR) 1f else 0f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "errorIntensity"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val base = minOf(this.size.width, this.size.height) * .28f

            // Rotation speed rises smoothly with load/activity intensity —
            // never a hard cut to a new tween duration.
            val spinSpeed = 1f + loadIntensity * 2.2f + activeIntensity * 0.8f
            val rotation = (phase * spinSpeed) % 360f

            val pulseScale = 1f + breathe * 0.09f * (1f + activeIntensity * 0.35f)
            val driftAngle = (wobblePhase * 0.6f) * Math.PI / 180.0
            val driftAmount = base * .11f * motion * (1f - loadIntensity * 0.4f)
            val center = Offset(
                cx + cos(driftAngle).toFloat() * driftAmount,
                cy + sin(driftAngle).toFloat() * driftAmount * .7f
            )

            // --- Ambient glow (extends well past the blob boundary) ---
            drawCircle(
                brush = Brush.radialGradient(
                    0f to scheme.primary.copy(alpha = .20f + activeIntensity * .16f),
                    .38f to scheme.tertiary.copy(alpha = .10f + activeIntensity * .09f),
                    1f to Color.Transparent
                ),
                radius = base * 2.35f * pulseScale * (1f + activeIntensity * .12f),
                center = center
            )

            // --- Liquid-glass blob boundary: an irregular, continuously
            // reshaping closed curve instead of a perfect circle. Loading
            // increases both the number of visible "lobes" and their
            // amplitude, so the morph reads as intentional deformation
            // rather than jitter. ---
            val lobes = 6
            val amplitude = base * (0.06f + loadIntensity * 0.16f) * motion
            val points = (0 until lobes).map { i ->
                val angleDeg = rotation + i * (360f / lobes)
                val a = angleDeg * Math.PI / 180.0
                val w = sin((wobblePhase * .026 + i * 1.7).toDouble()).toFloat()
                val r = base * pulseScale + amplitude * w
                Offset(center.x + cos(a).toFloat() * r, center.y + sin(a).toFloat() * r * .94f)
            }
            val blobPath = Path().apply {
                val mid0 = Offset((points[0].x + points.last().x) / 2f, (points[0].y + points.last().y) / 2f)
                moveTo(mid0.x, mid0.y)
                for (i in points.indices) {
                    val p0 = points[i]
                    val p1 = points[(i + 1) % points.size]
                    val mid = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                    quadraticBezierTo(p0.x, p0.y, mid.x, mid.y)
                }
                close()
            }
            drawPath(
                path = blobPath,
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = .30f + activeIntensity * .10f),
                    .45f to scheme.primary.copy(alpha = .30f + errorIntensity * .10f),
                    1f to scheme.tertiary.copy(alpha = .16f),
                    center = center,
                    radius = base * 1.3f
                )
            )
            // Thin glass-edge highlight so the boundary reads as a surface,
            // not a flat fill.
            drawPath(
                path = blobPath,
                brush = Brush.sweepGradient(
                    listOf(
                        Color.White.copy(alpha = .55f),
                        scheme.primary.copy(alpha = .05f),
                        scheme.tertiary.copy(alpha = .35f),
                        Color.White.copy(alpha = .55f)
                    ),
                    center = center
                ),
                style = Stroke(width = base * (.045f + loadIntensity * .02f))
            )

            // --- Free particles escape the blob so it reads as light, not
            // as plastic; their orbit radius/alpha fade continuously with
            // activeIntensity instead of appearing/disappearing. ---
            val colors = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primary.copy(alpha = .72f))
            repeat(16) { i ->
                val a = (i * 22.5f + phase * (if (i % 2 == 0) 1f else -.72f)) * Math.PI / 180.0
                val orbit = base * (1.05f + (i % 4) * .24f)
                val wobble = sin((phase * .017 + i).toDouble()).toFloat() * base * .10f * motion
                val x = cx + cos(a).toFloat() * (orbit + wobble)
                val y = cy + sin(a).toFloat() * (orbit * .72f + wobble)
                val r = base * (.022f + (i % 3) * .008f) * (1f + activeIntensity * .35f)
                val alpha = (.34f + activeIntensity * .38f)
                drawCircle(colors[i % colors.size].copy(alpha = alpha), r, Offset(x, y))
            }

            // --- Loading sweep: fades in/out with loadIntensity (alpha),
            // and rides the SAME `rotation` value the blob lobes use, so
            // the arc and the blob boundary always agree on where "now" is
            // — no separate clock to fall out of sync with. ---
            if (loadIntensity > 0.01f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primary)),
                    startAngle = rotation,
                    sweepAngle = 225f,
                    useCenter = false,
                    topLeft = Offset(cx - base * 1.15f, cy - base * 1.15f),
                    size = androidx.compose.ui.geometry.Size(base * 2.3f, base * 2.3f),
                    style = Stroke(width = base * .05f),
                    alpha = loadIntensity
                )
            }
        }
        if (modelState == ModelState.ERROR) {
            Text("!", style = MaterialTheme.typography.titleLarge, color = scheme.error)
        }
    }
}
