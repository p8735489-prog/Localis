package com.localaisearch.ui.components.v5

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun GeminiMessageCard(text: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Text(text = text, modifier = Modifier.padding(androidx.compose.ui.unit.dp.let { 16.dp }))
    }
}
