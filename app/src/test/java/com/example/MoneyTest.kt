package com.example

import com.example.domain.model.Money
import com.example.domain.usecase.FormatMoneyUseCase
import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

  private val formatMoney = FormatMoneyUseCase()

  @Test
  fun `decimal string avoids floating point drift`() {
    val a = Money.fromDecimalString("0.10", "USD")
    val b = Money.fromDecimalString("0.20", "USD")
    val sum = a + b

    assertEquals(30L, sum.minorUnits)
    assertEquals(BigDecimal("0.30"), sum.toBigDecimal())
    assertEquals("$0.30", formatMoney(sum, Locale.US))
  }

  @Test
  fun `respects zero decimal currencies like JPY and three decimal currencies like KWD`() {
    val jpy = Money.fromDecimalString("1500", "JPY")
    assertEquals(0, jpy.fractionDigits)
    assertEquals(1500L, jpy.minorUnits)

    val kwd = Money.fromDecimalString("12.345", "KWD")
    assertEquals(3, kwd.fractionDigits)
    assertEquals(12345L, kwd.minorUnits)
    assertEquals(BigDecimal("12.345"), kwd.toBigDecimal())
  }

  @Test
  fun `addition and subtraction reject mismatched currencies`() {
    val usd = Money.fromMinorUnits(1000L, "USD")
    val eur = Money.fromMinorUnits(1000L, "EUR")

    assertThrows(IllegalArgumentException::class.java) { usd + eur }
    assertThrows(IllegalArgumentException::class.java) { usd - eur }
  }

  @Test
  fun `subtraction and unary negation preserve exact minor units`() {
    val balance = Money.fromDecimalString("1250.75", "USD")
    val expense = Money.fromDecimalString("450.25", "USD")
    val remaining = balance - expense

    assertEquals(80050L, remaining.minorUnits)
    assertTrue(remaining.isPositive)
    assertEquals(-80050L, (-remaining).minorUnits)
  }

  @Test
  fun `ratio allocation rounds cleanly with half even`() {
    val total = Money.fromDecimalString("100.00", "USD")
    val third = total.allocateRatio(1L, 3L)
    assertEquals(3333L, third.minorUnits)
  }
}
