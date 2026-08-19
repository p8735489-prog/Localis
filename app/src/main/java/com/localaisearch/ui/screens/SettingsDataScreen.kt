package com.localaisearch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch
import com.localaisearch.data.repository.ConversationRepository
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.localaisearch.R

/**
 * Data settings sub-page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDataScreen(
    onNavigateBack: () -> Unit
) {
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { ConversationRepository(context) }
    val snackbar = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val content = repository.exportAllConversations()
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }; content.isNotBlank() }.getOrDefault(false)
            snackbar.showSnackbar(if (ok) context.getString(R.string.settings_export_success) else context.getString(R.string.settings_export_failed))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }.getOrNull()
            val result = if (text.isNullOrBlank()) Result.failure(IllegalArgumentException()) else repository.importAllConversations(text)
            snackbar.showSnackbar(result.fold({ context.getString(R.string.settings_import_success, it) }, { context.getString(R.string.settings_import_failed) }))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_data)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SettingsSectionTitle(stringResource(R.string.settings_chat_history)) }
            item {
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }) {
                    Text(stringResource(R.string.settings_import_history))
                }
            }
            item {
                TextButton(onClick = { exportLauncher.launch("localis-chat-history.json") }) {
                    Text(stringResource(R.string.settings_export_history))
                }
            }
            item {
                TextButton(
                    onClick = { showClearChatDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_clear_history))
                }
            }
            item { SettingsSectionTitle(stringResource(R.string.settings_cache)) }
            item {
                TextButton(
                    onClick = { showClearCacheDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_clear_cache))
                }
            }
            item { SettingsSectionTitle(stringResource(R.string.settings_danger_zone)) }
            item {
                TextButton(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_reset_data))
                }
            }
        }
    }

    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text(stringResource(R.string.settings_clear_history_q)) },
            text = { Text(stringResource(R.string.settings_clear_history_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearChatDialog = false
                        scope.launch { repository.clearAllConversations() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.settings_clear_cache_q)) },
            text = { Text(stringResource(R.string.settings_clear_cache_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        scope.launch { runCatching { context.cacheDir.deleteRecursively() } }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_q)) },
            text = { Text(stringResource(R.string.settings_reset_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        scope.launch {
                            repository.clearAllConversations()
                            context.cacheDir.deleteRecursively()
                            com.localaisearch.data.repository.SettingsRepository(context).clearAllSettings()
                            (context as? android.app.Activity)?.recreate()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
