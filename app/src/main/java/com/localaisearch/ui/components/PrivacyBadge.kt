package com.localaisearch.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localaisearch.R
import com.localaisearch.ui.animation.SpringSpecs

/**
 * Privacy Chip - Material 3 Chip style badge.
 *
 * Shows privacy mode status as a small, rounded chip.
 * - Privacy mode: displays "Privacy Mode" with lock icon on primary background
 * - Standard mode: optionally displays "Standard Mode" or is hidden
 *
 * @param isPrivacyMode Whether privacy mode is currently active.
 * @param onToggle Callback when the chip is clicked.
 * @param showStandardBadge Whether to show a badge when in standard mode.
 */
@Composable
fun PrivacyBadge(
    isPrivacyMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    showStandardBadge: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme

    AnimatedContent(
        targetState = isPrivacyMode,
        transitionSpec = {
            (fadeIn(SpringSpecs.fadeIn) + scaleIn(SpringSpecs.bouncy)) togetherWith
                    (fadeOut(SpringSpecs.fadeOut) + scaleOut(SpringSpecs.gentle))
        },
        label = "privacyChip",
        modifier = modifier
    ) { privacyMode ->
        if (privacyMode) {
            Surface(
                onClick = onToggle,
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = stringResource(R.string.privacy_mode_active),
                        tint = colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.privacy_mode_chip),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else if (showStandardBadge) {
            Surface(
                onClick = onToggle,
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surfaceContainerHighest,
                modifier = Modifier.padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = stringResource(R.string.privacy_mode_off),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.privacy_mode_off),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
