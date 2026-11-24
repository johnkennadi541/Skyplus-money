package com.example.feature.smartdetection.engine

/**
 * Metadata-only policy for the SMS-only automatic-entry phase.
 * Call this before accessing StatusBarNotification.notification.extras.
 */
data class SmsNotificationAccessContext(
  val packageName: String?,
  val defaultSmsPackage: String?,
  val isAuthenticated: Boolean,
  val hasCurrentConsent: Boolean,
  val isDetectionEnabled: Boolean,
)

enum class SmsNotificationGateBlockReason {
  INVALID_SOURCE,
  NOT_DEFAULT_SMS_APP,
  NOT_AUTHENTICATED,
  CONSENT_REQUIRED,
  DETECTION_DISABLED,
}

sealed interface SmsNotificationGateDecision {
  data object Allowed : SmsNotificationGateDecision

  data class Blocked(
    val reason: SmsNotificationGateBlockReason,
  ) : SmsNotificationGateDecision
}

object SmsNotificationPrivacyGate {
  fun isSourceAllowed(packageName: String?, defaultSmsPackage: String?): Boolean =
    !packageName.isNullOrBlank() &&
      !defaultSmsPackage.isNullOrBlank() &&
      packageName == defaultSmsPackage

  fun evaluate(access: SmsNotificationAccessContext): SmsNotificationGateDecision {
    val packageName = access.packageName
    val defaultSmsPackage = access.defaultSmsPackage

    if (packageName.isNullOrBlank() || defaultSmsPackage.isNullOrBlank()) {
      return SmsNotificationGateDecision.Blocked(SmsNotificationGateBlockReason.INVALID_SOURCE)
    }
    if (packageName != defaultSmsPackage) {
      return SmsNotificationGateDecision.Blocked(SmsNotificationGateBlockReason.NOT_DEFAULT_SMS_APP)
    }
    if (!access.isAuthenticated) {
      return SmsNotificationGateDecision.Blocked(SmsNotificationGateBlockReason.NOT_AUTHENTICATED)
    }
    if (!access.hasCurrentConsent) {
      return SmsNotificationGateDecision.Blocked(SmsNotificationGateBlockReason.CONSENT_REQUIRED)
    }
    if (!access.isDetectionEnabled) {
      return SmsNotificationGateDecision.Blocked(SmsNotificationGateBlockReason.DETECTION_DISABLED)
    }
    return SmsNotificationGateDecision.Allowed
  }
}
