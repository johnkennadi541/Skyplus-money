package com.example.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================================
// SKYPLUS MONEY — balanced Google-inspired palette.
// Blue is primary; red, yellow and green are supporting/semantic accents.
// This is an independent SKYPLUS palette, not a reproduction of Google's brand identity.
// ============================================================================================

// Brand & structural navy
val DeepOceanNavy = Color(0xFF061320)
val DeepOceanSurface = Color(0xFF091B2E)
val SubtleOceanBlue = Color(0xFF0E2640)
val SubtleOceanBlueElevated = Color(0xFF143252)
val OceanBorderBlue = Color(0xFF1E446B)

val DeepOceanBlue = Color(0xFF0B4F7C)
val OceanPrimaryLightMode = Color(0xFF4285F4) // Google-inspired blue
val OceanPrimaryDarkMode = Color(0xFF8AB4F8) // accessible blue on dark surfaces

val OceanTeal = Color(0xFF0D9488)
val OceanTealBright = Color(0xFF2DD4BF)

// Accent — yellow. No gold anywhere in the system.
val SkyplusYellow = Color(0xFFFACC15)
val SkyplusYellowBright = Color(0xFFFDE047)
val SkyplusYellowDeep = Color(0xFFA16207)
val SkyplusYellowContainerLight = Color(0xFFFEF3C7)
val SkyplusYellowContainerDark = Color(0xFF3E300B)
val SkyplusOnYellow = Color(0xFF231A00)

/** @deprecated Gold is not part of the SKYPLUS identity. Use [SkyplusYellow]. */
@Deprecated("Gold is not part of the SKYPLUS identity. Use SkyplusYellow.")
val OceanGold = SkyplusYellow

/** @deprecated Gold is not part of the SKYPLUS identity. Use [SkyplusYellowBright]. */
@Deprecated("Gold is not part of the SKYPLUS identity. Use SkyplusYellowBright.")
val OceanGoldBright = SkyplusYellowBright

/** @deprecated Gold is not part of the SKYPLUS identity. Use [SkyplusYellowDeep]. */
@Deprecated("Gold is not part of the SKYPLUS identity. Use SkyplusYellowDeep.")
val OceanGoldDeep = SkyplusYellowDeep

// Light theme surfaces
val OceanMistBackground = Color(0xFFF3F8FC)
val OceanMistSurface = Color(0xFFFFFFFF)
val OceanMistCardSubtle = Color(0xFFE9F2F9)
val OceanMistBorder = Color(0xFFCBDCEB)
val OceanTextDark = Color(0xFF091E32)
val OceanTextMutedLight = Color(0xFF47637E)

// Dark theme text
val OceanTextLight = Color(0xFFF1F6FB)
val OceanTextMutedDark = Color(0xFF9AB4CC)

// Semantic status colours
val OceanPositive = Color(0xFF10B981)
val OceanPositiveContainerDark = Color(0xFF0B352A)
val OceanPositiveContainerLight = Color(0xFFD1FAE5)

val OceanWarning = Color(0xFFF59E0B)
val OceanWarningContainerDark = Color(0xFF3A2809)
val OceanWarningContainerLight = Color(0xFFFEF3C7)

val OceanNegative = Color(0xFFEF4444)
val OceanNegativeContainerDark = Color(0xFF3D1419)
val OceanNegativeContainerLight = Color(0xFFFEE2E2)

val OceanInfo = Color(0xFF38BDF8)
val OceanInfoContainerDark = Color(0xFF0C2F45)
val OceanInfoContainerLight = Color(0xFFE0F2FE)

@Immutable
data class OceanExtendedColors(
  val cardSubtle: Color,
  val cardBorder: Color,
  /** Yellow accent. Replaces the old `gold`. */
  val accent: Color,
  val onAccent: Color,
  val accentContainer: Color,
  val onAccentContainer: Color,
  val positive: Color,
  val positiveContainer: Color,
  val warning: Color,
  val warningContainer: Color,
  val negative: Color,
  val negativeContainer: Color,
  val info: Color,
  val infoContainer: Color,
  val heroGradientStart: Color,
  val heroGradientEnd: Color,
  val isDark: Boolean,
) {
  /** @deprecated Use [accent]. Kept so screens written before Stage 1 keep compiling. */
  @Deprecated("Use accent")
  val gold: Color
    get() = accent

  /** @deprecated Use [onAccent]. Kept so screens written before Stage 1 keep compiling. */
  @Deprecated("Use onAccent")
  val onGold: Color
    get() = onAccent
}

/** Ordered palette for multi-series charts. Readable in both themes, no gold. */
@Immutable
data class SkyplusChartPalette(
  val series: List<Color>,
  val gridLine: Color,
  val axisLabel: Color,
  val positive: Color,
  val negative: Color,
)

val DarkSkyplusChartPalette =
  SkyplusChartPalette(
    series =
      listOf(
        Color(0xFF8AB4F8),
        Color(0xFFF28B82),
        SkyplusYellowBright,
        Color(0xFF81C995),
        Color(0xFFC58AF9),
      ),
    gridLine = Color(0xFF1E446B).copy(alpha = 0.55f),
    axisLabel = OceanTextMutedDark,
    positive = OceanPositive,
    negative = OceanNegative,
  )

val LightSkyplusChartPalette =
  SkyplusChartPalette(
    series =
      listOf(
        Color(0xFF4285F4),
        Color(0xFFEA4335),
        Color(0xFFF9AB00),
        Color(0xFF34A853),
        Color(0xFF7E57C2),
      ),
    gridLine = Color(0xFFCBDCEB),
    axisLabel = OceanTextMutedLight,
    positive = Color(0xFF059669),
    negative = Color(0xFFDC2626),
  )

val DarkOceanExtendedColors =
  OceanExtendedColors(
    cardSubtle = SubtleOceanBlue,
    cardBorder = OceanBorderBlue,
    accent = SkyplusYellowBright,
    onAccent = SkyplusOnYellow,
    accentContainer = SkyplusYellowContainerDark,
    onAccentContainer = SkyplusYellowBright,
    positive = OceanPositive,
    positiveContainer = OceanPositiveContainerDark,
    warning = OceanWarning,
    warningContainer = OceanWarningContainerDark,
    negative = OceanNegative,
    negativeContainer = OceanNegativeContainerDark,
    info = OceanInfo,
    infoContainer = OceanInfoContainerDark,
    heroGradientStart = Color(0xFF0A2A47),
    heroGradientEnd = Color(0xFF0B4F6C),
    isDark = true,
  )

val LightOceanExtendedColors =
  OceanExtendedColors(
    cardSubtle = OceanMistCardSubtle,
    cardBorder = OceanMistBorder,
    accent = SkyplusYellowDeep,
    onAccent = Color.White,
    accentContainer = SkyplusYellowContainerLight,
    onAccentContainer = Color(0xFF423103),
    positive = Color(0xFF059669),
    positiveContainer = OceanPositiveContainerLight,
    warning = Color(0xFFD97706),
    warningContainer = OceanWarningContainerLight,
    negative = Color(0xFFDC2626),
    negativeContainer = OceanNegativeContainerLight,
    info = Color(0xFF0284C7),
    infoContainer = OceanInfoContainerLight,
    heroGradientStart = Color(0xFF083254),
    heroGradientEnd = Color(0xFF0E647B),
    isDark = false,
  )

val LocalOceanColors = staticCompositionLocalOf { DarkOceanExtendedColors }

val LocalSkyplusChartPalette = staticCompositionLocalOf { DarkSkyplusChartPalette }
