package com.avatarfalante.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF5EEAD4)
val Coral = Color(0xFFFF8A65)

private val scheme = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF042F2B),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Coral,
    onSecondary = Color(0xFF3A1206),
    background = Color(0xFF0F1420),
    onBackground = Color(0xFFE7ECF5),
    surface = Color(0xFF0F1420),
    onSurface = Color(0xFFE7ECF5),
    surfaceVariant = Color(0xFF1E2636),
    onSurfaceVariant = Color(0xFFAAB4C6),
    surfaceContainer = Color(0xFF161D2B),
    surfaceContainerHigh = Color(0xFF1E2636),
    surfaceContainerHighest = Color(0xFF273043),
    outline = Color(0xFF4A5568),
)

@Composable
fun AvatarTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
