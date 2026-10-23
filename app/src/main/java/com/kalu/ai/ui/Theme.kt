package com.kalu.ai.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Current theme name. Changing it in Settings updates the whole app immediately. */
object ThemeState {
    var name by mutableStateOf("blue")
}

private fun scheme(name: String): ColorScheme = when (name) {
    "purple" -> darkColorScheme(
        primary = Color(0xFFB48CFF), onPrimary = Color(0xFF21004A),
        background = Color(0xFF120B20), onBackground = Color(0xFFEDE6F7),
        surface = Color(0xFF1B1230), onSurface = Color(0xFFEDE6F7),
        surfaceVariant = Color(0xFF2A1F45), onSurfaceVariant = Color(0xFFCDBFE6),
        primaryContainer = Color(0xFF4B2A8A), onPrimaryContainer = Color(0xFFF1E8FF)
    )
    "green" -> darkColorScheme(
        primary = Color(0xFF4DD08A), onPrimary = Color(0xFF00391F),
        background = Color(0xFF0A1410), onBackground = Color(0xFFE3F3EA),
        surface = Color(0xFF10201A), onSurface = Color(0xFFE3F3EA),
        surfaceVariant = Color(0xFF1A3328), onSurfaceVariant = Color(0xFFB4D6C3),
        primaryContainer = Color(0xFF1B6B45), onPrimaryContainer = Color(0xFFE3FFF0)
    )
    "orange" -> darkColorScheme(
        primary = Color(0xFFFFA24D), onPrimary = Color(0xFF3A1B00),
        background = Color(0xFF1A0F08), onBackground = Color(0xFFF7EBE0),
        surface = Color(0xFF251810), onSurface = Color(0xFFF7EBE0),
        surfaceVariant = Color(0xFF3A2616), onSurfaceVariant = Color(0xFFE0C7AE),
        primaryContainer = Color(0xFF8A4A12), onPrimaryContainer = Color(0xFFFFEEDD)
    )
    "pink" -> darkColorScheme(
        primary = Color(0xFFFF7EB6), onPrimary = Color(0xFF4A0027),
        background = Color(0xFF1C0B14), onBackground = Color(0xFFF7E6EE),
        surface = Color(0xFF2A1220), onSurface = Color(0xFFF7E6EE),
        surfaceVariant = Color(0xFF3A1B30), onSurfaceVariant = Color(0xFFE0BCD0),
        primaryContainer = Color(0xFF8A2A5C), onPrimaryContainer = Color(0xFFFFE6F1)
    )
    "amoled" -> darkColorScheme(
        primary = Color(0xFF4DA3FF), onPrimary = Color(0xFF00203D),
        background = Color(0xFF000000), onBackground = Color(0xFFE6EDF7),
        surface = Color(0xFF000000), onSurface = Color(0xFFE6EDF7),
        surfaceVariant = Color(0xFF141A26), onSurfaceVariant = Color(0xFFB4C2D9),
        primaryContainer = Color(0xFF1B4F8A), onPrimaryContainer = Color(0xFFE6F1FF)
    )
    else -> darkColorScheme(
        primary = Color(0xFF4DA3FF), onPrimary = Color(0xFF00203D),
        background = Color(0xFF0B1220), onBackground = Color(0xFFE6EDF7),
        surface = Color(0xFF121B2E), onSurface = Color(0xFFE6EDF7),
        surfaceVariant = Color(0xFF1A2640), onSurfaceVariant = Color(0xFFB4C2D9),
        primaryContainer = Color(0xFF1B4F8A), onPrimaryContainer = Color(0xFFE6F1FF)
    )
}

@Composable
fun KaluTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(ThemeState.name)) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
