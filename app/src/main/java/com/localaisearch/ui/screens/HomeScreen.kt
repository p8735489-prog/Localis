package com.localaisearch.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.R
import com.localaisearch.data.model.AgentState
import com.localaisearch.ui.components.AIOrb
import com.localaisearch.ui.components.AgentProgressBar
import com.localaisearch.ui.components.ChatBubble
import com.localaisearch.ui.components.MorphingSendButton
import com.localaisearch.ui.components.PrivacyBadge
import com.localaisearch.ui.components.SearchInputBar
import com.localaisearch.ui.components.SendButtonState
import com.localaisearch.ui.components.SourceCard
import com.localaisearch.ui.viewmodel.ChatViewModel
import com.localaisearch.ui.viewmodel.ConversationViewModel
import kotlinx.coroutines.launch

/**
 * Home screen - Modern ChatGPT-style AI home.
 *
 * Layout:
 * - Minimalist top bar (hamburger + title + more)
 * - Side drawer for navigation (history, models, memory, settings)
 * - Central AI Core (orb) with welcome text when no messages
 * - Large floating Composer at bottom
 * - Smooth transition from empty state to chat view
 * - Privacy mode indicator handled gracefully (not crowding top bar)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToModelCenter: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToDataSecurity: () -> Unit,
    viewModel: ChatViewModel = viewModel(),
    conversationViewModel: ConversationViewModel = viewModel()
) {
    val colorScheme = MaterialTheme.colorScheme
    val conversation by viewModel.conversation.collectAsState()
    val agentStatus by viewModel.agentStatus.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val citations by viewModel.citations.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val error by viewModel.error.collectAsState()
    val internetSearchEnabled by viewModel.internetSearchEnabled.collectAsState()
    val isPrivacyMode by viewModel.isPrivacyMode.collectAsState()
    val privacySessionEnded by viewModel.privacySessionEnded.collectAsState()
    val modelLoaded by viewModel.modelLoaded.collectAsState()
    val loadedModelName by viewModel.loadedModelName.collectAsState()
    val defaultSystemPrompt by viewModel.defaultSystemPrompt.collectAsState()
    val storedConversations by conversationViewModel.conversations.collectAsState()

    var showSystemPromptDialog by remember { mutableStateOf(false) }

    var inputText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val sendButtonState = when {
        isProcessing && agentStatus.state == AgentState.SEARCHING -> SendButtonState.SEARCHING
        isProcessing && agentStatus.state == AgentState.DONE -> SendButtonState.DONE
        isProcessing -> SendButtonState.SEARCHING
        else -> SendButtonState.IDLE
    }

    val orbState = if (isProcessing) agentStatus.state else AgentState.IDLE
    val hasMessages = conversation.messages.isNotEmpty()

    // Privacy session ended notification
    if (privacySessionEnded) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPrivacySessionNotification() },
            icon = {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = colorScheme.primary
                            )
            },
            title = { Text(stringResource(R.string.privacy_session_ended)) },
            text = {
                Text(stringResource(R.string.privacy_session_ended_desc))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPrivacySessionNotification() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showSystemPromptDialog) {
        SystemPromptDialog(
            selected = defaultSystemPrompt,
            onSelect = { viewModel.setDefaultSystemPrompt(it) },
            onDismiss = { showSystemPromptDialog = false }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                windowInsets = WindowInsets.statusBars,
                modifier = Modifier.width(280.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.drawer_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Message, contentDescription = null) },
                    label = { Text(stringResource(R.string.new_chat)) },
                    selected = false,
                    onClick = {
                        viewModel.newConversation()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Text(
                    text = stringResource(R.string.history),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                if (storedConversations.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_history),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                } else {
                    storedConversations.take(8).forEach { stored ->
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = {
                                Text(
                                    stored.conversation.title.ifBlank { stringResource(R.string.new_chat) },
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            },
                            selected = stored.conversation.id == conversation.id,
                            onClick = {
                                viewModel.loadConversation(stored.conversation.id)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Science, contentDescription = null) },
                    label = { Text(stringResource(R.string.models)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToModels()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Star, contentDescription = null) },
                    label = { Text(stringResource(R.string.memory_center)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToMemory()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Save, contentDescription = null) },
                    label = { Text(stringResource(R.string.model_center)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToModelCenter()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.settings)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.home_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isPrivacyMode) {
                                Text(
                                    text = stringResource(R.string.privacy_mode),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                if (drawerState.currentValue == DrawerValue.Closed) drawerState.open()
                                else drawerState.close()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.open_menu)
                            )
                        }
                    },
                    actions = {
                        // Privacy toggle (small, not crowding)
                        IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                            Icon(
                                imageVector = if (isPrivacyMode)
                                    androidx.compose.material.icons.Icons.Filled.Lock
                                else
                                    Icons.Filled.Lock,
                                contentDescription = if (isPrivacyMode) stringResource(R.string.privacy_on) else stringResource(R.string.privacy_off),
                                tint = if (isPrivacyMode) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                        }
                        // Pixel-style compact feature menu
                        Box {
                            IconButton(onClick = { showMenu = !showMenu }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.more_options)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                shape = RoundedCornerShape(20.dp),
                                containerColor = colorScheme.surfaceContainerHigh,
                                tonalElevation = 3.dp
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                                    text = { Text(stringResource(R.string.system_prompt)) },
                                    onClick = { showMenu = false; showSystemPromptDialog = true }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                    text = { Text(stringResource(if (internetSearchEnabled) R.string.disable_web_search else R.string.enable_web_search)) },
                                    onClick = { showMenu = false; viewModel.toggleInternetSearch() }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Lock, null) },
                                    text = { Text(stringResource(if (isPrivacyMode) R.string.disable_private_mode else R.string.enable_private_mode)) },
                                    onClick = { showMenu = false; viewModel.togglePrivacyMode() }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                    text = { Text(stringResource(R.string.settings)) },
                                    onClick = { showMenu = false; onNavigateToSettings() }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            },
            modifier = Modifier.imePadding()
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = colorScheme.background
            ) {
                AnimatedContent(
                    targetState = hasMessages,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
                    label = "homeTransition"
                ) { hasMsgs ->
                    if (!hasMsgs) {
                        EmptyStateScreen(
                            orbState = orbState,
                            modelLoaded = modelLoaded,
                            modelName = loadedModelName,
                            onSelectModel = onNavigateToModels,
                            isPrivacyMode = isPrivacyMode,
                            internetSearchEnabled = internetSearchEnabled,
                            inputText = inputText,
                            onInputChange = { inputText = it },
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendQuery(inputText)
                                    inputText = ""
                                }
                            },
                            sendButtonState = sendButtonState,
                            isProcessing = isProcessing,
                            inputEnabled = modelLoaded,
                            agentStatus = agentStatus
                        )
                    } else {
                        ChatScreen(
                            messages = conversation.messages,
                            orbState = orbState,
                            searchResults = searchResults,
                            citations = citations,
                            agentStatus = agentStatus,
                            isProcessing = isProcessing,
                            inputText = inputText,
                            onInputChange = { inputText = it },
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendQuery(inputText)
                                    inputText = ""
                                }
                            },
                            sendButtonState = sendButtonState,
                            internetSearchEnabled = internetSearchEnabled,
                            onToggleSearch = { viewModel.toggleInternetSearch() },
                            onCancel = { viewModel.cancel() },
                            error = error,
                            isPrivacyMode = isPrivacyMode,
                            onNewConversation = { viewModel.newConversation() },
                            inputEnabled = modelLoaded
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateScreen(
    orbState: AgentState,
    modelLoaded: Boolean,
    modelName: String,
    onSelectModel: () -> Unit,
    isPrivacyMode: Boolean,
    internetSearchEnabled: Boolean,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    sendButtonState: SendButtonState,
    isProcessing: Boolean,
    agentStatus: com.localaisearch.data.model.AgentStatus,
    inputEnabled: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1.15f))

        // Large neutral model card, matching modern GPT-style empty states.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(292.dp),
            shape = RoundedCornerShape(24.dp),
            color = colorScheme.surfaceVariant.copy(alpha = 0.72f),
            tonalElevation = 1.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AIOrb(
                    state = orbState,
                    size = 190.dp,
                    animationLevel = "standard",
                    modelState = if (modelLoaded) com.localaisearch.ui.components.ModelState.LOADED else com.localaisearch.ui.components.ModelState.NO_MODEL,
                    modelName = modelName,
                    onSelectModel = onSelectModel
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Welcome text
        Text(
            text = stringResource(R.string.ask_anything),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                isPrivacyMode -> stringResource(R.string.private_mode_active_no_data)
                internetSearchEnabled -> stringResource(R.string.local_ai_realtime_search)
                else -> stringResource(R.string.ai_on_device)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Large floating Composer
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            SearchInputBar(
                value = inputText,
                onValueChange = onInputChange,
                onSend = onSend,
                internetSearchEnabled = internetSearchEnabled,
                modifier = Modifier
                    .weight(1f)
                    .imePadding(),
                enabled = inputEnabled
            )
        }

        if (!inputEnabled) {
            Text(
                text = "模型尚未加载，加载完成后才能发送消息",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        // Progress indicator
        AnimatedVisibility(
            visible = isProcessing,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AgentProgressBar(status = agentStatus)
            }
        }

        Spacer(modifier = Modifier.weight(0.22f))

        // Bottom padding for navigation bar
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun ChatScreen(
    messages: List<com.localaisearch.data.model.ChatMessage>,
    orbState: AgentState,
    searchResults: List<com.localaisearch.data.model.SearchResult>,
    citations: List<com.localaisearch.data.model.Citation>,
    agentStatus: com.localaisearch.data.model.AgentStatus,
    isProcessing: Boolean,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    sendButtonState: SendButtonState,
    internetSearchEnabled: Boolean,
    onToggleSearch: () -> Unit,
    onCancel: () -> Unit,
    error: String?,
    isPrivacyMode: Boolean,
    onNewConversation: () -> Unit,
    inputEnabled: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Messages list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Small orb at top when processing
            if (isProcessing) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AIOrb(
                            state = orbState,
                            size = 60.dp,
                            animationLevel = "standard"
                        )
                    }
                }
                item {
                    AgentProgressBar(
                        status = agentStatus,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            // Chat messages
            items(messages, key = { it.id }) { message ->
                ChatBubble(message = message)
            }

            // Source cards
            if (citations.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sources, citations.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(citations, key = { it.url }) { citation ->
                    SourceCard(
                        citation = citation,
                        index = citation.index - 1,
                        onClick = { uriHandler.openUri(citation.url) },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            // Error display
            if (error != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Floating Composer at bottom
        Surface(
            color = colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // New conversation button
                if (!isProcessing && messages.isNotEmpty()) {
                    IconButton(onClick = onNewConversation) {
                        Icon(
                            imageVector = Icons.Filled.Message,
                            contentDescription = stringResource(R.string.new_conversation),
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }

                SearchInputBar(
                    value = inputText,
                    onValueChange = onInputChange,
                    onSend = onSend,
                    internetSearchEnabled = internetSearchEnabled,
                    modifier = Modifier.weight(1f),
                    enabled = inputEnabled
                )
            }
        }
    }
}


@Composable
private fun SystemPromptDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        Triple("general", "通用助手", "友好、可靠，适合日常问答"),
        Triple("concise", "简洁回答", "优先结论，减少无关铺垫"),
        Triple("precise", "严谨回答", "区分事实、推测与不确定信息"),
        Triple("coding", "编程助手", "优先提供可靠、可执行的代码方案"),
        Triple("chinese", "中文助手", "默认使用自然准确的简体中文")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.default_ai_system_prompt)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    val id = option.first
                    val title = option.second
                    val desc = option.third
                    Surface(
                        onClick = { onSelect(id); onDismiss() },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected == id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.SemiBold)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected == id) {
                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        shape = RoundedCornerShape(24.dp)
    )
}
