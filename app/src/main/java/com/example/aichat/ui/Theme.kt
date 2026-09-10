package com.example.aichat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.aichat.data.model.DEFAULT_THEME_COLOR

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

enum class AppThemeColor(
    val key: String,
    val label: String,
    val colorHex: String,
    val lightPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
) {
    CLASSIC_BLUE(
        key = "classic_blue",
        label = "经典蓝",
        colorHex = "#315BCE",
        lightPrimary = Color(0xFF315BCE),
        lightPrimaryContainer = Color(0xFFDCE5FF),
        lightOnPrimaryContainer = Color(0xFF10275F),
        darkPrimary = Color(0xFFADC6FF),
        darkOnPrimary = Color(0xFF002E69),
        darkPrimaryContainer = Color(0xFF19449B),
        darkOnPrimaryContainer = Color(0xFFDCE5FF),
    ),
    EMERALD_GREEN(
        key = "emerald_green",
        label = "翡翠绿",
        colorHex = "#1B8754",
        lightPrimary = Color(0xFF1B8754),
        lightPrimaryContainer = Color(0xFFD4F4E2),
        lightOnPrimaryContainer = Color(0xFF00391E),
        darkPrimary = Color(0xFF81D4A0),
        darkOnPrimary = Color(0xFF00391E),
        darkPrimaryContainer = Color(0xFF00522E),
        darkOnPrimaryContainer = Color(0xFFD4F4E2),
    ),
    CORAL_ORANGE(
        key = "coral_orange",
        label = "活力橙",
        colorHex = "#D84315",
        lightPrimary = Color(0xFFD84315),
        lightPrimaryContainer = Color(0xFFFFDBD0),
        lightOnPrimaryContainer = Color(0xFF3D0C00),
        darkPrimary = Color(0xFFFFB59D),
        darkOnPrimary = Color(0xFF5F1500),
        darkPrimaryContainer = Color(0xFF862200),
        darkOnPrimaryContainer = Color(0xFFFFDBD0),
    ),
    ELEGANT_PURPLE(
        key = "elegant_purple",
        label = "优雅紫",
        colorHex = "#6750A4",
        lightPrimary = Color(0xFF6750A4),
        lightPrimaryContainer = Color(0xFFEADDFF),
        lightOnPrimaryContainer = Color(0xFF21005D),
        darkPrimary = Color(0xFFD0BCFF),
        darkOnPrimary = Color(0xFF381E72),
        darkPrimaryContainer = Color(0xFF4F378B),
        darkOnPrimaryContainer = Color(0xFFEADDFF),
    ),
    ROSE_PINK(
        key = "rose_pink",
        label = "樱花粉",
        colorHex = "#C2185B",
        lightPrimary = Color(0xFFC2185B),
        lightPrimaryContainer = Color(0xFFFFD9E2),
        lightOnPrimaryContainer = Color(0xFF3E001D),
        darkPrimary = Color(0xFFFFAFD0),
        darkOnPrimary = Color(0xFF5C002E),
        darkPrimaryContainer = Color(0xFF800042),
        darkOnPrimaryContainer = Color(0xFFFFD9E2),
    ),
    AMBER_GOLD(
        key = "amber_gold",
        label = "琥珀金",
        colorHex = "#B26A00",
        lightPrimary = Color(0xFFB26A00),
        lightPrimaryContainer = Color(0xFFFFDDB3),
        lightOnPrimaryContainer = Color(0xFF2B1700),
        darkPrimary = Color(0xFFFFB951),
        darkOnPrimary = Color(0xFF452B00),
        darkPrimaryContainer = Color(0xFF633F00),
        darkOnPrimaryContainer = Color(0xFFFFDDB3),
    ),
    TEAL_CYAN(
        key = "teal_cyan",
        label = "极客青",
        colorHex = "#00796B",
        lightPrimary = Color(0xFF00796B),
        lightPrimaryContainer = Color(0xFFB2DFDB),
        lightOnPrimaryContainer = Color(0xFF00201C),
        darkPrimary = Color(0xFF80CBC4),
        darkOnPrimary = Color(0xFF003730),
        darkPrimaryContainer = Color(0xFF004D40),
        darkOnPrimaryContainer = Color(0xFFB2DFDB),
    ),
    SLATE_CHARCOAL(
        key = "slate_charcoal",
        label = "玄武黑",
        colorHex = "#455A64",
        lightPrimary = Color(0xFF455A64),
        lightPrimaryContainer = Color(0xFFCFD8DC),
        lightOnPrimaryContainer = Color(0xFF1C272C),
        darkPrimary = Color(0xFFB0BEC5),
        darkOnPrimary = Color(0xFF263238),
        darkPrimaryContainer = Color(0xFF37474F),
        darkOnPrimaryContainer = Color(0xFFCFD8DC),
    );

    companion object {
        fun fromKey(key: String?): AppThemeColor =
            entries.firstOrNull { it.key == key } ?: CLASSIC_BLUE
    }
}

@Composable
fun AiChatTheme(
    themeColorKey: String = DEFAULT_THEME_COLOR,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val theme = AppThemeColor.fromKey(themeColorKey)
    val colorScheme = if (darkTheme) {
        DarkColors.copy(
            primary = theme.darkPrimary,
            onPrimary = theme.darkOnPrimary,
            primaryContainer = theme.darkPrimaryContainer,
            onPrimaryContainer = theme.darkOnPrimaryContainer,
        )
    } else {
        LightColors.copy(
            primary = theme.lightPrimary,
            onPrimary = Color.White,
            primaryContainer = theme.lightPrimaryContainer,
            onPrimaryContainer = theme.lightOnPrimaryContainer,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
