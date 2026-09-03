package com.example.aichat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F1E7),
    onPrimaryContainer = Color(0xFF003731),
    secondary = Color(0xFF52615E),
    background = Color(0xFFF5F7F6),
    surface = Color(0xFFF5F7F6),
    surfaceContainer = Color(0xFFE9EFED),
    onSurface = Color(0xFF18201E),
    outline = Color(0xFF73817D),
)

@Composable
fun AiChatTheme(content: @Composable () -> Unit) {
    // The MVP intentionally keeps one calm light theme to match Android's native chat surfaces.
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
