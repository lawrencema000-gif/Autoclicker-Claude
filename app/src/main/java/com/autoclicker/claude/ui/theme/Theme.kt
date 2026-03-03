package com.autoclicker.claude.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0B0F19),
    primaryContainer = Color(0xFF0E3A5C),
    onPrimaryContainer = Color(0xFF38BDF8),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF0B0F19),
    secondaryContainer = Color(0xFF0E3D2E),
    onSecondaryContainer = Color(0xFF34D399),
    background = Color(0xFF0B0F19),
    onBackground = Color(0xFFE8ECF4),
    surface = Color(0xFF131825),
    onSurface = Color(0xFFE8ECF4),
    surfaceVariant = Color(0xFF1A2035),
    onSurfaceVariant = Color(0xFF8B95B0),
    outline = Color(0xFF2A3250),
    error = Color(0xFFF87171),
    onError = Color(0xFF0B0F19),
    errorContainer = Color(0xFF3D1515),
    onErrorContainer = Color(0xFFF87171),
)

@Composable
fun AutoClickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
