package com.localaisearch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Static Gemini-like four-point sparkle used by the Localis home state. */
@Composable
fun LocalisSparkle(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) * .48f
        fun point(angle: Double, radius: Float): Offset =
            Offset(cx + cos(angle).toFloat() * radius, cy + sin(angle).toFloat() * radius)
        val path = Path().apply {
            moveTo(cx, cy - r)
            quadraticBezierTo(cx + r * .12f, cy - r * .18f, cx + r, cy)
            quadraticBezierTo(cx + r * .18f, cy + r * .12f, cx, cy + r)
            quadraticBezierTo(cx - r * .12f, cy + r * .18f, cx - r, cy)
            quadraticBezierTo(cx - r * .18f, cy - r * .12f, cx, cy - r)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                0f to androidx.compose.ui.graphics.Color(0xFFFF6B6B),
                .32f to androidx.compose.ui.graphics.Color(0xFFFFC857),
                .58f to androidx.compose.ui.graphics.Color(0xFF5ED0FF),
                1f to androidx.compose.ui.graphics.Color(0xFF8C7BFF)
            )
        )
        // tiny static glint, deliberately not animated
        drawCircle(
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = .75f),
            radius = r * .035f,
            center = point(-2.35, r * .78f)
        )
    }
}
