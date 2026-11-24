package com.example

import com.example.feature.smartdetection.engine.NotificationAccessContext
import com.example.feature.smartdetection.engine.NotificationGateBlockReason
import com.example.feature.smartdetection.engine.NotificationGateDecision
import com.example.feature.smartdetection.engine.NotificationPrivacyGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPrivacyGateTest {
  private val enabledAccess =
    NotificationAccessContext(
      isAuthenticated = true,
      hasCurrentConsent = true,
      isDetectionEnabled = true,
    )

  @Test
  fun `all app sources are eligible by default when user access gates pass`() {
    val result = NotificationPrivacyGate.evaluate("com.unlisted.wallet", enabledAccess)
    assertEquals(NotificationGateDecision.Allowed, result)
  }

  @Test
  fun `excluded source is blocked before content access`() {
    val access = enabledAccess.copy(excludedPackages = setOf("com.private.chat"))
    val result = NotificationPrivacyGate.evaluate("com.private.chat", access)
    assertEquals(
      NotificationGateDecision.Blocked(NotificationGateBlockReason.SOURCE_EXCLUDED),
      result,
    )
  }

  @Test
  fun `missing authentication fails closed`() {
    val result = NotificationPrivacyGate.evaluate("com.bank.app", enabledAccess.copy(isAuthenticated = false))
    assertEquals(
      NotificationGateDecision.Blocked(NotificationGateBlockReason.NOT_AUTHENTICATED),
      result,
    )
  }

  @Test
  fun `missing consent fails closed`() {
    val result = NotificationPrivacyGate.evaluate("com.bank.app", enabledAccess.copy(hasCurrentConsent = false))
    assertEquals(
      NotificationGateDecision.Blocked(NotificationGateBlockReason.CONSENT_REQUIRED),
      result,
    )
  }

  @Test
  fun `disabled detection fails closed`() {
    val result = NotificationPrivacyGate.evaluate("com.bank.app", enabledAccess.copy(isDetectionEnabled = false))
    assertEquals(
      NotificationGateDecision.Blocked(NotificationGateBlockReason.DETECTION_DISABLED),
      result,
    )
  }

  @Test
  fun `blank package is rejected`() {
    val result = NotificationPrivacyGate.evaluate(" ", enabledAccess)
    assertTrue(result is NotificationGateDecision.Blocked)
    assertEquals(
      NotificationGateBlockReason.INVALID_SOURCE,
      (result as NotificationGateDecision.Blocked).reason,
    )
  }
}
