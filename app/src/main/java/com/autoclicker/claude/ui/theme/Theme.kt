package com.autoclicker.claude.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0B0F19),
    primaryContainer = Color(0xFF0E3A5C),
    onPrimaryContainer = Color(0xFF38BDF8),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF0B0F19),
    secondaryContainer = Color(0xFF0E3D2E),
    onSecondaryContainer = Color(0xFF34D399),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF0B0F19),
    tertiaryContainer = Color(0xFF2E2352),
    onTertiaryContainer = Color(0xFFC4B5FD),
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

private val LightColors = lightColorScheme(
    primary = Color(0xFF0369A1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0EEFF),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF059669),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCFBE5),
    onSecondaryContainer = Color(0xFF059669),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE4FF),
    onTertiaryContainer = Color(0xFF7C3AED),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFFDC2626),
)

@Composable
fun AutoClickerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: the app has a deliberate brand palette (cyan primary,
    // green success, purple tertiary for schedule/OEM UI). Wallpaper-derived
    // dynamic color would override all of it and wash out the semantic meaning.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
