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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaisearch.data.model.ChatMessage
import com.localaisearch.data.model.MessageRole
import com.localaisearch.data.model.hasCitations
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
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current

    val isUser = message.role == MessageRole.USER

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) colorScheme.primaryContainer else colorScheme.surfaceContainer,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Message content
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) colorScheme.onPrimaryContainer else colorScheme.onSurface
                )

                // Streaming indicator
                if (message.isStreaming) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Citations
                if (message.hasCitations) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sources_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    message.citations.forEach { citation ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "[${citation.index}] ",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = citation.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { uriHandler.openUri(citation.url) }
                            )
                        }
                    }
                }

                // Agent status (if present)
                message.agentStatus?.let { status ->
                    if (status.state.isActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AgentProgressBar(status = status)
                    }
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
