package com.localaisearch.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.localaisearch.ui.screens.ConversationHistoryScreen
import com.localaisearch.ui.screens.DataSecurityScreen
import com.localaisearch.ui.screens.HomeScreen
import com.localaisearch.ui.screens.MemoryCenterScreen
import com.localaisearch.ui.screens.ModelCenterScreen
import com.localaisearch.ui.screens.ModelManagerScreen
import com.localaisearch.ui.screens.SettingsScreen

/**
 * Navigation routes.
 */
object Routes {
    const val HOME = "home"
    const val MODELS = "models"
    const val MODEL_CENTER = "model_center"
    const val SETTINGS = "settings"
    const val CONVERSATION_HISTORY = "conversation_history"
    const val MEMORY_CENTER = "memory_center"
    const val DATA_SECURITY = "data_security"
}

/**
 * App navigation graph.
 *
 * Wires all screens into the navigation graph:
 * - Home (main chat)
 * - Model Manager (local models)
 * - Model Center (online download)
 * - Settings (search API, inference, appearance)
 * - Conversation History (browse/search/manage past chats)
 * - Memory Center (view/edit/delete long-term memories)
 * - Data & Security (privacy, memory toggle, delete data)
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToModels = { navController.navigate(Routes.MODELS) },
                onNavigateToModelCenter = { navController.navigate(Routes.MODEL_CENTER) },
                onNavigateToHistory = { navController.navigate(Routes.CONVERSATION_HISTORY) },
                onNavigateToMemory = { navController.navigate(Routes.MEMORY_CENTER) },
                onNavigateToDataSecurity = { navController.navigate(Routes.DATA_SECURITY) }
            )
        }
        composable(Routes.MODELS) {
            ModelManagerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.MODEL_CENTER) {
            ModelCenterScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDataSecurity = { navController.navigate(Routes.DATA_SECURITY) }
            )
        }
        composable(Routes.CONVERSATION_HISTORY) {
            ConversationHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNewConversation = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onOpenConversation = { conversationId ->
                    // Navigate back to home; ChatViewModel.loadConversation is called
                    // from the HomeScreen via a shared mechanism.
                    // For now, pop back to home.
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
    }
}
