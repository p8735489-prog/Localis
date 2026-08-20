@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.localaisearch.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaisearch.data.model.ChatMessage
import com.localaisearch.data.model.MessageRole
import com.localaisearch.data.model.hasCitations
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.localaisearch.R
import androidx.compose.ui.res.stringResource

/**
 * Chat message bubble.
 *
 * For user messages: right-aligned, primary-colored.
 * For assistant messages: left-aligned, surface-colored, with citations.
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
    var menuExpanded by remember { mutableStateOf(false) }
    val isUser = message.role == MessageRole.USER

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
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(AnnotatedString(message.content))
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
                    onClick = { clipboard.setText(AnnotatedString(message.content)); menuExpanded = false }
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
        }
    }
}

/**
 * A full chat message list.
 */
@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    onRegenerate: ((ChatMessage) -> Unit)? = null,
    onOtherAi: ((ChatMessage) -> Unit)? = null
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            ChatBubble(message = message, onRegenerate = { onRegenerate?.invoke(message) }, onOtherAi = { onOtherAi?.invoke(message) })
        }
    }
}
/**
 * A full chat message list.
 */
@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 8.dp
        )
    ) {
        items(messages, key = { it.id }) { message ->
            ChatBubble(message = message)
        }
    }
}
