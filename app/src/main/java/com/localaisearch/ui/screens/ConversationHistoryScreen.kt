package com.localaisearch.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.data.repository.ConversationRepository
import com.localaisearch.data.repository.StoredConversation
import com.localaisearch.ui.animation.SpringSpecs
import com.localaisearch.ui.components.ConversationItem
import com.localaisearch.ui.viewmodel.ConversationViewModel

/**
 * Full screen for browsing conversation history.
 *
 * Features:
 * - Top app bar with search field and "New Chat" action
 * - Scrollable list of conversations rendered with [ConversationItem]
 * - FAB to start a new conversation
 * - Real-time search/filter functionality
 * - Empty state when no conversations exist
 * - Uses existing Material 3 components and theme
 *
 * @param onNavigateBack Callback to navigate back.
 * @param onNewConversation Callback when the user starts a new conversation.
 * @param onOpenConversation Callback with conversation ID to open a specific conversation.
 * @param viewModel The [ConversationViewModel] that provides conversation data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationHistoryScreen(
    onNavigateBack: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    viewModel: ConversationViewModel = viewModel()
) {
    val colorScheme = MaterialTheme.colorScheme
    val conversations by viewModel.conversations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<StoredConversation?>(null) }
    var renameText by remember { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<StoredConversation?>(null) }

    var showClearAllDialog by remember { mutableStateOf(false) }
    val exportResult by viewModel.exportResult.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversations") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Clear all button (only when conversations exist)
                    if (conversations.isNotEmpty()) {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear all history",
                                tint = colorScheme.error
                            )
                        }
                    }
                    TextButton(onClick = onNewConversation) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewConversation,
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New conversation")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search conversations...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            if (conversations.isEmpty()) {
                EmptyConversationState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(conversations, key = { it.conversation.id }) { stored ->
                        ConversationItem(
                            conversation = stored,
                            isSelected = false,
                            onClick = { onOpenConversation(stored.conversation.id) },
                            onLongClick = {},
                            onPin = { viewModel.togglePin(stored.conversation.id) },
                            onDelete = {
                                deleteTarget = stored
                                showDeleteDialog = true
                            },
                            onRename = {
                                renameTarget = stored
                                renameText = stored.conversation.title
                                showRenameDialog = true
                            },
                            onExport = {
                                viewModel.exportConversation(stored.conversation.id)
                            }
                        )
                    }
                }
            }
        }
    }

    // Rename dialog
    if (showRenameDialog && renameTarget != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameTarget?.let {
                            viewModel.renameConversation(it.conversation.id, renameText)
                        }
                        showRenameDialog = false
                        renameTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog && deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Conversation?") },
            text = {
                Text(
                    "Are you sure you want to delete \"${deleteTarget!!.conversation.title}\"? " +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget?.let {
                            viewModel.deleteConversation(it.conversation.id)
                        }
                        showDeleteDialog = false
                        deleteTarget = null
                    }
                ) {
                    Text("Delete", color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear all history confirmation dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    tint = colorScheme.error
                )
            },
            title = { Text("Clear All History?") },
            text = {
                Text(
                    "This will permanently delete ALL conversation history. " +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllConversations()
                        showClearAllDialog = false
                    }
                ) {
                    Text("Clear All", color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export result dialog
    if (exportResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearExportResult() },
            title = { Text("Export Conversation") },
            text = {
                Text(
                    text = if (exportResult!!.isNotBlank()) {
                        "Conversation exported successfully. The JSON data has been " +
                            "generated and can be shared or saved externally."
                    } else {
                        "Failed to export conversation."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearExportResult() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun EmptyConversationState() {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = "No conversations yet",
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = "Start a new chat to begin",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
