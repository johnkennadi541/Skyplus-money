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
    onPrimary = DeepOceanNavy,
    primaryContainer = SkyplusIndigo,
    onPrimaryContainer = OceanTextLight,
    secondary = OceanTealBright,
    onSecondary = DeepOceanNavy,
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = SkyplusYellow,
    onTertiary = SkyplusDarkBackground,
    tertiaryContainer = Color(0xFF3E300B),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = DeepOceanNavy,
    onBackground = OceanTextLight,
    surface = DeepOceanSurface,
    onSurface = OceanTextLight,
    surfaceVariant = SubtleOceanBlue,
    onSurfaceVariant = OceanTextMutedDark,
    surfaceContainerLow = DeepOceanSurface,
    surfaceContainer = SubtleOceanBlue,
    surfaceContainerHigh = SubtleOceanBlueElevated,
    outline = SkyplusDarkBorder,
    outlineVariant = Color(0xFF263449),
    error = OceanNegative,
    onError = Color.White,
  )

private val LightOceanColorScheme =
  lightColorScheme(
    primary = OceanPrimaryLightMode,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF16265C),
    secondary = OceanTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF063D38),
    // Light tertiary text and icons use the deep accent (6.85:1 on white); the
    // bright #FACC15 would fail contrast here. The container stays a soft yellow.
    tertiary = SkyplusYellowDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF9C3),
    onTertiaryContainer = Color(0xFF422006),
    background = OceanMistBackground,
    onBackground = OceanTextDark,
    surface = OceanMistSurface,
    onSurface = OceanTextDark,
    surfaceVariant = OceanMistCardSubtle,
    onSurfaceVariant = OceanTextMutedLight,
    surfaceContainerLow = OceanMistSurface,
    surfaceContainer = OceanMistCardSubtle,
    surfaceContainerHigh = Color(0xFFE9EEF5),
    outline = SkyplusLightBorder,
    outlineVariant = Color(0xFFEEF2F7),
    error = Color(0xFFDC2626),
    onError = Color.White,
  )

object OceanTheme {
  val colors: OceanExtendedColors
    @Composable @ReadOnlyComposable get() = LocalOceanColors.current
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

  CompositionLocalProvider(LocalOceanColors provides extendedColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = OceanTypography,
      shapes = OceanShapes,
      content = content,
    )
  }
}
