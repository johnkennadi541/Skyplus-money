package com.example.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.example.domain.model.ThemePreference

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

  val colorScheme = if (useDarkTheme) SkyplusDarkColorScheme else SkyplusLightColorScheme
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
