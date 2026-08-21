
package com.localaisearch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.R
import com.localaisearch.data.search.SearchProviderType
import com.localaisearch.data.repository.ProxyConfig
import com.localaisearch.data.repository.TorManager
import com.localaisearch.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNetworkScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val searchConfig by viewModel.searchConfig.collectAsState()
    val internetSearch by viewModel.internetSearchEnabled.collectAsState()
    val proxy by viewModel.proxyConfig.collectAsState()
    val torEnabled by viewModel.torEnabled.collectAsState()
    val torBridges by viewModel.torBridges.collectAsState()
    val torStatus by viewModel.torStatus.collectAsState()
    val modelSource by viewModel.modelSource.collectAsState()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            SettingsTopBar(title = stringResource(R.string.network_and_search), onBack = onNavigateBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = SettingsContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SettingsSectionTitle(stringResource(R.string.settings_model_center)) }
            item {
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.settings_model_source), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_model_source_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (torStatus != TorManager.Status.OFF) {
                            Text(
                                stringResource(R.string.settings_hf_official),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            DropdownRow(
                                stringResource(R.string.settings_current_source),
                                if (modelSource == "hugging_face") stringResource(R.string.settings_hf_official) else stringResource(R.string.settings_hf_mirror),
                                listOf(
                                    stringResource(R.string.settings_hf_mirror) to "tsinghua_mirror",
                                    stringResource(R.string.settings_hf_official) to "hugging_face"
                                )
                            ) { viewModel.updateModelSource(it) }
                        }
                        if (torStatus != TorManager.Status.OFF) {
                            Text(
                                stringResource(R.string.settings_tor_hf_pinned_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            stringResource(R.string.settings_model_source_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }
            item { SettingsSectionTitle(stringResource(R.string.search_engine)) }
            item {
                Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DropdownRow(stringResource(R.string.settings_search_provider), searchConfig.providerType.displayName,
                            SearchProviderType.entries.map { it.displayName to it }) { viewModel.updateSearchProvider(it) }
                        OutlinedTextField(
                            value = searchConfig.apiUrl,
                            onValueChange = viewModel::updateApiUrl,
                            label = { Text(stringResource(R.string.settings_api_url)) },
                            placeholder = { Text(stringResource(R.string.settings_api_url_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                        OutlinedTextField(
                            value = searchConfig.apiKey,
                            onValueChange = viewModel::updateApiKey,
                            label = { Text(stringResource(R.string.settings_api_key)) },
                            placeholder = { Text(stringResource(R.string.optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                }
            }
            item { SettingsSectionTitle(stringResource(R.string.search_options)) }
            item { SliderRow(stringResource(R.string.settings_max_results), searchConfig.maxResults.toFloat(), 5f..20f, 14, searchConfig.maxResults.toString()) { viewModel.updateMaxResults(it.toInt()) } }
            item { SliderRow(stringResource(R.string.settings_max_rounds), searchConfig.maxSearchRounds.toFloat(), 1f..3f, 1, searchConfig.maxSearchRounds.toString()) { viewModel.updateMaxRounds(it.toInt()) } }
            item { SwitchRow(stringResource(R.string.settings_safe_search), searchConfig.enableSafeSearch) { viewModel.updateSafeSearch(it) } }
            item { SwitchRow(stringResource(R.string.settings_enable_internet_search), internetSearch) { viewModel.updateInternetSearch(it) } }

            item { SettingsSectionTitle(stringResource(R.string.settings_tor_routing)) }
            item {
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RowSwitch(
                            title = stringResource(R.string.settings_tor_route_title),
                            subtitle = stringResource(R.string.settings_tor_route_desc),
                            checked = torStatus == TorManager.Status.ON,
                            onCheckedChange = viewModel::setTorEnabled
                        )
                        if (torStatus == TorManager.Status.STARTING) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.settings_tor_connecting),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        stringResource(R.string.settings_tor_typing_disabled),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else if (torStatus == TorManager.Status.ON) {
                            Text(
                                stringResource(R.string.settings_tor_connected),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.settings_tor_security_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (torStatus == TorManager.Status.ERROR) {
                            Text(
                                stringResource(R.string.settings_tor_failed),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    TorManager.lastError ?: stringResource(R.string.settings_tor_service_error),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.setTorEnabled(false); viewModel.setTorEnabled(true) }) {
                                    Text(stringResource(R.string.settings_tor_retry))
                                }
                            }
                        }
                        OutlinedTextField(
                            value = torBridges,
                            onValueChange = viewModel::updateTorBridges,
                            label = { Text(stringResource(R.string.settings_custom_bridges)) },
                            placeholder = { Text(stringResource(R.string.settings_bridge_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            shape = MaterialTheme.shapes.medium,
                            enabled = torStatus == TorManager.Status.OFF
                        )
                        Text(
                            stringResource(if (torStatus != TorManager.Status.OFF) R.string.settings_bridge_disabled_note else R.string.settings_bridge_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        RowSwitch(
                            title = stringResource(R.string.settings_use_saved_bridges),
                            subtitle = if (torBridges.isBlank()) stringResource(R.string.settings_add_bridges_first) else stringResource(R.string.settings_bridge_saved_note),
                            checked = torStatus == TorManager.Status.ON && torBridges.isNotBlank(),
                            onCheckedChange = { if (torStatus == TorManager.Status.OFF) viewModel.setTorEnabled(true) }
                        )
                        TextButton(onClick = { uriHandler.openUri("https://www.torproject.org/") }) {
                            Icon(Icons.Rounded.Public, contentDescription = null)
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 3.dp))
                            Text(stringResource(R.string.settings_tor_official))
                        }
                    }
                }
            }

            item { SettingsSectionTitle(stringResource(R.string.settings_manual_proxy)) }
            item {
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RowSwitch(stringResource(R.string.settings_use_app_proxy), stringResource(R.string.settings_app_proxy_desc), proxy.enabled && !torEnabled) {
                            if (!torEnabled) viewModel.updateProxy(proxy.copy(enabled = it))
                        }
                        if (proxy.enabled && !torEnabled) {
                            DropdownRow(stringResource(R.string.settings_proxy_type), proxy.type, listOf("HTTP" to "HTTP", "SOCKS" to "SOCKS")) { viewModel.updateProxy(proxy.copy(type = it)) }
                            OutlinedTextField(proxy.host, { viewModel.updateProxy(proxy.copy(host = it)) }, label = { Text(stringResource(R.string.settings_proxy_host)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.medium)
                            OutlinedTextField(if (proxy.port == 0) "" else proxy.port.toString(), { value -> viewModel.updateProxy(proxy.copy(port = value.filter(Char::isDigit).toIntOrNull() ?: 0)) }, label = { Text(stringResource(R.string.settings_proxy_port)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.medium)
                        }
                        if (torStatus == TorManager.Status.ON) {
                            Text(stringResource(R.string.settings_tor_app_only_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
