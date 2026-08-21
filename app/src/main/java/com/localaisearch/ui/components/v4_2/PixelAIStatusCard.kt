package com.localaisearch.ui.components.v4_2

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PixelAIStatusCard(
    model: String,
    speed: String,
    memory: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("AI Runtime", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Model: $model")
            Text("Speed: $speed")
            Text("Memory: $memory")
        }
    }
}
