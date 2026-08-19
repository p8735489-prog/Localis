package com.localaisearch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.R
import com.localaisearch.ui.viewmodel.SettingsViewModel
import com.localaisearch.data.performance.HardwareDetector
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPerformanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val inferenceConfig by viewModel.inferenceConfig.collectAsState()
    val memoryOpt by viewModel.perfMemoryOptimization.collectAsState(true)
    val background by viewModel.perfBackgroundInference.collectAsState(false)
    val tempProtection by viewModel.perfTemperatureProtection.collectAsState(true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_performance)) },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SettingsSectionTitle(stringResource(R.string.settings_hardware_acceleration)) }
            item { SwitchRow(stringResource(R.string.settings_gpu_acceleration), inferenceConfig.useGpu) { viewModel.updateUseGpu(it) } }
            if (inferenceConfig.useGpu) {
                item { SliderRow(stringResource(R.string.settings_gpu_layers), inferenceConfig.gpuLayers.toFloat(), 0f..100f, 99, inferenceConfig.gpuLayers.toString()) { viewModel.updateGpuLayers(it.toInt()) } }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.settings_threads), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_threads_fast), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(0 to stringResource(R.string.settings_threads_original), 4 to stringResource(R.string.settings_threads_4), 6 to stringResource(R.string.settings_threads_6), 12 to stringResource(R.string.settings_threads_12)).forEach { (value, label) ->
                            FilterChip(selected = inferenceConfig.threads == value, onClick = { viewModel.updateThreads(value) }, label = { Text(label) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                val context = LocalContext.current
                val hardware = remember { HardwareDetector.detectHardware(context) }
                val ramGb = hardware.totalRamBytes / (1024.0 * 1024.0 * 1024.0)
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.settings_hardware_detected), style = MaterialTheme.typography.titleMedium)
                        Text("${hardware.buildManufacturer} · ${hardware.buildHardware}", style = MaterialTheme.typography.bodyMedium)
                        Text("${"%.1f".format(ramGb)} GB RAM · ${hardware.cpuCores} CPU · ${hardware.gpuVendor ?: "GPU —"} · ${hardware.chipsetFamily}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(when { hardware.supportsGoogleTpu -> R.string.hardware_tpu_status; hardware.supportsMediaTekApu -> R.string.hardware_mediatek_status; hardware.hasNpu -> R.string.hardware_npu_status; else -> R.string.hardware_cpu_status }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SettingsSectionTitle(stringResource(R.string.settings_optimization)) }
            item { SwitchRow(stringResource(R.string.settings_memory_optimization), memoryOpt) { viewModel.updatePerfMemoryOptimization(it) } }
            item { SwitchRow(stringResource(R.string.settings_background_inference), background) { viewModel.updatePerfBackgroundInference(it) } }
            item { SwitchRow(stringResource(R.string.settings_temperature_protection), tempProtection) { viewModel.updatePerfTemperatureProtection(it) } }
        }
    }
}
