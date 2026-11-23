package com.example

import com.example.domain.usecase.ShoppingReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingReceiptParserTest {
  @Test fun `extracts explicitly labelled grand total and merchant candidate`() {
    val result = ShoppingReceiptParser.parse("Fresh Market\\nTomatoes 2.000\\nSubtotal 4.000\\nGrand Total KWD 4.250")
    assertEquals("4.25", result.totalDecimal)
    assertEquals("KWD", result.currencyCodeCandidate)
    assertEquals("Fresh Market", result.merchantCandidate)
    assertFalse(result.needsReview)
  }

  @Test fun `does not guess when total is ambiguous`() {
    val result = ShoppingReceiptParser.parse("Corner Shop\\nTotal 12.00\\nTotal 15.00")
    assertNull(result.totalDecimal)
    assertTrue(result.needsReview)
  }

  @Test fun `missing total stays review only`() {
    val result = ShoppingReceiptParser.parse("Shop\\nMilk 1.500")
    assertNull(result.totalDecimal)
    assertTrue(result.needsReview)
  }

  @Test fun `fingerprint is stable and changes with receipt text`() {
    val first = ShoppingReceiptParser.parse("Shop\\nGrand Total 5.00")
    val same = ShoppingReceiptParser.parse("Shop\\nGrand Total 5.00")
    val other = ShoppingReceiptParser.parse("Shop\\nGrand Total 6.00")
    assertEquals(first.fingerprint, same.fingerprint)
    assertNotEquals(first.fingerprint, other.fingerprint)
    assertEquals(64, first.fingerprint.length)
  }
}
