package io.github.caendeith.awakr.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    primaryContainer = ContainerHighDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = Amber,
    onSecondary = OnAmber,
    background = Ink,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = ContainerDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = ContainerDark,
    surfaceContainerHigh = ContainerHighDark,
    surfaceContainerHighest = ContainerHighDark,
    surfaceContainerLow = SurfaceDark,
    error = ErrorRed,
    onError = OnAmber,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
)

private val LightScheme = lightColorScheme(
    primary = AmberDeep,
    onPrimary = SurfaceLight,
    primaryContainer = ContainerLight,
    onPrimaryContainer = TextPrimaryLight,
    secondary = AmberDeep,
    onSecondary = SurfaceLight,
    background = PaperLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = ContainerLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = ContainerLight,
    surfaceContainerHigh = ContainerHighLight,
    surfaceContainerHighest = ContainerHighLight,
    surfaceContainerLow = SurfaceLight,
    error = ErrorRed,
    onError = TextPrimaryLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
)

/**
 * Awakr theme. Dark by default: the app is used in the dark, minutes after waking up.
 * Dynamic color is opt-in and never the default brand experience.
 */
@Composable
fun AwakrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && false -> DarkScheme // dynamic color lands in phase 6
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AwakrTypography,
        shapes = AwakrShapes,
        content = content,
    )
}
