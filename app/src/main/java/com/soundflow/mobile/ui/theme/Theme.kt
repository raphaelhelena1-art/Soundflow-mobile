package com.soundflow.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// SoundFlow n'a qu'un thème sombre + vert pour l'instant (pas de mode clair, décision produit).
private val SoundFlowColorScheme = darkColorScheme(
    primary = SoundFlowGreen,
    onPrimary = TextPrimary,
    secondary = SoundFlowGreenDark,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
)

@Composable
fun SoundFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SoundFlowColorScheme,
        typography = SoundFlowTypography,
        content = content,
    )
}
