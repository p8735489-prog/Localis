package com.localaisearch.ui.components.v4

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GeminiMessageCard(role:String, text:String){
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = MaterialTheme.shapes.large
    ){
        Column(Modifier.padding(18.dp)){
            Text(role, style=MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(text, style=MaterialTheme.typography.bodyLarge)
        }
    }
}
