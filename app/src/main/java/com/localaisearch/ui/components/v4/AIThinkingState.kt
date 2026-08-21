package com.localaisearch.ui.components.v4

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AIThinkingState(){
    Row(modifier = Modifier.padding(16.dp)){
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("Thinking…", style = MaterialTheme.typography.bodyMedium)
    }
}
