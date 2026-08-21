package com.localaisearch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

// Fallback color schemes (used when dynamic color is unavailable)
private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer
)

/**
 * App theme with Material 3 Expressive + Monet dynamic color support.
 *
 * @param darkMode "system", "light", or "dark"
 * @param dynamicColor Whether to use Android 12+ dynamic color (Monet)
 */
@Composable
fun LocalAISearchTheme(
    darkMode: String = "system",
    dynamicColor: Boolean = true,
    themePreset: String = "blue",
    fontMode: String = "system",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (darkMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> presetColorScheme(themePreset, isDark)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography(fontMode),
        // Use the library's canonical Material 3 shape tokens instead of a
        // custom "Pixel" shape system so surfaces, dialogs and controls remain
        // visually consistent with Google Material 3.
        // Official Material 3 shape tokens: 8/12/16/28dp. Keeping one token
        // system across settings, memory and model center prevents the custom
        // 14/20/24dp "card language" that made those pages look unlike Google M3.
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}


private fun presetColorScheme(preset: String, dark: Boolean) = when (preset) {
    "red" -> if (dark) darkColorScheme(
        primary = Color(0xFFFFB4AB), onPrimary = Color(0xFF690005),
        primaryContainer = Color(0xFF93000A), onPrimaryContainer = Color(0xFFFFDAD6),
        secondary = Color(0xFFE7BDB8), onSecondary = Color(0xFF442927),
        secondaryContainer = Color(0xFF5D3F3C), onSecondaryContainer = Color(0xFFFFDAD6),
        tertiary = Color(0xFFE4C18D), onTertiary = Color(0xFF422C00),
        tertiaryContainer = Color(0xFF5C4200), onTertiaryContainer = Color(0xFFFFDDB0),
        surface = Color(0xFF1A1110), onSurface = Color(0xFFF5DDDA),
        surfaceVariant = Color(0xFF534341), onSurfaceVariant = Color(0xFFD8C2BE),
        surfaceContainer = Color(0xFF211918), surfaceContainerHigh = Color(0xFF2C2322)
    ) else lightColorScheme(
        primary = Color(0xFFBA1A1A), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDAD6), onPrimaryContainer = Color(0xFF410002),
        secondary = Color(0xFF775652), onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDAD6), onSecondaryContainer = Color(0xFF2C1513),
        tertiary = Color(0xFF705D35), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF9E0AA), onTertiaryContainer = Color(0xFF261A00),
        surface = Color(0xFFFFF8F6), onSurface = Color(0xFF201A19),
        surfaceVariant = Color(0xFFF5DDDA), onSurfaceVariant = Color(0xFF534341),
        surfaceContainer = Color(0xFFFBEDEA), surfaceContainerHigh = Color(0xFFF6E7E4)
    )
    "yellow" -> if (dark) darkColorScheme(
        primary = Color(0xFFE8C548), onPrimary = Color(0xFF3B3000),
        primaryContainer = Color(0xFF554700), onPrimaryContainer = Color(0xFFFFE377),
        secondary = Color(0xFFD4C58D), onSecondary = Color(0xFF373016),
        secondaryContainer = Color(0xFF4E472A), onSecondaryContainer = Color(0xFFF1E3A8),
        tertiary = Color(0xFFAFCF8A), onTertiary = Color(0xFF1D370B),
        tertiaryContainer = Color(0xFF34511F), onTertiaryContainer = Color(0xFFCAEEA2),
        surface = Color(0xFF15130B), onSurface = Color(0xFFEAE2CF),
        surfaceVariant = Color(0xFF49452F), onSurfaceVariant = Color(0xFFCEC6AE),
        surfaceContainer = Color(0xFF1E1C13), surfaceContainerHigh = Color(0xFF29271B)
    ) else lightColorScheme(
        primary = Color(0xFF725D00), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE17B), onPrimaryContainer = Color(0xFF231B00),
        secondary = Color(0xFF675F40), onSecondary = Color.White,
        secondaryContainer = Color(0xFFEEE3BB), onSecondaryContainer = Color(0xFF211C05),
        tertiary = Color(0xFF4F6539), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD0EAB1), onTertiaryContainer = Color(0xFF0F2004),
        surface = Color(0xFFFFFBF1), onSurface = Color(0xFF1D1B16),
        surfaceVariant = Color(0xFFF0E4C2), onSurfaceVariant = Color(0xFF4D4632),
        surfaceContainer = Color(0xFFF6F0DE), surfaceContainerHigh = Color(0xFFF0E9D4)
    )
    "green" -> if (dark) darkColorScheme(
        primary = Color(0xFF7DDA9B), onPrimary = Color(0xFF00391A),
        primaryContainer = Color(0xFF00532A), onPrimaryContainer = Color(0xFF99F6B4),
        secondary = Color(0xFFB5CCB7), onSecondary = Color(0xFF203525),
        secondaryContainer = Color(0xFF374B3A), onSecondaryContainer = Color(0xFFD1E8D2),
        tertiary = Color(0xFFA8CDD7), onTertiary = Color(0xFF07353E),
        tertiaryContainer = Color(0xFF214C56), onTertiaryContainer = Color(0xFFC3EAF4),
        surface = Color(0xFF0E1510), onSurface = Color(0xFFDDE9DD),
        surfaceVariant = Color(0xFF414941), onSurfaceVariant = Color(0xFFC1CCC1),
        surfaceContainer = Color(0xFF171F19), surfaceContainerHigh = Color(0xFF222A24)
    ) else lightColorScheme(
        primary = Color(0xFF276B43), onPrimary = Color.White,
        primaryContainer = Color(0xFFA8F5C1), onPrimaryContainer = Color(0xFF00210D),
        secondary = Color(0xFF4F6353), onSecondary = Color.White,
        secondaryContainer = Color(0xFFD1E8D2), onSecondaryContainer = Color(0xFF0D1F12),
        tertiary = Color(0xFF3E626A), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFC1EAF3), onTertiaryContainer = Color(0xFF001F25),
        surface = Color(0xFFF8FCF7), onSurface = Color(0xFF191D19),
        surfaceVariant = Color(0xFFE0E9E0), onSurfaceVariant = Color(0xFF424942),
        surfaceContainer = Color(0xFFEDF5ED), surfaceContainerHigh = Color(0xFFE7F0E7)
    )
    else -> if (dark) DarkColors else LightColors
}
