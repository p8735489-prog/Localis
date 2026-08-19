package com.localaisearch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.data.search.SearchProviderType
import com.localaisearch.ui.viewmodel.SettingsViewModel
import androidx.compose.ui.res.stringResource
import com.localaisearch.R

/**
 * Settings screen - search API, model parameters, appearance, privacy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDataSecurity: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val searchConfig by viewModel.searchConfig.collectAsState()
    val inferenceConfig by viewModel.inferenceConfig.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val internetSearch by viewModel.internetSearchEnabled.collectAsState()

    val language by viewModel.language.collectAsState()
    val animationLevel by viewModel.animationLevel.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Search Engine ──
            item {
                SectionCard(title = stringResource(R.string.settings_search_engine)) {
                    DropdownRow(
                        label = stringResource(R.string.settings_search_provider),
                        selectedValue = searchConfig.providerType.displayName,
                        options = SearchProviderType.entries.map { it.displayName to it },
                        onSelect = { viewModel.updateSearchProvider(it) }
                    )

                    OutlinedTextFieldRow(
                        label = stringResource(R.string.settings_api_url),
                        value = searchConfig.apiUrl,
                        onValueChange = { viewModel.updateApiUrl(it) },
                        placeholder = stringResource(R.string.settings_api_url_placeholder)
                    )

                    OutlinedTextFieldRow(
                        label = stringResource(R.string.settings_api_key),
                        value = searchConfig.apiKey,
                        onValueChange = { viewModel.updateApiKey(it) },
                        placeholder = stringResource(R.string.settings_api_key_placeholder),
                        password = true
                    )

                    DropdownRow(
                        label = stringResource(R.string.settings_search_language),
                        selectedValue = searchConfig.searchLanguage,
                        options = listOf(
                            "auto" to "auto", "en" to "en", "zh" to "zh",
                            "de" to "de", "fr" to "fr", "es" to "es",
                            "ja" to "ja", "ko" to "ko", "ru" to "ru"
                        ),
                        onSelect = { viewModel.updateSearchLanguage(it) }
                    )

                    DropdownRow(
                        label = stringResource(R.string.settings_search_region),
                        selectedValue = searchConfig.searchRegion,
                        options = listOf(
                            "global" to "global", "us" to "us", "cn" to "cn",
                            "de" to "de", "fr" to "fr", "jp" to "jp", "uk" to "uk"
                        ),
                        onSelect = { viewModel.updateSearchRegion(it) }
                    )

                    SliderRow(
                        label = stringResource(R.string.settings_max_results),
                        value = searchConfig.maxResults.toFloat(),
                        range = 5f..20f,
                        steps = 14,
                        valueText = searchConfig.maxResults.toString(),
                        onValueChange = { viewModel.updateMaxResults(it.toInt()) }
                    )

                    SliderRow(
                        label = stringResource(R.string.settings_max_rounds),
                        value = searchConfig.maxSearchRounds.toFloat(),
                        range = 1f..3f,
                        steps = 1,
                        valueText = searchConfig.maxSearchRounds.toString(),
                        onValueChange = { viewModel.updateMaxRounds(it.toInt()) }
                    )

                    SwitchRow(
                        label = stringResource(R.string.settings_safe_search),
                        checked = searchConfig.enableSafeSearch,
                        onCheckedChange = { viewModel.updateSafeSearch(it) }
                    )
                }
            }

            // ── Model Parameters ──
            item {
                SectionCard(title = stringResource(R.string.settings_model_parameters)) {
                    SliderRow(
                        label = stringResource(R.string.settings_temperature),
                        value = inferenceConfig.temperature,
                        range = 0f..2f,
                        steps = 39,
                        valueText = "%.2f".format(inferenceConfig.temperature),
                        onValueChange = { viewModel.updateTemperature(it) }
                    )

                    SliderRow(
                        label = stringResource(R.string.settings_top_p),
                        value = inferenceConfig.topP,
                        range = 0f..1f,
                        steps = 19,
                        valueText = "%.2f".format(inferenceConfig.topP),
                        onValueChange = { viewModel.updateTopP(it) }
                    )

                    SliderRow(
                        label = stringResource(R.string.settings_top_k),
                        value = inferenceConfig.topK.toFloat(),
                        range = 1f..100f,
                        steps = 98,
                        valueText = inferenceConfig.topK.toString(),
                        onValueChange = { viewModel.updateTopK(it.toInt()) }
                    )

                    SliderRow(
                        label = stringResource(R.string.settings_context_length),
                        value = inferenceConfig.contextLength.toFloat(),
                        range = 512f..32768f,
                        steps = 50,
                        valueText = inferenceConfig.contextLength.toString(),
                        onValueChange = { viewModel.updateContextLength(it.toInt()) }
                    )

                    SliderRow(
                        label = stringResource(R.string.settings_max_tokens),
                        value = inferenceConfig.maxTokens.toFloat(),
                        range = 128f..8192f,
                        steps = 50,
                        valueText = inferenceConfig.maxTokens.toString(),
                        onValueChange = { viewModel.updateMaxTokens(it.toInt()) }
                    )

                    SwitchRow(
                        label = stringResource(R.string.settings_gpu_acceleration),
                        checked = inferenceConfig.useGpu,
                        onCheckedChange = { viewModel.updateUseGpu(it) }
                    )

                    if (inferenceConfig.useGpu) {
                        SliderRow(
                            label = stringResource(R.string.settings_gpu_layers),
                            value = inferenceConfig.gpuLayers.toFloat(),
                            range = 0f..100f,
                            steps = 99,
                            valueText = inferenceConfig.gpuLayers.toString(),
                            onValueChange = { viewModel.updateGpuLayers(it.toInt()) }
                        )
                    }

                    SliderRow(
                        label = stringResource(R.string.settings_download_threads),
                        value = inferenceConfig.threads.toFloat(),
                        range = 1f..8f,
                        steps = 6,
                        valueText = inferenceConfig.threads.toString(),
                        onValueChange = { viewModel.updateThreads(it.toInt()) }
                    )
                }
            }

            // ── Appearance ──
            item {
                SectionCard(title = stringResource(R.string.settings_appearance_section)) {
                    DropdownRow(
                        label = stringResource(R.string.appearance_dark_mode),
                        selectedValue = darkMode,
                        options = listOf(
                            "system" to "system",
                            "light" to "light",
                            "dark" to "dark"
                        ),
                        onSelect = { viewModel.updateDarkMode(it) }
                    )

                    SwitchRow(
                        label = stringResource(R.string.appearance_monet),
                        checked = dynamicColor,
                        onCheckedChange = { viewModel.updateDynamicColor(it) }
                    )
                }
            }

            // ── Privacy & Data ──
            item {
                SectionCard(title = stringResource(R.string.settings_privacy_data)) {
                    SwitchRow(
                        label = stringResource(R.string.settings_enable_internet_search),
                        checked = internetSearch,
                        onCheckedChange = { viewModel.updateInternetSearch(it) }
                    )

                    if (internetSearch) {
                        Text(
                            text = stringResource(R.string.settings_search_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_gguf_privacy_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Data & Security navigation entry
                    Surface(
                        onClick = onNavigateToDataSecurity,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.settings_data_security),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_data_security_desc_short),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}
