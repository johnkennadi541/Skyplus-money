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
  fun everyChartSizeUpToTwelveKeepsColoursUnique() {
    listOf(1, 6, 7, 8, 10, 12).forEach { size ->
      val keys = (1..size).map { "KEY-$it" }
      listOf(false, true).forEach { isDark ->
        val assigned = PortfolioPalette.assign(keys, isDark)
        assertEquals("size $size dark=$isDark", size, assigned.size)
        assertEquals("size $size dark=$isDark", size, assigned.values.toSet().size)
      }
    }
  }

  @Test
  fun overflowKeepsWorkingBeyondTheSixApprovedColours() {
    // 12 keys force the six overflow hues; 20 force the first blended tier.
    listOf(13, 20, 24).forEach { size ->
      val keys = (1..size).map { "SERIES-$it" }
      val assigned = PortfolioPalette.assign(keys, isDark = false)
      assertEquals(size, assigned.values.toSet().size)
    }
    val many = PortfolioPalette.assign((1..PortfolioPalette.CAPACITY).map { "K$it" }, isDark = true)
    assertEquals(PortfolioPalette.CAPACITY, many.values.toSet().size)
  }

  @Test
  fun askingForMoreThanTheCapacityFailsLoudlyInsteadOfRepeating() {
    val tooMany = (1..(PortfolioPalette.CAPACITY + 1)).map { "K$it" }
    var thrown = false
    try {
      PortfolioPalette.assign(tooMany, isDark = false)
    } catch (error: IllegalArgumentException) {
      thrown = true
    }
    assertTrue("assign() must refuse to repeat a colour", thrown)
  }

  @Test
  fun blankAndDuplicateKeysAreHandledSafely() {
    val assigned =
      PortfolioPalette.assign(
        listOf("STOCKS", "STOCKS", "  ", "", "GOLD", " gold ", "STOCKS"),
        isDark = false,
      )
    // Keys come back canonicalised, so every caller can look a colour up reliably.
    assertEquals(2, assigned.size)
    assertEquals(setOf("stocks", "gold"), assigned.keys)
    assertEquals(2, assigned.values.toSet().size)
  }

  @Test
  fun caseVariantsAreOrderIndependent() {
    val forward = PortfolioPalette.assign(listOf("GOLD", "gold"), isDark = false)
    val reverse = PortfolioPalette.assign(listOf("gold", "GOLD"), isDark = false)
    assertEquals(forward, reverse)
    assertEquals(1, forward.size)

    // A mixed set must also be stable in both directions.
    val a = PortfolioPalette.assign(listOf("GOLD", "gold", "STOCKS", "Stocks"), isDark = true)
    val b = PortfolioPalette.assign(listOf("Stocks", "gold", "STOCKS", "GOLD"), isDark = true)
    assertEquals(a, b)
    assertEquals(2, a.size)
  }

  @Test
  fun canonicalKeysAreTrimmedAndLowerCased() {
    assertEquals("gold", PortfolioPalette.canonical("  GOLD "))
    assertEquals("stocks:reliance", PortfolioPalette.canonical("Stocks:Reliance"))
    assertEquals("", PortfolioPalette.canonical("   "))

    // The colour for a key is the colour for its canonical form.
    val assigned = PortfolioPalette.assign(listOf("  GOLD "), isDark = false)
    assertEquals(assigned["gold"], PortfolioPalette.colorFor("gold", isDark = false))
  }

  @Test
  fun repeatedCallsGiveTheSameColours() {
    val keys = listOf("STOCKS", "GOLD", "ETFS", "OTHER", "FIXED_DEPOSITS", "MUTUAL_FUNDS", "X")
    val first = PortfolioPalette.assign(keys, isDark = false)
    repeat(3) { assertEquals(first, PortfolioPalette.assign(keys, isDark = false)) }
    repeat(3) { assertEquals(PortfolioPalette.assign(keys, true), PortfolioPalette.assign(keys, true)) }
  }

  @Test
  fun overflowColoursNeverBecomeTheSemanticGreenOrRed() {
    val all = PortfolioPalette.capacityPalette(isDark = false) + PortfolioPalette.capacityPalette(isDark = true)
    all.forEach { colour ->
      assertNotEquals(SkyplusIncome, colour)
      assertNotEquals(SkyplusExpense, colour)
    }
    assertEquals(PortfolioPalette.CAPACITY, PortfolioPalette.capacityPalette(false).toSet().size)
    assertEquals(PortfolioPalette.CAPACITY, PortfolioPalette.capacityPalette(true).toSet().size)
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
