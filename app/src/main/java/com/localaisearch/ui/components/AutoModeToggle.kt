package com.localaisearch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.localaisearch.ui.animation.SpringSpecs
import com.localaisearch.R

/**
 * Toggle switch for "Auto Mode" (Localis Auto).
 *
 * Displays a sparkle/star icon with "Localis Auto" label alongside a Switch
 * control. The card background subtly changes color when enabled.
 *
 * @param enabled Whether Auto Mode is currently enabled.
 * @param onToggle Callback invoked with the new enabled state when toggled.
 */
@Composable
fun AutoModeToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val hapticView = LocalView.current

    val iconScale by animateFloatAsState(
        targetValue = if (enabled) 1.2f else 1f,
        animationSpec = SpringSpecs.elastic,
        label = "iconScale"
    )

    val cardColor by animateColorAsState(
        targetValue = if (enabled) colorScheme.primaryContainer else colorScheme.surfaceContainer,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = com.localaisearch.ui.animation.AnimDurations.NORMAL
        ),
        label = "cardColor"
    )

    Card(
        onClick = { AppHaptics.tap(hapticView); onToggle(!enabled) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 2.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                        imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(com.localaisearch.R.string.auto_mode),
                    tint = if (enabled) colorScheme.primary else colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .scale(scaleX = iconScale, scaleY = iconScale)
                )
                Text(
                    text = "Localis Auto",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = { AppHaptics.tap(hapticView); onToggle(it) }
            )
        }
    }
}
