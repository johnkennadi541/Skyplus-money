package com.example

import com.example.feature.smartdetection.engine.SensitiveNotificationFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveNotificationFilterTest {
  @Test
  fun `rejects English authentication secrets even inside transaction alerts`() {
    val samples = listOf(
      "Your OTP for INR 500.00 transaction is 123456",
      "Enter UPI PIN to pay Rs 400.00",
      "CVV verification code for USD 20.00 payment",
      "Never share your password for this transaction",
    )
    samples.forEach { assertTrue(SensitiveNotificationFilter.containsSensitiveContent(it)) }
  }

  @Test
  fun `rejects Tamil and Arabic security messages`() {
    val samples = listOf(
      "உங்கள் ஓடிபி எண் 123456. Rs 500.00 பரிவர்த்தனை",
      "பாதுகாப்பு குறியீடு 123456",
      "رمز التحقق هو 123456",
      "الرقم السري للبطاقة",
    )
    samples.forEach { assertTrue(SensitiveNotificationFilter.containsSensitiveContent(it)) }
  }

  @Test
  fun `allows ordinary completed transaction notifications`() {
    assertFalse(
      SensitiveNotificationFilter.containsSensitiveContent(
        "Rs. 1,450.50 debited from A/c XX4321 paid to Swiggy Ref 432187654321"
      )
    )
  }

  @Test
  fun `rejects oversized notification bodies`() {
    assertTrue(SensitiveNotificationFilter.containsSensitiveContent("x".repeat(4_097)))
  }
}
