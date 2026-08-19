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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.res.stringResource
import com.localaisearch.R
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
                title = { Text(stringResource(R.string.data_security)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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
                SectionCard(title = stringResource(R.string.privacy_session_title), icon = Icons.Filled.Lock) {
                    SwitchRow(
                        label = stringResource(R.string.privacy_mode),
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
                                text = stringResource(R.string.privacy_on_warning)
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
                                text = stringResource(R.string.privacy_off_info)
                            )
                        }
                    }
                }
            }

            // ── Memory System Section ──
            item {
                SectionCard(title = stringResource(R.string.memory_system), icon = Icons.Filled.Save) {
                    SwitchRow(
                        label = stringResource(R.string.enable_memory),
                        checked = isMemoryEnabled,
                        onCheckedChange = { viewModel.setMemoryEnabled(it) }
                    )

                    InfoRow(
                        text = stringResource(R.string.memory_desc)
                    )
                }
            }

            // ── Storage Statistics ──
            item {
                SectionCard(title = stringResource(R.string.storage_usage), icon = Icons.Filled.Save) {
                    StorageStatRow(label = stringResource(R.string.conversations), value = storageStats.conversationsCount)
                    StorageStatRow(label = stringResource(R.string.memories), value = storageStats.memoriesCount)
                    StorageStatRow(label = stringResource(R.string.image_cache), value = storageStats.imageCacheSize)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.estimated_total),
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
                SectionCard(title = stringResource(R.string.data_management), icon = Icons.Filled.Delete) {
                    OutlinedButton(
                        onClick = { showDeleteConversationsDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_all_conversations))
                    }

                    OutlinedButton(
                        onClick = { showDeleteMemoriesDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_all_memories))
                    }

                    OutlinedButton(
                        onClick = { showClearCacheDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.clear_image_cache))
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
                                    text = stringResource(R.string.danger_zone),
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
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.delete_all_local_data), color = colorScheme.error)
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
            title = { Text(stringResource(R.string.delete_all_conversations_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_conversations_confirm)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllConversations()
                        showDeleteConversationsDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete_all), color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConversationsDialog = false }) {
                    Text(stringResource(R.string.cancel))
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
            title = { Text(stringResource(R.string.delete_all_memories_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_memories_confirm)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllMemories()
                        showDeleteMemoriesDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete_all), color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMemoriesDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── Clear Cache Confirmation ──
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.clear_image_cache_title)) },
            text = {
                Text(
                    stringResource(R.string.clear_cache_confirm)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearImageCache()
                        showClearCacheDialog = false
                    }
                ) {
                    Text(stringResource(R.string.clear_cache), color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(stringResource(R.string.cancel))
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
            title = { Text(stringResource(R.string.delete_all_local_data_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.delete_all_confirm),
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.error
                    )
                    Text(
                        stringResource(R.string.delete_confirm_hint),
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
                    Text(stringResource(R.string.nuclear_delete), color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        showDeleteAllConfirmText = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
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
                imageVector = Icons.Filled.Star,
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
