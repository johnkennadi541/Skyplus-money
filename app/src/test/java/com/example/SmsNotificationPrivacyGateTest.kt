package com.example

import com.example.feature.smartdetection.engine.SmsNotificationAccessContext
import com.example.feature.smartdetection.engine.SmsNotificationGateBlockReason
import com.example.feature.smartdetection.engine.SmsNotificationGateDecision
import com.example.feature.smartdetection.engine.SmsNotificationPrivacyGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsNotificationPrivacyGateTest {
  private fun allowedContext() =
    SmsNotificationAccessContext(
      packageName = "com.google.android.apps.messaging",
      defaultSmsPackage = "com.google.android.apps.messaging",
      isAuthenticated = true,
      hasCurrentConsent = true,
      isDetectionEnabled = true,
    )

  @Test
  fun `allows only the current default SMS app after all gates pass`() {
    assertEquals(SmsNotificationGateDecision.Allowed, SmsNotificationPrivacyGate.evaluate(allowedContext()))
  }

  @Test
  fun `blocks missing or mismatched source identity`() {
    val missing = SmsNotificationPrivacyGate.evaluate(allowedContext().copy(packageName = null))
    assertEquals(
      SmsNotificationGateDecision.Blocked(SmsNotificationGateBlockReason.INVALID_SOURCE),
      missing,
    )

    val otherApp = SmsNotificationPrivacyGate.evaluate(allowedContext().copy(packageName = "com.gmail"))
    assertEquals(
      SmsNotificationGateDecision.Blocked(SmsNotificationGateBlockReason.NOT_DEFAULT_SMS_APP),
      otherApp,
    )
  }

  @Test
  fun `blocks unauthenticated users outdated consent and disabled detection`() {
    val unauthenticated = SmsNotificationPrivacyGate.evaluate(allowedContext().copy(isAuthenticated = false))
    assertEquals(
      SmsNotificationGateDecision.Blocked(SmsNotificationGateBlockReason.NOT_AUTHENTICATED),
      unauthenticated,
    )

    val noConsent = SmsNotificationPrivacyGate.evaluate(allowedContext().copy(hasCurrentConsent = false))
    assertEquals(
      SmsNotificationGateDecision.Blocked(SmsNotificationGateBlockReason.CONSENT_REQUIRED),
      noConsent,
    )

    val disabled = SmsNotificationPrivacyGate.evaluate(allowedContext().copy(isDetectionEnabled = false))
    assertEquals(
      SmsNotificationGateDecision.Blocked(SmsNotificationGateBlockReason.DETECTION_DISABLED),
      disabled,
    )
  }
}
