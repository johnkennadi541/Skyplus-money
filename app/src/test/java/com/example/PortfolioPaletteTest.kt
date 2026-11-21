package com.example

import androidx.compose.ui.graphics.Color
import com.example.core.designsystem.theme.PortfolioPalette
import com.example.core.designsystem.theme.SkyplusExpense
import com.example.core.designsystem.theme.SkyplusIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the chart palette contract:
 * - the same key always maps to the same colour, across runs and restarts;
 * - two keys on one chart never share a colour;
 * - the palette never reuses the income/expense semantics.
 */
class PortfolioPaletteTest {

  @Test
  fun theApprovedSeriesColoursAreExact() {
    assertEquals(6, PortfolioPalette.light.size)
    assertEquals(6, PortfolioPalette.dark.size)
    assertEquals(
      listOf(
        Color(0xFFEA580C),
        Color(0xFFD97706),
        Color(0xFF059669),
        Color(0xFF2563EB),
        Color(0xFF0891B2),
        Color(0xFF7C3AED),
      ),
      PortfolioPalette.light,
    )
  }

  @Test
  fun theSameKeyAlwaysGetsTheSameColour() {
    val keys = listOf("STOCKS", "MUTUAL_FUNDS", "ETFS", "GOLD", "FIXED_DEPOSITS", "OTHER")
    keys.forEach { key ->
      val first = PortfolioPalette.colorFor(key, isDark = false)
      val second = PortfolioPalette.colorFor(key, isDark = false)
      val third = PortfolioPalette.colorFor(key, isDark = false)
      assertEquals(first, second)
      assertEquals(second, third)
    }
  }

  @Test
  fun theHashIsStableAcrossProcesses() {
    // Case and surrounding spaces must not change a key's colour.
    val stocks = PortfolioPalette.stableHash("STOCKS")
    assertEquals(stocks, PortfolioPalette.stableHash("stocks"))
    assertEquals(stocks, PortfolioPalette.stableHash("  STOCKS  "))
    assertNotEquals(stocks, PortfolioPalette.stableHash("GOLD"))
  }

  @Test
  fun oneChartNeverRepeatsAColour() {
    val keys = listOf("STOCKS", "MUTUAL_FUNDS", "ETFS", "GOLD", "FIXED_DEPOSITS", "OTHER")
    val assigned = PortfolioPalette.assign(keys, isDark = false)
    assertEquals(keys.size, assigned.size)
    assertEquals(keys.size, assigned.values.toSet().size)
  }

  @Test
  fun assignmentDoesNotDependOnInputOrder() {
    val a = PortfolioPalette.assign(listOf("GOLD", "STOCKS", "ETFS"), isDark = false)
    val b = PortfolioPalette.assign(listOf("ETFS", "GOLD", "STOCKS"), isDark = false)
    assertEquals(a, b)
  }

  @Test
  fun darkSeriesDiffersFromLightSeries() {
    val light = PortfolioPalette.seriesFor(isDark = false)
    val dark = PortfolioPalette.seriesFor(isDark = true)
    assertEquals(light.size, dark.size)
    light.forEachIndexed { index, colour -> assertNotEquals(colour, dark[index]) }
  }

  @Test
  fun thePaletteNeverReusesTheIncomeOrExpenseColour() {
    // Green and red mean money in and money out. A portfolio series must not borrow
    // either meaning, in either theme.
    (PortfolioPalette.light + PortfolioPalette.dark).forEach { colour ->
      assertNotEquals(SkyplusIncome, colour)
      assertNotEquals(SkyplusExpense, colour)
    }
  }

  @Test
  fun unknownKeysStillResolveToARealColour() {
    val colour = PortfolioPalette.colorFor("a category that does not exist yet", isDark = true)
    assertTrue(PortfolioPalette.dark.contains(colour))
    assertFalse(PortfolioPalette.light.contains(colour))
  }
}
