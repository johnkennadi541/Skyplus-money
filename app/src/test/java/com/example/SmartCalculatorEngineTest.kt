package com.example

import com.example.core.common.SmartCalculatorEngine
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SmartCalculatorEngineTest {
  @Test fun respectsOperatorPrecedenceAndBrackets() {
    assertEquals(BigDecimal("2150.00"), SmartCalculatorEngine.evaluate("1250 + 450 × 2"))
    assertEquals(BigDecimal("3400.00"), SmartCalculatorEngine.evaluate("(1250 + 450) * 2"))
  }

  @Test fun supportsPercentAndUnaryMinus() {
    assertEquals(BigDecimal("25.00"), SmartCalculatorEngine.evaluate("200 * 12.5%"))
    assertEquals(BigDecimal("-3.00"), SmartCalculatorEngine.evaluate("2 - 5"))
  }

  @Test fun roundsToRequestedCurrencyScale() {
    assertEquals(BigDecimal("1.24"), SmartCalculatorEngine.evaluate("1.235", 2))
    assertEquals(BigDecimal("1.235"), SmartCalculatorEngine.evaluate("1.235", 3))
  }

  @Test fun rejectsDivisionByZeroAndMalformedExpressions() {
    assertThrows(IllegalArgumentException::class.java) { SmartCalculatorEngine.evaluate("10 / 0") }
    assertThrows(IllegalArgumentException::class.java) { SmartCalculatorEngine.evaluate("(2 + 3") }
    assertThrows(IllegalArgumentException::class.java) { SmartCalculatorEngine.evaluate("2 + nope") }
  }
}
