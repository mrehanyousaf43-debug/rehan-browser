package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val RehanDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF001F26),
    primaryContainer = CyanContainer,
    onPrimaryContainer = Color(0xFFA6F2FF),
    secondary = IndigoAccent,
    onSecondary = Color(0xFF0F0D2E),
    secondaryContainer = IndigoContainer,
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = EmeraldShield,
    onTertiary = Color(0xFF002116),
    background = ObsidianBg,
    onBackground = DarkTextPrimary,
    surface = MidnightSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerHigh = MidnightElevated,
    outline = DarkOutline,
    error = CoralDanger,
    onError = Color.White
)

private val RehanLightColorScheme = lightColorScheme(
    primary = DeepOcean,
    onPrimary = Color.White,
    primaryContainer = OceanContainer,
    onPrimaryContainer = Color(0xFF001F26),
    secondary = LightIndigo,
    onSecondary = Color.White,
    secondaryContainer = LightIndigoContainer,
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = EmeraldShield,
    onTertiary = Color.White,
    background = PearlBg,
    onBackground = LightTextPrimary,
    surface = PearlSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = PearlSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    error = CoralDanger,
    onError = Color.White
)

val RehanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RehanBrowserTheme(
    darkTheme: Boolean = true,
    isRtl: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) RehanDarkColorScheme else RehanLightColorScheme
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RehanTypography,
            shapes = RehanShapes,
            content = content
        )
    }
}
