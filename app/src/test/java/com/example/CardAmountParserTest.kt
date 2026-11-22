package com.example

import com.example.feature.cards.parseAmountToMinor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Amount parsing must follow the currency's ISO-4217 fraction digits, never a hardcoded 2.
 */
class CardAmountParserTest {

  @Test
  fun `indian rupees use two decimals`() {
    assertEquals(100_00L, parseAmountToMinor("100", "INR"))
    assertEquals(100_50L, parseAmountToMinor("100.50", "INR"))
    assertEquals(1_23_456L, parseAmountToMinor("1234.56", "INR"))
  }

  @Test
  fun `kuwaiti dinars use three decimals`() {
    assertEquals(100_000L, parseAmountToMinor("100", "KWD"))
    assertEquals(100_500L, parseAmountToMinor("100.500", "KWD"))
    // 100.1234 rounds HALF_EVEN at three decimals -> 100.123
    assertEquals(100_123L, parseAmountToMinor("100.1234", "KWD"))
  }

  @Test
  fun `japanese yen uses zero decimals`() {
    assertEquals(100L, parseAmountToMinor("100", "JPY"))
    // 100.4 rounds to 100, 100.5 rounds HALF_EVEN to 100, 101.5 rounds to 102
    assertEquals(100L, parseAmountToMinor("100.4", "JPY"))
    assertEquals(100L, parseAmountToMinor("100.5", "JPY"))
    assertEquals(102L, parseAmountToMinor("101.5", "JPY"))
  }

  @Test
  fun `an empty amount is zero`() {
    assertEquals(0L, parseAmountToMinor("", "INR"))
    assertEquals(0L, parseAmountToMinor("   ", "JPY"))
  }

  @Test
  fun `a negative amount is rejected`() {
    assertNull(parseAmountToMinor("-1", "INR"))
    assertNull(parseAmountToMinor("-0.01", "KWD"))
  }

  @Test
  fun `a non numeric amount is rejected`() {
    assertNull(parseAmountToMinor("abc", "INR"))
    assertNull(parseAmountToMinor("1,000", "INR"))
    assertNull(parseAmountToMinor("100%", "INR"))
  }

  @Test
  fun `an amount too large for Long is rejected instead of wrapping`() {
    assertNull(parseAmountToMinor("99999999999999999999", "INR"))
  }
}
