package com.localaisearch.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localaisearch.ui.animation.SpringSpecs

/**
 * A small chip/badge that indicates privacy mode is active.
 *
 * Shows a lock icon with "隐私会话" (Private Session) text on a primary-colored
 * background. Can be clicked to toggle privacy mode off.
 *
 * @param isPrivacyMode Whether privacy mode is currently active.
 * @param onToggle Callback when the badge is clicked.
 */
@Composable
fun PrivacyBadge(
    isPrivacyMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    AnimatedVisibility(
        visible = isPrivacyMode,
        enter = fadeIn(SpringSpecs.fadeIn) + scaleIn(SpringSpecs.bouncy),
        exit = fadeOut(SpringSpecs.fadeOut) + scaleOut(SpringSpecs.gentle),
        modifier = modifier
    ) {
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.primary,
            modifier = Modifier.padding(4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Privacy mode active",
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "隐私会话",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
