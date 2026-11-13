package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

val DarkLuxColorScheme: ColorScheme = darkColorScheme(
    primary = LuxPurplePrimary,
    onPrimary = Color.White,
    primaryContainer = LuxPurpleSurfaceVariant,
    onPrimaryContainer = LuxSilverBright,
    secondary = LuxGoldAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4A3C08),
    onSecondaryContainer = LuxGoldBright,
    tertiary = LuxSilverText,
    onTertiary = Color.Black,
    background = LuxDeepPurple,
    onBackground = LuxSilverBright,
    surface = LuxDeepPurpleSurface,
    onSurface = LuxSilverBright,
    surfaceVariant = LuxPurpleSurfaceVariant,
    onSurfaceVariant = LuxSilverText,
    outline = LuxSilverMuted,
    outlineVariant = Color(0xFF433268),
    error = LuxError,
    onError = Color.Black
)

val LightLuxColorScheme: ColorScheme = lightColorScheme(
    primary = LuxLightPrimary,
    onPrimary = Color.White,
    primaryContainer = LuxLightSurfaceVariant,
    onPrimaryContainer = LuxLightPrimary,
    secondary = LuxGoldAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFF7E6AC),
    onSecondaryContainer = Color(0xFF4A3C08),
    tertiary = LuxLightOnSurfaceVariant,
    onTertiary = Color.White,
    background = LuxLightBg,
    onBackground = LuxLightOnSurface,
    surface = LuxLightSurface,
    onSurface = LuxLightOnSurface,
    surfaceVariant = LuxLightSurfaceVariant,
    onSurfaceVariant = LuxLightOnSurfaceVariant,
    outline = Color(0xFF888294),
    outlineVariant = Color(0xFFD3CCE0),
    error = LuxError,
    onError = Color.White
)

@Composable
fun PlayLuxTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkLuxColorScheme else LightLuxColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backwards compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    PlayLuxTheme(
        themeMode = if (darkTheme) AppThemeMode.DARK else AppThemeMode.LIGHT,
        content = content
    )
}
