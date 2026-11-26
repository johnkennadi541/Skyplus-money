package com.example.data.repository

import org.junit.Assert.assertDoesNotThrow
import org.junit.Assert.assertThrows
import org.junit.Test

class DetectedAmountGuardTest {
  @Test
  fun `accepts positive minor unit amounts`() {
    assertDoesNotThrow { requirePositiveDetectedAmount(1L) }
    assertDoesNotThrow { requirePositiveDetectedAmount(150050L) }
  }

  @Test
  fun `rejects zero and negative minor unit amounts`() {
    assertThrows(IllegalArgumentException::class.java) { requirePositiveDetectedAmount(0L) }
    assertThrows(IllegalArgumentException::class.java) { requirePositiveDetectedAmount(-1L) }
  }
}
