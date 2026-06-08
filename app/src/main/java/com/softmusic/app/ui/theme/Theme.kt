package com.softmusic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val label: String) {
    System("Sistema"),
    Light("Claro"),
    Dark("Oscuro"),
    Midnight("Noche azul"),
    Forest("Bosque"),
    Sunset("Atardecer"),
    Lavender("Lavanda"),
    Graphite("Grafito"),
}

enum class AppColorPalette(
    val label: String,
    val lightPrimary: Color,
    val darkPrimary: Color,
    val lightSurfaceVariant: Color,
    val darkSurfaceVariant: Color,
) {
    Green(
        label = "Verde",
        lightPrimary = Color(0xFF138A3D),
        darkPrimary = Color(0xFF1DB954),
        lightSurfaceVariant = Color(0xFFE6EFE4),
        darkSurfaceVariant = Color(0xFF1A211A),
    ),
    Blue(
        label = "Azul",
        lightPrimary = Color(0xFF1D5FD6),
        darkPrimary = Color(0xFF5F9BFF),
        lightSurfaceVariant = Color(0xFFE4ECFA),
        darkSurfaceVariant = Color(0xFF151D2B),
    ),
    Purple(
        label = "Morado",
        lightPrimary = Color(0xFF7B2EDB),
        darkPrimary = Color(0xFFC084FC),
        lightSurfaceVariant = Color(0xFFF0E7FA),
        darkSurfaceVariant = Color(0xFF25182F),
    ),
    Orange(
        label = "Naranja",
        lightPrimary = Color(0xFFC95F00),
        darkPrimary = Color(0xFFFFB15F),
        lightSurfaceVariant = Color(0xFFF7E8D8),
        darkSurfaceVariant = Color(0xFF2A1B11),
    ),
    Red(
        label = "Rojo",
        lightPrimary = Color(0xFFC62828),
        darkPrimary = Color(0xFFFF6B6B),
        lightSurfaceVariant = Color(0xFFF8E1E1),
        darkSurfaceVariant = Color(0xFF2D1717),
    ),
    Pink(
        label = "Rosa",
        lightPrimary = Color(0xFFC2185B),
        darkPrimary = Color(0xFFFF79B0),
        lightSurfaceVariant = Color(0xFFF8E1EC),
        darkSurfaceVariant = Color(0xFF2D1521),
    ),
    Teal(
        label = "Turquesa",
        lightPrimary = Color(0xFF00796B),
        darkPrimary = Color(0xFF4DD0C0),
        lightSurfaceVariant = Color(0xFFDDF1EE),
        darkSurfaceVariant = Color(0xFF102522),
    ),
    Gold(
        label = "Dorado",
        lightPrimary = Color(0xFF9A6A00),
        darkPrimary = Color(0xFFFFCC4D),
        lightSurfaceVariant = Color(0xFFF4EAD1),
        darkSurfaceVariant = Color(0xFF2A2110),
    ),
}

private val LightBackground = Color(0xFFFAFBFE)
private val LightSurface = Color.White
private val DarkBackground = Color(0xFF08090C)
private val DarkSurface = Color(0xFF101217)

private fun softLightColorScheme(colorPalette: AppColorPalette) = lightColorScheme(
    primary = colorPalette.lightPrimary,
    onPrimary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF121419),
    surface = LightSurface,
    onSurface = Color(0xFF121419),
    surfaceVariant = colorPalette.lightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4E5663),
)

private fun softDarkColorScheme(colorPalette: AppColorPalette) = darkColorScheme(
    primary = colorPalette.darkPrimary,
    onPrimary = Color.Black,
    background = DarkBackground,
    onBackground = Color(0xFFF3F5F8),
    surface = DarkSurface,
    onSurface = Color(0xFFF3F5F8),
    surfaceVariant = colorPalette.darkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB8BEC8),
)

private fun midnightColorScheme(colorPalette: AppColorPalette) = darkColorScheme(
    primary = colorPalette.darkPrimary,
    onPrimary = Color.Black,
    background = Color(0xFF07111F),
    onBackground = Color(0xFFEAF2FF),
    surface = Color(0xFF0E1A2B),
    onSurface = Color(0xFFEAF2FF),
    surfaceVariant = Color(0xFF172A42),
    onSurfaceVariant = Color(0xFFB8C7DB),
)

private fun forestColorScheme(colorPalette: AppColorPalette) = darkColorScheme(
    primary = colorPalette.darkPrimary,
    onPrimary = Color.Black,
    background = Color(0xFF07120C),
    onBackground = Color(0xFFF0F7EF),
    surface = Color(0xFF0E1B13),
    onSurface = Color(0xFFF0F7EF),
    surfaceVariant = Color(0xFF183020),
    onSurfaceVariant = Color(0xFFBBD0BD),
)

private fun sunsetColorScheme(colorPalette: AppColorPalette) = lightColorScheme(
    primary = colorPalette.lightPrimary,
    onPrimary = Color.White,
    background = Color(0xFFFFF7EF),
    onBackground = Color(0xFF231811),
    surface = Color(0xFFFFFCF8),
    onSurface = Color(0xFF231811),
    surfaceVariant = Color(0xFFF2DDC8),
    onSurfaceVariant = Color(0xFF6B5545),
)

private fun lavenderColorScheme(colorPalette: AppColorPalette) = lightColorScheme(
    primary = colorPalette.lightPrimary,
    onPrimary = Color.White,
    background = Color(0xFFF8F3FF),
    onBackground = Color(0xFF1F1726),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1F1726),
    surfaceVariant = Color(0xFFE8DDF7),
    onSurfaceVariant = Color(0xFF5C5167),
)

private fun graphiteColorScheme(colorPalette: AppColorPalette) = darkColorScheme(
    primary = colorPalette.darkPrimary,
    onPrimary = Color.Black,
    background = Color(0xFF111315),
    onBackground = Color(0xFFF2F4F7),
    surface = Color(0xFF1A1D21),
    onSurface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFF2A3036),
    onSurfaceVariant = Color(0xFFC0C6CF),
)

@Composable
fun SoftMusicTheme(
    themeMode: AppThemeMode = AppThemeMode.System,
    colorPalette: AppColorPalette = AppColorPalette.Green,
    content: @Composable () -> Unit,
) {
    val systemDarkTheme = isSystemInDarkTheme()
    val colors = when (themeMode) {
        AppThemeMode.System -> if (systemDarkTheme) softDarkColorScheme(colorPalette) else softLightColorScheme(colorPalette)
        AppThemeMode.Light -> softLightColorScheme(colorPalette)
        AppThemeMode.Dark -> softDarkColorScheme(colorPalette)
        AppThemeMode.Midnight -> midnightColorScheme(colorPalette)
        AppThemeMode.Forest -> forestColorScheme(colorPalette)
        AppThemeMode.Sunset -> sunsetColorScheme(colorPalette)
        AppThemeMode.Lavender -> lavenderColorScheme(colorPalette)
        AppThemeMode.Graphite -> graphiteColorScheme(colorPalette)
    }

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
