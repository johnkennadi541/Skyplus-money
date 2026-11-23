package com.example.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.example.domain.model.ThemePreference

private val DarkOceanColorScheme =
  darkColorScheme(
    primary = OceanPrimaryDarkMode,
    onPrimary = Color(0xFF062044),
    primaryContainer = DeepOceanBlue,
    onPrimaryContainer = OceanTextLight,
    secondary = OceanTealBright,
    onSecondary = DeepOceanNavy,
    secondaryContainer = Color(0xFF0A3E3A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    // tertiary carries the yellow accent
    tertiary = SkyplusYellowBright,
    onTertiary = SkyplusOnYellow,
    tertiaryContainer = SkyplusYellowContainerDark,
    onTertiaryContainer = SkyplusYellowBright,
    background = DeepOceanNavy,
    onBackground = OceanTextLight,
    surface = DeepOceanSurface,
    onSurface = OceanTextLight,
    surfaceVariant = SubtleOceanBlue,
    onSurfaceVariant = OceanTextMutedDark,
    surfaceContainerLow = DeepOceanSurface,
    surfaceContainer = SubtleOceanBlue,
    surfaceContainerHigh = SubtleOceanBlueElevated,
    outline = OceanBorderBlue,
    outlineVariant = Color(0xFF153453),
    error = OceanNegative,
    onError = Color.White,
  )

private val LightOceanColorScheme =
  lightColorScheme(
    primary = OceanPrimaryLightMode,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E9F8),
    onPrimaryContainer = Color(0xFF06253C),
    secondary = OceanTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF063D38),
    // deep amber keeps yellow readable on white surfaces
    tertiary = SkyplusYellowDeep,
    onTertiary = Color.White,
    tertiaryContainer = SkyplusYellowContainerLight,
    onTertiaryContainer = Color(0xFF423103),
    background = OceanMistBackground,
    onBackground = OceanTextDark,
    surface = OceanMistSurface,
    onSurface = OceanTextDark,
    surfaceVariant = OceanMistCardSubtle,
    onSurfaceVariant = OceanTextMutedLight,
    surfaceContainerLow = OceanMistSurface,
    surfaceContainer = OceanMistCardSubtle,
    surfaceContainerHigh = Color(0xFFDCEBF7),
    outline = OceanMistBorder,
    outlineVariant = Color(0xFFDCE8F3),
    error = Color(0xFFDC2626),
    onError = Color.White,
  )

object OceanTheme {
  val colors: OceanExtendedColors
    @Composable @ReadOnlyComposable get() = LocalOceanColors.current

  val chartPalette: SkyplusChartPalette
    @Composable @ReadOnlyComposable get() = LocalSkyplusChartPalette.current
}

@Composable
fun SkyplusOceanTheme(
  themePreference: ThemePreference = ThemePreference.DARK,
  content: @Composable () -> Unit,
) {
  val useDarkTheme =
    when (themePreference) {
      ThemePreference.DARK -> true
      ThemePreference.LIGHT -> false
      ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

  val colorScheme = if (useDarkTheme) DarkOceanColorScheme else LightOceanColorScheme
  val extendedColors = if (useDarkTheme) DarkOceanExtendedColors else LightOceanExtendedColors
  val chartPalette = if (useDarkTheme) DarkSkyplusChartPalette else LightSkyplusChartPalette

  CompositionLocalProvider(
    LocalOceanColors provides extendedColors,
    LocalSkyplusChartPalette provides chartPalette,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = OceanTypography,
      shapes = OceanShapes,
      content = content,
    )
  }
}
