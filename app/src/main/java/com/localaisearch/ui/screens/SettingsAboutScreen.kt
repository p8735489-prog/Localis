package com.localaisearch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localaisearch.R
import com.localaisearch.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAboutScreen(onNavigateBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = SettingsContentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.about_heading), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            item { AboutSectionTitle(stringResource(R.string.about_info)) }
            item {
                AboutCard(Icons.Rounded.Person, stringResource(R.string.about_author), stringResource(R.string.about_author_desc)) {
                    uriHandler.openUri("https://github.com/p8735489-prog")
                }
            }
            item {
                AboutCard(Icons.Rounded.Info, stringResource(R.string.about_version), "v${BuildConfig.VERSION_NAME}")
            }
            item { AboutSectionTitle(stringResource(R.string.about_open_source)) }
            item {
                AboutCard(Icons.Rounded.Code, stringResource(R.string.about_source), "github.com/p8735489-prog") {
                    uriHandler.openUri("https://github.com/p8735489-prog")
                }
            }
            item {
                AboutCard(Icons.Rounded.Code, stringResource(R.string.about_technology), stringResource(R.string.about_technology_desc))
            }
            item {
                AboutCard(Icons.Rounded.Public, stringResource(R.string.about_tor), stringResource(R.string.about_tor_desc)) {
                    uriHandler.openUri("https://www.torproject.org/")
                }
            }
            item { AboutSectionTitle(stringResource(R.string.about_privacy)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Text(
                        stringResource(R.string.about_privacy_desc),
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSectionTitle(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun AboutCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
