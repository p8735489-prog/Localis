package com.localaisearch.ui.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Centralized spring animation specs for the entire app.
 *
 * Inspired by Agora-style physics: bouncy, weighty, elastic.
 * No plain rotation loaders - everything uses spring physics.
 */
object SpringSpecs {

    // ── Standard Springs ──

    /** Gentle spring for subtle UI changes */
    val gentle = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** Bouncy spring for cards and interactive elements */
    val bouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Highly bouncy spring for the Orb and playful elements */
    val elastic = spring<Float>(
        dampingRatio = Spring.DampingRatioHighBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Snappy spring for quick transitions */
    val snappy = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    /** Slow, weighty spring for emphasis */
    val weighty = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessVeryLow
    )

    /** Morphing spring for button shape changes */
    val morph = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = 380f
    )

    /** Orb breathing spring - very slow and gentle */
    val breathing = spring<Float>(
        dampingRatio = 1f,
        stiffness = 50f
    )

    /** Completion spring - high bounce for the "done" effect */
    val completion = spring<Float>(
        dampingRatio = Spring.DampingRatioHighBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // ── Dp Springs (for Dp-based animations) ──

    val gentleDp = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val bouncyDp = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val morphDp = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = 0.8f,
        stiffness = 380f
    )

    val elasticDp = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioHighBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // ── Int Spring ──

    val bouncyInt = spring<Int>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // ── Custom Easings ──

    /** Emphasized easing for Material 3 motion */
    val emphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Emphasized decelerate */
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Emphasized accelerate */
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // ── Tween Specs ──

    /** Fade in duration */
    val fadeIn = tween<Float>(durationMillis = 300, easing = emphasizedDecelerate)

    /** Fade out duration */
    val fadeOut = tween<Float>(durationMillis = 200, easing = emphasizedAccelerate)

    /** Scale in for cards */
    val scaleIn = tween<Float>(durationMillis = 350, easing = emphasizedDecelerate)
}

/**
 * Animation durations in milliseconds.
 */
object AnimDurations {
    const val FAST = 150
    const val NORMAL = 300
    const val SLOW = 500
    const val ORB_BREATHING = 3000
    const val ORB_THINKING = 2000
    const val ORB_SEARCHING = 1500
    const val ORB_COMPLETION = 800
}

/**
 * Morphing transition data for shape changes.
 */
data class MorphData(
    val cornerRadius: Float,
    val scale: Float,
    val rotation: Float,
    val alpha: Float
) {
    companion object {
        val Idle = MorphData(cornerRadius = 28f, scale = 1f, rotation = 0f, alpha = 1f)
        val Active = MorphData(cornerRadius = 16f, scale = 0.92f, rotation = 0f, alpha = 1f)
        val Loading = MorphData(cornerRadius = 24f, scale = 0.95f, rotation = 0f, alpha = 0.8f)
        val Done = MorphData(cornerRadius = 32f, scale = 1.05f, rotation = 0f, alpha = 1f)
    }
}
