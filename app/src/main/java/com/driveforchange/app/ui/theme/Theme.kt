package com.driveforchange.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SkyColors = lightColorScheme(
    primary = SkyBlue,
    onPrimary = CardWhite,
    primaryContainer = SkyBlueSoft,
    onPrimaryContainer = SkyBlue,
    secondary = Amber,
    onSecondary = Ink,
    secondaryContainer = PillGray,
    onSecondaryContainer = Ink,
    background = SkyBottom,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = PillGray,
    onSurfaceVariant = InkMuted,
    outline = TrackGray,
    error = ErrorRed,
    errorContainer = ErrorSoft,
)

private val SkyShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Light only for now: the soft-gradient look is designed around a light ground, and a
 * dark variant is its own design pass.
 */
@Composable
fun DriveForChangeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SkyColors,
        typography = AppTypography,
        shapes = SkyShapes,
        content = content,
    )
}
