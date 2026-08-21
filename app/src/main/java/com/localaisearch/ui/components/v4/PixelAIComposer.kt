package com.localaisearch.ui.components.v4

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PixelAIComposer(
    value: String,
    onValueChange: (String)->Unit,
    onSend: ()->Unit,
    enabled: Boolean = true
){
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ){
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ){
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask AI") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                maxLines = 6
            )
            IconButton(onClick = onSend, enabled = enabled){
                Icon(Icons.AutoMirrored.Rounded.Send, "send")
            }
        }
    }
}
