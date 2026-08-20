
package com.localaisearch.ui.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compatibility wrapper. The actual animation is provided by AndroidX Material 3
 * Expressive; no custom Canvas spinner is used here.
 */
@Composable
fun Material3LoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    CircularProgressIndicator(modifier = modifier.size(size))
}
