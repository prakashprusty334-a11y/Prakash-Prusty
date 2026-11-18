package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CerevixDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF172B59),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = LuminousCyan,
    onSecondary = Color(0xFF03121E),
    secondaryContainer = Color(0xFF0D2D3E),
    onSecondaryContainer = Color(0xFFA5F3FC),
    tertiary = VioletAccent,
    onTertiary = Color.White,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = MidnightNavy,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = MidnightNavy,
    surfaceContainer = SlateSurface,
    surfaceContainerHigh = ElevatedCard,
    outline = BorderGlass,
    outlineVariant = Color(0xFF1A2642),
    error = RoseError,
    onError = Color.White
)

private val CerevixShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CerevixTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CerevixDarkColorScheme,
        typography = CerevixTypography,
        shapes = CerevixShapes,
        content = content
    )
}
