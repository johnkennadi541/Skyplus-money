package com.example

import com.example.domain.usecase.SipCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SipCalculationEngineTest {
  private val engine = SipCalculationEngine()

  @Test
  fun `zero return rate maturity equals total invested`() {
    val result = engine.calculate("500", "0", 12, "INR").getOrThrow()
    assertEquals(600000L, result.totalInvested.minorUnits)
    assertEquals(result.totalInvested.minorUnits, result.maturityValue.minorUnits)
    assertEquals(0L, result.estimatedReturns.minorUnits)
  }

  @Test
  fun `positive return rate produces a decimal projection above invested amount`() {
    val result = engine.calculate("500", "12", 12, "INR").getOrThrow()
    assertEquals(600000L, result.totalInvested.minorUnits)
    assertTrue(result.maturityValue.minorUnits > result.totalInvested.minorUnits)
    assertTrue(result.maturityValue.toBigDecimal() > BigDecimal("6300"))
    assertTrue(result.maturityValue.toBigDecimal() < BigDecimal("6400"))
  }

  @Test
  fun `rejects invalid tenure and negative rate`() {
    assertTrue(engine.calculate("500", "8", 0, "INR").isFailure)
    assertTrue(engine.calculate("500", "-1", 12, "INR").isFailure)
  }

  @Test
  fun `rounds investment using currency fraction digits`() {
    val result = engine.calculate("10.555", "0", 1, "KWD").getOrThrow()
    assertEquals(10555L, result.monthlyInvestment.minorUnits)
    assertEquals(10555L, result.totalInvested.minorUnits)
  }
}
