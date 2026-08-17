package com.localaisearch.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.localaisearch.ui.animation.SpringSpecs
import com.localaisearch.ui.viewmodel.DataSecurityViewModel

/**
 * Settings screen for data and privacy management.
 *
 * Organized into three sections:
 * 1. **Privacy Session** - Toggle privacy mode with warning text
 * 2. **Memory System** - Toggle memory system with explanation
 * 3. **Data Management** - Delete conversations, memories, all local data,
 *    and clear image cache with appropriate confirmation dialogs.
 *
 * Also displays current storage usage statistics.
 *
 * @param onNavigateBack Callback to navigate back.
 * @param viewModel The [DataSecurityViewModel] that provides privacy and data state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSecurityScreen(
    onNavigateBack: () -> Unit,
    viewModel: DataSecurityViewModel = viewModel()
) {
    val colorScheme = MaterialTheme.colorScheme

    val isPrivacyMode by viewModel.isPrivacyMode.collectAsState()
    val isMemoryEnabled by viewModel.isMemoryEnabled.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()

    var showDeleteConversationsDialog by remember { mutableStateOf(false) }
    var showDeleteMemoriesDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showDeleteAllConfirmText by remember { mutableStateOf("") }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data & Security") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Privacy Session Section ──
            item {
                SectionCard(title = "Privacy Session", icon = Icons.Filled.Security) {
                    SwitchRow(
                        label = "Privacy Mode",
                        checked = isPrivacyMode,
                        onCheckedChange = { viewModel.togglePrivacyMode() }
                    )

                    AnimatedVisibility(
                        visible = isPrivacyMode,
                        enter = fadeIn(SpringSpecs.fadeIn),
                        exit = fadeOut(SpringSpecs.fadeOut)
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            WarningRow(
                                text = "Privacy mode is ON. Conversations, search history, " +
                                    "and image cache will NOT be saved. Memory generation is disabled."
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = !isPrivacyMode,
                        enter = fadeIn(SpringSpecs.fadeIn),
                        exit = fadeOut(SpringSpecs.fadeOut)
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            InfoRow(
                                text = "Privacy mode is OFF. Normal data persistence is active. " +
                                    "Enable privacy mode before sensitive queries."
                            )
                        }
                    }
                }
            }

            // ── Memory System Section ──
            item {
                SectionCard(title = "Memory System", icon = Icons.Filled.Memory) {
                    SwitchRow(
                        label = "Enable Memory System",
                        checked = isMemoryEnabled,
                        onCheckedChange = { viewModel.setMemoryEnabled(it) }
                    )

                    InfoRow(
                        text = "When enabled, the AI remembers facts and preferences from your " +
                            "conversations to provide more personalized responses. " +
                            "Memory is automatically disabled when Privacy Mode is active."
                    )
                }
            }

            // ── Storage Statistics ──
            item {
                SectionCard(title = "Storage Usage", icon = Icons.Filled.Storage) {
                    StorageStatRow(label = "Conversations", value = storageStats.conversationsCount)
                    StorageStatRow(label = "Memories", value = storageStats.memoriesCount)
                    StorageStatRow(label = "Image Cache", value = storageStats.imageCacheSize)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estimated Total",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = storageStats.formattedTotal,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                    }
                }
            }

            // ── Data Management Section ──
            item {
                SectionCard(title = "Data Management", icon = Icons.Filled.DeleteForever) {
                    OutlinedButton(
                        onClick = { showDeleteConversationsDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete All Conversations")
                    }

                    OutlinedButton(
                        onClick = { showDeleteMemoriesDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete All Memories")
                    }

                    OutlinedButton(
                        onClick = { showClearCacheDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Image Cache")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Nuclear option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Danger Zone",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showDeleteAllDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteForever,
                                    contentDescription = null,
                                    tint = colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete All Local Data", color = colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Delete Conversations Confirmation ──
    if (showDeleteConversationsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConversationsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = colorScheme.error
                )
            },
            title = { Text("Delete All Conversations?") },
            text = {
                Text(
                    "This will permanently delete all conversation history. " +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllConversations()
                        showDeleteConversationsDialog = false
                    }
                ) {
                    Text("Delete All", color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConversationsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Delete Memories Confirmation ──
    if (showDeleteMemoriesDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteMemoriesDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = colorScheme.error
                )
            },
            title = { Text("Delete All Memories?") },
            text = {
                Text(
                    "This will permanently delete all stored memories and preferences. " +
                        "The AI will no longer remember past context. This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllMemories()
                        showDeleteMemoriesDialog = false
                    }
                ) {
                    Text("Delete All", color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMemoriesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Clear Cache Confirmation ──
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Image Cache?") },
            text = {
                Text(
                    "This will remove all cached images. They will be re-downloaded " +
                        "if needed. This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearImageCache()
                        showClearCacheDialog = false
                    }
                ) {
                    Text("Clear Cache", color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Nuclear: Delete All Local Data (extreme confirmation) ──
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = colorScheme.error
                )
            },
            title = { Text("Delete ALL Local Data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This is an irreversible action. ALL conversations, memories, " +
                            "settings, and cached data will be permanently erased.",
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.error
                    )
                    Text(
                        "Type \"DELETE\" below to confirm:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    androidx.compose.material3.TextField(
                        value = showDeleteAllConfirmText,
                        onValueChange = { showDeleteAllConfirmText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllLocalData()
                        showDeleteAllDialog = false
                        showDeleteAllConfirmText = ""
                    },
                    enabled = showDeleteAllConfirmText == "DELETE"
                ) {
                    Text("Nuclear Delete", color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        showDeleteAllConfirmText = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Reusable Section Components ──

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun WarningRow(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun InfoRow(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun StorageStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
