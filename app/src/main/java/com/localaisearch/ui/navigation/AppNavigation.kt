package com.localaisearch.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import com.localaisearch.ui.screens.ConversationHistoryScreen
import com.localaisearch.ui.screens.DataSecurityScreen
import com.localaisearch.ui.screens.HomeScreen
import com.localaisearch.ui.screens.LanguageScreen
import com.localaisearch.ui.screens.MemoryCenterScreen
import com.localaisearch.ui.screens.ModelCenterScreen
import com.localaisearch.ui.screens.ModelManagerScreen
import com.localaisearch.ui.screens.SettingsHubScreen
import com.localaisearch.ui.screens.SettingsAIScreen
import com.localaisearch.ui.screens.SettingsNetworkScreen
import com.localaisearch.ui.screens.SettingsTorProxyScreen
import com.localaisearch.ui.screens.SettingsPrivacyScreen
import com.localaisearch.ui.screens.SettingsAppearanceScreen
import com.localaisearch.ui.screens.SettingsChatScreen
import com.localaisearch.ui.screens.SettingsPerformanceScreen
import com.localaisearch.ui.screens.SettingsDataScreen
import com.localaisearch.ui.screens.SettingsAboutScreen
import com.localaisearch.ui.screens.VisionModelScreen
import com.localaisearch.ui.viewmodel.ChatViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs

/**
 * Prevent duplicate destinations when users tap navigation targets repeatedly.
 * Navigation itself remains synchronous and never blocks the UI thread.
 */
