
package com.localaisearch.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.R
import com.localaisearch.data.repository.DownloadState
import com.localaisearch.data.repository.HFModelFile
import com.localaisearch.data.repository.HFModelInfo
import com.localaisearch.ui.viewmodel.ModelCenterViewModel

/**
 * Dedicated vision-recognition model catalog.  Only repositories that look
 * vision-capable are shown, keeping the model manager focused on image
 * recognition rather than mixing text-only GGUF files into the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionModelScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModelCenterViewModel = viewModel()
) {
    val results by viewModel.searchResults.collectAsState()
    val searching by viewModel.isSearching.collectAsState()
    val downloads by viewModel.downloadStates.collectAsState()
    var query by remember { mutableStateOf("vision") }
    var selected by remember { mutableStateOf<HFModelInfo?>(null) }
    var files by remember { mutableStateOf<List<HFModelFile>>(emptyList()) }
    var loadingFiles by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.search("vision") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_vision_models)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.search(query.ifBlank { "vision" }) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_vision_source)) },
                shape = MaterialTheme.shapes.large
            )
            Text(
                stringResource(R.string.settings_vision_models_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            if (searching && results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                }
            } else {
                val vision = results.filter { model ->
                    val text = (model.id + " " + model.name + " " + model.description + " " + model.tags.joinToString(" ")).lowercase()
                    listOf("vision", "vlm", "multimodal", "llava", "qwen-vl", "qwen2-vl", "qwen3-vl", "internvl", "cogvlm", "gemma3", "paligemma").any(text::contains)
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(vision, key = { it.id }) { model ->
                        Card(
                            onClick = {
                                selected = model
                                loadingFiles = true
                                viewModel.viewModelScopeLaunch {
                                    files = viewModel.listModelFiles(model.id).getOrElse { emptyList() }
                                    loadingFiles = false
                                }
                            },
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(model.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(R.string.performance_downloads, model.downloads), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { model ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(model.name) },
            text = {
                if (loadingFiles) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(48.dp)) }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.gguf_files), style = MaterialTheme.typography.titleSmall)
                        files.forEach { file ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(file.path.substringAfterLast('/'), maxLines = 1)
                                    Text("${file.displaySize} · ${file.quantization}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { viewModel.startDownload(model, file) }) { Text(stringResource(R.string.download)) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.close)) } }
        )
    }
}
