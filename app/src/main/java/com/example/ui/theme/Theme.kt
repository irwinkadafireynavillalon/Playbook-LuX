package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(
    val displayName: String,
    val subtitle: String,
    val isLightPalette: Boolean = false
) {
    DARK("Púrpura Imperial", "Morado nocturno y oro clásico"),
    LIGHT("Marfil Dorado", "Claro luminoso con bronce", isLightPalette = true),
    SAPPHIRE_BLUE("Zafiro Cósmico", "Azul profundo con cian neón"),
    EMERALD_GOLD("Esmeralda Real", "Verde bosque con menta y ámbar"),
    CRIMSON_COPPER("Rubí Carmesí", "Vino elegante con cobre rosado"),
    SYSTEM("Seguir al sistema", "Alterna entre Imperial y Marfil")
}

@Immutable
data class LuxBrandPalette(
    val accent: Color,
    val accentBright: Color,
    val secondaryText: Color,
    val brightText: Color,
    val topBarStart: Color,
    val topBarMid: Color,
    val topBarEnd: Color,
    val badgeBg: Color,
    val isDark: Boolean
)

val LocalLuxPalette = staticCompositionLocalOf {
    LuxBrandPalette(
        accent = LuxGoldAccent,
        accentBright = LuxGoldBright,
        secondaryText = LuxSilverText,
        brightText = LuxSilverBright,
        topBarStart = Color(0xFF1A0F33),
        topBarMid = Color(0xFF2B1B4E),
        topBarEnd = Color(0xFF21143F),
        badgeBg = Color(0xFF33205D),
        isDark = true
    )
}

val MaterialTheme.luxPalette: LuxBrandPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalLuxPalette.current

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
    secondary = LuxLightAccent,
    onSecondary = Color.White,
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
    error = Color(0xFFB3261E),
    onError = Color.White
)

val SapphireLuxColorScheme: ColorScheme = darkColorScheme(
    primary = SapphirePrimary,
    onPrimary = Color.White,
    primaryContainer = SapphireSurfaceVariant,
    onPrimaryContainer = SapphireText,
    secondary = SapphireAccent,
    onSecondary = Color(0xFF042029),
    secondaryContainer = Color(0xFF0E3A54),
    onSecondaryContainer = SapphireAccentBright,
    tertiary = SapphireMuted,
    onTertiary = Color.Black,
    background = SapphireBg,
    onBackground = SapphireText,
    surface = SapphireSurface,
    onSurface = SapphireText,
    surfaceVariant = SapphireSurfaceVariant,
    onSurfaceVariant = SapphireMuted,
    outline = Color(0xFF3E618C),
    outlineVariant = Color(0xFF26446D),
    error = LuxError,
    onError = Color.Black
)

val EmeraldLuxColorScheme: ColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldSurfaceVariant,
    onPrimaryContainer = EmeraldText,
    secondary = EmeraldAccent,
    onSecondary = Color(0xFF052419),
    secondaryContainer = Color(0xFF154736),
    onSecondaryContainer = EmeraldAccentBright,
    tertiary = EmeraldMuted,
    onTertiary = Color.Black,
    background = EmeraldBg,
    onBackground = EmeraldText,
    surface = EmeraldSurface,
    onSurface = EmeraldText,
    surfaceVariant = EmeraldSurfaceVariant,
    onSurfaceVariant = EmeraldMuted,
    outline = Color(0xFF3A735E),
    outlineVariant = Color(0xFF205240),
    error = LuxError,
    onError = Color.Black
)

val CrimsonLuxColorScheme: ColorScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    primaryContainer = CrimsonSurfaceVariant,
    onPrimaryContainer = CrimsonText,
    secondary = CrimsonAccent,
    onSecondary = Color(0xFF2E0712),
    secondaryContainer = Color(0xFF5A1C33),
    onSecondaryContainer = CrimsonAccentBright,
    tertiary = CrimsonMuted,
    onTertiary = Color.Black,
    background = CrimsonBg,
    onBackground = CrimsonText,
    surface = CrimsonSurface,
    onSurface = CrimsonText,
    surfaceVariant = CrimsonSurfaceVariant,
    onSurfaceVariant = CrimsonMuted,
    outline = Color(0xFF8C465D),
    outlineVariant = Color(0xFF5E2238),
    error = Color(0xFFFF8A80),
    onError = Color.Black
)

fun resolveColorSchemeAndBrand(
    themeMode: AppThemeMode,
    systemIsDark: Boolean
): Pair<ColorScheme, LuxBrandPalette> {
    val resolvedMode = when (themeMode) {
        AppThemeMode.SYSTEM -> if (systemIsDark) AppThemeMode.DARK else AppThemeMode.LIGHT
        else -> themeMode
    }
    return when (resolvedMode) {
        AppThemeMode.DARK, AppThemeMode.SYSTEM -> DarkLuxColorScheme to LuxBrandPalette(
            accent = LuxGoldAccent,
            accentBright = LuxGoldBright,
            secondaryText = LuxSilverText,
            brightText = LuxSilverBright,
            topBarStart = Color(0xFF1A0F33),
            topBarMid = Color(0xFF2B1B4E),
            topBarEnd = Color(0xFF21143F),
            badgeBg = Color(0xFF33205D),
            isDark = true
        )
        AppThemeMode.LIGHT -> LightLuxColorScheme to LuxBrandPalette(
            accent = LuxLightAccent,
            accentBright = LuxLightPrimary,
            secondaryText = LuxLightOnSurfaceVariant,
            brightText = LuxLightOnSurface,
            topBarStart = Color(0xFFF6F1E5),
            topBarMid = Color(0xFFECE2CE),
            topBarEnd = Color(0xFFF6F1E5),
            badgeBg = Color(0xFFE5D7B8),
            isDark = false
        )
        AppThemeMode.SAPPHIRE_BLUE -> SapphireLuxColorScheme to LuxBrandPalette(
            accent = SapphireAccent,
            accentBright = SapphireAccentBright,
            secondaryText = SapphireMuted,
            brightText = SapphireText,
            topBarStart = Color(0xFF0A1931),
            topBarMid = Color(0xFF132E59),
            topBarEnd = Color(0xFF0E2346),
            badgeBg = Color(0xFF1A3A6E),
            isDark = true
        )
        AppThemeMode.EMERALD_GOLD -> EmeraldLuxColorScheme to LuxBrandPalette(
            accent = EmeraldAccent,
            accentBright = EmeraldAccentBright,
            secondaryText = EmeraldMuted,
            brightText = EmeraldText,
            topBarStart = Color(0xFF08221B),
            topBarMid = Color(0xFF113E31),
            topBarEnd = Color(0xFF0C2E24),
            badgeBg = Color(0xFF184D3D),
            isDark = true
        )
        AppThemeMode.CRIMSON_COPPER -> CrimsonLuxColorScheme to LuxBrandPalette(
            accent = CrimsonAccent,
            accentBright = CrimsonAccentBright,
            secondaryText = CrimsonMuted,
            brightText = CrimsonText,
            topBarStart = Color(0xFF260915),
            topBarMid = Color(0xFF471429),
            topBarEnd = Color(0xFF350F1E),
            badgeBg = Color(0xFF5A1A34),
            isDark = true
        )
    }
}

@Composable
fun PlayLuxTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val (colorScheme, brandPalette) = resolveColorSchemeAndBrand(themeMode, systemDark)

    CompositionLocalProvider(LocalLuxPalette provides brandPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

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