private fun NavHostController.navigateSafely(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Navigation routes.
 */
object Routes {
    const val HOME = "home"
    const val MODELS = "models"
    const val MODEL_CENTER = "model_center"
    const val SETTINGS_HUB = "settings_hub"
    const val SETTINGS_AI = "settings_ai"
    const val VISION_MODELS = "vision_models"
    const val SETTINGS_NETWORK = "settings_network"
    const val SETTINGS_TOR_PROXY = "settings_tor_proxy"
    const val SETTINGS_PRIVACY = "settings_privacy"
    const val SETTINGS_APPEARANCE = "settings_appearance"
    const val SETTINGS_CHAT = "settings_chat"
    const val SETTINGS_PERFORMANCE = "settings_performance"
    const val SETTINGS_DATA = "settings_data"
    const val SETTINGS_ABOUT = "settings_about"
    const val CONVERSATION_HISTORY = "conversation_history"
    const val MEMORY_CENTER = "memory_center"
    const val DATA_SECURITY = "data_security"
    const val LANGUAGE = "language"
}

/**
 * App navigation graph.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = viewModel()

    val density = LocalDensity.current
    var edgeDrag by remember { mutableFloatStateOf(0f) }
    var edgeStart by remember { mutableFloatStateOf(-1f) }
    val dragAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(navController) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val edge = with(density) { 32.dp.toPx() }
                            edgeStart = when {
                                start.x <= edge -> -1f
                                start.x >= size.width - edge -> 1f
                                else -> 0f
                            }
                            edgeDrag = 0f
                        },
                        onDrag = { change, amount ->
                            if (edgeStart == 0f) return@detectDragGestures
                            val directed = amount.x
                            // Left edge: drag right. Right edge: drag left.
                            if ((edgeStart < 0f && directed > 0f) || (edgeStart > 0f && directed < 0f)) {
                                edgeDrag += directed
                                scope.launch { dragAnim.snapTo(edgeDrag) }
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (edgeStart == 0f) return@detectDragGestures
                            val threshold = with(density) { 96.dp.toPx() }
                            val shouldPop = abs(edgeDrag) >= threshold
                            if (shouldPop && navController.previousBackStackEntry != null) {
                                val target = if (edgeStart < 0f) with(density) { 420.dp.toPx() } else -with(density) { 420.dp.toPx() }
                                scope.launch {
                                    dragAnim.snapTo(edgeDrag)
                                    dragAnim.animateTo(target, tween(180, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
                                    navController.popBackStack()
                                    dragAnim.snapTo(0f)
                                }
                            } else {
                                scope.launch {
                                    dragAnim.snapTo(edgeDrag)
                                    dragAnim.animateTo(0f, tween(260, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)))
                                }
                            }
                            edgeStart = 0f
                            edgeDrag = 0f
                        },
                        onDragCancel = {
                            edgeStart = 0f
                            edgeDrag = 0f
                            scope.launch {
                                dragAnim.animateTo(0f, tween(240, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)))
                            }
                        }
                    )
                }
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.graphicsLayer {
                    translationX = dragAnim.value
                    scaleX = 1f - (abs(dragAnim.value) / 10000f).coerceAtMost(0.018f)
                    scaleY = 1f - (abs(dragAnim.value) / 10000f).coerceAtMost(0.018f)
                },
                enterTransition = {
                    fadeIn(tween(360, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))) +
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(420, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
                },
                exitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(380, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)))
                },
                popEnterTransition = {
                    fadeIn(tween(360, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))) +
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(420, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)))
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(380, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)))
                }
            ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToSettings = { navController.navigateSafely(Routes.SETTINGS_HUB) },
                onNavigateToModels = { navController.navigateSafely(Routes.MODELS) },
                onNavigateToModelCenter = { navController.navigateSafely(Routes.MODEL_CENTER) },
                onNavigateToHistory = { navController.navigateSafely(Routes.CONVERSATION_HISTORY) },
                onNavigateToMemory = { navController.navigateSafely(Routes.MEMORY_CENTER) },
                onNavigateToDataSecurity = { navController.navigateSafely(Routes.DATA_SECURITY) },
                viewModel = chatViewModel
            )
        }
        composable(Routes.MODELS) {
            ModelManagerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.VISION_MODELS) {
            VisionModelScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.MODEL_CENTER) {
            ModelCenterScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        // Settings Hub - main settings entry point
        composable(Routes.SETTINGS_HUB) {
            SettingsHubScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAI = { navController.navigateSafely(Routes.SETTINGS_AI) },
                onNavigateToNetwork = { navController.navigateSafely(Routes.SETTINGS_NETWORK) },
                onNavigateToTorProxy = { navController.navigateSafely(Routes.SETTINGS_TOR_PROXY) },
                onNavigateToPrivacy = { navController.navigateSafely(Routes.SETTINGS_PRIVACY) },
                onNavigateToAppearance = { navController.navigateSafely(Routes.SETTINGS_APPEARANCE) },
                onNavigateToChat = { navController.navigateSafely(Routes.SETTINGS_CHAT) },
                onNavigateToPerformance = { navController.navigateSafely(Routes.SETTINGS_PERFORMANCE) },
                onNavigateToData = { navController.navigateSafely(Routes.SETTINGS_DATA) },
                onNavigateToAbout = { navController.navigateSafely(Routes.SETTINGS_ABOUT) },
                onNavigateToLanguage = { navController.navigateSafely(Routes.LANGUAGE) },
                onNavigateToDataSecurity = { navController.navigateSafely(Routes.DATA_SECURITY) },
                viewModel = chatViewModel
            )
        }
        // Settings sub-pages
        composable(Routes.SETTINGS_AI) {
            SettingsAIScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModelManager = { navController.navigateSafely(Routes.MODELS) },
                onNavigateToVisionModels = { navController.navigateSafely(Routes.VISION_MODELS) }
            )
        }
        composable(Routes.SETTINGS_NETWORK) {
            SettingsNetworkScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS_TOR_PROXY) {
            SettingsTorProxyScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_PRIVACY) {
            SettingsPrivacyScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDataSecurity = { navController.navigateSafely(Routes.DATA_SECURITY) },
                viewModel = chatViewModel
            )
        }
        composable(Routes.SETTINGS_APPEARANCE) {
            SettingsAppearanceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS_CHAT) {
            SettingsChatScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS_PERFORMANCE) {
            SettingsPerformanceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS_DATA) {
            SettingsDataScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS_ABOUT) {
            SettingsAboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.CONVERSATION_HISTORY) {
            ConversationHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNewConversation = {
                    // Navigation alone does not reset the shared ChatViewModel.
                    // Explicitly create a fresh conversation before returning home.
                    chatViewModel.newConversation()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenConversation = { conversationId ->
                    chatViewModel.loadConversation(conversationId)
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }
        composable(Routes.MEMORY_CENTER) {
            MemoryCenterScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.DATA_SECURITY) {
            DataSecurityScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.LANGUAGE) {
            LanguageScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
            }
        }
    }
}
