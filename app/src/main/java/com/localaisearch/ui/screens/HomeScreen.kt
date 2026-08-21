
package com.localaisearch.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Chat
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalUriHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.R
import com.localaisearch.data.model.AgentState
import com.localaisearch.data.repository.TorManager
import com.localaisearch.ui.components.LocalisSparkle
import com.localaisearch.ui.components.AgentProgressBar
import com.localaisearch.ui.components.ChatBubble
import com.localaisearch.ui.components.ExpressiveCard
import com.localaisearch.ui.components.MorphingSendButton
import com.localaisearch.ui.components.PrivacyBadge
import com.localaisearch.ui.components.SearchInputBar
import com.localaisearch.ui.components.SendButtonState
import com.localaisearch.ui.components.SourceCard
import com.localaisearch.ui.viewmodel.ChatViewModel
import com.localaisearch.ui.viewmodel.ConversationViewModel
import kotlinx.coroutines.delay
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
    val imageInputAvailable by viewModel.imageInputAvailable.collectAsState()
    val pendingImageUri by viewModel.pendingImageUri.collectAsState()
    val defaultSystemPrompt by viewModel.defaultSystemPrompt.collectAsState()
    val storedConversations by conversationViewModel.conversations.collectAsState()
    val torStatus by TorManager.statusFlow.collectAsState()
    val torRoutingActive = torStatus == TorManager.Status.ON

    var showSystemPromptDialog by remember { mutableStateOf(false) }

    var inputText by remember { mutableStateOf("") }
    var showImageModelHint by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        // Image selection is intentionally capability-gated. The selected URI will
        // be wired into the multimodal engine when the native mtmd/mmproj bridge is
        // available; until then the UI never pretends a text-only model can see pixels.
        if (uri != null && imageInputAvailable) {
            viewModel.setPendingImage(uri)
        }
    }
    var showMenu by remember { mutableStateOf(false) }

    var drawerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val closeDrawerAndThen: ((() -> Unit)?) -> Unit = { action ->
        drawerOpen = false
        if (action != null) {
            scope.launch {
                delay(440L)
                action()
            }
        }
    }

    val sendButtonState = when {
        isProcessing && agentStatus.state == AgentState.SEARCHING -> SendButtonState.SEARCHING
        isProcessing && agentStatus.state == AgentState.DONE -> SendButtonState.DONE
        isProcessing -> SendButtonState.SEARCHING
        else -> SendButtonState.IDLE
    }

    val orbState = if (isProcessing) agentStatus.state else AgentState.IDLE
    val hasMessages = conversation.messages.isNotEmpty()

    if (showImageModelHint) {
        AlertDialog(
            onDismissRequest = { showImageModelHint = false },
            title = { Text(stringResource(R.string.image_input_unavailable_title)) },
            text = { Text(stringResource(R.string.image_input_unavailable_desc)) },
            confirmButton = {
                TextButton(onClick = { showImageModelHint = false; onNavigateToModelCenter() }) {
                    Text(stringResource(R.string.download_model))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImageModelHint = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Privacy session ended notification
    if (privacySessionEnded) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPrivacySessionNotification() },
            icon = {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
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

    SmoothNavigationDrawer(
        open = drawerOpen,
        onDismiss = { drawerOpen = false },
        drawerContent = {
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
                    icon = { Icon(Icons.Outlined.Chat, contentDescription = null) },
                    label = { Text(stringResource(R.string.new_chat)) },
                    selected = false,
                    onClick = {
                        viewModel.newConversation()
                        drawerOpen = false
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
                                drawerOpen = false
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
                        closeDrawerAndThen(onNavigateToModels)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Star, contentDescription = null) },
                    label = { Text(stringResource(R.string.memory_center)) },
                    selected = false,
                    onClick = {
                        closeDrawerAndThen(onNavigateToMemory)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Save, contentDescription = null) },
                    label = { Text(stringResource(R.string.model_center)) },
                    selected = false,
                    onClick = {
                        closeDrawerAndThen(onNavigateToModelCenter)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.settings)) },
                    selected = false,
                    onClick = {
                        closeDrawerAndThen(onNavigateToSettings)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.clickable { onNavigateToModels() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = loadedModelName.ifBlank { stringResource(R.string.home_title) },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.models),
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { drawerOpen = !drawerOpen }) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.open_menu)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                            Icon(
                                imageVector = if (isPrivacyMode) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                contentDescription = if (isPrivacyMode) stringResource(R.string.privacy_on) else stringResource(R.string.privacy_off),
                                tint = if (isPrivacyMode) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                        }
                        Image(
                            painter = painterResource(R.drawable.localis_avatar),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.size(40.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            },
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
                                if (inputText.isNotBlank() || pendingImageUri != null) {
                                    viewModel.sendQuery(inputText)
                                    inputText = ""
                                }
                            },
                            sendButtonState = sendButtonState,
                            isProcessing = isProcessing,
                            inputEnabled = modelLoaded,
                            agentStatus = agentStatus,
                            torRoutingActive = torRoutingActive,
                            imageInputAvailable = imageInputAvailable,
                            pendingImageAvailable = pendingImageUri != null,
                            onAttachClick = { imagePicker.launch("image/*") },
                            onImageUnavailableClick = { showImageModelHint = true }
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
                                if (inputText.isNotBlank() || pendingImageUri != null) {
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
                            inputEnabled = modelLoaded,
                            imageInputAvailable = imageInputAvailable,
                            pendingImageAvailable = pendingImageUri != null,
                            onAttachClick = { imagePicker.launch("image/*") },
                            onImageUnavailableClick = { showImageModelHint = true },
                            onRegenerate = { viewModel.regenerateMessage(it.id) },
                            onOtherAi = { onNavigateToModels() },
                            torRoutingActive = torRoutingActive
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmoothNavigationDrawer(
    open: Boolean,
    onDismiss: () -> Unit,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(440, easing = FastOutSlowInEasing),
        label = "drawerProgress"
    )
    val drawerWidth = with(density) { 304.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        content()

        // Keep the sheet composed while closing so the outgoing frame is continuous.
        if (progress > 0f || open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f * progress))
                    .clickable(onClick = onDismiss)
            )
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(304.dp)
                    .zIndex(11f)
                    .graphicsLayer {
                        translationX = -drawerWidth * (1f - progress)
                        shadowElevation = 4.dp.toPx() * progress
                    },
                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    drawerContent()
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
    inputEnabled: Boolean,
    torRoutingActive: Boolean,
    imageInputAvailable: Boolean,
    pendingImageAvailable: Boolean,
    onAttachClick: () -> Unit,
    onImageUnavailableClick: () -> Unit
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

        // Ambient light field: no grey backing card. The glow owns the empty state.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            contentAlignment = Alignment.Center
        ) {
            LocalisSparkle(modifier = Modifier.size(52.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Static welcome copy: no particle/orb animation, matching the calm Gemini-like empty state.
        val greetings = listOf(
            "我在这里", "我准备好了", "随时为你效劳", "等你发来第一句话",
            "交给我吧", "我们开始", "好了，开始吧", "有我在呢",
            "想聊点什么?看你的了!", "随时可以开始", "慢慢来，不着急",
            "就从这里开始吧", "现在，刚刚好", "一切准备就绪", "随时等你",
            "这里一直有回应", "让我们开始吧"
        )
        val greeting = remember { greetings.random() }
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Normal,
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
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            SearchInputBar(
                value = inputText,
                onValueChange = onInputChange,
                onSend = onSend,
                internetSearchEnabled = internetSearchEnabled,
                modifier = Modifier.weight(1f),
                enabled = inputEnabled && !torRoutingActive,
                disabledReason = if (torRoutingActive) stringResource(R.string.tor_input_disabled) else null,
                imageInputAvailable = imageInputAvailable,
                sendButtonState = sendButtonState,
                pendingImageAvailable = pendingImageAvailable,
                imageUnavailableReason = stringResource(R.string.image_input_unavailable_desc),
                onAttachClick = onAttachClick,
                onImageUnavailableClick = onImageUnavailableClick
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
    inputEnabled: Boolean,
    imageInputAvailable: Boolean,
    pendingImageAvailable: Boolean,
    onAttachClick: () -> Unit,
    onImageUnavailableClick: () -> Unit,
    onRegenerate: (com.localaisearch.data.model.ChatMessage) -> Unit,
    onOtherAi: (com.localaisearch.data.model.ChatMessage) -> Unit,
    torRoutingActive: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current

    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val showJumpToBottom by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Small orb at top when processing
            if (isProcessing) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        LocalisSparkle(modifier = Modifier.size(32.dp))
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
                // Keep the empty streaming placeholder out of the list. The single
                // shared progress header above the messages owns the generation UI.
                // This prevents a second loading surface from flashing in/out while
                // the first tokens are arriving. Once content exists, the same keyed
                // item becomes the normal assistant bubble without changing identity.
                if (!message.isStreaming || message.content.isNotBlank()) {
                    ChatBubble(
                        message = message,
                        onRegenerate = { onRegenerate(message) },
                        onOtherAi = { onOtherAi(message) }
                    )
                }
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

        // GPT-style composer: directly above the system gesture area and translated by IME insets.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
                // New conversation button
                if (!isProcessing && messages.isNotEmpty()) {
                    IconButton(onClick = onNewConversation) {
                        Icon(
                            imageVector = Icons.Rounded.Message,
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
                enabled = inputEnabled && !torRoutingActive,
                disabledReason = if (torRoutingActive) stringResource(R.string.tor_input_disabled) else null,
                imageInputAvailable = imageInputAvailable,
                sendButtonState = sendButtonState,
                pendingImageAvailable = pendingImageAvailable,
                imageUnavailableReason = stringResource(R.string.image_input_unavailable_desc),
                onAttachClick = onAttachClick,
                onImageUnavailableClick = onImageUnavailableClick
            )
        }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showJumpToBottom,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp)
        ) {
            FloatingActionButton(
                onClick = { scrollScope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) } },
                containerColor = colorScheme.surfaceContainerHigh,
                contentColor = colorScheme.onSurface
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.chat_scroll_to_bottom))
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
                    ExpressiveCard(
                        onClick = { onSelect(id); onDismiss() },
                        selected = selected == id,
                        modifier = Modifier.fillMaxWidth()
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
