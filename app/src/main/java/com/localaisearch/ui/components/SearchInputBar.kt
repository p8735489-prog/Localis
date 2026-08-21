package com.localaisearch.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaisearch.R
import com.localaisearch.ui.animation.SpringSpecs

/** Pixel-inspired compact composer: large corner radius, no TextField indicator/white bar. */
@Composable
fun SearchInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    onAttachClick: () -> Unit = {},
    internetSearchEnabled: Boolean = false,
    enabled: Boolean = true,
    disabledReason: String? = null,
    imageInputAvailable: Boolean = false,
    sendButtonState: SendButtonState = SendButtonState.IDLE,
    pendingImageAvailable: Boolean = false,
    imageUnavailableReason: String? = null,
    onImageUnavailableClick: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val hapticView = LocalView.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 0.96f else 0.82f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp, max = 72.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    AppHaptics.tap(hapticView)
                    if (imageInputAvailable && enabled) onAttachClick() else onImageUnavailableClick()
                },
                enabled = enabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.add),
                    tint = colorScheme.onSurfaceVariant.copy(alpha = if (enabled && imageInputAvailable) 0.85f else 0.32f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 32.dp, max = 48.dp),
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
                cursorBrush = SolidColor(colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (enabled) { AppHaptics.confirm(hapticView); onSend() } }),
                singleLine = true,
                maxLines = 1,
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = if (enabled) stringResource(R.string.ask_anything) else (disabledReason ?: stringResource(R.string.model_not_loaded_input)),
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                        AnimatedVisibility(
                            visible = value.isNotEmpty() && enabled,
                            enter = fadeIn() + scaleIn(SpringSpecs.bouncy),
                            exit = fadeOut() + scaleOut(SpringSpecs.gentle)
                        ) {
                            IconButton(onClick = { AppHaptics.tap(hapticView); onValueChange("") }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.clear),
                                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(4.dp))

            MorphingSendButton(
                state = sendButtonState,
                onClick = onSend,
                enabled = enabled && (value.isNotBlank() || pendingImageAvailable)
            )
        }
    }
}
