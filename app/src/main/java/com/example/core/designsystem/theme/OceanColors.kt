package com.example.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * SKYPLUS MONEY — approved design tokens.
 *
 * The approved palette is indigo + teal + yellow on a slate base. The old gold
 * accent is gone: yellow #FACC15 is a SMALL accent only and is never a dominant
 * background, a primary button, or light-mode text.
 *
 * Light: primary #1E3A8A, teal #0D9488, accent #FACC15 (deep #854D0E for text),
 *        background #F8FAFC, cards #FFFFFF, border #E2E8F0, primary text #0F172A,
 *        secondary text #64748B.
 * Dark:  background #0B1120, surface #1E293B, primary text #F1F5F9,
 *        secondary text #94A3B8, teal #2DD4BF, accent #FACC15.
 *
 * Contrast, measured: #FACC15 on #FFFFFF is 1.53:1 and must never carry text;
 * on #0B1120 it is 12.30:1 and on #1E293B it is 9.55:1, both safe. #854D0E on
 * #FFFFFF is 6.85:1, which is why light-mode accent text uses the deep value.
 *
 * The semantic names below (DeepOceanNavy, OceanMistBackground, …) are historical:
 * they are used by every existing screen, so they are kept as the public API and
 * now carry the approved values. The `Skyplus*` values are the single source of
 * truth for the approved hex codes — change a colour here, nowhere else.
 */

// ---- Approved palette (single source of truth) --------------------------------

/** Approved light primary. */
val SkyplusIndigo = Color(0xFF1E3A8A)

/** Approved dark-mode primary: a lighter indigo that reads on #0B1120. */
val SkyplusIndigoLight = Color(0xFF818CF8)

val SkyplusTeal = Color(0xFF0D9488)
val SkyplusTealBright = Color(0xFF2DD4BF)

/**
 * Approved small accent. Badges, chart points and one highlight per screen only.
 * Never a dominant background, never a primary button, never light-mode text.
 */
val SkyplusYellow = Color(0xFFFACC15)

/** Deep yellow for accent text and icons on light surfaces (6.85:1 on #FFFFFF). */
val SkyplusYellowDeep = Color(0xFF854D0E)

/**
 * Historical accent name. Kept so every existing call site keeps compiling, and
 * now carries the approved yellow. Prefer [SkyplusYellow] in new code.
 */
val SkyplusGold = SkyplusYellow

/** Historical deep-accent name; now the accessible deep yellow. */
val SkyplusGoldDeep = SkyplusYellowDeep

val SkyplusLightBackground = Color(0xFFF8FAFC)
val SkyplusLightSurface = Color(0xFFFFFFFF)
val SkyplusLightBorder = Color(0xFFE2E8F0)
val SkyplusLightTextPrimary = Color(0xFF0F172A)
val SkyplusLightTextSecondary = Color(0xFF64748B)

val SkyplusDarkBackground = Color(0xFF0B1120)
val SkyplusDarkSurface = Color(0xFF1E293B)
val SkyplusDarkBorder = Color(0xFF334155)
val SkyplusDarkTextPrimary = Color(0xFFF1F5F9)
val SkyplusDarkTextSecondary = Color(0xFF94A3B8)

// ---- Semantic status colours --------------------------------------------------
//
// Income is ALWAYS green and expenses are ALWAYS red, everywhere: cards, charts,
// legends, indicators and summaries. These two values are never swapped.

/** Income, light and dark. Approved #16A34A. */
val SkyplusIncome = Color(0xFF16A34A)

/** Income accent, for secondary marks and chart emphasis. Approved #22C55E. */
val SkyplusIncomeAccent = Color(0xFF22C55E)

/** Expense, light and dark. Approved #DC2626. */
val SkyplusExpense = Color(0xFFDC2626)

/** Expense accent. Approved #EF4444. */
val SkyplusExpenseAccent = Color(0xFFEF4444)

val OceanPositive = SkyplusIncome
val OceanPositiveContainerDark = Color(0xFF0B352A)
val OceanPositiveContainerLight = Color(0xFFDCFCE7)

val OceanWarning = Color(0xFFF59E0B)
val OceanWarningContainerDark = Color(0xFF3A2809)
val OceanWarningContainerLight = Color(0xFFFEF3C7)

