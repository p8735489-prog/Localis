package com.localaisearch.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.localaisearch.ui.animation.SpringSpecs

/**
 * Search/query input bar.
 *
 * Features:
 * - Rounded, surface-tonal style
 * - Internet search toggle indicator
 * - Clear button
 * - Enter-to-send support
 */
@Composable
fun SearchInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask anything...",
    internetSearchEnabled: Boolean = false,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme

    val containerColor by animateColorAsState(
        targetValue = if (enabled) colorScheme.surfaceContainerHigh else colorScheme.surfaceVariant,
        label = "containerColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp, max = 160.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp, max = 160.dp),
            placeholder = {
                Text(
                    text = placeholder,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                AnimatedVisibility(
                    visible = internetSearchEnabled,
                    enter = fadeIn() + androidx.compose.animation.scaleIn(SpringSpecs.bouncy),
                    exit = fadeOut() + androidx.compose.animation.scaleOut(SpringSpecs.gentle)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TravelExplore,
                        contentDescription = "Internet search enabled",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            trailingIcon = {
                AnimatedVisibility(
                    visible = value.isNotEmpty(),
                    enter = fadeIn() + androidx.compose.animation.scaleIn(SpringSpecs.bouncy),
                    exit = fadeOut() + androidx.compose.animation.scaleOut(SpringSpecs.gentle)
                ) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = containerColor,
                unfocusedContainerColor = containerColor,
                disabledContainerColor = colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = colorScheme.onSurface,
                unfocusedTextColor = colorScheme.onSurface,
                cursorColor = colorScheme.primary
            ),
            enabled = enabled,
            maxLines = 5
        )
    }
}
