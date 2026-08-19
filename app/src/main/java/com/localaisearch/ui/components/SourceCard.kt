package com.localaisearch.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaisearch.data.model.Citation
import com.localaisearch.ui.animation.SpringSpecs

/**
 * A dynamic source/citation card with spring, fade, and scale animations.
 *
 * Displays:
 * - Citation index badge
 * - Source title
 * - Domain/source name
 * - Snippet preview
 * - URL (clickable to open)
 *
 * Animations:
 * - Scale-in with spring bounce on appear
 * - Alpha fade-in
 * - Subtle scale on press
 */
@Composable
fun SourceCard(
    citation: Citation,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    // Entry animation - spring scale + fade
    val scaleAnim = remember { Animatable(0.7f) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(citation.url) {
        // Stagger by index
        kotlinx.coroutines.delay(index * 80L)
        scaleAnim.animateTo(1f, SpringSpecs.bouncy)
        alphaAnim.animateTo(1f, SpringSpecs.gentle)
    }

    // Press animation
    val pressedScale = animateFloatAsState(
        targetValue = 1f,
        animationSpec = SpringSpecs.bouncy,
        label = "pressed"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scaleX = scaleAnim.value * pressedScale.value, scaleY = scaleAnim.value * pressedScale.value)
            .alpha(alphaAnim.value),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Citation index badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.primaryContainer,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${citation.index}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onPrimaryContainer,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title
                Text(
                    text = citation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Source domain
                Text(
                    text = citation.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary
                )

                // Snippet
                if (citation.snippet.isNotBlank()) {
                    Text(
                        text = citation.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // URL
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = citation.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.open_source),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Lazy column item wrapper for animated source cards.
 */
@Composable
fun AnimatedSourceCard(
    citation: Citation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SourceCard(
        citation = citation,
        index = citation.index - 1,
        onClick = onClick,
        modifier = modifier
    )
}
