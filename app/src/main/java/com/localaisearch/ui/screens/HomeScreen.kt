package com.localaisearch.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.data.model.AgentState
import com.localaisearch.ui.components.AIOrb
import com.localaisearch.ui.components.AgentProgressBar
import com.localaisearch.ui.components.AutoModeToggle
import com.localaisearch.ui.components.ChatBubble
import com.localaisearch.ui.components.MorphingSendButton
import com.localaisearch.ui.components.PrivacyBadge
import com.localaisearch.ui.components.SearchInputBar
import com.localaisearch.ui.components.SendButtonState
import com.localaisearch.ui.components.SourceCard
import com.localaisearch.ui.viewmodel.ChatViewModel

/**
 * Home screen - the main entry point of the app.
 *
 * Layout:
 * - Top bar with navigation icons (history, memory, models, model center, settings)
 * - Privacy mode toggle (lock icon)
 * - Auto Mode toggle card
 * - Central AI Orb
 * - Search input bar + morphing send button
 * - Chat messages (when conversation exists)
 * - Source cards (when search results exist)
 * - Agent progress bar (when processing)
 * - Privacy session end notification
 */
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToModelCenter: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToDataSecurity: () -> Unit,
    viewModel: ChatViewModel = viewModel()
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
    val isAutoMode by viewModel.isAutoModeEnabled.collectAsState()
    val privacySessionEnded by viewModel.privacySessionEnded.collectAsState()

    var inputText by remember { mutableStateOf("") }

    val sendButtonState = when {
        isProcessing && agentStatus.state == AgentState.SEARCHING -> SendButtonState.SEARCHING
        isProcessing && agentStatus.state == AgentState.DONE -> SendButtonState.DONE
        isProcessing -> SendButtonState.SEARCHING
        else -> SendButtonState.IDLE
    }

    val orbState = if (isProcessing) agentStatus.state else AgentState.IDLE

    // Privacy session ended notification
    if (privacySessionEnded) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPrivacySessionNotification() },
            icon = {
                Icon(
                    imageVector = Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = colorScheme.primary
                )
            },
            title = { Text("Privacy Session Ended") },
            text = {
                Text("Privacy session has ended. All temporary data from this session has been cleaned up.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPrivacySessionNotification() }) {
                    Text("OK")
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // -- Top bar --
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Privacy mode toggle
                    IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                        Icon(
                            imageVector = if (isPrivacyMode) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (isPrivacyMode) "Privacy mode ON" else "Privacy mode OFF",
                            tint = if (isPrivacyMode) colorScheme.primary else colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "Localis",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )

                    // Privacy badge when active
                    PrivacyBadge(
                        isPrivacyMode = isPrivacyMode,
                        onToggle = { viewModel.togglePrivacyMode() }
                    )
                }

                Row {
                    // Conversation history
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "Conversation History",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                    // Memory center
                    IconButton(onClick = onNavigateToMemory) {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = "Memory Center",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                    // Model center (download)
                    IconButton(onClick = onNavigateToModelCenter) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = "Model Center",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                    // Local models
                    IconButton(onClick = onNavigateToModels) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = "Models",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                    // Settings
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // -- Auto Mode toggle (compact, only when no conversation) --
            AnimatedVisibility(
                visible = conversation.messages.isEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                AutoModeToggle(
                    enabled = isAutoMode,
                    onToggle = { viewModel.toggleAutoMode() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // -- Main content area --
            if (conversation.messages.isEmpty()) {
                // Empty state: Orb + search bar
                EmptyStateContent(
                    orbState = orbState,
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
                    agentStatus = agentStatus,
                    isProcessing = isProcessing,
                    isPrivacyMode = isPrivacyMode
                )
            } else {
                // Conversation state: messages + input at bottom
                ConversationContent(
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
                    onNewConversation = { viewModel.newConversation() }
                )
            }
        }
    }
}

@Composable
private fun EmptyStateContent(
    orbState: AgentState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    sendButtonState: SendButtonState,
    internetSearchEnabled: Boolean,
    onToggleSearch: () -> Unit,
    agentStatus: com.localaisearch.data.model.AgentStatus,
    isProcessing: Boolean,
    isPrivacyMode: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // AI Orb
        AIOrb(
            state = orbState,
            size = 140.dp,
            primaryColor = colorScheme.primary,
            secondaryColor = colorScheme.tertiary,
            accentColor = colorScheme.primary.copy(alpha = 0.7f),
            glowColor = colorScheme.primary.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Ask anything",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                isPrivacyMode -> "Privacy mode - no data will be saved"
                internetSearchEnabled -> "Local AI + Internet search enabled"
                else -> "Local AI - fully private, on-device"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (isPrivacyMode) colorScheme.primary else colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Search bar + send button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            SearchInputBar(
                value = inputText,
                onValueChange = onInputChange,
                onSend = onSend,
                internetSearchEnabled = internetSearchEnabled,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.padding(4.dp))
            MorphingSendButton(
                state = sendButtonState,
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isProcessing
            )
        }

        // Agent progress
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
    }
}

@Composable
private fun ConversationContent(
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
    onNewConversation: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Messages list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                            size = 80.dp,
                            primaryColor = colorScheme.primary,
                            secondaryColor = colorScheme.tertiary,
                            accentColor = colorScheme.primary.copy(alpha = 0.7f),
                            glowColor = colorScheme.primary.copy(alpha = 0.3f)
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
                        text = "Sources (${citations.size})",
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

        // Bottom input bar
        Surface(
            color = colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // New conversation button (only when not processing and has messages)
                if (!isProcessing && messages.isNotEmpty()) {
                    IconButton(onClick = onNewConversation) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "New conversation",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }

                SearchInputBar(
                    value = inputText,
                    onValueChange = onInputChange,
                    onSend = onSend,
                    internetSearchEnabled = internetSearchEnabled,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.padding(4.dp))
                MorphingSendButton(
                    state = sendButtonState,
                    onClick = onSend,
                    enabled = inputText.isNotBlank() && !isProcessing
                )
            }
        }
    }
}