val OceanNegative = SkyplusExpense
val OceanNegativeContainerDark = Color(0xFF3D1419)
val OceanNegativeContainerLight = Color(0xFFFEE2E2)

// ---- Dark surfaces ------------------------------------------------------------

/** Dark background — approved #0B1120. */
val DeepOceanNavy = SkyplusDarkBackground

/** Dark surface — approved #1E293B. */
val DeepOceanSurface = SkyplusDarkSurface

/** Slightly raised dark surface, between background and surface. */
val SubtleOceanBlue = Color(0xFF17223A)

/** Highest dark surface, used for elevated containers. */
val SubtleOceanBlueElevated = Color(0xFF243044)

/** Dark outline / border — approved slate border. */
val OceanBorderBlue = SkyplusDarkBorder

/** Legacy accent blue, retained for callers that still reference it. */
val DeepOceanBlue = SkyplusIndigo

// ---- Primary ---------------------------------------------------------------

/** Primary in light mode — approved #1E3A8A. */
val OceanPrimaryLightMode = SkyplusIndigo

/** Primary in dark mode — light indigo for contrast on #0B1120. */
val OceanPrimaryDarkMode = SkyplusIndigoLight

// ---- Accents ---------------------------------------------------------------

val OceanTeal = SkyplusTeal
val OceanTealBright = SkyplusTealBright
val OceanGold = SkyplusGold
val OceanGoldBright = SkyplusGold
val OceanGoldDeep = SkyplusGoldDeep

// ---- Light surfaces --------------------------------------------------------

/** Light background — approved #F8FAFC. */
val OceanMistBackground = SkyplusLightBackground

/** Light surface — approved #FFFFFF. */
val OceanMistSurface = SkyplusLightSurface

/** Light surface variant, one step off white for inset areas. */
val OceanMistCardSubtle = Color(0xFFF1F5F9)

/** Light border — approved #E2E8F0. */
val OceanMistBorder = SkyplusLightBorder

/** Light primary text — approved #0F172A. */
val OceanTextDark = SkyplusLightTextPrimary

/** Light secondary text — approved #64748B. */
val OceanTextMutedLight = SkyplusLightTextSecondary

// ---- Dark text -------------------------------------------------------------

/** Dark primary text — approved #F1F5F9. */
val OceanTextLight = SkyplusDarkTextPrimary

/** Dark secondary text — approved #94A3B8. */
val OceanTextMutedDark = SkyplusDarkTextSecondary

/**
 * Extra semantic colours exposed through [OceanTheme.colors].
 * The field set is unchanged from the previous design system so that every
 * existing screen keeps compiling without modification.
 */
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
    cardSubtle = DeepOceanSurface,
    cardBorder = SkyplusDarkBorder,
    gold = SkyplusYellow,
    onGold = SkyplusDarkBackground,
    positive = SkyplusIncome,
    positiveContainer = OceanPositiveContainerDark,
    warning = OceanWarning,
    warningContainer = OceanWarningContainerDark,
    negative = OceanNegative,
    negativeContainer = OceanNegativeContainerDark,
    heroGradientStart = Color(0xFF16224A),
    heroGradientEnd = Color(0xFF1E3A8A),
    isDark = true,
  )

val LightOceanExtendedColors =
  OceanExtendedColors(
    cardSubtle = SkyplusLightSurface,
    cardBorder = SkyplusLightBorder,
    // Light mode uses the deep accent so text and icons stay readable on white
    // (6.85:1). The bright #FACC15 fails contrast on white and is never used here.
    gold = SkyplusYellowDeep,
    onGold = Color.White,
    positive = SkyplusIncome,
    positiveContainer = OceanPositiveContainerLight,
    warning = Color(0xFFD97706),
    warningContainer = OceanWarningContainerLight,
    negative = Color(0xFFDC2626),
    negativeContainer = OceanNegativeContainerLight,
    heroGradientStart = SkyplusIndigo,
    heroGradientEnd = SkyplusTeal,
    isDark = false,
  )

val LocalOceanColors = staticCompositionLocalOf { DarkOceanExtendedColors }
