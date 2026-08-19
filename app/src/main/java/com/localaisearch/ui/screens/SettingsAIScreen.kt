@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.localaisearch.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.R
import com.localaisearch.data.model.HardwareBackend
import com.localaisearch.ui.viewmodel.LoadStatus
import com.localaisearch.ui.viewmodel.ModelViewModel
import com.localaisearch.ui.viewmodel.SettingsViewModel

/**
 * AI & Models settings sub-page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAIScreen(
    onNavigateBack: () -> Unit,
    onNavigateToModelManager: () -> Unit,
    onNavigateToVisionModels: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
    modelViewModel: ModelViewModel = viewModel()
) {
    val inferenceConfig by viewModel.inferenceConfig.collectAsState()
    val loadStatus by modelViewModel.loadStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_ai_models)) },
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
            item { LoadModelCard(loadStatus, onNavigateToModelManager) }
            item {
                Card(
                    onClick = onNavigateToVisionModels,
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_vision_models), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.settings_vision_models_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                }
            }
            item { SettingsSectionTitle(stringResource(R.string.settings_inference_parameters)) }
            item {
                SliderRow(stringResource(R.string.settings_temperature), inferenceConfig.temperature, 0f..2f, 39, "%.2f".format(inferenceConfig.temperature)) { viewModel.updateTemperature(it) }
            }
            item {
                SliderRow(stringResource(R.string.settings_top_p), inferenceConfig.topP, 0f..1f, 19, "%.2f".format(inferenceConfig.topP)) { viewModel.updateTopP(it) }
            }
            item {
                SliderRow(stringResource(R.string.settings_top_k), inferenceConfig.topK.toFloat(), 1f..100f, 98, inferenceConfig.topK.toString()) { viewModel.updateTopK(it.toInt()) }
            }
            item {
                SliderRow(stringResource(R.string.settings_context_length), inferenceConfig.contextLength.toFloat(), 512f..32768f, 50, inferenceConfig.contextLength.toString()) { viewModel.updateContextLength(it.toInt()) }
            }
            item {
                SliderRow(stringResource(R.string.settings_max_tokens), inferenceConfig.maxTokens.toFloat(), 128f..8192f, 50, inferenceConfig.maxTokens.toString()) { viewModel.updateMaxTokens(it.toInt()) }
            }
            item { SettingsSectionTitle(stringResource(R.string.settings_penalties)) }
            item {
                SliderRow(stringResource(R.string.settings_frequency_penalty), inferenceConfig.frequencyPenalty, -2f..2f, 40, if (inferenceConfig.frequencyPenalty == 0f) stringResource(R.string.settings_penalty_unspecified) else "%.2f".format(inferenceConfig.frequencyPenalty)) { viewModel.updateFrequencyPenalty(it) }
                Text(stringResource(R.string.settings_repeat_token_penalty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            item {
                SliderRow(stringResource(R.string.settings_presence_penalty), inferenceConfig.presencePenalty, -2f..2f, 40, if (inferenceConfig.presencePenalty == 0f) stringResource(R.string.settings_penalty_unspecified) else "%.2f".format(inferenceConfig.presencePenalty)) { viewModel.updatePresencePenalty(it) }
                Text(stringResource(R.string.settings_topic_diversity), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            item { SettingsSectionTitle(stringResource(R.string.settings_thinking_depth)) }
            item {
                SliderRow(
                    stringResource(R.string.settings_thinking_depth),
                    inferenceConfig.thinkingDepth.toFloat(),
                    1f..4f,
                    3,
                    stringResource(
                        when (inferenceConfig.thinkingDepth) {
                            1 -> R.string.settings_thinking_fast
                            2 -> R.string.settings_thinking_balanced
                            3 -> R.string.settings_thinking_deep
                            else -> R.string.settings_thinking_max
                        }
                    )
                ) { viewModel.updateThinkingDepth(it.toInt()) }
            }
            item {
                Text(stringResource(R.string.settings_compute_backend), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        HardwareBackend.CPU to R.string.settings_backend_cpu,
                        HardwareBackend.GPU to R.string.settings_backend_gpu,
                        HardwareBackend.NPU to R.string.settings_backend_npu
                    ).forEach { (backend, label) ->
                        FilterChip(
                            selected = inferenceConfig.backend == backend,
                            onClick = { viewModel.updateBackend(backend) },
                            label = { Text(stringResource(label)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    stringResource(R.string.settings_backend_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            item { SettingsSectionTitle(stringResource(R.string.settings_hardware)) }
            item {
                SwitchRow(stringResource(R.string.settings_gpu_acceleration), inferenceConfig.useGpu) { viewModel.updateUseGpu(it) }
            }
            if (inferenceConfig.useGpu) {
                item {
                    SliderRow(stringResource(R.string.settings_gpu_layers), inferenceConfig.gpuLayers.toFloat(), 0f..100f, 99, inferenceConfig.gpuLayers.toString()) { viewModel.updateGpuLayers(it.toInt()) }
                }
            }
            item {
                SliderRow(stringResource(R.string.settings_threads), inferenceConfig.threads.toFloat(), 1f..8f, 6, inferenceConfig.threads.toString()) { viewModel.updateThreads(it.toInt()) }
            }
        }
    }
}

@Composable
private fun LoadModelCard(
    loadStatus: LoadStatus,
    onClick: () -> Unit
) {
    val isLoading = loadStatus is LoadStatus.Loading

    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val animatedValue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientAnim"
    )

    val gradientBrush = if (isLoading) {
        val color1 = Color(0xFF6B5B95)
        val color2 = Color(0xFF8B7FD4)
        val color3 = Color(0xFF7FB8D4)
        Brush.linearGradient(
            colors = listOf(color1, color2, color3, color2, color1),
            start = androidx.compose.ui.geometry.Offset(animatedValue * 1000f, 0f),
            end = androidx.compose.ui.geometry.Offset(animatedValue * 1000f + 1000f, 0f),
            tileMode = TileMode.Mirror
        )
    } else {
        null
    }

    val title = stringResource(R.string.load_model)
    val subtitle = when (loadStatus) {
        is LoadStatus.Loading -> "正在加载模型…"
        is LoadStatus.Loaded -> "模型已加载"
        is LoadStatus.Error -> "模型加载失败"
        else -> "选择并加载本地 AI 模型"
    }
    val statusText = when (loadStatus) {
        is LoadStatus.Loading -> loadStatus.model.name
        is LoadStatus.Loaded -> loadStatus.model.name
        is LoadStatus.Error -> "未加载"
        else -> "未加载"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoading) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (gradientBrush != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(gradientBrush)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isLoading) Color.White else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLoading) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isLoading) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLoading) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isLoading) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
