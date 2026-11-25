package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// Unified Navy + Crimson Red + Deep Black ColorScheme
private val NavyRedBlackColorScheme = darkColorScheme(
    primary = CrimsonBright,
    onPrimary = Color.White,
    primaryContainer = CrimsonContainer,
    onPrimaryContainer = CrimsonOnContainer,
    secondary = NavyElevated,
    onSecondary = IceWhite,
    secondaryContainer = NavyPrimary,
    onSecondaryContainer = WarmHighlight,
    tertiary = CrimsonRed,
    onTertiary = Color.White,
    background = VoidBlack,
    onBackground = IceWhite,
    surface = NavyDeep,
    onSurface = IceWhite,
    surfaceVariant = NavyPrimary,
    onSurfaceVariant = SilverText,
    outline = NavyBorder,
    error = Color(0xFFFF6B7D),
    onError = ObsidianBlack
)

private val AlMuezzinShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AlMuezzinTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = NavyRedBlackColorScheme,
            typography = AlMuezzinTypography,
            shapes = AlMuezzinShapes,
            content = content
        )
    }
}
