package com.softmusic.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Turquoise = Color(0xFF2CE4D4)
private val InverseTurquoise = Color(0xFF00796B)
private val TurquoiseContainer = Color(0xFF102522)
private val OnTurquoiseContainer = Color(0xFFB2F1E6)

private val DarkColors = darkColorScheme(
    primary = Turquoise,
    onPrimary = Color.Black,
    primaryContainer = TurquoiseContainer,
    onPrimaryContainer = OnTurquoiseContainer,
    inversePrimary = InverseTurquoise,
    secondary = Turquoise,
    onSecondary = Color.Black,
    secondaryContainer = TurquoiseContainer,
    onSecondaryContainer = OnTurquoiseContainer,
    tertiary = Turquoise,
    onTertiary = Color.Black,
    tertiaryContainer = TurquoiseContainer,
    onTertiaryContainer = OnTurquoiseContainer,
    background = Color(0xFF08090C),
    onBackground = Color(0xFFF3F5F8),
    surface = Color(0xFF101217),
    onSurface = Color(0xFFF3F5F8),
    surfaceVariant = TurquoiseContainer,
    onSurfaceVariant = Color(0xFFA8AFBD),
    surfaceTint = Turquoise,
)

@Composable
fun SoftMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
