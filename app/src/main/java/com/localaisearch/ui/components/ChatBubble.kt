@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.localaisearch.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaisearch.data.model.ChatMessage
import com.localaisearch.data.model.MessageRole
import com.localaisearch.data.model.hasCitations
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import com.localaisearch.ui.animation.SpringSpecs
import com.localaisearch.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

/**
 * Chat message bubble.
 *
 * For user messages: right-aligned, primary-colored.
 * For assistant messages: left-aligned, surface-colored, with citations.
 *
 * Interactions:
 * - Tap: default Material ripple (via [combinedClickable]'s indication).
 * - Press: elastic scale-down/up (spring physics), matching the rest of the
 *   app's tactile motion language.
 * - Long-press: haptic tap, copies the message to the clipboard, shows a
 *   momentary animated "Copied" badge, and opens the action menu.
 */
@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onRegenerate: (() -> Unit)? = null,
    onOtherAi: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val hapticView = LocalView.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showCopiedBadge by remember { mutableStateOf(false) }
    val isUser = message.role == MessageRole.USER

    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> isPressed = false
            }
        }
    }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = SpringSpecs.elastic,
        label = "bubblePressScale"
    )

    fun copyToClipboard() {
        clipboard.setText(AnnotatedString(message.content))
        AppHaptics.confirm(hapticView)
        showCopiedBadge = true
    }

    LaunchedEffect(showCopiedBadge) {
        if (showCopiedBadge) {
            delay(1200)
            showCopiedBadge = false
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (isUser) colorScheme.primaryContainer else colorScheme.surfaceContainer,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = {},
                        onLongClick = {
                            AppHaptics.tap(hapticView)
                            copyToClipboard()
                            menuExpanded = true
                        }
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) colorScheme.onPrimaryContainer else colorScheme.onSurface
                    )
                    if (message.isStreaming) {
                        Spacer(modifier = Modifier.height(4.dp))
                        CircularProgressIndicator(modifier = Modifier.width(28.dp))
                    }
                    if (message.hasCitations) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.sources_label), style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        message.citations.forEach { citation ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "[${citation.index}] ", style = MaterialTheme.typography.labelSmall, color = colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(text = citation.title, style = MaterialTheme.typography.labelSmall, color = colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { uriHandler.openUri(citation.url) })
                            }
                        }
                    }
                    message.agentStatus?.let { status ->
                        if (status.state.isActive) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AgentProgressBar(status = status)
                        }
                    }
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_copied)) },
                    onClick = { copyToClipboard(); menuExpanded = false }
                )
                if (!isUser && onRegenerate != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_regenerate)) },
                        onClick = { menuExpanded = false; onRegenerate() }
                    )
                }
                if (!isUser && onOtherAi != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_other_ai)) },
                        onClick = { menuExpanded = false; onOtherAi() }
                    )
                }
            }

            // Momentary elastic "Copied" confirmation badge, anchored above the bubble.
            AnimatedVisibility(
                visible = showCopiedBadge,
                enter = fadeIn(SpringSpecs.fadeIn) + scaleIn(initialScale = 0.6f, animationSpec = SpringSpecs.elastic),
                exit = fadeOut(SpringSpecs.fadeOut) + scaleOut(targetScale = 0.6f, animationSpec = SpringSpecs.snappy),
                modifier = Modifier
                    .align(if (isUser) Alignment.TopEnd else Alignment.TopStart)
                    .padding(bottom = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colorScheme.inverseSurface,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colorScheme.inverseOnSurface,
                            modifier = Modifier.width(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.chat_copied),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.inverseOnSurface
                        )
                    }
                }
            }
        }
    }
}
