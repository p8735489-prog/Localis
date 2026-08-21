
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.data.repository.MemoryEntry
import com.localaisearch.data.repository.MemorySearchPreset
import androidx.compose.material3.FilterChip
import com.localaisearch.ui.animation.SpringSpecs
import com.localaisearch.ui.viewmodel.MemoryViewModel
import com.localaisearch.R
import androidx.compose.ui.res.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen for managing long-term memory entries.
 *
 * Displays a list of memories with content, topic, and source.
 * Supports editing and deleting individual memories, searching/filtering
 * by topic, and adding new memories via an FAB.
 *
 * @param onNavigateBack Callback to navigate back.
 * @param viewModel The [MemoryViewModel] that provides memory data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryCenterScreen(
    onNavigateBack: () -> Unit,
    viewModel: MemoryViewModel = viewModel()
) {
    val colorScheme = MaterialTheme.colorScheme
    val memories by viewModel.memories.collectAsState()
    val visibleMemories by viewModel.visibleMemories.collectAsState()
    val searchPreset by viewModel.searchPreset.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTopic by viewModel.selectedTopic.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<MemoryEntry?>(null) }
    var editText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MemoryEntry?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    // Derive available topics from current memories
    val topics = remember(memories) {
        (memories.map { it.topic }.distinct() + listOf("all")).sorted()
    }

    val filteredMemories = remember(visibleMemories, selectedTopic) {
        visibleMemories.filter { memory -> selectedTopic == "all" || memory.topic == selectedTopic }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.memory_center_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (memories.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearAllDialog = true },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.clear_all_memories_desc),
                                tint = if (isLoading) colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else colorScheme.error
                            )
                        }
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
                onClick = { if (!isLoading) showAddDialog = true },
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                modifier = Modifier.alpha(if (isLoading) 0.5f else 1f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_memory_desc))
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
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSearch = { },
                active = false,
                onActiveChange = { },
                placeholder = { Text(stringResource(R.string.search_memories), maxLines = 1) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                colors = SearchBarDefaults.colors(containerColor = colorScheme.surfaceContainerLow)
            ) {}

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    MemorySearchPreset.ALL to R.string.memory_preset_all,
                    MemorySearchPreset.PREFERENCES to R.string.memory_preset_preferences,
                    MemorySearchPreset.PROJECTS to R.string.memory_preset_projects,
                    MemorySearchPreset.DEVICE to R.string.memory_preset_device,
                    MemorySearchPreset.RECENT to R.string.memory_preset_recent
                ).forEach { (preset, label) ->
                    FilterChip(
                        selected = searchPreset == preset,
                        onClick = { viewModel.setSearchPreset(preset) },
                        label = { Text(stringResource(label)) }
                    )
                }
            }

            // Topic filter chips
            if (topics.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    topics.forEach { topic ->
                        val isSelected = selectedTopic == topic
                        Surface(
                            onClick = { viewModel.setSelectedTopic(topic) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = topic.replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            if (filteredMemories.isEmpty()) {
                EmptyMemoryState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredMemories, key = { it.id }) { memory ->
                        MemoryItemCard(
                            memory = memory,
                            isLoading = isLoading,
                            onEdit = {
                                if (!isLoading) {
                                    editTarget = memory
                                    editText = memory.content
                                    showEditDialog = true
                                }
                            },
                            onDelete = {
                                if (!isLoading) {
                                    deleteTarget = memory
                                    showDeleteDialog = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Memory Dialog
    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { content, topic ->
                viewModel.addMemory(content, topic)
                showAddDialog = false
            },
            isLoading = isLoading
        )
    }

    // Edit Memory Dialog
    if (showEditDialog && editTarget != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.edit_memory)) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text(stringResource(R.string.content)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editTarget?.let { viewModel.updateMemory(it.id, editText) }
                        showEditDialog = false
                        editTarget = null
                    },
                    enabled = !isLoading
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete Memory Dialog
    if (showDeleteDialog && deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_memory)) },
            text = { Text(stringResource(R.string.delete_memory_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget?.let { viewModel.deleteMemory(it.id) }
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

    // Clear all memories confirmation dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Message,
                    contentDescription = null,
                    tint = colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.clear_all_memories)) },
            text = {
                Text(
                    stringResource(R.string.clear_memories_confirm)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllMemories()
                        showClearAllDialog = false
                    },
                    enabled = !isLoading
                ) {
                    Text(stringResource(R.string.clear_all_memories_action), color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun MemoryItemCard(
    memory: MemoryEntry,
    isLoading: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val createdDate = dateFormat.format(Date(memory.createdAt))

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Topic chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = memory.topic.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        enabled = !isLoading,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.edit_memory),
                            tint = if (isLoading) colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        enabled = !isLoading,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.delete_memory),
                            tint = if (isLoading) colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.source, memory.sourceConversationId.take(8)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = createdDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun EmptyMemoryState() {
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
                imageVector = Icons.Rounded.Save,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = stringResource(R.string.no_memories),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.memory_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    }
}

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (content: String, topic: String) -> Unit,
    isLoading: Boolean
) {
    var content by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("general") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_memory)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.content)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text(stringResource(R.string.topic)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (content.isNotBlank()) {
                        onConfirm(content, topic.ifBlank { "general" })
                    }
                },
                enabled = content.isNotBlank() && !isLoading
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
