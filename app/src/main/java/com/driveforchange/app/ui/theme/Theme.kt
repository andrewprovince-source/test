package com.driveforchange.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = OffWhite,
    primaryContainer = ForestGreenLight,
    secondary = WarmAmber,
    background = OffWhite,
    surface = OffWhite,
    onBackground = Charcoal,
    onSurface = Charcoal,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = ForestGreenLight,
    onPrimary = Charcoal,
    primaryContainer = ForestGreenDark,
    secondary = WarmAmber,
    background = Charcoal,
    surface = Charcoal,
    error = ErrorRed,
)

@Composable
fun DriveForChangeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
