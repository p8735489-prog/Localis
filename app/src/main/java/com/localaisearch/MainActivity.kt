@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.localaisearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localaisearch.data.repository.LanguageManager
import com.localaisearch.data.repository.SettingsRepository
import com.localaisearch.ui.navigation.AppNavigation
import com.localaisearch.ui.screens.OnboardingScreen
import com.localaisearch.ui.theme.LocalAISearchTheme
import com.localaisearch.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var keepSplash = true
    private var languageReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Match the themed surface during navigation/recreation to avoid black flashes.
        window.setBackgroundDrawableResource(android.R.color.transparent)
        super.onCreate(savedInstanceState)

        // Edge-to-edge with transparent system bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )

        // Apply saved language preference before setContent
        val settingsRepo = SettingsRepository(this)
        val languageManager = LanguageManager(this)

        lifecycleScope.launch {
            val savedLanguage = settingsRepo.appLanguage.first()
            if (savedLanguage != LanguageManager.SYSTEM_DEFAULT) {
                languageManager.applyLanguage(this@MainActivity, savedLanguage)
            }
            languageReady = true
            keepSplash = false
        }

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val darkMode by settingsViewModel.darkMode.collectAsState()
            val dynamicColor by settingsViewModel.dynamicColor.collectAsState()
            val themePreset by settingsViewModel.themePreset.collectAsState()
            val fontMode by settingsViewModel.fontMode.collectAsState()
            val onboardingCompleted by settingsViewModel.onboardingCompleted.collectAsState()
            val settingsLoaded by settingsViewModel.settingsLoaded.collectAsState()

            LocalAISearchTheme(
                darkMode = darkMode,
                dynamicColor = dynamicColor,
                themePreset = themePreset,
                fontMode = fontMode
            ) {
                if (!languageReady || !settingsLoaded) {
                    // Keep the first frame on the themed surface. Do not briefly render
                    // onboarding and then replace it with the real destination.
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.LoadingIndicator()
                    }
                } else if (onboardingCompleted) {
                    AppNavigation()
                } else {
                    OnboardingScreen(onComplete = settingsViewModel::completeOnboarding)
                }
            }
        }
    }
}
