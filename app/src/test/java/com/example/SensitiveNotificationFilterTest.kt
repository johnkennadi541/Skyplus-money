package com.example

import com.example.feature.smartdetection.engine.SensitiveNotificationFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveNotificationFilterTest {
  @Test
  fun `rejects OTP PIN CVV password and obfuscated variants`() {
    val sensitive =
      listOf(
        "Your OTP for INR 500.00 is 123456",
        "Your O T P for Rs 500.00 is 123456",
        "Enter O.T.P 123456 to approve the payment",
        "Enter your UPI PIN to pay INR 250.00",
        "Your P I N was requested for Rs 300.00",
        "Verify card C.V.V for USD 20.00",
        "Use pass code 998877",
        "Never share your password",
        "Your 2FA code for INR 900.00",
      )

    sensitive.forEach { assertTrue("Must reject: $it", SensitiveNotificationFilter.containsSensitiveContent(it)) }
  }

  @Test
  fun `rejects Tamil and Arabic security phrases`() {
    val sensitive =
      listOf(
        "உங்கள் ஓடிபி எண் 123456",
        "சரிபார்ப்பு குறியீடு 123456",
        "உங்கள் கடவுச்சொல்",
        "உங்கள் பின் எண்",
        "رمز التحقق هو 123456",
        "كلمة المرور الخاصة بك",
        "الرقم السري",
      )

    sensitive.forEach { assertTrue("Must reject: $it", SensitiveNotificationFilter.containsSensitiveContent(it)) }
  }

  @Test
  fun `rejects whole mixed transaction and OTP notification`() {
    val mixed = "INR 1,250.00 debited from account XX1234. Your OTP is 112233."
    assertTrue(SensitiveNotificationFilter.containsSensitiveContent(mixed))
  }

  @Test
  fun `does not reject ordinary completed bank transaction`() {
    val transaction = "INR 1,250.00 debited from account XX1234 paid to Swiggy Ref TXN123456"
    assertFalse(SensitiveNotificationFilter.containsSensitiveContent(transaction))
  }

  @Test
  fun `oversized notification fails closed`() {
    assertTrue(SensitiveNotificationFilter.containsSensitiveContent("A".repeat(8_193)))
  }
}
