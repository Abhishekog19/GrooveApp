package com.groove.music.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = GroovePurple,
    onPrimary        = GrooveTextPrimary,
    primaryContainer = GroovePurpleDim,
    secondary        = GrooveBlue,
    onSecondary      = GrooveTextPrimary,
    tertiary         = GrooveGreen,
    background       = GrooveBg,
    surface          = GrooveSurface,
    surfaceVariant   = GrooveSurfaceHigh,
    onBackground     = GrooveTextPrimary,
    onSurface        = GrooveTextPrimary,
    onSurfaceVariant = GrooveTextMuted,
    error            = GrooveRed,
    outline          = GrooveBorder
)

@Composable
fun GrooveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = GrooveTypography,
        content     = content
    )
}
