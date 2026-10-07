package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val SamDarkColorScheme = darkColorScheme(
    primary = BrandCyanPrimary,
    onPrimary = BrandCyanOnPrimary,
    primaryContainer = BrandCyanContainer,
    onPrimaryContainer = BrandCyanOnContainer,
    secondary = BrandBlueSecondary,
    onSecondary = BrandBlueOnSecondary,
    secondaryContainer = BrandBlueContainer,
    onSecondaryContainer = BrandBlueOnContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkSurfaceBorder,
    error = SemanticExpenseRed,
    onError = DarkBackground
)

private val SamLightColorScheme = lightColorScheme(
    primary = BrandCyanPrimary,
    onPrimary = BrandCyanOnPrimary,
    primaryContainer = BrandCyanOnContainer,
    onPrimaryContainer = BrandCyanContainer,
    secondary = BrandBlueSecondary,
    onSecondary = BrandBlueOnSecondary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightSurfaceBorder,
    error = SemanticExpenseRed,
    onError = LightBackground
)

@Composable
fun SamMikrotikTheme(
    darkTheme: Boolean = true, // Dark theme is default for high-tech ISP operations
    layoutDirection: LayoutDirection = LayoutDirection.Rtl, // Arabic RTL first
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SamDarkColorScheme else SamLightColorScheme

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
