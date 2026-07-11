package com.bravoscribe.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightWarmColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Parchment,
    primaryContainer = AccentLight,
    onPrimaryContainer = Ink,
    secondary = Gold,
    onSecondary = Parchment,
    secondaryContainer = GoldLight,
    onSecondaryContainer = Ink,
    tertiary = Teal,
    tertiaryContainer = TealLight,
    background = Parchment,
    onBackground = Ink,
    surface = Parchment,
    onSurface = Ink,
    surfaceVariant = Parchment2,
    onSurfaceVariant = Ink3,
    outline = Border,
    outlineVariant = Parchment3,
    error = Rust,
    onError = Parchment,
)

private val DarkChronicleColorScheme = darkColorScheme(
    primary = ChronicleGold,
    onPrimary = ChronicleDeep,
    primaryContainer = ChronicleGold,
    onPrimaryContainer = ChronicleDeep,
    secondary = ChronicleTeal,
    onSecondary = ChronicleDeep,
    background = ChronicleDeep,
    onBackground = ChronicleCream,
    surface = ChroniclePanel,
    onSurface = ChronicleCream,
    surfaceVariant = ChroniclePanel,
    onSurfaceVariant = ChronicleDim,
    outline = ChronicleBorder,
    outlineVariant = ChronicleBorder,
    error = ChronicleRuby,
    onError = ChronicleCream,
)

/** Streak/editing-context accent — outside Material3's standard ColorScheme slots. */
data class BravoscribeExtraColors(
    val streak: Color,
    val streakContainer: Color,
)

private val LightExtraColors = BravoscribeExtraColors(
    streak = StreakPurple,
    streakContainer = StreakPurpleLight,
)

private val ChronicleExtraColors = BravoscribeExtraColors(
    streak = StreakPurpleChronicle,
    streakContainer = StreakPurpleChronicleContainer,
)

private val LocalBravoscribeExtraColors = compositionLocalOf { LightExtraColors }

object BravoscribeExtras {
    val colors: BravoscribeExtraColors
        @Composable get() = LocalBravoscribeExtraColors.current
}

@Composable
fun BravoscribeTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (isDark) DarkChronicleColorScheme else LightWarmColorScheme
    val extraColors = if (isDark) ChronicleExtraColors else LightExtraColors
    CompositionLocalProvider(LocalBravoscribeExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BravoscribeTypography,
            content = content,
        )
    }
}
