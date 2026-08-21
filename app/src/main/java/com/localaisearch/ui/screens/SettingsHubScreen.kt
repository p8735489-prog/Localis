package com.localaisearch.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.R
import com.localaisearch.ui.viewmodel.SettingsViewModel

/** Completely re-laid-out M3 settings hub: quick actions first, grouped destinations second. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAI: () -> Unit,
    onNavigateToNetwork: () -> Unit,
    onNavigateToTorProxy: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToPerformance: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToDataSecurity: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val inferenceConfig by viewModel.inferenceConfig.collectAsState()
    val privateMode by viewModel.privacyMode.collectAsState()
    val searchEnabled by viewModel.internetSearchEnabled.collectAsState()
    val torStatus by viewModel.torStatus.collectAsState()
    var query by remember { mutableStateOf("") }

    val sections = remember {
        listOf(
            SettingsEntry("ai", Icons.Rounded.AutoAwesome, R.string.settings_ai_models, R.string.settings_ai_models_desc, onNavigateToAI),
            SettingsEntry("performance", Icons.Rounded.Speed, R.string.settings_performance, R.string.settings_performance_desc, onNavigateToPerformance),
            SettingsEntry("appearance", Icons.Rounded.Palette, R.string.settings_appearance, R.string.settings_appearance_desc, onNavigateToAppearance),
            SettingsEntry("chat", Icons.Rounded.Settings, R.string.settings_chat, R.string.settings_chat_desc, onNavigateToChat),
            SettingsEntry("network", Icons.Rounded.Public, R.string.settings_network_search, R.string.settings_network_search_desc, onNavigateToNetwork),
            SettingsEntry("tor", Icons.Rounded.VpnKey, R.string.settings_tor_proxy, R.string.settings_tor_proxy_desc, onNavigateToTorProxy),
            SettingsEntry("privacy", Icons.Rounded.Lock, R.string.settings_privacy_security, R.string.settings_privacy_security_desc, onNavigateToPrivacy),
            SettingsEntry("data", Icons.Rounded.Memory, R.string.settings_data, R.string.settings_data_desc, onNavigateToData),
            SettingsEntry("security", Icons.Rounded.Lock, R.string.settings_data_security, R.string.settings_data_security_desc, onNavigateToDataSecurity),
            SettingsEntry("language", Icons.Rounded.Language, R.string.settings_language, R.string.settings_language, onNavigateToLanguage),
            SettingsEntry("about", Icons.Rounded.Settings, R.string.settings_about, R.string.settings_about_desc, onNavigateToAbout)
        )
    }
    val filtered = if (query.isBlank()) sections else sections.filter { entry ->
        stringResource(entry.title).contains(query, true) || stringResource(entry.description).contains(query, true)
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.settings),
                onBack = onNavigateBack,
                subtitle = stringResource(R.string.settings_quick_start)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(18.dp, padding.calculateTopPadding() + 6.dp, 18.dp, padding.calculateBottomPadding() + 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {},
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text(stringResource(R.string.settings_search), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {}
            }

            if (query.isBlank()) {
                item { QuickStartPanel(inferenceConfig.useGpu, privateMode, searchEnabled, torStatus.name == "ON", viewModel, onNavigateToPerformance) }
                item { SettingsSectionHeader(stringResource(R.string.settings_ai_models)) }
                item { DestinationCard(sections.first { it.key == "ai" }) }
                item { DestinationCard(sections.first { it.key == "performance" }) }
                item { DestinationCard(sections.first { it.key == "appearance" }) }
                item { SettingsSectionHeader(stringResource(R.string.settings_network_search)) }
                item { DestinationCard(sections.first { it.key == "network" }) }
                item { DestinationCard(sections.first { it.key == "tor" }) }
                item { DestinationCard(sections.first { it.key == "privacy" }) }
                item { SettingsSectionHeader(stringResource(R.string.settings_data)) }
                item { DestinationCard(sections.first { it.key == "data" }) }
                item { DestinationCard(sections.first { it.key == "security" }) }
                item { DestinationCard(sections.first { it.key == "language" }) }
                item { DestinationCard(sections.first { it.key == "about" }) }
            } else {
                items(filtered, key = { it.key }) { DestinationCard(it) }
            }
        }
    }
}

private data class SettingsEntry(val key: String, val icon: ImageVector, val title: Int, val description: Int, val onClick: () -> Unit)


@Composable
private fun SettingsSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp))
}

@Composable
private fun QuickStartPanel(
    gpu: Boolean,
    privacy: Boolean,
    search: Boolean,
    tor: Boolean,
    viewModel: SettingsViewModel,
    onPerformance: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(11.dp).size(22.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.settings_quick_start), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.settings_quick_start_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            Surface(
                onClick = viewModel::enableRecommendedAcceleration,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_fast_inference), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(if (gpu) stringResource(R.string.settings_acceleration_ready) else stringResource(R.string.settings_cpu_fallback), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { TextButton(onClick = onPerformance) { Text(stringResource(R.string.settings_performance)) } }
                )
            }
            SettingToggleCard(stringResource(R.string.settings_privacy_security), privacy, viewModel::updatePrivacyMode)
            SettingToggleCard(stringResource(R.string.settings_network_search), search, viewModel::updateInternetSearch, supporting = if (tor) stringResource(R.string.settings_tor_connected) else null)
        }
    }
}

@Composable
private fun SettingToggleCard(title: String, checked: Boolean, onChange: (Boolean) -> Unit, supporting: String? = null) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        ListItem(
            headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = supporting?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            trailingContent = { Switch(checked = checked, onCheckedChange = onChange) }
        )
    }
}

@Composable
private fun DestinationCard(entry: SettingsEntry) {
    val interactionSource = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 650f),
        label = "settingsCardScale"
    )
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }
    Card(
        onClick = entry.onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        ListItem(
            leadingContent = { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) { Icon(entry.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(9.dp).size(22.dp)) } },
            headlineContent = { Text(stringResource(entry.title), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(stringResource(entry.description), maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
    }
}
