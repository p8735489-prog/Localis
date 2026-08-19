package com.localaisearch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import com.localaisearch.R
import com.localaisearch.data.repository.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onNavigateBack: () -> Unit, onNavigateToAI: () -> Unit, onNavigateToNetwork: () -> Unit,
    onNavigateToPrivacy: () -> Unit, onNavigateToAppearance: () -> Unit, onNavigateToChat: () -> Unit,
    onNavigateToPerformance: () -> Unit, onNavigateToData: () -> Unit, onNavigateToAbout: () -> Unit,
    onNavigateToLanguage: () -> Unit, onNavigateToDataSecurity: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val languageManager = remember { LanguageManager(context) }
    val currentLanguageCode by languageManager.currentLanguage.collectAsState(initial = LanguageManager.SYSTEM_DEFAULT)
    val currentLanguageDisplay = remember(currentLanguageCode) { languageManager.getLanguageDisplayName(currentLanguageCode) }

    val groups = listOf(
        SettingsGroup(stringResource(R.string.settings_general), listOf(
            SettingsCategory(Icons.Filled.Language, stringResource(R.string.settings_language), currentLanguageDisplay, onNavigateToLanguage, "language 语言 idioma español english 日本語 한국어"),
            SettingsCategory(Icons.Filled.Lock, stringResource(R.string.settings_data_security), stringResource(R.string.settings_data_security_desc), onNavigateToDataSecurity, "data security 数据安全 privacy")
        )),
        SettingsGroup(stringResource(R.string.settings_ai), listOf(
            SettingsCategory(Icons.Filled.AutoAwesome, stringResource(R.string.settings_ai_models), stringResource(R.string.settings_ai_models_desc), onNavigateToAI, "ai model models 模型 llm") ,
            SettingsCategory(Icons.Filled.Chat, stringResource(R.string.settings_chat), stringResource(R.string.settings_chat_desc), onNavigateToChat, "chat 聊天 conversation") ,
            SettingsCategory(Icons.Filled.Speed, stringResource(R.string.settings_performance), stringResource(R.string.settings_performance_desc), onNavigateToPerformance, "performance 性能 speed cpu gpu")
        )),
        SettingsGroup(stringResource(R.string.settings_network), listOf(
            SettingsCategory(Icons.Filled.Cloud, stringResource(R.string.settings_network_search), stringResource(R.string.settings_network_search_desc), onNavigateToNetwork, "network 网络 search 搜索 internet")
        )),
        SettingsGroup(stringResource(R.string.settings_privacy), listOf(
            SettingsCategory(Icons.Filled.Security, stringResource(R.string.settings_privacy_security), stringResource(R.string.settings_privacy_security_desc), onNavigateToPrivacy, "privacy 隐私 security 安全")
        )),
        SettingsGroup(stringResource(R.string.settings_appearance_group), listOf(
            SettingsCategory(Icons.Filled.Palette, stringResource(R.string.settings_appearance), stringResource(R.string.settings_appearance_desc), onNavigateToAppearance, "appearance 外观 theme 主题 color 颜色 monet")
        )),
        SettingsGroup(stringResource(R.string.settings_data), listOf(
            SettingsCategory(Icons.Filled.Folder, stringResource(R.string.settings_data), stringResource(R.string.settings_data_desc), onNavigateToData, "data 数据 storage 存储")
        )),
        SettingsGroup(stringResource(R.string.settings_about), listOf(
            SettingsCategory(Icons.Filled.Info, stringResource(R.string.settings_about), stringResource(R.string.settings_about_desc), onNavigateToAbout, "about 关于 version 版本")
        ))
    )

    val visibleGroups = remember(searchQuery, groups) {
        if (searchQuery.isBlank()) groups else groups.mapNotNull { g ->
            val query = searchQuery.trim()
            val filtered = g.items.filter {
                it.label.contains(query, true) ||
                    it.description.contains(query, true) ||
                    it.aliases.contains(query, true)
            }
            if (filtered.isEmpty()) null else g.copy(items = filtered)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, padding.calculateTopPadding() + 8.dp, 20.dp, padding.calculateBottomPadding() + 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.settings_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            }
            visibleGroups.forEach { group ->
                item(key = group.title) { SettingsGroupBlock(group) }
            }
        }
    }
}

private data class SettingsGroup(val title: String, val items: List<SettingsCategory>)
private data class SettingsCategory(
    val icon: ImageVector,
    val label: String,
    val description: String,
    val onClick: () -> Unit,
    val aliases: String = ""
)

@Composable
private fun SettingsGroupBlock(group: SettingsGroup) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            group.title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 7.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Column {
                group.items.forEachIndexed { index, item ->
                    SettingsCategoryRow(item)
                    if (index < group.items.lastIndex) {
                        HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(category: SettingsCategory) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .985f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "settingsPress")
    Row(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.clickable(source, null, onClick = category.onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(category.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(21.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(category.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(category.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.6f))
    }
}
