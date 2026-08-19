package com.localaisearch.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/**
 * Physics-based animation utilities.
 * Provides reusable composable animation helpers.
 */
object PhysicsAnimation {

    /**
     * Remember a scale animation that responds to state changes.
     * Uses spring physics for a natural, weighty feel.
     */
    @Composable
    fun rememberScaleAnimation(
        target: Float,
        spec: androidx.compose.animation.core.SpringSpec<Float> = SpringSpecs.bouncy
    ): Float {
        val animatable = remember { Animatable(target) }
        LaunchedEffect(target) {
            animatable.animateTo(target, spec)
        }
        return animatable.value
    }

    /**
     * Apply a spring-based scale transformation.
     */
    @Composable
    fun Modifier.springScale(
        scale: Float,
        spec: androidx.compose.animation.core.SpringSpec<Float> = SpringSpecs.bouncy
    ): Modifier {
        val animatable = remember { Animatable(scale) }
        LaunchedEffect(scale) {
            animatable.animateTo(scale, spec)
        }
        return this.graphicsLayer {
            this.scaleX = animatable.value
            this.scaleY = animatable.value
        }
    }

    /**
     * Apply a spring-based alpha transformation.
     */
    @Composable
    fun Modifier.springAlpha(
        alpha: Float,
        spec: androidx.compose.animation.core.SpringSpec<Float> = SpringSpecs.gentle
    ): Modifier {
        val animatable = remember { Animatable(alpha) }
        LaunchedEffect(alpha) {
            animatable.animateTo(alpha, spec)
        }
        return this.graphicsLayer {
            this.alpha = animatable.value
        }
    }
}

/**
 * Infinite animation helpers for continuous effects (breathing, flowing).
 */
object InfiniteAnimations {

    /**
     * Breathing animation - slow scale pulse.
     * Used for the Orb's idle state.
     */
    @Composable
    fun breathingPulse(
        min: Float = 0.95f,
        max: Float = 1.05f,
        durationMs: Int = AnimDurations.ORB_BREATHING
    ): Float {
        val transition = rememberInfiniteTransition(label = "breathing")
        return transition.animateFloat(
            initialValue = min,
            targetValue = max,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathingScale"
        ).value
    }

    /**
     * Rotation animation for flowing effect.
     * Used for the Orb's analyzing state.
     */
    @Composable
    fun flowingRotation(durationMs: Int = 4000): Float {
        val transition = rememberInfiniteTransition(label = "flowing")
        return transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "flowingRotation"
        ).value
    }

    /**
     * Wave animation for search expansion.
     * Returns multiple expanding rings.
     */
    @Composable
    fun expandingWave(durationMs: Int = 2000): List<Float> {
        val transition = rememberInfiniteTransition(label = "wave")
        return (0..2).map { index ->
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMs, delayMillis = index * 400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "wave_$index"
            ).value
        }
    }

    /**
     * Morphing shape animation - corner radius oscillation.
     */
    @Composable
    fun morphingShape(durationMs: Int = 3000): Float {
        val transition = rememberInfiniteTransition(label = "morph")
        return transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "morphShape"
        ).value
    }

    /**
     * Flow offset for internal orb particles.
     */
    @Composable
    fun flowOffset(count: Int = 5, durationMs: Int = 2000): List<Float> {
        val transition = rememberInfiniteTransition(label = "flow")
        return (0 until count).map { index ->
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMs, delayMillis = index * (durationMs / count), easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "flow_$index"
            ).value
        }
    }
}
