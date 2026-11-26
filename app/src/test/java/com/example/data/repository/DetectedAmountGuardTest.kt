package com.example.data.repository

import org.junit.Assert.assertThrows
import org.junit.Test

class DetectedAmountGuardTest {
  @Test
  fun `accepts positive minor unit amounts`() {
    requirePositiveDetectedAmount(1L)
    requirePositiveDetectedAmount(150050L)
  }

  @Test
  fun `rejects zero and negative minor unit amounts`() {
    assertThrows(IllegalArgumentException::class.java) { requirePositiveDetectedAmount(0L) }
    assertThrows(IllegalArgumentException::class.java) { requirePositiveDetectedAmount(-1L) }
  }
}
