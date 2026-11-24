package com.example.feature.smartdetection.engine

/**
 * Only non-content metadata is supplied to this gate. It has no access to notification extras.
 */
data class NotificationAccessContext(
  val isAuthenticated: Boolean,
  val hasCurrentConsent: Boolean,
  val hasAllAppNotificationConsent: Boolean = false,
  val isDetectionEnabled: Boolean,
  val excludedPackages: Set<String> = emptySet(),
)

enum class NotificationGateBlockReason {
  INVALID_SOURCE,
  NOT_AUTHENTICATED,
  CONSENT_REQUIRED,
  ALL_APP_CONSENT_REQUIRED,
  DETECTION_DISABLED,
  SOURCE_EXCLUDED,
}

sealed interface NotificationGateDecision {
  data object Allowed : NotificationGateDecision

  data class Blocked(
    val reason: NotificationGateBlockReason,
  ) : NotificationGateDecision
}

/**
 * Default policy is all-app eligibility. Users may exclude packages explicitly.
 * The caller must evaluate this gate before reading notification text or extras.
 */
object NotificationPrivacyGate {
  fun evaluate(
    packageName: String?,
    access: NotificationAccessContext,
  ): NotificationGateDecision {
    if (packageName.isNullOrBlank()) {
      return NotificationGateDecision.Blocked(NotificationGateBlockReason.INVALID_SOURCE)
    }
    if (!access.isAuthenticated) {
      return NotificationGateDecision.Blocked(NotificationGateBlockReason.NOT_AUTHENTICATED)
    }
    if (!access.hasCurrentConsent) {
      return NotificationGateDecision.Blocked(NotificationGateBlockReason.CONSENT_REQUIRED)
    }
    if (!access.hasAllAppNotificationConsent) {
      return NotificationGateDecision.Blocked(NotificationGateBlockReason.ALL_APP_CONSENT_REQUIRED)
    }
    if (!access.isDetectionEnabled) {
      return NotificationGateDecision.Blocked(NotificationGateBlockReason.DETECTION_DISABLED)
    }
    if (packageName in access.excludedPackages) {
      return NotificationGateDecision.Blocked(NotificationGateBlockReason.SOURCE_EXCLUDED)
    }
    return NotificationGateDecision.Allowed
  }
}
