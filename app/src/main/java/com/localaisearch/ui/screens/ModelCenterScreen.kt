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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.data.model.GGUFModel
import com.localaisearch.data.repository.DownloadState
import com.localaisearch.data.repository.HFModelFile
import com.localaisearch.data.repository.HFModelInfo
import com.localaisearch.data.repository.ModelRepositoryFactory
import com.localaisearch.ui.animation.SpringSpecs
import com.localaisearch.ui.viewmodel.ModelCenterViewModel

/**
 * Model Center screen - discover, search, browse, and download GGUF models
 * from Hugging Face Hub and Tsinghua University mirror.
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
                title = { Text("Model Center") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(end = 8.dp)) {
                        SegmentedButton(
                            selected = selectedSource == ModelRepositoryFactory.Source.HUGGING_FACE,
                            onClick = { viewModel.setSource(ModelRepositoryFactory.Source.HUGGING_FACE) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) {
                            Text("HF", style = MaterialTheme.typography.labelSmall)
                        }
                        SegmentedButton(
                            selected = selectedSource == ModelRepositoryFactory.Source.TSINGHUA_MIRROR,
                            onClick = { viewModel.setSource(ModelRepositoryFactory.Source.TSINGHUA_MIRROR) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) {
                            Text("Mirror", style = MaterialTheme.typography.labelSmall)
                        }
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
            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search models (e.g., llama, qwen, phi)") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = colorScheme.surfaceContainer,
                        unfocusedContainerColor = colorScheme.surfaceContainer,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.search(searchQuery) },
                    enabled = searchQuery.isNotBlank() && !isSearching
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.onPrimary
                        )
                    } else {
                        Text("Search")
                    }
                }
            }

            // Error display
            error?.let { errorMsg ->
                Surface(
                    color = colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Error,
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
                            Text("Dismiss")
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
                    label = { Text("Trending") }
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Search Results") }
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Downloaded") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            when (selectedTab) {
                0 -> TrendingList(
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
        EmptyState(message = "Loading trending models...")
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
                CircularProgressIndicator()
            }
        }
        models.isEmpty() -> {
            EmptyState(message = "Search for GGUF models to get started")
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
        EmptyState(message = "No downloaded models yet.\nSearch and download from the Trending or Search tabs.")
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
        shape = RoundedCornerShape(12.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "@${model.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.primary
                    )
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
                                imageVector = Icons.Filled.CloudDownload,
                                contentDescription = "Downloaded",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        is DownloadState.Error -> {
                            Icon(
                                imageVector = Icons.Filled.CloudOff,
                                contentDescription = "Error",
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
            }
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
        shape = RoundedCornerShape(12.dp),
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
                imageVector = Icons.Outlined.FolderOpen,
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
                    contentDescription = "Delete",
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
            Column {
                Text(
                    text = "@${model.author}",
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
                    IconWithText(Icons.Filled.Star, "${model.downloads / 1000}k downloads")
                    IconWithText(Icons.Filled.Favorite, "${model.likes} likes")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "GGUF Files",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoadingFiles) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (files.isEmpty()) {
                    Text(
                        text = "No .gguf files found in this repository.",
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
                Text("Close")
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
        shape = RoundedCornerShape(8.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.path.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${file.displaySize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        if (file.quantization != "unknown") {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = file.quantization,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Action buttons based on download state
                when (val state = downloadState) {
                    is DownloadState.Downloading -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Pause, contentDescription = "Pause", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Cancel", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    is DownloadState.Paused -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Cancel", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    is DownloadState.Completed -> {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = "Downloaded",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is DownloadState.Error -> {
                        IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Error, contentDescription = "Retry", tint = colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                    else -> {
                        Button(onClick = onDownload) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download")
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
                        text = "ETA: $etaText",
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
