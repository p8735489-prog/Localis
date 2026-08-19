package com.localaisearch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
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
import com.localaisearch.ui.components.Material3LoadingIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
            TopAppBar(
                title = { Text(stringResource(R.string.network_and_search)) },
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SettingsSectionTitle(stringResource(R.string.settings_model_center)) }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.settings_model_source), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_model_source_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        DropdownRow(
                            stringResource(R.string.settings_current_source),
                            if (modelSource == "hugging_face") stringResource(R.string.settings_hf_official) else stringResource(R.string.settings_hf_mirror),
                            listOf(
                                stringResource(R.string.settings_hf_mirror) to "tsinghua_mirror",
                                stringResource(R.string.settings_hf_official) to "hugging_face"
                            )
                        ) { viewModel.updateModelSource(it) }
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
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
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
                            shape = RoundedCornerShape(20.dp)
                        )
                        OutlinedTextField(
                            value = searchConfig.apiKey,
                            onValueChange = viewModel::updateApiKey,
                            label = { Text(stringResource(R.string.settings_api_key)) },
                            placeholder = { Text(stringResource(R.string.optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp)
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
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RowSwitch(
                            title = stringResource(R.string.settings_tor_route_title),
                            subtitle = stringResource(R.string.settings_tor_route_desc),
                            checked = torEnabled,
                            onCheckedChange = viewModel::setTorEnabled
                        )
                        if (torStatus == TorManager.Status.STARTING) {
                            val transition = rememberInfiniteTransition(label = "tor-connecting")
                            val pulse by transition.animateFloat(
                                initialValue = 0.25f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
                                label = "tor-pulse"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Material3LoadingIndicator(modifier = Modifier.size(20.dp), size = 20.dp)
                                Text(
                                    stringResource(R.string.settings_tor_connecting),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f + pulse * 0.35f)
                                )
                            }
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else if (torStatus == TorManager.Status.ON) {
                            Text(
                                stringResource(R.string.settings_tor_connected),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
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
                            shape = RoundedCornerShape(24.dp),
                            enabled = !torEnabled
                        )
                        Text(
                            stringResource(R.string.settings_bridge_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        RowSwitch(
                            title = stringResource(R.string.settings_use_saved_bridges),
                            subtitle = if (torBridges.isBlank()) stringResource(R.string.settings_add_bridges_first) else stringResource(R.string.settings_bridge_saved_note),
                            checked = torEnabled && torBridges.isNotBlank(),
                            onCheckedChange = { if (!torEnabled) viewModel.setTorEnabled(true) }
                        )
                        TextButton(onClick = { uriHandler.openUri("https://www.torproject.org/") }) {
                            Icon(Icons.Filled.Public, contentDescription = null)
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 3.dp))
                            Text(stringResource(R.string.settings_tor_official))
                        }
                    }
                }
            }

            item { SettingsSectionTitle(stringResource(R.string.settings_manual_proxy)) }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RowSwitch(stringResource(R.string.settings_use_app_proxy), stringResource(R.string.settings_app_proxy_desc), proxy.enabled && !torEnabled) {
                            if (!torEnabled) viewModel.updateProxy(proxy.copy(enabled = it))
                        }
                        if (proxy.enabled && !torEnabled) {
                            DropdownRow(stringResource(R.string.settings_proxy_type), proxy.type, listOf("HTTP" to "HTTP", "SOCKS" to "SOCKS")) { viewModel.updateProxy(proxy.copy(type = it)) }
                            OutlinedTextField(proxy.host, { viewModel.updateProxy(proxy.copy(host = it)) }, label = { Text(stringResource(R.string.settings_proxy_host)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(20.dp))
                            OutlinedTextField(if (proxy.port == 0) "" else proxy.port.toString(), { value -> viewModel.updateProxy(proxy.copy(port = value.filter(Char::isDigit).toIntOrNull() ?: 0)) }, label = { Text(stringResource(R.string.settings_proxy_port)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(20.dp))
                        }
                        if (torEnabled) {
                            Text(stringResource(R.string.settings_tor_active_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
