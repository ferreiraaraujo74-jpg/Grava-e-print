package com.gravaeprint.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Orange = Color(0xFFFF7A00)
val OrangeDark = Color(0xFFB85600)

private val colors = darkColorScheme(
    primary = Orange,
    onPrimary = Color.Black,
    primaryContainer = OrangeDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFFB074),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3A2614),
    onSecondaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainer = Color(0xFF1C1C1C),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2C2C2C),
    outline = Color(0xFF5A5A5A),
    error = Color(0xFFFF5449),
)

@Composable
fun GravaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
