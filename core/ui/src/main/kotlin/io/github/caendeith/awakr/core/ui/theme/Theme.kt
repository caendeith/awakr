package io.github.caendeith.awakr.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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

/** The accent the user picked in Settings. Amber is the brand default. */
enum class AccentColor {
    AMBER,
    BLUE,
    GREEN,
    VIOLET,
    ;

    companion object {
        fun of(preference: String?): AccentColor =
            entries.firstOrNull { it.name.equals(preference, ignoreCase = true) } ?: AMBER
    }
}

/** Swaps only the primary family, so the ink surfaces and the typography stay untouched. */
private fun ColorScheme.withAccent(accent: AccentColor): ColorScheme = when (accent) {
    AccentColor.AMBER -> this
    AccentColor.BLUE -> copy(primary = Blue, onPrimary = OnBlue, primaryContainer = BlueContainer, secondary = Blue, onSecondary = OnBlue)
    AccentColor.GREEN -> copy(primary = Green, onPrimary = OnGreen, primaryContainer = GreenContainer, secondary = Green, onSecondary = OnGreen)
    AccentColor.VIOLET -> copy(primary = Violet, onPrimary = OnViolet, primaryContainer = VioletContainer, secondary = Violet, onSecondary = OnViolet)
}

/**
 * Awakr theme. Dark by default: the app is used in the dark, minutes after waking up.
 * Dynamic color is opt-in and never the default brand experience.
 */
@Composable
fun AwakrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accent: AccentColor = AccentColor.AMBER,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicColor && supportsDynamic ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme.withAccent(accent)
        else -> LightScheme.withAccent(accent)
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
