package com.example.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * SKYPLUS MONEY brand palette.
 *
 * Google-inspired four-colour accents are used semantically and sparingly.
 * Neutral surfaces keep financial information readable and reduce visual noise.
 * These are original app tokens, not a copy of Google's product UI or branding.
 */
object SkyplusBrandColors {
  val blue = Color(0xFF4285F4)
  val red = Color(0xFFEA4335)
  val yellow = Color(0xFFFBBC05)
  val green = Color(0xFF34A853)

  val blueLight = Color(0xFF8AB4F8)
  val redLight = Color(0xFFF28B82)
  val yellowLight = Color(0xFFFDD663)
  val greenLight = Color(0xFF81C995)

  val lightBackground = Color(0xFFF8F9FA)
  val lightSurface = Color(0xFFFFFFFF)
  val lightSurfaceLow = Color(0xFFF8F9FA)
  val lightSurfaceContainer = Color(0xFFF1F3F4)
  val lightSurfaceHigh = Color(0xFFE8EAED)
  val lightText = Color(0xFF202124)
  val lightTextMuted = Color(0xFF5F6368)
  val lightOutline = Color(0xFFDADCE0)

  val darkBackground = Color(0xFF131314)
  val darkSurface = Color(0xFF1E1F20)
  val darkSurfaceLow = Color(0xFF17181A)
  val darkSurfaceContainer = Color(0xFF282A2C)
  val darkSurfaceHigh = Color(0xFF303134)
  val darkText = Color(0xFFE8EAED)
  val darkTextMuted = Color(0xFFBDC1C6)
  val darkOutline = Color(0xFF5F6368)

  val lightSuccess = Color(0xFF137333)
  val lightWarning = Color(0xFF8D5B00)
  val lightError = Color(0xFFB3261E)
  val darkSuccess = Color(0xFF81C995)
  val darkWarning = Color(0xFFFDD663)
  val darkError = Color(0xFFF28B82)
}

val SkyplusLightColorScheme: ColorScheme =
  lightColorScheme(
    primary = SkyplusBrandColors.blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = SkyplusBrandColors.green,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4EED0),
    onSecondaryContainer = Color(0xFF072711),
    tertiary = Color(0xFF9A6700),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF0C2),
    onTertiaryContainer = Color(0xFF2B1B00),
    error = SkyplusBrandColors.lightError,
    onError = Color.White,
    background = SkyplusBrandColors.lightBackground,
    onBackground = SkyplusBrandColors.lightText,
    surface = SkyplusBrandColors.lightSurface,
    onSurface = SkyplusBrandColors.lightText,
    surfaceVariant = SkyplusBrandColors.lightSurfaceContainer,
    onSurfaceVariant = SkyplusBrandColors.lightTextMuted,
    surfaceContainerLow = SkyplusBrandColors.lightSurfaceLow,
    surfaceContainer = SkyplusBrandColors.lightSurfaceContainer,
    surfaceContainerHigh = SkyplusBrandColors.lightSurfaceHigh,
    outline = SkyplusBrandColors.lightOutline,
    outlineVariant = Color(0xFFE8EAED),
  )

val SkyplusDarkColorScheme: ColorScheme =
  darkColorScheme(
    primary = SkyplusBrandColors.blueLight,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF174EA6),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = SkyplusBrandColors.greenLight,
    onSecondary = Color(0xFF073B1B),
    secondaryContainer = Color(0xFF0F5223),
    onSecondaryContainer = Color(0xFFC4EED0),
    tertiary = SkyplusBrandColors.yellowLight,
    onTertiary = Color(0xFF2B1B00),
    tertiaryContainer = Color(0xFF594000),
    onTertiaryContainer = Color(0xFFFFE08A),
    error = SkyplusBrandColors.redLight,
    onError = Color(0xFF601410),
    background = SkyplusBrandColors.darkBackground,
    onBackground = SkyplusBrandColors.darkText,
    surface = SkyplusBrandColors.darkSurface,
    onSurface = SkyplusBrandColors.darkText,
    surfaceVariant = SkyplusBrandColors.darkSurfaceContainer,
    onSurfaceVariant = SkyplusBrandColors.darkTextMuted,
    surfaceContainerLow = SkyplusBrandColors.darkSurfaceLow,
    surfaceContainer = SkyplusBrandColors.darkSurfaceContainer,
    surfaceContainerHigh = SkyplusBrandColors.darkSurfaceHigh,
    outline = SkyplusBrandColors.darkOutline,
    outlineVariant = Color(0xFF444746),
  )
