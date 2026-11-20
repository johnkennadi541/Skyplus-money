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
import com.example.core.designsystem.theme.SkyplusGold
import com.example.core.designsystem.theme.SkyplusIndigo
import com.example.core.designsystem.theme.SkyplusLightBackground
import com.example.core.designsystem.theme.SkyplusLightBorder
import com.example.core.designsystem.theme.SkyplusLightSurface
import com.example.core.designsystem.theme.SkyplusLightTextPrimary
import com.example.core.designsystem.theme.SkyplusLightTextSecondary
import com.example.core.designsystem.theme.SkyplusTeal
import com.example.core.designsystem.theme.SkyplusTealBright
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
    assertEquals(Color(0xFFD6B45A), SkyplusGold)
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
    assertEquals(Color(0xFFD6B45A), SkyplusGold)
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

    assertEquals(SkyplusGold, DarkOceanExtendedColors.gold)
    assertEquals(SkyplusGold, LightOceanExtendedColors.gold)
    assertEquals(Color(0xFFFFFFFF), LightOceanExtendedColors.cardSubtle)
    assertEquals(SkyplusDarkSurface, DarkOceanExtendedColors.cardSubtle)
  }
}
