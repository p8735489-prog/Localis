package com.localaisearch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.R
import com.localaisearch.data.repository.TorManager
import com.localaisearch.ui.viewmodel.SettingsViewModel

/** Dedicated app-only Tor + proxy settings, modelled after the simple connection-first flow used by Tor apps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTorProxyScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val torStatus by viewModel.torStatus.collectAsState()
    val torBridges by viewModel.torBridges.collectAsState()
    val proxy by viewModel.proxyConfig.collectAsState()
    val torConnected = torStatus == TorManager.Status.ON
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_tor_proxy)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_tor_route_title), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.settings_tor_app_only_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = torConnected, onCheckedChange = viewModel::setTorEnabled)
                        }

                        when (torStatus) {
                            TorManager.Status.STARTING -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    Column {
                                        Text(stringResource(R.string.settings_tor_connecting), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.settings_tor_connecting_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            TorManager.Status.ON -> {
                                Text(stringResource(R.string.settings_tor_connected), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.settings_tor_security_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TorManager.Status.ERROR -> {
                                Text(stringResource(R.string.settings_tor_failed), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                                Text(TorManager.lastError ?: stringResource(R.string.settings_tor_service_error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { viewModel.setTorEnabled(false); viewModel.setTorEnabled(true) }) { Text(stringResource(R.string.settings_tor_retry)) }
                            }
                            TorManager.Status.OFF -> Unit
                        }

                        Text(stringResource(R.string.settings_tor_scope_detail), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.VpnKey, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_manual_proxy), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.settings_app_proxy_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = proxy.enabled && !torConnected, enabled = !torConnected, onCheckedChange = { viewModel.updateProxy(proxy.copy(enabled = it)) })
                        }
                        if (proxy.enabled && !torConnected) {
                            DropdownRow(stringResource(R.string.settings_proxy_type), proxy.type, listOf("HTTP" to "HTTP", "SOCKS" to "SOCKS")) { viewModel.updateProxy(proxy.copy(type = it)) }
                            OutlinedTextField(proxy.host, { viewModel.updateProxy(proxy.copy(host = it)) }, label = { Text(stringResource(R.string.settings_proxy_host)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(if (proxy.port == 0) "" else proxy.port.toString(), { value -> viewModel.updateProxy(proxy.copy(port = value.filter(Char::isDigit).toIntOrNull() ?: 0)) }, label = { Text(stringResource(R.string.settings_proxy_port)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        }
                        if (torConnected) Text(stringResource(R.string.settings_proxy_disabled_by_tor), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.settings_tor_connection_method), style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = torBridges,
                            onValueChange = viewModel::updateTorBridges,
                            enabled = torStatus == TorManager.Status.OFF,
                            label = { Text(stringResource(R.string.settings_custom_bridges)) },
                            placeholder = { Text(stringResource(R.string.settings_bridge_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3, maxLines = 6
                        )
                        Text(
                            stringResource(if (torStatus == TorManager.Status.OFF) R.string.settings_bridge_note else R.string.settings_bridge_disabled_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { uriHandler.openUri("https://www.torproject.org/" ) }) {
                            Icon(Icons.Filled.Public, null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.settings_tor_official))
                        }
                    }
                }
            }
        }
    }
}
