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

    Scaffold(
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
                TextButton(onClick = { /* Import */ }) {
                    Text(stringResource(R.string.settings_import_history))
                }
            }
            item {
                TextButton(onClick = { /* Export */ }) {
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
                    onClick = { showClearChatDialog = false },
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
                    onClick = { showClearCacheDialog = false },
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
                    onClick = { showResetDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
