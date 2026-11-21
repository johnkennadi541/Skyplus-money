package com.example

import androidx.compose.ui.graphics.Color
import com.example.core.designsystem.theme.DarkOceanExtendedColors
import com.example.core.designsystem.theme.DeepOceanNavy
import com.example.core.designsystem.theme.DeepOceanSurface
import com.example.core.designsystem.theme.LightOceanExtendedColors
import com.example.core.designsystem.theme.OceanMistBackground
import com.example.core.designsystem.theme.OceanMistBorder
import com.example.core.designsystem.theme.OceanMistSurface
import com.example.core.designsystem.theme.OceanPrimaryLightMode
import com.example.core.designsystem.theme.OceanTeal
import com.example.core.designsystem.theme.OceanTealBright
import com.example.core.designsystem.theme.OceanTextDark
import com.example.core.designsystem.theme.OceanTextLight
import com.example.core.designsystem.theme.OceanTextMutedDark
import com.example.core.designsystem.theme.OceanTextMutedLight
import com.example.core.designsystem.theme.SkyplusDarkBackground
import com.example.core.designsystem.theme.SkyplusDarkSurface
import com.example.core.designsystem.theme.SkyplusExpense
import com.example.core.designsystem.theme.SkyplusExpenseAccent
import com.example.core.designsystem.theme.SkyplusGold
import com.example.core.designsystem.theme.SkyplusGoldDeep
import com.example.core.designsystem.theme.SkyplusIncome
import com.example.core.designsystem.theme.SkyplusIncomeAccent
import com.example.core.designsystem.theme.SkyplusIndigo
import com.example.core.designsystem.theme.SkyplusLightBackground
import com.example.core.designsystem.theme.SkyplusLightBorder
import com.example.core.designsystem.theme.SkyplusLightSurface
import com.example.core.designsystem.theme.SkyplusLightTextPrimary
import com.example.core.designsystem.theme.SkyplusLightTextSecondary
import com.example.core.designsystem.theme.SkyplusTeal
import com.example.core.designsystem.theme.SkyplusTealBright
import com.example.core.designsystem.theme.SkyplusYellow
import com.example.core.designsystem.theme.SkyplusYellowDeep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 1 guard: the design system must carry exactly the approved SKYPLUS MONEY
 * palette. If someone drifts a hex value, this test fails instead of the UI
 * silently changing colour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OceanDesignTokensTest {

  @Test
  fun lightPaletteMatchesApprovedTokens() {
    assertEquals(Color(0xFF1E3A8A), OceanPrimaryLightMode)
    assertEquals(Color(0xFF0D9488), OceanTeal)
    assertEquals(Color(0xFFFACC15), SkyplusGold)
    assertEquals(Color(0xFFF8FAFC), OceanMistBackground)
    assertEquals(Color(0xFFFFFFFF), OceanMistSurface)
    assertEquals(Color(0xFFE2E8F0), OceanMistBorder)
    assertEquals(Color(0xFF0F172A), OceanTextDark)
    assertEquals(Color(0xFF64748B), OceanTextMutedLight)
  }

  @Test
  fun darkPaletteMatchesApprovedTokens() {
    assertEquals(Color(0xFF0B1120), DeepOceanNavy)
    assertEquals(Color(0xFF1E293B), DeepOceanSurface)
    assertEquals(Color(0xFFF1F5F9), OceanTextLight)
    assertEquals(Color(0xFF94A3B8), OceanTextMutedDark)
    assertEquals(Color(0xFF2DD4BF), OceanTealBright)
    assertEquals(Color(0xFFFACC15), SkyplusGold)
  }

  @Test
  fun approvedValuesAreTheSingleSourceOfTruth() {
    assertEquals(SkyplusIndigo, OceanPrimaryLightMode)
    assertEquals(SkyplusTeal, OceanTeal)
    assertEquals(SkyplusTealBright, OceanTealBright)
    assertEquals(SkyplusLightBackground, OceanMistBackground)
    assertEquals(SkyplusLightSurface, OceanMistSurface)
    assertEquals(SkyplusLightBorder, OceanMistBorder)
    assertEquals(SkyplusLightTextPrimary, OceanTextDark)
    assertEquals(SkyplusLightTextSecondary, OceanTextMutedLight)
    assertEquals(SkyplusDarkBackground, DeepOceanNavy)
    assertEquals(SkyplusDarkSurface, DeepOceanSurface)
  }

  @Test
  fun extendedColoursFollowTheActiveMode() {
    assertTrue(DarkOceanExtendedColors.isDark)
    assertFalse(LightOceanExtendedColors.isDark)

    // Dark mode can use the bright accent; light mode must use the deep one, because
    // #FACC15 on white is 1.53:1 and would fail contrast for text and icons.
    assertEquals(SkyplusYellow, DarkOceanExtendedColors.gold)
    assertEquals(SkyplusYellowDeep, LightOceanExtendedColors.gold)
    assertEquals(Color(0xFFFFFFFF), LightOceanExtendedColors.cardSubtle)
    assertEquals(SkyplusDarkSurface, DarkOceanExtendedColors.cardSubtle)
  }

  @Test
  fun theApprovedYellowAndItsDeepVariantAreExact() {
    assertEquals(Color(0xFFFACC15), SkyplusYellow)
    assertEquals(Color(0xFF854D0E), SkyplusYellowDeep)
    assertEquals(SkyplusYellow, SkyplusGold)
    assertEquals(SkyplusYellowDeep, SkyplusGoldDeep)
  }

  @Test
  fun incomeIsAlwaysGreenAndExpenseIsAlwaysRed() {
    assertEquals(Color(0xFF16A34A), SkyplusIncome)
    assertEquals(Color(0xFF22C55E), SkyplusIncomeAccent)
    assertEquals(Color(0xFFDC2626), SkyplusExpense)
    assertEquals(Color(0xFFEF4444), SkyplusExpenseAccent)

    // The extended colours every screen reads must follow the same semantics, in both
    // modes, so income can never render red or expenses green.
    assertEquals(SkyplusIncome, DarkOceanExtendedColors.positive)
    assertEquals(SkyplusIncome, LightOceanExtendedColors.positive)
    assertEquals(SkyplusExpense, DarkOceanExtendedColors.negative)
    assertEquals(SkyplusExpense, LightOceanExtendedColors.negative)
  }
}
