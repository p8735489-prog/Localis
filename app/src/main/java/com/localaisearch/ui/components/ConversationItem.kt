package com.localaisearch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaisearch.data.repository.StoredConversation
import com.localaisearch.ui.animation.SpringSpecs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * List item for a conversation in the conversation history.
 *
 * Displays:
 * - Title (auto-generated from first user message or custom)
 * - Timestamp of last update
 * - Message count
 * - Pin icon if the conversation is pinned
 *
 * Supports long-press to show a context menu with options:
 * Rename, Delete, Pin/Unpin, Export.
 *
 * @param conversation The stored conversation metadata.
 * @param isSelected Whether this item is currently selected.
 * @param onClick Callback when the item is clicked.
 * @param onLongClick Callback when the item is long-pressed.
 * @param onPin Callback to pin/unpin this conversation.
 * @param onDelete Callback to delete this conversation.
 * @param onRename Callback to rename this conversation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: StoredConversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPin: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRename: () -> Unit = {},
    onExport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var showMenu by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> colorScheme.primaryContainer
            else -> colorScheme.surfaceContainer
        },
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = com.localaisearch.ui.animation.AnimDurations.FAST
        ),
        label = "backgroundColor"
    )

    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val updatedAt = dateFormat.format(Date(conversation.conversation.updatedAt))
    val messageCount = conversation.conversation.messages.size

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (conversation.pinned) 2.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        onLongClick()
                        showMenu = true
                    }
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (conversation.pinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = conversation.conversation.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Text(
                    text = updatedAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (messageCount == 1) "$messageCount message" else "$messageCount messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        // Context menu on long press
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    showMenu = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = {
                    Text(if (conversation.pinned) "Unpin" else "Pin")
                },
                leadingIcon = {
                    Icon(
                        if (conversation.pinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    showMenu = false
                    onPin()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
            DropdownMenuItem(
                text = { Text("Export") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.IosShare,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    showMenu = false
                    onExport()
                }
            )
        }
    }
}
