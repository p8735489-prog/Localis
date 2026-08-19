package com.localaisearch.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.R
import com.localaisearch.data.model.GGUFModel
import com.localaisearch.data.model.displaySize
import com.localaisearch.data.repository.DownloadState
import com.localaisearch.data.repository.HFModelFile
import com.localaisearch.data.repository.HFModelInfo
import com.localaisearch.data.repository.ModelRepositoryFactory
import com.localaisearch.data.performance.HardwareDetector
import com.localaisearch.ui.animation.SpringSpecs
import com.localaisearch.ui.components.Material3LoadingIndicator
import com.localaisearch.ui.viewmodel.ModelCenterViewModel

/**
 * Model Center screen - discover, search, browse, and download GGUF models
 * from Hugging Face Hub and domestic Hugging Face mirror.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCenterScreen(
    onNavigateBack: () -> Unit,
    onModelLoaded: () -> Unit = {},
    viewModel: ModelCenterViewModel = viewModel()
) {
    val colorScheme = MaterialTheme.colorScheme
    val searchResults by viewModel.searchResults.collectAsState()
    val trendingModels by viewModel.trendingModels.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadedModels by viewModel.downloadedModels.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf<HFModelInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.model_center)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh_models))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Material 3 unified search field: one clean row, no parenthetical suffixes.
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {
                    val hot = searchQuery.trim().equals("热门", true) ||
                        searchQuery.trim().equals("hot", true) ||
                        searchQuery.trim().equals("trending", true)
                    selectedTab = 1
                    viewModel.search(if (hot) "" else searchQuery)
                },
                active = false,
                onActiveChange = {},
                placeholder = { Text(stringResource(R.string.search_models_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (isSearching) {
                        Material3LoadingIndicator(modifier = Modifier.size(28.dp), size = 28.dp)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = SearchBarDefaults.colors(
                    containerColor = colorScheme.surfaceContainerHigh
                )
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surfaceContainerLow
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(18.dp), tint = colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selectedSource == ModelRepositoryFactory.Source.HUGGING_FACE) stringResource(R.string.hf_official_source) else stringResource(R.string.hf_domestic_mirror),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.model_source_settings_hint), style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                }
            }

            // Error display
            error?.let { errorMsg ->
                Surface(
                    color = colorScheme.errorContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                }
            }

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text(stringResource(R.string.trending)) }
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text(stringResource(R.string.search_results)) }
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text(stringResource(R.string.downloaded)) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            when (selectedTab) {
                0 -> if (isLoadingTrending && trendingModels.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Material3LoadingIndicator(size = 72.dp)
                    }
                } else TrendingList(
                    models = trendingModels,
                    downloadStates = downloadStates,
                    onModelClick = { selectedModel = it },
                    onDownloadClick = { model, file ->
                        viewModel.startDownload(model, file)
                    }
                )
                1 -> SearchResultsList(
                    models = searchResults,
                    downloadStates = downloadStates,
                    isSearching = isSearching,
                    onModelClick = { selectedModel = it },
                    onDownloadClick = { model, file ->
                        viewModel.startDownload(model, file)
                    }
                )
                2 -> DownloadedModelsList(
                    models = downloadedModels,
                    downloadStates = downloadStates,
                    onDelete = { viewModel.deleteDownloadedModel(it) },
                    onLoad = { onModelLoaded() }
                )
            }
        }
    }

    // Model detail dialog
    selectedModel?.let { model ->
        ModelDetailDialog(
            model = model,
            downloadStates = downloadStates,
            onDismiss = { selectedModel = null },
            onDownload = { file ->
                viewModel.startDownload(model, file)
            },
            onCancel = { file ->
                viewModel.cancelDownload(model.id, file.path)
            },
            onPause = { file ->
                viewModel.pauseDownload(model.id)
            },
            onResume = { file ->
                viewModel.resumeDownload(model.id, file)
            }
        )
    }
}

@Composable
private fun TrendingList(
    models: List<HFModelInfo>,
    downloadStates: Map<String, DownloadState>,
    onModelClick: (HFModelInfo) -> Unit,
    onDownloadClick: (HFModelInfo, HFModelFile) -> Unit
) {
    if (models.isEmpty()) {
        EmptyState(message = stringResource(R.string.loading_trending))
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(models, key = { it.id }) { model ->
            ModelCard(
                model = model,
                downloadState = downloadStates[model.id],
                onClick = { onModelClick(model) },
                onDownload = { /* handled in detail dialog */ }
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    models: List<HFModelInfo>,
    downloadStates: Map<String, DownloadState>,
    isSearching: Boolean,
    onModelClick: (HFModelInfo) -> Unit,
    onDownloadClick: (HFModelInfo, HFModelFile) -> Unit
) {
    when {
        isSearching -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Material3LoadingIndicator(size = 56.dp)
            }
        }
        models.isEmpty() -> {
            EmptyState(message = stringResource(R.string.search_to_start))
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(models, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        downloadState = downloadStates[model.id],
                        onClick = { onModelClick(model) },
                        onDownload = { }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedModelsList(
    models: List<GGUFModel>,
    downloadStates: Map<String, DownloadState>,
    onDelete: (GGUFModel) -> Unit,
    onLoad: () -> Unit
) {
    if (models.isEmpty()) {
        EmptyState(message = stringResource(R.string.no_downloaded))
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(models, key = { it.id }) { model ->
            DownloadedModelCard(
                model = model,
                onDelete = { onDelete(model) },
                onLoad = onLoad
            )
        }
    }
}

@Composable
private fun ModelCard(
    model: HFModelInfo,
    downloadState: DownloadState?,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (model.author.isNotBlank()) {
                        Text(
                            text = model.author,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Download status indicator
                downloadState?.let { state ->
                    when (state) {
                        is DownloadState.Downloading -> {
                            CircularProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        is DownloadState.Completed -> {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = stringResource(R.string.downloaded),
                                tint = colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        is DownloadState.Error -> {
                            Icon(
                                imageVector = Icons.Filled.CloudOff,
                                contentDescription = stringResource(R.string.model_error_status),
                                tint = colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        else -> {}
                    }
                }
            }

            if (model.description.isNotBlank()) {
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconWithText(
                    icon = Icons.Filled.Star,
                    text = "${model.downloads / 1000}k",
                    color = colorScheme.onSurfaceVariant
                )
                IconWithText(
                    icon = Icons.Filled.Favorite,
                    text = "${model.likes}",
                    color = colorScheme.onSurfaceVariant
                )
                model.tags.firstOrNull()?.let { tag ->
                    if (tag.contains("gguf", ignoreCase = true)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "GGUF",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                ModelFitChip(modelName = model.name)
            }
        }
    }
}

@Composable
private fun ModelFitChip(modelName: String) {
    val hardware = remember { HardwareDetector.detectHardware(androidx.compose.ui.platform.LocalContext.current) }
    val ramGb = hardware.totalRamBytes / (1024.0 * 1024.0 * 1024.0)
    val params = Regex("(\\d+(?:\\.\\d+)?)B", RegexOption.IGNORE_CASE).find(modelName)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    val level = when {
        params == null -> Triple("", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        ramGb >= params * 0.65 + 2.0 -> Triple(stringResource(R.string.performance_recommended), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        ramGb >= params * 0.38 + 1.5 -> Triple(stringResource(R.string.performance_possible), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        else -> Triple(stringResource(R.string.performance_insufficient), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    if (level.first.isNotBlank()) {
        Surface(shape = MaterialTheme.shapes.small, color = level.second) {
            Text(level.first, style = MaterialTheme.typography.labelSmall, color = level.third, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
        }
    }
}

@Composable
private fun DownloadedModelCard(
    model: GGUFModel,
    onDelete: () -> Unit,
    onLoad: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Size: ${model.displaySize}  Quant: ${model.quantization}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ModelDetailDialog(
    model: HFModelInfo,
    downloadStates: Map<String, DownloadState>,
    onDismiss: () -> Unit,
    onDownload: (HFModelFile) -> Unit,
    onCancel: (HFModelFile) -> Unit,
    onPause: (HFModelFile) -> Unit,
    onResume: (HFModelFile) -> Unit
) {
    var files by remember { mutableStateOf<List<HFModelFile>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(true) }
    val viewModel: ModelCenterViewModel = viewModel()

    // Load files on dialog open
    androidx.compose.runtime.LaunchedEffect(model.id) {
        val result = viewModel.listModelFiles(model.id)
        result.onSuccess { files = it }
        isLoadingFiles = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(model.name) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = model.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                if (model.description.isNotBlank()) {
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    IconWithText(Icons.Filled.Star, stringResource(R.string.performance_downloads, "${model.downloads / 1000}k"))
                    IconWithText(Icons.Filled.Favorite, "${model.likes} likes")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = stringResource(R.string.gguf_files),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoadingFiles) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Material3LoadingIndicator(modifier = Modifier.size(40.dp), size = 40.dp)
                    }
                } else if (files.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_gguf_files),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    files.forEach { file ->
                        FileDownloadCard(
                            file = file,
                            modelId = model.id,
                            downloadState = downloadStates[model.id],
                            onDownload = { onDownload(file) },
                            onCancel = { onCancel(file) },
                            onPause = { onPause(file) },
                            onResume = { onResume(file) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun FileDownloadCard(
    file: HFModelFile,
    modelId: String,
    downloadState: DownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = file.path.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = file.displaySize,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (file.quantization != "unknown") {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = file.quantization,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Action buttons based on download state
                when (val state = downloadState) {
                    is DownloadState.Downloading -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.pause), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    is DownloadState.Paused -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.resume), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    is DownloadState.Completed -> {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = stringResource(R.string.downloaded),
                            tint = colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is DownloadState.Error -> {
                        IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = stringResource(R.string.retry), tint = colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                    else -> {
                        Button(onClick = onDownload) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.download))
                        }
                    }
                }
            }

            // Progress indicator
            if (downloadState is DownloadState.Downloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${downloadState.progress.times(100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    val speed = downloadState.bytesPerSecond
                    val speedText = when {
                        speed > 1024 * 1024 -> "%.1f MB/s".format(speed / (1024.0 * 1024.0))
                        speed > 1024 -> "%.1f KB/s".format(speed / 1024.0)
                        else -> "$speed B/s"
                    }
                    Text(
                        text = speedText,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    val remainingBytes = downloadState.totalBytes - downloadState.bytesDownloaded
                    val etaSeconds = if (speed > 0) remainingBytes / speed else 0
                    val etaText = when {
                        etaSeconds > 3600 -> "%d h %d m".format(etaSeconds / 3600, (etaSeconds % 3600) / 60)
                        etaSeconds > 60 -> "%d m %d s".format(etaSeconds / 60, etaSeconds % 60)
                        else -> "%d s".format(etaSeconds)
                    }
                    Text(
                        text = stringResource(R.string.eta_format, etaText),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun IconWithText(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
