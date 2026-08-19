package com.localaisearch.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.localaisearch.R
import com.localaisearch.data.repository.LanguageManager
import com.localaisearch.ui.viewmodel.SettingsViewModel

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val settings: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var page by remember { mutableIntStateOf(0) }
    var selectedLanguage by remember { mutableStateOf(LanguageManager.SYSTEM_DEFAULT) }
    val languages = listOf(
        LanguageManager.SYSTEM_DEFAULT to stringResource(R.string.language_system),
        LanguageManager.SIMPLIFIED_CHINESE to stringResource(R.string.language_simplified_chinese),
        LanguageManager.ENGLISH to stringResource(R.string.language_english),
        LanguageManager.JAPANESE to stringResource(R.string.language_japanese),
        LanguageManager.KOREAN to stringResource(R.string.language_korean)
    )

    fun next() {
        if (page < 2) page++ else onComplete()
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "onboardingPage"
                ) { currentPage ->
                    when (currentPage) {
                        0 -> LanguagePage(languages, selectedLanguage) { code ->
                            selectedLanguage = code
                            settings.updateLanguage(code)
                        }
                        1 -> InfoPage(
                            onSource = { uriHandler.openUri("https://github.com/p8735489-prog") }
                        )
                        else -> WelcomePage()
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                repeat(3) { index ->
                    val selected = page == index
                    Surface(
                        modifier = Modifier.size(if (selected) 22.dp else 7.dp, 7.dp),
                        shape = RoundedCornerShape(99.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ) {}
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page > 0) {
                    OutlinedButton(
                        onClick = { page-- },
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) { Icon(Icons.Filled.ArrowBack, null) }
                }
                Button(
                    onClick = {
                        if (page == 2 && selectedLanguage != LanguageManager.SYSTEM_DEFAULT) {
                            (context as? android.app.Activity)?.let { activity ->
                                LanguageManager(activity).applyLanguage(activity, selectedLanguage)
                            }
                        }
                        next()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(if (page == 2) stringResource(R.string.onboarding_enter) else stringResource(R.string.onboarding_next))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.ArrowForward, null)
                }
            }
        }
    }
}

@Composable
private fun LanguagePage(
    languages: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Language, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.onboarding_language), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_language_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                languages.forEach { (code, label) ->
                    FilterChip(
                        selected = selected == code,
                        onClick = { onSelect(code) },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoPage(onSource: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.onboarding_about_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_about_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        InfoCard(Icons.Filled.Lock, stringResource(R.string.onboarding_security_title), stringResource(R.string.onboarding_security_desc))
        Spacer(Modifier.height(10.dp))
        InfoCard(Icons.Filled.Memory, stringResource(R.string.onboarding_memory_title), stringResource(R.string.onboarding_memory_desc))
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onSource, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.Filled.Code, null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.onboarding_source))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_qq), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Localis", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.onboarding_final_desc), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.onboarding_ready),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(22.dp)
            )
        }
    }
}
