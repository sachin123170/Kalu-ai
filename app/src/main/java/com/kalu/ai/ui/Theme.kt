package com.kalu.ai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KaluColors = darkColorScheme(
    primary = Color(0xFF4DA3FF),
    onPrimary = Color(0xFF00203D),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF121B2E),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1A2640),
    onSurfaceVariant = Color(0xFFB4C2D9),
    primaryContainer = Color(0xFF1B4F8A),
    onPrimaryContainer = Color(0xFFE6F1FF)
)

@Composable
fun KaluTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KaluColors, content = content)
}
