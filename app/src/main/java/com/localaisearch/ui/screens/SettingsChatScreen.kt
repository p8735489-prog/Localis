package com.localaisearch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.R
import com.localaisearch.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsChatScreen(onNavigateBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val autoScroll by viewModel.chatAutoScroll.collectAsState(true)
    val markdown by viewModel.chatMarkdown.collectAsState(true)
    val code by viewModel.chatCodeHighlight.collectAsState(true)
    val enterSend by viewModel.chatEnterSend.collectAsState(true)
    val autoCopy by viewModel.chatAutoCopy.collectAsState(false)
    Scaffold(topBar = { SettingsTopBar(title = stringResource(R.string.settings_chat), onBack = onNavigateBack) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = SettingsContentPadding, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item { SettingsSectionTitle(stringResource(R.string.settings_message_display)) }
            item { SwitchRow(stringResource(R.string.settings_auto_scroll), autoScroll) { viewModel.updateChatAutoScroll(it) } }
            item { SwitchRow(stringResource(R.string.settings_markdown), markdown) { viewModel.updateChatMarkdown(it) } }
            item { SwitchRow(stringResource(R.string.settings_code_highlighting), code) { viewModel.updateChatCodeHighlight(it) } }
            item { SettingsSectionTitle(stringResource(R.string.settings_behavior)) }
            item { SwitchRow(stringResource(R.string.settings_enter_sends), enterSend) { viewModel.updateChatEnterSend(it) } }
            item { SwitchRow(stringResource(R.string.settings_auto_copy), autoCopy) { viewModel.updateChatAutoCopy(it) } }
        }
    }
}
