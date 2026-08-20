package com.localaisearch.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.R
import com.localaisearch.data.repository.LanguageManager
import com.localaisearch.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(onNavigateBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val selected by viewModel.language.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    var query by rememberSaveable { mutableStateOf("") }
    val languages = LanguageManager.SUPPORTED_LANGUAGES
    val filtered = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) languages else languages.filter { code ->
            languageSearchText(code).lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.language_settings), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(46.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.language_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.language_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraLarge,
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            placeholder = { Text(stringResource(R.string.language_search)) },
                            label = null
                        )
                    }
                }
            }
            items(filtered, key = { it }) { code ->
                LanguageItem(code, selected == code) {
                    viewModel.updateLanguage(code)
                    activity?.let { LanguageManager(it).applyLanguage(it, code) }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_results),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.language_restart_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private fun languageSearchText(code: String): String = when (code) {
    LanguageManager.SYSTEM_DEFAULT -> "system default 系统默认 system"
    LanguageManager.ENGLISH -> "english 英语 english"
    LanguageManager.SIMPLIFIED_CHINESE -> "简体中文 simplified chinese chinese 中文 zh"
    LanguageManager.TRADITIONAL_CHINESE -> "繁體中文 traditional chinese chinese 中文 zh"
    LanguageManager.RUSSIAN -> "русский russian 俄语 русский"
    LanguageManager.KOREAN -> "한국어 korean 韩语 한국어"
    LanguageManager.JAPANESE -> "日本語 japanese 日语 日本語"
    LanguageManager.ARABIC -> "العربية arabic 阿拉伯语 العربية"
    LanguageManager.PORTUGUESE -> "português portuguese 葡萄牙语 português"
    LanguageManager.FRENCH -> "français french 法语 français"
    LanguageManager.GERMAN -> "deutsch german 德语 deutsch"
    else -> code
}

@Composable
private fun LanguageItem(code: String, selected: Boolean, onSelect: () -> Unit) {
    val title = when(code) {
        LanguageManager.SYSTEM_DEFAULT -> stringResource(R.string.language_system)
        LanguageManager.ENGLISH -> stringResource(R.string.language_english)
        LanguageManager.SIMPLIFIED_CHINESE -> stringResource(R.string.language_simplified_chinese)
        LanguageManager.TRADITIONAL_CHINESE -> stringResource(R.string.language_traditional_chinese)
        LanguageManager.RUSSIAN -> stringResource(R.string.language_russian)
        LanguageManager.KOREAN -> stringResource(R.string.language_korean)
        LanguageManager.JAPANESE -> stringResource(R.string.language_japanese)
        LanguageManager.ARABIC -> stringResource(R.string.language_arabic)
        LanguageManager.PORTUGUESE -> stringResource(R.string.language_portuguese)
        LanguageManager.FRENCH -> stringResource(R.string.language_french)
        LanguageManager.GERMAN -> stringResource(R.string.language_german)
        else -> code
    }
    val native = when(code) {
        LanguageManager.SYSTEM_DEFAULT -> "Auto"
        LanguageManager.ENGLISH -> "English"
        LanguageManager.SIMPLIFIED_CHINESE -> "简体中文"
        LanguageManager.TRADITIONAL_CHINESE -> "繁體中文"
        LanguageManager.RUSSIAN -> "Русский"
        LanguageManager.KOREAN -> "한국어"
        LanguageManager.JAPANESE -> "日本語"
        LanguageManager.ARABIC -> "العربية"
        LanguageManager.PORTUGUESE -> "Português"
        LanguageManager.FRENCH -> "Français"
        LanguageManager.GERMAN -> "Deutsch"
        else -> code
    }

    Surface(
        color = if(selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = if(selected) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().selectable(selected, onSelect, role = Role.RadioButton)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = native.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(native, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
