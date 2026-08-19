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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val hapticView = LocalView.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 0.96f else 0.82f),
        tonalElevation = 0.dp,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp, max = 86.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { AppHaptics.tap(hapticView); onAttachClick() }, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add),
                    tint = colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.85f else 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp, max = 68.dp),
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
                cursorBrush = SolidColor(colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (enabled) { AppHaptics.confirm(hapticView); onSend() } }),
                maxLines = 4,
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = if (enabled) stringResource(R.string.search_placeholder) else "模型加载完成后可输入",
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
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
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.clear),
                                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}
