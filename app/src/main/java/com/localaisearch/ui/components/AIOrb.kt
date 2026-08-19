package com.localaisearch.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.localaisearch.R
import com.localaisearch.data.model.AgentState
import com.localaisearch.ui.animation.SpringSpecs
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Model loading state for the AI Core orb.
 */
enum class ModelState {
    NO_MODEL,
    LOADING,
    LOADED,
    ERROR
}

/**
 * AI Core - A 3D-like liquid glass sphere with organic color flow.
 *
 * Visual identity:
 * - Distinct spherical body with volume, depth, and surface
 * - Soft cyan-blue-purple-pink color palette, naturally blended
 * - Liquid glass surface with soft highlights and internal refraction
 * - Gentle ambient glow serving the sphere, not replacing it
 * - State-responsive micro-animations (idle/thinking/searching/output)
 * - GPU-first Canvas rendering, no per-frame Bitmap creation
 *
 * @param state Agent execution state (thinking/searching/etc.)
 * @param modelState Model loading state (no model / loading / loaded / error)
 * @param modelName Name of the currently loaded model (shown when loaded)
 * @param tokensPerSecond Real-time token generation speed (shown when active)
 * @param loadProgress Model loading progress 0.0f..1.0f (shown when loading)
 * @param onSelectModel Callback for "Select Model" button (shown when NO_MODEL)
 * @param onReloadModel Callback for "Reload" button (shown when ERROR)
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
    // Theme-aware palette: dark mode uses deeper tones instead of bright plastic-like whites.
    val colorScheme = MaterialTheme.colorScheme
    val darkSurface = colorScheme.background.luminance() < 0.5f
    val coreCyan = if (darkSurface) Color(0xFF5CC8D6) else Color(0xFF42A5B8)
    val coreBlue = if (darkSurface) Color(0xFF7899E8) else Color(0xFF5B73B8)
    val corePurple = if (darkSurface) Color(0xFF9B82D8) else Color(0xFF7D68B7)
    val corePink = if (darkSurface) Color(0xFFD17FB1) else Color(0xFFB56B96)
    val coreIndigo = if (darkSurface) Color(0xFF7482D8) else Color(0xFF6674B5)

    // Animation intensity based on level
    val animScale = when (animationLevel) {
        "off" -> 0f
        "low" -> 0.4f
        "standard" -> 1f
        "high" -> 1.3f
        else -> 1f
    }

    // The orb remains visually stable when Android's notification shade changes window focus.
    // Animation is already throttled by Compose/lifecycle, so focus is not used as a render gate.

    // State-driven animation values
    val scaleAnim = remember { Animatable(1f) }
    val glowAnim = remember { Animatable(0.4f) }
    val speedAnim = remember { Animatable(1f) }

    // Model state driven animation values
    val rotationAnim = remember { Animatable(0f) }
    val modelGlowAnim = remember { Animatable(0.4f) }

    // -- Infinite base animations (only when visible) --
    val infiniteTransition = rememberInfiniteTransition(label = "aiCore")

    // Breathing: slow sphere pulsation (8s cycle)
    val breathing by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(7600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Organic phase: the interior should drift like fluid, not rotate as one flat gradient.
    val colorRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "organicFlowPhase"
    )

    // Surface highlight drift (7.2s cycle)
    val highlightDrift by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(7200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "highlightDrift"
    )

    // A second, slower phase prevents all internal blobs from moving together.
    val flowTurbulence by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(11800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowTurbulence"
    )

    // Rotation animation for loading state
    val loadingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingRotation"
    )

    // -- State-driven transitions (AgentState + ModelState) --
    LaunchedEffect(state, modelState) {
        when (modelState) {
            ModelState.NO_MODEL -> {
                launch { scaleAnim.animateTo(0.95f, SpringSpecs.gentle) }
                launch { glowAnim.animateTo(0.3f, SpringSpecs.gentle) }
                launch { speedAnim.animateTo(0.6f, SpringSpecs.gentle) }
                launch { rotationAnim.animateTo(0f, SpringSpecs.gentle) }
                launch { modelGlowAnim.animateTo(0.3f, SpringSpecs.gentle) }
            }
            ModelState.LOADING -> {
                launch { scaleAnim.animateTo(1.06f, SpringSpecs.bouncy) }
                launch { glowAnim.animateTo(0.75f, SpringSpecs.gentle) }
                launch { speedAnim.animateTo(1.8f, SpringSpecs.gentle) }
                launch { rotationAnim.animateTo(360f, SpringSpecs.gentle) }
                launch { modelGlowAnim.animateTo(0.8f, SpringSpecs.gentle) }
            }
            ModelState.LOADED -> {
                launch { scaleAnim.animateTo(1f, SpringSpecs.gentle) }
                launch { glowAnim.animateTo(0.4f, SpringSpecs.gentle) }
                launch { speedAnim.animateTo(1f, SpringSpecs.gentle) }
                launch { rotationAnim.animateTo(0f, SpringSpecs.gentle) }
                launch { modelGlowAnim.animateTo(0.4f, SpringSpecs.gentle) }
            }
            ModelState.ERROR -> {
                launch { scaleAnim.animateTo(0.95f, SpringSpecs.snappy) }
                launch { glowAnim.animateTo(0.85f, SpringSpecs.snappy) }
                launch { speedAnim.animateTo(0.5f, SpringSpecs.snappy) }
                launch { rotationAnim.animateTo(0f, SpringSpecs.snappy) }
                launch { modelGlowAnim.animateTo(0.9f, SpringSpecs.snappy) }
            }
        }
        // Agent state further modulates animations when model is loaded
        if (modelState == ModelState.LOADED) {
            when (state) {
                AgentState.IDLE -> {
                    launch { scaleAnim.animateTo(1f, SpringSpecs.gentle) }
                    launch { glowAnim.animateTo(0.4f, SpringSpecs.gentle) }
                    launch { speedAnim.animateTo(1f, SpringSpecs.gentle) }
                }
                AgentState.THINKING -> {
                    launch { scaleAnim.animateTo(1.03f, SpringSpecs.bouncy) }
                    launch { glowAnim.animateTo(0.55f, SpringSpecs.gentle) }
                    launch { speedAnim.animateTo(1.3f, SpringSpecs.gentle) }
                }
                AgentState.SEARCHING -> {
                    launch { scaleAnim.animateTo(1.05f, SpringSpecs.elastic) }
                    launch { glowAnim.animateTo(0.7f, SpringSpecs.gentle) }
                    launch { speedAnim.animateTo(1.5f, SpringSpecs.gentle) }
                }
                AgentState.READING -> {
                    launch { scaleAnim.animateTo(1.02f, SpringSpecs.bouncy) }
                    launch { glowAnim.animateTo(0.6f, SpringSpecs.gentle) }
                    launch { speedAnim.animateTo(1.2f, SpringSpecs.gentle) }
                }
                AgentState.ANALYZING -> {
                    launch { scaleAnim.animateTo(1.0f, SpringSpecs.bouncy) }
                    launch { glowAnim.animateTo(0.65f, SpringSpecs.gentle) }
                    launch { speedAnim.animateTo(1.1f, SpringSpecs.gentle) }
                }
                AgentState.VALIDATING -> {
                    launch { scaleAnim.animateTo(1.02f, SpringSpecs.bouncy) }
                    launch { glowAnim.animateTo(0.6f, SpringSpecs.gentle) }
                    launch { speedAnim.animateTo(1.0f, SpringSpecs.gentle) }
                }
                AgentState.ANSWERING -> {
                    launch { scaleAnim.animateTo(1.0f, SpringSpecs.gentle) }
                    launch { glowAnim.animateTo(0.5f, SpringSpecs.gentle) }
                    launch { speedAnim.animateTo(1.0f, SpringSpecs.gentle) }
                }
                AgentState.DONE -> {
                    launch { scaleAnim.animateTo(1f, SpringSpecs.completion) }
                    launch { glowAnim.animateTo(0.4f, SpringSpecs.completion) }
                    launch { speedAnim.animateTo(1f, SpringSpecs.completion) }
                }
                AgentState.ERROR -> {
                    launch { scaleAnim.animateTo(0.97f, SpringSpecs.snappy) }
                    launch { glowAnim.animateTo(0.8f, SpringSpecs.snappy) }
                    launch { speedAnim.animateTo(0.5f, SpringSpecs.snappy) }
                }
            }
        }
    }

    val effectiveScale = scaleAnim.value * breathing
    val currentGlow = glowAnim.value * animScale
    val effectiveSpeed = speedAnim.value * animScale
    val modelRotation = (rotationAnim.value + if (modelState == ModelState.LOADING) loadingRotation else 0f) % 360f
    val isError = state == AgentState.ERROR || modelState == ModelState.ERROR

    Box(
        modifier = modifier
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasCenter = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 2f * 0.48f
            val center = canvasCenter

            // Keep motion mostly local. A tiny shared drift creates life without looking like a spinner.
            val sphereRadius = baseRadius * effectiveScale
            val rotationRad = colorRotation * (0.16f + effectiveSpeed * 0.04f)
            val organicX = sin(colorRotation * 0.71f) * sphereRadius * 0.055f
            val organicY = cos(flowTurbulence * 0.83f) * sphereRadius * 0.045f

            // === 1. Outer ambient glow (very subtle, serves the sphere) ===
            if (animScale > 0.1f) {
                val glowRadius = baseRadius * (2.1f + currentGlow * 0.85f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isError) Color(0xFFEF4444).copy(alpha = currentGlow * 0.18f)
                            else if (modelState == ModelState.LOADING) coreCyan.copy(alpha = currentGlow * 0.18f)
                            else if (modelState == ModelState.NO_MODEL) coreBlue.copy(alpha = currentGlow * 0.06f)
                            else corePurple.copy(alpha = currentGlow * 0.08f),
                            coreBlue.copy(alpha = currentGlow * 0.07f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = glowRadius
                    ),
                    center = center,
                    radius = glowRadius
                )
            }

            // === 2. Floating light field: the core is light, not a hard orb ===
            if (animScale > 0.05f && !isError) {
                val lightColors = listOf(coreCyan, coreBlue, corePurple, corePink, coreIndigo)
                for (i in 0 until 10) {
                    val t = colorRotation * (0.55f + i * 0.035f) + i * 0.73f
                    val orbit = sphereRadius * (0.55f + 0.20f * sin(flowTurbulence * 0.45f + i))
                    val p = Offset(
                        center.x + cos(t) * orbit,
                        center.y + sin(t * 1.13f) * orbit * 0.72f
                    )
                    val r = sphereRadius * (0.10f + 0.035f * sin(flowTurbulence + i))
                    val c = lightColors[i % lightColors.size]
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                c.copy(alpha = 0.42f * currentGlow),
                                c.copy(alpha = 0.13f * currentGlow),
                                Color.Transparent
                            ),
                            center = p, radius = r * 2.8f
                        ),
                        center = p, radius = r * 2.8f
                    )
                }
                // A soft central light connects the moving particles into one living field.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            coreCyan.copy(alpha = 0.20f * currentGlow),
                            corePurple.copy(alpha = 0.10f * currentGlow),
                            Color.Transparent
                        ),
                        center = Offset(center.x - sphereRadius * 0.08f, center.y - sphereRadius * 0.04f),
                        radius = sphereRadius * 1.35f
                    ),
                    center = Offset(center.x - sphereRadius * 0.08f, center.y - sphereRadius * 0.04f),
                    radius = sphereRadius * 1.35f
                )
            }

            // === 2. Diffuse light ribbons: deliberately larger than the core ===
            if (animScale > 0.05f && !isError) {
                val ribbonColors = listOf(coreCyan, coreBlue, corePurple, corePink)
                for (i in 0 until 6) {
                    val phase = flowTurbulence * (0.22f + i * 0.025f) + i * 1.05f
                    val x = center.x + cos(phase) * baseRadius * (0.75f + i * 0.07f)
                    val y = center.y + sin(phase * 1.27f) * baseRadius * (0.62f + i * 0.06f)
                    val radius = baseRadius * (0.18f + 0.035f * sin(phase))
                    val c = ribbonColors[i % ribbonColors.size]
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                c.copy(alpha = 0.20f * currentGlow),
                                c.copy(alpha = 0.06f * currentGlow),
                                Color.Transparent
                            ),
                            center = Offset(x, y),
                            radius = radius * 3.2f
                        ),
                        center = Offset(x, y),
                        radius = radius * 3.2f
                    )
                }
            }

            // === 3. Main light core: translucent, not a solid ball ===

            // Base sphere gradient (gives 3D roundness)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (isError) Color(0xFFFCA5A5).copy(alpha = 0.9f)
                        else if (modelState == ModelState.LOADING) Color(0xFFE0F2FE).copy(alpha = 0.85f)
                        else if (modelState == ModelState.NO_MODEL) colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        else if (darkSurface) colorScheme.primaryContainer.copy(alpha = 0.92f)
                        else Color(0xFFE8E0F8).copy(alpha = 0.28f),
                        if (isError) Color(0xFFF87171).copy(alpha = 0.7f)
                        else if (modelState == ModelState.LOADING) Color(0xFFBAE6FD).copy(alpha = 0.6f)
                        else if (modelState == ModelState.NO_MODEL) colorScheme.outlineVariant.copy(alpha = 0.5f)
                        else if (darkSurface) colorScheme.secondaryContainer.copy(alpha = 0.72f)
                        else Color(0xFFD4C8F0).copy(alpha = 0.20f),
                        if (isError) Color(0xFFEF4444).copy(alpha = 0.3f)
                        else if (modelState == ModelState.LOADING) Color(0xFF7DD3FC).copy(alpha = 0.25f)
                        else if (modelState == ModelState.NO_MODEL) colorScheme.outline.copy(alpha = 0.2f)
                        else if (darkSurface) coreBlue.copy(alpha = 0.35f)
                        else Color(0xFFB8A8E0).copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - sphereRadius * 0.25f, center.y - sphereRadius * 0.3f),
                    radius = sphereRadius * 1.4f
                ),
                center = center,
                radius = sphereRadius * 0.86f
            )

            // === 3. Internal organic color blobs (liquid glass interior) ===
            if (animScale > 0.1f && !isError) {
                // Blob positions rotate slowly, creating internal flow
                val blobConfigs = listOf(
                    Triple(coreCyan, 0.35f, 0.4f),
                    Triple(coreBlue, 0.30f, 0.7f),
                    Triple(corePurple, 0.25f, 1.0f),
                    Triple(corePink, 0.20f, 1.3f),
                    Triple(coreIndigo, 0.18f, 1.6f)
                )

                blobConfigs.forEachIndexed { index, (color, relativeRadius, phase) ->
                    // Each blob follows a different phase. This prevents the orb from looking like
                    // one flat gradient rotating as a single layer.
                    val localPhase = flowTurbulence * (1.2f + index * 0.08f)
                    val blobAngle = rotationRad * (0.62f + index * 0.05f) +
                        (phase * PI).toFloat() +
                        sin(localPhase * PI.toFloat()) * 0.18f
                    val distance = sphereRadius * 0.35f * (
                        0.6f + 0.4f * sin(
                            flowTurbulence * 2f * PI.toFloat() + index * 1.2f
                        )
                    )
                    val blobCenter = Offset(
                        center.x + cos(blobAngle) * distance,
                        center.y + sin(blobAngle) * distance * 0.7f // Elliptical for 3D perspective
                    )
                    val blobRadius = sphereRadius * relativeRadius * (0.9f + 0.1f * sin(System.currentTimeMillis() / 3000f + phase).toFloat())

                    val alphaMultiplier = when (modelState) {
                        ModelState.NO_MODEL -> 0.3f
                        ModelState.LOADING -> 0.7f
                        else -> 1.0f
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = 0.5f * currentGlow * alphaMultiplier),
                                color.copy(alpha = 0.2f * currentGlow * alphaMultiplier),
                                Color.Transparent
                            ),
                            center = blobCenter,
                            radius = blobRadius
                        ),
                        center = blobCenter,
                        radius = blobRadius
                    )
                }
            }

            // === 4. Surface highlight (liquid glass reflection) ===
            val highlightOffset = Offset(
                center.x + highlightDrift * sphereRadius * 0.4f - sphereRadius * 0.25f,
                center.y - sphereRadius * 0.35f + highlightDrift * sphereRadius * 0.15f
            )
            val highlightRadius = sphereRadius * 0.28f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (if (darkSurface) 0.18f else 0.30f) * currentGlow),
                        Color.White.copy(alpha = (if (darkSurface) 0.07f else 0.12f) * currentGlow),
                        Color.Transparent
                    ),
                    center = highlightOffset,
                    radius = highlightRadius
                ),
                center = highlightOffset,
                radius = highlightRadius
            )

            // Secondary highlight (smaller, sharper)
            val secondaryHighlight = Offset(
                center.x + sphereRadius * 0.15f,
                center.y - sphereRadius * 0.45f
            )
            drawCircle(
                color = Color.White.copy(alpha = (if (darkSurface) 0.08f else 0.15f) * currentGlow),
                radius = sphereRadius * 0.08f,
                center = secondaryHighlight
            )

            // === 5. Bottom rim light (grounding the sphere) ===
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreBlue.copy(alpha = 0.15f * currentGlow),
                        corePurple.copy(alpha = 0.08f * currentGlow),
                        Color.Transparent
                    ),
                    center = Offset(center.x, center.y + sphereRadius * 0.6f),
                    radius = sphereRadius * 0.5f
                ),
                center = Offset(center.x, center.y + sphereRadius * 0.6f),
                radius = sphereRadius * 0.5f
            )

            // === 6. Edge rim (subtle border defining the sphere shape) ===
            if (animScale > 0.1f) {
                val rimColor = when {
                    isError -> Color(0xFFEF4444)
                    modelState == ModelState.LOADING -> coreCyan
                    modelState == ModelState.NO_MODEL -> Color(0xFF9CA3AF)
                    else -> coreBlue
                }
                drawCircle(
                    color = rimColor.copy(alpha = 0.025f * currentGlow),
                    radius = sphereRadius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                )
            }

            // === 7. Internal energy core (very subtle center glow) ===
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f * currentGlow),
                        Color.Transparent
                    ),
                    center = center,
                    radius = sphereRadius * 0.2f
                ),
                center = center,
                radius = sphereRadius * 0.2f
            )

            // === 8. Subtle orbit ring (only when active/searching/loading) ===
            if ((effectiveSpeed > 1.1f && !isError) || modelState == ModelState.LOADING) {
                val ringAlpha = if (modelState == ModelState.LOADING) {
                    0.15f * currentGlow
                } else {
                    min(0.06f * (effectiveSpeed - 1.1f) * 5f, 0.08f) * currentGlow
                }
                drawOrbitRing(
                    center = center,
                    radiusX = sphereRadius * 1.15f,
                    radiusY = sphereRadius * 0.85f,
                    rotation = if (modelState == ModelState.LOADING) (modelRotation * PI / 180f).toFloat() else rotationRad * 0.5f,
                    color = if (modelState == ModelState.LOADING) coreCyan.copy(alpha = ringAlpha)
                            else coreCyan.copy(alpha = ringAlpha)
                )
            }

            // === 9. Loading progress arc (when model loading) ===
            if (modelState == ModelState.LOADING && loadProgress > 0f) {
                val progressAngle = loadProgress * 360f
                val arcColor = coreCyan.copy(alpha = 0.6f * currentGlow)
                val arcPath = androidx.compose.ui.graphics.Path().apply {
                    val arcRadius = sphereRadius * 1.25f
                    val startAngle = -90f
                    val sweepAngle = progressAngle
                    addArc(
                        androidx.compose.ui.geometry.Rect(
                            center.x - arcRadius,
                            center.y - arcRadius,
                            center.x + arcRadius,
                            center.y + arcRadius
                        ),
                        startAngle,
                        sweepAngle
                    )
                }
                drawPath(
                    path = arcPath,
                    color = arcColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                )
            }
        }

        // Overlay: Model state info text and action buttons
        if (modelState != ModelState.LOADED || state != AgentState.IDLE) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            ) {
                when (modelState) {
                    ModelState.NO_MODEL -> {
                        Text(
                            text = stringResource(R.string.no_model_loaded),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        onSelectModel?.let {
                            Button(
                                onClick = it,
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(stringResource(R.string.select_model), fontSize = 11.sp)
                            }
                        }
                    }
                    ModelState.LOADING -> {
                        Text(
                            text = if (modelName.isNotBlank()) stringResource(R.string.loading_named_model, modelName) else stringResource(R.string.loading_model),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (loadProgress > 0f) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${(loadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                    ModelState.LOADED -> {
                        if (tokensPerSecond > 0f) {
                            Text(
                                text = "${"%.1f".format(tokensPerSecond)} tok/s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (modelName.isNotBlank() && state == AgentState.IDLE) {
                            Text(
                                text = modelName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                    ModelState.ERROR -> {
                        Text(
                            text = stringResource(R.string.model_error),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        onReloadModel?.let {
                            Button(
                                onClick = it,
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(stringResource(R.string.reload), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draw a very faint elliptical orbit ring.
 */
private fun DrawScope.drawOrbitRing(
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    rotation: Float,
    color: Color
) {
    val points = (0..48).map { i ->
        val angle = (i / 48f) * 2f * PI.toFloat()
        val x = center.x + (cos(angle) * radiusX * cos(rotation) - sin(angle) * radiusY * sin(rotation))
        val y = center.y + (cos(angle) * radiusX * sin(rotation) + sin(angle) * radiusY * cos(rotation))
        Offset(x, y)
    }

    for (i in 0 until points.size - 1) {
        drawLine(
            color = color,
            start = points[i],
            end = points[i + 1],
            strokeWidth = 0.8f
        )
    }
}
