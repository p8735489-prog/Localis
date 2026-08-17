package com.localaisearch.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradient
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.localaisearch.data.model.AgentState
import com.localaisearch.ui.animation.AnimDurations
import com.localaisearch.ui.animation.InfiniteAnimations
import com.localaisearch.ui.animation.SpringSpecs
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The central AI Orb - the visual heart of the app.
 *
 * Displays different animations based on the current AgentState:
 * - IDLE: Breathing animation (slow scale pulse with soft glow)
 * - THINKING: Slight morphing (organic shape distortion)
 * - SEARCHING: Outward expanding rings (search signal)
 * - READING: Particles flowing inward (ingesting sources)
 * - ANALYZING: Internal flowing pattern (processing)
 * - VALIDATING: Gentle pulse with rings
 * - ANSWERING: Steady glow with slight pulse
 * - DONE: Spring bounce back
 * - ERROR: Red tint with shake
 *
 * All animations use Spring physics and Canvas drawing.
 * No plain rotation loaders.
 */
@Composable
fun AIOrb(
    state: AgentState,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    glowColor: Color
) {
    // -- State-driven animation values --
    val scaleAnim = remember { Animatable(1f) }
    val morphAnim = remember { Animatable(0f) }
    val glowAnim = remember { Animatable(0.3f) }

    // -- Infinite animations --
    val breathingScale = InfiniteAnimations.breathingPulse(
        min = 0.96f, max = 1.04f, durationMs = AnimDurations.ORB_BREATHING
    )
    val morphingShape = InfiniteAnimations.morphingShape(durationMs = 4000)
    val flowingRotation = InfiniteAnimations.flowingRotation(durationMs = 6000)
    val expandingWaves = InfiniteAnimations.expandingWave(durationMs = 1800)
    val flowOffsets = InfiniteAnimations.flowOffset(count = 6, durationMs = 2500)

    // -- Particles for reading state --
    val particles = remember {
        List(8) { i ->
            ParticleData(
                angle = (i * 45f),
                distance = 1.5f,
                speed = 0.5f + Random.nextFloat() * 0.5f
            )
        }
    }

    // -- Trigger state transitions --
    LaunchedEffect(state) {
        when (state) {
            AgentState.IDLE -> {
                scaleAnim.animateTo(1f, SpringSpecs.gentle)
                morphAnim.animateTo(0f, SpringSpecs.gentle)
                glowAnim.animateTo(0.3f, SpringSpecs.gentle)
            }
            AgentState.THINKING -> {
                scaleAnim.animateTo(1.02f, SpringSpecs.bouncy)
                morphAnim.animateTo(0.15f, SpringSpecs.gentle)
                glowAnim.animateTo(0.5f, SpringSpecs.gentle)
            }
            AgentState.SEARCHING -> {
                scaleAnim.animateTo(1.08f, SpringSpecs.elastic)
                morphAnim.animateTo(0.1f, SpringSpecs.gentle)
                glowAnim.animateTo(0.7f, SpringSpecs.gentle)
            }
            AgentState.READING -> {
                scaleAnim.animateTo(0.98f, SpringSpecs.bouncy)
                morphAnim.animateTo(0.2f, SpringSpecs.gentle)
                glowAnim.animateTo(0.6f, SpringSpecs.gentle)
            }
            AgentState.ANALYZING -> {
                scaleAnim.animateTo(1.0f, SpringSpecs.bouncy)
                morphAnim.animateTo(0.25f, SpringSpecs.gentle)
                glowAnim.animateTo(0.65f, SpringSpecs.gentle)
            }
            AgentState.VALIDATING -> {
                scaleAnim.animateTo(1.03f, SpringSpecs.bouncy)
                morphAnim.animateTo(0.15f, SpringSpecs.gentle)
                glowAnim.animateTo(0.55f, SpringSpecs.gentle)
            }
            AgentState.ANSWERING -> {
                scaleAnim.animateTo(1.0f, SpringSpecs.gentle)
                morphAnim.animateTo(0.05f, SpringSpecs.gentle)
                glowAnim.animateTo(0.45f, SpringSpecs.gentle)
            }
            AgentState.DONE -> {
                scaleAnim.animateTo(1f, SpringSpecs.completion)
                morphAnim.animateTo(0f, SpringSpecs.completion)
                glowAnim.animateTo(0.3f, SpringSpecs.completion)
            }
            AgentState.ERROR -> {
                scaleAnim.animateTo(0.95f, SpringSpecs.snappy)
                morphAnim.animateTo(0.3f, SpringSpecs.snappy)
                glowAnim.animateTo(0.8f, SpringSpecs.snappy)
            }
        }
    }

    // -- Combined scale based on state --
    val effectiveScale = when (state) {
        AgentState.IDLE -> scaleAnim.value * breathingScale
        AgentState.THINKING -> scaleAnim.value * (1f + morphingShape * 0.02f)
        AgentState.SEARCHING -> scaleAnim.value * (1f + sin(System.currentTimeMillis() / 300.0).toFloat() * 0.03f)
        AgentState.READING -> scaleAnim.value * (1f - morphingShape * 0.03f)
        AgentState.ANALYZING -> scaleAnim.value * (1f + sin(System.currentTimeMillis() / 500.0).toFloat() * 0.02f)
        AgentState.VALIDATING -> scaleAnim.value * (1f + sin(System.currentTimeMillis() / 400.0).toFloat() * 0.02f)
        AgentState.ANSWERING -> scaleAnim.value * breathingScale
        AgentState.DONE -> scaleAnim.value * (1f + (1f - scaleAnim.value) * 0.5f)
        AgentState.ERROR -> scaleAnim.value
    }

    val isError = state == AgentState.ERROR

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = effectiveScale
                scaleY = effectiveScale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(size)
        ) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 2f * 0.7f
            val isErr = isError

            // Determine colors based on state
            val pColor = if (isErr) Color(0xFFEF4444) else primaryColor
            val sColor = if (isErr) Color(0xFFDC2626) else secondaryColor
            val aColor = if (isErr) Color(0xFFF87171) else accentColor
            val gColor = if (isErr) Color(0xFFEF4444) else glowColor

            // -- Draw expanding waves (searching state) --
            if (state == AgentState.SEARCHING || state == AgentState.VALIDATING) {
                expandingWaves.forEachIndexed { index, progress ->
                    val waveRadius = baseRadius * (1f + progress * 1.5f)
                    val waveAlpha = (1f - progress) * 0.4f
                    drawCircle(
                        color = gColor.copy(alpha = waveAlpha),
                        radius = waveRadius,
                        center = center,
                        style = Stroke(width = 2f)
                    )
                }
            }

            // -- Draw outer glow --
            val glowRadius = baseRadius * (1.4f + glowAnim.value * 0.3f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        gColor.copy(alpha = glowAnim.value * 0.3f),
                        gColor.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = glowRadius
                ),
                center = center,
                radius = glowRadius
            )

            // -- Draw main orb body with radial gradient --
            val morphRadius = baseRadius * (1f + morphAnim.value * 0.05f * sin(System.currentTimeMillis() / 1000.0).toFloat())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        aColor.copy(alpha = 0.95f),
                        pColor.copy(alpha = 0.9f),
                        sColor.copy(alpha = 0.85f)
                    ),
                    center = Offset(center.x - baseRadius * 0.2f, center.y - baseRadius * 0.2f),
                    radius = morphRadius
                ),
                center = center,
                radius = morphRadius
            )

            // -- Draw highlight (top-left specular) --
            val highlightOffset = Offset(
                center.x - baseRadius * 0.3f,
                center.y - baseRadius * 0.35f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.White.copy(alpha = 0f)
                    ),
                    center = highlightOffset,
                    radius = baseRadius * 0.4f
                ),
                center = highlightOffset,
                radius = baseRadius * 0.4f
            )

            // -- Draw flowing particles (analyzing state) --
            if (state == AgentState.ANALYZING || state == AgentState.ANSWERING) {
                val rotationRad = flowingRotation * (Math.PI.toFloat() / 180f)
                flowOffsets.forEachIndexed { index, progress ->
                    val angle = rotationRad + (index * (360f / flowOffsets.size)) * (Math.PI.toFloat() / 180f)
                    val dist = baseRadius * 0.5f * (0.5f + progress * 0.5f)
                    val px = center.x + cos(angle) * dist
                    val py = center.y + sin(angle) * dist
                    val particleAlpha = (1f - progress) * 0.6f + 0.2f
                    drawCircle(
                        color = Color.White.copy(alpha = particleAlpha),
                        radius = 3f + progress * 2f,
                        center = Offset(px, py)
                    )
                }
            }

            // -- Draw inward-flowing particles (reading state) --
            if (state == AgentState.READING) {
                particles.forEach { particle ->
                    val progress = (System.currentTimeMillis() / 1000f * particle.speed) % 1f
                    val angle = particle.angle * (Math.PI.toFloat() / 180f)
                    val startDist = baseRadius * 1.3f
                    val endDist = baseRadius * 0.3f
                    val currentDist = startDist * (1f - progress) + endDist * progress
                    val px = center.x + cos(angle) * currentDist
                    val py = center.y + sin(angle) * currentDist
                    val particleAlpha = (1f - progress) * 0.8f
                    drawCircle(
                        color = aColor.copy(alpha = particleAlpha),
                        radius = 4f * (1f - progress * 0.5f),
                        center = Offset(px, py)
                    )
                }
            }

            // -- Draw morphing rings (thinking state) --
            if (state == AgentState.THINKING) {
                val ringProgress = (System.currentTimeMillis() % 2000) / 2000f
                drawCircle(
                    color = aColor.copy(alpha = 0.2f * (1f - ringProgress)),
                    radius = baseRadius * (1f + ringProgress * 0.2f),
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            // -- Draw inner core glow --
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = baseRadius * 0.5f
                ),
                center = center,
                radius = baseRadius * 0.5f
            )
        }
    }
}

// -- Helper data class for particles --
private data class ParticleData(
    val angle: Float,
    val distance: Float,
    val speed: Float
)
