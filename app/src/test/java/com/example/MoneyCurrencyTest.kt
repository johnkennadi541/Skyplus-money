package com.example

import com.example.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Currency support: real ISO codes resolve to their own fraction digits, and everything else is
 * reported as unsupported instead of throwing.
 */
class MoneyCurrencyTest {

  @Test
  fun `real currencies resolve to their own fraction digits`() {
    assertEquals(2, Money.fractionDigitsOrNull("INR"))
    assertEquals(3, Money.fractionDigitsOrNull("KWD"))
    assertEquals(0, Money.fractionDigitsOrNull("JPY"))
    assertEquals(2, Money.fractionDigitsOrNull("USD"))
  }

  @Test
  fun `supported currencies are recognised`() {
    assertTrue(Money.isSupportedCurrency("INR"))
    assertTrue(Money.isSupportedCurrency("KWD"))
    assertTrue(Money.isSupportedCurrency("JPY"))
    // Lower case and surrounding spaces are tolerated.
    assertTrue(Money.isSupportedCurrency(" inr "))
  }

  @Test
  fun `an invented code is not supported`() {
    assertFalse(Money.isSupportedCurrency("ABC"))
    assertFalse(Money.isSupportedCurrency("ZZZ"))
    assertNull(Money.fractionDigitsOrNull("ABC"))
  }

  @Test
  fun `XXX is treated as unsupported because it means no currency`() {
    // java.util.Currency accepts XXX with a negative fraction digit; the app must not.
    assertFalse(Money.isSupportedCurrency("XXX"))
    assertNull(Money.fractionDigitsOrNull("XXX"))
  }

  @Test
  fun `malformed codes are not supported`() {
    assertFalse(Money.isSupportedCurrency(""))
    assertFalse(Money.isSupportedCurrency("IN"))
    assertFalse(Money.isSupportedCurrency("INRR"))
    assertFalse(Money.isSupportedCurrency("I1R"))
  }

  @Test
  fun `the non throwing helper never throws for any input`() {
    listOf("", " ", "ABC", "XXX", "inr", "12", "!!!!").forEach { input ->
      val result = runCatching { Money.fractionDigitsOrNull(input) }
      assertTrue("threw for '$input'", result.isSuccess)
    }
  }
}
