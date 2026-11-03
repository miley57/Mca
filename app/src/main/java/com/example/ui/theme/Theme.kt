package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LiquidGlassDarkScheme = darkColorScheme(
    primary = LiquidCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = Color(0xFF003B46),
    onPrimaryContainer = Color(0xFFB8F8FF),
    secondary = ElectricViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF281952),
    onSecondaryContainer = Color(0xFFE5DEFF),
    tertiary = IridescentMint,
    onTertiary = Color(0xFF00210E),
    background = ObsidianMidnight,
    onBackground = FrostWhite,
    surface = DeepSpaceSurface,
    onSurface = FrostWhite,
    surfaceVariant = Color(0xFF1A233A),
    onSurfaceVariant = MutedSilver,
    outline = FrostedBorderDark,
    error = CoralAlert,
    onError = Color.White
)

private val LiquidGlassLightScheme = lightColorScheme(
    primary = DeepCobalt,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF001D3D),
    secondary = RoyalIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE7F6),
    onSecondaryContainer = Color(0xFF21005D),
    tertiary = Color(0xFF00897B),
    onTertiary = Color.White,
    background = PearlMist,
    onBackground = SlateInk,
    surface = Color(0xFFFFFFFF),
    onSurface = SlateInk,
    surfaceVariant = Color(0xFFE3EAF4),
    onSurfaceVariant = SlateMuted,
    outline = FrostedBorderLight,
    error = Color(0xFFD32F2F),
    onError = Color.White
)

val LiquidGlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LiquidGlassDarkScheme else LiquidGlassLightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = LiquidGlassShapes,
        content = content
    )
}
