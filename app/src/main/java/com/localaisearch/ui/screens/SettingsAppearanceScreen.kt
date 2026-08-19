package com.localaisearch.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material3.*
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
        TopAppBar(
            title = { Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SettingsSectionTitle(stringResource(R.string.appearance_theme)) }
            item {
                SettingsChoiceCard(
                    icon = Icons.Filled.DarkMode,
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
                    icon = Icons.Filled.Colorize,
                    title = stringResource(R.string.appearance_monet),
                    subtitle = stringResource(R.string.appearance_monet_desc),
                    checked = dynamicColor,
                    onCheckedChange = viewModel::updateDynamicColor
                )
            }
            item {
                SettingsChoiceCard(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.appearance_font_family),
                    value = when (fontMode) {
                        "google_sans" -> stringResource(R.string.appearance_font_google)
                        "pingfang" -> stringResource(R.string.appearance_font_pingfang)
                        else -> stringResource(R.string.appearance_font_system)
                    },
                    options = listOf(
                        stringResource(R.string.appearance_font_system) to "system",
                        stringResource(R.string.appearance_font_google) to "google_sans",
                        stringResource(R.string.appearance_font_pingfang) to "pingfang"
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
                    icon = Icons.Filled.Animation,
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
private fun SettingsChoiceCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, options: List<Pair<String,String>>, onSelect: (String)->Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        DropdownRow(label = title, selectedValue = value, options = options, onSelect = onSelect)
    }
}

@Composable
private fun SettingsSwitchCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean)->Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end=12.dp))
                Column { Text(title, style=MaterialTheme.typography.bodyLarge); Text(subtitle, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Switch(checked, onCheckedChange)
        }
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
