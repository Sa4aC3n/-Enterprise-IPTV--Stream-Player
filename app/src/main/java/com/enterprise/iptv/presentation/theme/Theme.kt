package com.enterprise.iptv.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentTeal,
    onPrimary = Color.Black,
    primaryContainer = DarkElevated,
    onPrimaryContainer = AccentTeal,
    secondary = NeonBlue,
    onSecondary = Color.White,
    secondaryContainer = DarkElevated,
    onSecondaryContainer = Color.White,
    tertiary = GoldAmber,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    error = LiveRed,
    onError = Color.White
)

private val LightColorScheme = darkColorScheme( // Keep dark cinema theme default for TV/Media experience
    primary = AccentTeal,
    onPrimary = Color.Black,
    background = DarkBackground,
    surface = DarkSurface
)

@Composable
fun EnterpriseIPTVTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
