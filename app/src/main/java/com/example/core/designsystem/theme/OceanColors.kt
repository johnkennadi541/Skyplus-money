package com.example.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand & Semantic Palette — Deep Ocean Blue + Teal + Gold
val DeepOceanNavy = Color(0xFF061320)
val DeepOceanSurface = Color(0xFF091B2E)
val SubtleOceanBlue = Color(0xFF0E2640)
val SubtleOceanBlueElevated = Color(0xFF143252)
val OceanBorderBlue = Color(0xFF1E446B)

val DeepOceanBlue = Color(0xFF0B4F7C)
val OceanPrimaryLightMode = Color(0xFF0B4A74)
val OceanPrimaryDarkMode = Color(0xFF4CA8E0)

val OceanTeal = Color(0xFF0D9488)
val OceanTealBright = Color(0xFF2DD4BF)
val OceanGold = Color(0xFFD4AF37)
val OceanGoldBright = Color(0xFFF5C451)
val OceanGoldDeep = Color(0xFF9A7B16)

// Light Theme Surfaces
val OceanMistBackground = Color(0xFFF3F8FC)
val OceanMistSurface = Color(0xFFFFFFFF)
val OceanMistCardSubtle = Color(0xFFE9F2F9)
val OceanMistBorder = Color(0xFFCBDCEB)
val OceanTextDark = Color(0xFF091E32)
val OceanTextMutedLight = Color(0xFF47637E)

// Dark Theme Text
val OceanTextLight = Color(0xFFF1F6FB)
val OceanTextMutedDark = Color(0xFF9AB4CC)

// Semantic Status Colors
val OceanPositive = Color(0xFF10B981)
val OceanPositiveContainerDark = Color(0xFF0B352A)
val OceanPositiveContainerLight = Color(0xFFD1FAE5)

val OceanWarning = Color(0xFFF59E0B)
val OceanWarningContainerDark = Color(0xFF3A2809)
val OceanWarningContainerLight = Color(0xFFFEF3C7)

val OceanNegative = Color(0xFFEF4444)
val OceanNegativeContainerDark = Color(0xFF3D1419)
val OceanNegativeContainerLight = Color(0xFFFEE2E2)

@Immutable
data class OceanExtendedColors(
  val cardSubtle: Color,
  val cardBorder: Color,
  val gold: Color,
  val onGold: Color,
  val positive: Color,
  val positiveContainer: Color,
  val warning: Color,
  val warningContainer: Color,
  val negative: Color,
  val negativeContainer: Color,
  val heroGradientStart: Color,
  val heroGradientEnd: Color,
  val isDark: Boolean,
)

val DarkOceanExtendedColors =
  OceanExtendedColors(
    cardSubtle = SubtleOceanBlue,
    cardBorder = OceanBorderBlue,
    gold = OceanGoldBright,
    onGold = DeepOceanNavy,
    positive = OceanPositive,
    positiveContainer = OceanPositiveContainerDark,
    warning = OceanWarning,
    warningContainer = OceanWarningContainerDark,
    negative = OceanNegative,
    negativeContainer = OceanNegativeContainerDark,
    heroGradientStart = Color(0xFF0A2A47),
    heroGradientEnd = Color(0xFF0B4F6C),
    isDark = true,
  )

val LightOceanExtendedColors =
  OceanExtendedColors(
    cardSubtle = OceanMistCardSubtle,
    cardBorder = OceanMistBorder,
    gold = OceanGoldDeep,
    onGold = Color.White,
    positive = Color(0xFF059669),
    positiveContainer = OceanPositiveContainerLight,
    warning = Color(0xFFD97706),
    warningContainer = OceanWarningContainerLight,
    negative = Color(0xFFDC2626),
    negativeContainer = OceanNegativeContainerLight,
    heroGradientStart = Color(0xFF083254),
    heroGradientEnd = Color(0xFF0E647B),
    isDark = false,
  )

val LocalOceanColors = staticCompositionLocalOf { DarkOceanExtendedColors }
