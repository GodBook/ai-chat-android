package com.example.aichat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4F8),
    surfaceContainer = Color(0xFFEEF1F7),
    surfaceContainerHigh = Color(0xFFE7EBF3),
    surfaceContainerHighest = Color(0xFFDDE3EE),
    onSurface = Color(0xFF1A1D26),
    onSurfaceVariant = Color(0xFF626A7A),
    outline = Color(0xFF8A93A5),
    outlineVariant = Color(0xFFD4D9E4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF19449B),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFF59657A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3E4756),
    onSecondaryContainer = Color(0xFFDCE3F1),
    tertiary = Color(0xFFFFB59D),
    tertiaryContainer = Color(0xFF7A2E16),
    onTertiaryContainer = Color(0xFFFFDBCE),
    background = Color(0xFF121418),
    surface = Color(0xFF121418),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF1A1C22),
    surfaceContainer = Color(0xFF22252C),
    surfaceContainerHigh = Color(0xFF2C3038),
    surfaceContainerHighest = Color(0xFF373B45),
    onSurface = Color(0xFFE3E5ED),
    onSurfaceVariant = Color(0xFFC2C7D0),
    outline = Color(0xFF8C919D),
    outlineVariant = Color(0xFF424750),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8f),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12f),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16f),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24f),
)

private val AppTypography = Typography()

@Composable
fun AiChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
