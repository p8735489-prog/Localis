
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.data.repository.ConversationRepository
import com.localaisearch.data.repository.StoredConversation
import com.localaisearch.ui.animation.SpringSpecs
import com.localaisearch.ui.components.ConversationItem
import com.localaisearch.ui.viewmodel.ConversationViewModel
import androidx.compose.ui.res.stringResource
import com.localaisearch.R

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
    val isLoading by viewModel.isLoading.collectAsState()

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
                title = { Text(stringResource(R.string.conversations_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    // Clear all button (only when conversations exist)
                    if (conversations.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearAllDialog = true },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.clear_all_history_desc),
                                tint = if (isLoading) colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else colorScheme.error
                            )
                        }
                    }
                        TextButton(
                            onClick = onNewConversation,
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.new_chat))
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
                contentColor = colorScheme.onPrimary,
                modifier = Modifier.alpha(if (isLoading) 0.5f else 1f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.new_chat))
                }
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
                placeholder = { Text(stringResource(R.string.search_conversations)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
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
                val pinnedConversations = conversations.filter { it.pinned }
                val recentConversations = conversations.filterNot { it.pinned }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (pinnedConversations.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.pinned),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 6.dp)
                            )
                        }
                        items(pinnedConversations, key = { it.conversation.id }) { stored ->
                            HistoryRow(stored, conversations, isLoading, viewModel, colorScheme, onOpenConversation)
                        }
                        if (recentConversations.isNotEmpty()) {
                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 14.dp),
                                    color = colorScheme.outlineVariant.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }
                    if (recentConversations.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.history),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                            )
                        }
                        items(recentConversations, key = { it.conversation.id }) { stored ->
                            HistoryRow(stored, conversations, isLoading, viewModel, colorScheme, onOpenConversation)
                        }
                    }
                }

            }
        }
    }

    // Rename dialog
    if (showRenameDialog && renameTarget != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_conversation)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.title)) },
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
                    },
                    enabled = !isLoading
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog && deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_conversation)) },
            text = {
                Text(
                    stringResource(R.string.delete_conversation_confirm, deleteTarget?.conversation?.title ?: stringResource(R.string.unknown))
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
                    },
                    enabled = !isLoading
                ) {
                    Text(stringResource(R.string.delete), color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
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
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.clear_all_history)) },
            text = {
                Text(
                    stringResource(R.string.clear_history_confirm)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllConversations()
                        showClearAllDialog = false
                    },
                    enabled = !isLoading
                ) {
                    Text(stringResource(R.string.clear_all), color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Export result dialog
    if (exportResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearExportResult() },
            title = { Text(stringResource(R.string.export_conversation)) },
            text = {
                Text(
                    text = if (exportResult?.isNotBlank() == true) {
                        stringResource(R.string.export_success)
                    } else {
                        stringResource(R.string.export_failed)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearExportResult() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    stored: StoredConversation,
    allConversations: List<StoredConversation>,
    isLoading: Boolean,
    viewModel: ConversationViewModel,
    colorScheme: androidx.compose.material3.ColorScheme,
    onOpenConversation: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        ConversationItem(
            conversation = stored,
            isSelected = false,
            onClick = { if (!isLoading) onOpenConversation(stored.conversation.id) },
            onLongClick = {},
            onPin = { if (!isLoading) viewModel.togglePin(stored.conversation.id) },
            onDelete = {},
            onRename = {},
            onExport = { if (!isLoading) viewModel.exportConversation(stored.conversation.id) }
        )
        if (stored != allConversations.lastOrNull()) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp),
                color = colorScheme.outlineVariant.copy(alpha = 0.28f)
            )
        }
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
                imageVector = Icons.Rounded.Message,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = stringResource(R.string.no_conversations),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.start_chat_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
