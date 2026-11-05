package com.tracomagico.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Lilac = Color(0xFFA78BFA)
val Pink = Color(0xFFF472B6)
val Paper = Color(0xFFF6F1E7)

private val scheme = darkColorScheme(
    primary = Lilac,
    onPrimary = Color(0xFF1B1030),
    primaryContainer = Color(0xFF3B2A66),
    onPrimaryContainer = Color(0xFFEDE6FF),
    secondary = Pink,
    onSecondary = Color(0xFF2D0A1C),
    tertiary = Color(0xFFF4C27A),
    background = Color(0xFF15131A),
    onBackground = Color(0xFFEDEAF2),
    surface = Color(0xFF15131A),
    onSurface = Color(0xFFEDEAF2),
    surfaceVariant = Color(0xFF26222F),
    onSurfaceVariant = Color(0xFFB9B3C6),
    surfaceContainer = Color(0xFF1D1A24),
    surfaceContainerHigh = Color(0xFF26222F),
    surfaceContainerHighest = Color(0xFF2F2A3A),
    outline = Color(0xFF5B5568),
)

@Composable
fun TracoTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
