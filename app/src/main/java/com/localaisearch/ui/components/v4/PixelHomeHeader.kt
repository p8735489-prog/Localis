package com.localaisearch.ui.components.v4

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PixelHomeHeader(userName: String = "") {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Local AI", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (userName.isEmpty()) "Ready when you are" else "Good to see you, $userName",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun AIStatusCard(status: String = "Idle") {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("●", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("AI Engine")
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
