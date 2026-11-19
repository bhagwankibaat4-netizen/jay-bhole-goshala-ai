package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = PureWhite,
    primaryContainer = SoftGreenTint,
    onPrimaryContainer = ForestGreen,
    secondary = GoshalaGreen,
    onSecondary = PureWhite,
    secondaryContainer = SoftGoldTint,
    onSecondaryContainer = DarkText,
    tertiary = Saffron,
    onTertiary = PureWhite,
    tertiaryContainer = SoftSaffronTint,
    onTertiaryContainer = DarkText,
    background = Cream,
    onBackground = DarkText,
    surface = PureWhite,
    onSurface = DarkText,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = MutedText,
    outline = OutlineWarm,
    outlineVariant = Golden,
    error = AlertRed,
    onError = PureWhite,
    errorContainer = SoftRedTint,
    onErrorContainer = AlertRed
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkGoshalaGreen,
    onPrimary = DarkForestBackground,
    primaryContainer = ForestGreen,
    onPrimaryContainer = DarkCreamText,
    secondary = DarkGolden,
    onSecondary = DarkForestBackground,
    secondaryContainer = DarkForestSurfaceHigh,
    onSecondaryContainer = DarkCreamText,
    tertiary = DarkSaffron,
    onTertiary = DarkForestBackground,
    tertiaryContainer = Color(0xFF3E2912),
    onTertiaryContainer = DarkCreamText,
    background = DarkForestBackground,
    onBackground = DarkCreamText,
    surface = DarkForestSurface,
    onSurface = DarkCreamText,
    surfaceVariant = DarkForestSurfaceHigh,
    onSurfaceVariant = DarkMutedText,
    outline = DarkOutline,
    outlineVariant = DarkGolden,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

private val GosalaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GosalaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = GosalaTypography,
        shapes = GosalaShapes,
        content = content
    )
}
