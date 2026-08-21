package com.localaisearch.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material3.*
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.R
import com.localaisearch.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearanceScreen(onNavigateBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val darkMode by viewModel.darkMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val themePreset by viewModel.themePreset.collectAsState()
    val animationLevel by viewModel.animationLevel.collectAsState()
    val fontMode by viewModel.fontMode.collectAsState()
    val activity = LocalContext.current as? Activity

    Scaffold(topBar = {
        SettingsTopBar(title = stringResource(R.string.settings_appearance), onBack = onNavigateBack)
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = SettingsContentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SettingsSectionTitle(stringResource(R.string.appearance_theme)) }
            item {
                SettingsChoiceCard(
                    icon = Icons.Rounded.DarkMode,
                    title = stringResource(R.string.appearance_dark_mode),
                    value = when(darkMode) {
                        "light" -> stringResource(R.string.appearance_light)
                        "dark" -> stringResource(R.string.appearance_dark)
                        else -> stringResource(R.string.appearance_system)
                    },
                    options = listOf(
                        stringResource(R.string.appearance_system) to "system",
                        stringResource(R.string.appearance_light) to "light",
                        stringResource(R.string.appearance_dark) to "dark"
                    ),
                    onSelect = viewModel::updateDarkMode
                )
            }
            item {
                SettingsSwitchCard(
                    icon = Icons.Rounded.Colorize,
                    title = stringResource(R.string.appearance_monet),
                    subtitle = stringResource(R.string.appearance_monet_desc),
                    checked = dynamicColor,
                    onCheckedChange = viewModel::updateDynamicColor
                )
            }
            item {
                SettingsChoiceCard(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.appearance_font_family),
                    value = when (fontMode) {
                        "google_sans" -> stringResource(R.string.appearance_font_google)
                        else -> stringResource(R.string.appearance_font_system)
                    },
                    options = listOf(
                        stringResource(R.string.appearance_font_system) to "system",
                        stringResource(R.string.appearance_font_google) to "google_sans"
                    ),
                    onSelect = { mode ->
                        viewModel.updateFontMode(mode)
                        activity?.window?.decorView?.postDelayed({ activity.recreate() }, 220)
                    }
                )
            }
            item {
                ThemePresetCard(
                    selected = themePreset,
                    onSelect = { preset ->
                    viewModel.updateDynamicColor(false)
                    viewModel.updateThemePreset(preset)
                }
                )
            }

            item { SettingsSectionTitle(stringResource(R.string.appearance_ai_core)) }
            item {
                SettingsChoiceCard(
                    icon = Icons.Rounded.Animation,
                    title = stringResource(R.string.appearance_animation_level),
                    value = when(animationLevel) {
                        "off" -> stringResource(R.string.appearance_animation_off)
                        "low" -> stringResource(R.string.appearance_animation_low)
                        "high" -> stringResource(R.string.appearance_animation_high)
                        else -> stringResource(R.string.appearance_animation_standard)
                    },
                    options = listOf(
                        stringResource(R.string.appearance_animation_off) to "off",
                        stringResource(R.string.appearance_animation_low) to "low",
                        stringResource(R.string.appearance_animation_standard) to "standard",
                        stringResource(R.string.appearance_animation_high) to "high"
                    ),
                    onSelect = viewModel::updateAnimationLevel
                )
            }
        }
    }
}

@Composable
private fun SettingsChoiceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            ListItem(
                leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(value, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = { Icon(Icons.Rounded.KeyboardArrowDown, null) },
                modifier = Modifier.fillMaxWidth().clickable { expanded = true }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (display, id) ->
                    DropdownMenuItem(text = { Text(display, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onSelect(id); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = { Switch(checked, onCheckedChange) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ThemePresetCard(selected: String, onSelect: (String) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.appearance_presets), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.appearance_presets_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Triple("blue", R.string.appearance_blue, androidx.compose.ui.graphics.Color(0xFF6F8DFF)),
                    Triple("red", R.string.appearance_red, androidx.compose.ui.graphics.Color(0xFFE85B5B)),
                    Triple("yellow", R.string.appearance_yellow, androidx.compose.ui.graphics.Color(0xFFE7C34B)),
                    Triple("green", R.string.appearance_green, androidx.compose.ui.graphics.Color(0xFF58B978))
                ).forEach { (key, label, swatch) ->
                    FilterChip(
                        selected = selected == key,
                        onClick = { onSelect(key) },
                        leadingIcon = {
                            Box(Modifier.size(18.dp).background(swatch, CircleShape))
                        },
                        label = { Text(stringResource(label)) }
                    )
                }
            }
        }
    }
}
