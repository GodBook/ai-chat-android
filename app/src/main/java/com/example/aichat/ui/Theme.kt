package com.example.aichat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF315BCE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF10275F),
    secondary = Color(0xFF59657A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E9F2),
    onSecondaryContainer = Color(0xFF171D2A),
    tertiary = Color(0xFFB85A3B),
    tertiaryContainer = Color(0xFFFFDBCE),
    onTertiaryContainer = Color(0xFF3D0D03),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF1F7),
    surfaceContainerHigh = Color(0xFFE7EBF3),
    surfaceContainerHighest = Color(0xFFDDE3EE),
    onSurface = Color(0xFF1A1D26),
    onSurfaceVariant = Color(0xFF626A7A),
    outline = Color(0xFF8A93A5),
    outlineVariant = Color(0xFFD4D9E4),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8f),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12f),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16f),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24f),
)

private val AppTypography = Typography()

@Composable
fun AiChatTheme(content: @Composable () -> Unit) {
    // The MVP intentionally keeps one calm light theme to match Android's native chat surfaces.
    MaterialTheme(
        colorScheme = LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
