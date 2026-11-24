package com.example.feature.smartdetection.engine

data class NotificationMetadata(
  val packageName: String?,
  val notificationKey: String?,
  val postTime: Long,
  val receivedAtWallClockMillis: Long,
  val receivedAtElapsedRealtimeMillis: Long,
)

data class NotificationText(
  val title: String = "",
  val text: String = "",
  val bigText: String = "",
) {
  fun combined(): String = "$title $text $bigText".trim()
}

sealed interface ReadOnlyNotificationResult {
  data class Blocked(val reason: NotificationGateBlockReason) : ReadOnlyNotificationResult

  data object Duplicate : ReadOnlyNotificationResult

  data object Empty : ReadOnlyNotificationResult

  data object ContentUnavailable : ReadOnlyNotificationResult

  data class Classified(
    val classification: NotificationClassification,
  ) : ReadOnlyNotificationResult
}

/**
 * Read-only pipeline. Its constructor and API intentionally expose no persistence dependency.
 * Content is requested lazily, only after the metadata-only privacy gate passes.
 */
class ReadOnlyNotificationProcessor(
  private val deduplicationCache: NotificationDeduplicationCache =
    NotificationDeduplicationCache(),
) {
  fun process(
    metadata: NotificationMetadata,
    access: NotificationAccessContext,
    contentProvider: () -> NotificationText?,
  ): ReadOnlyNotificationResult {
    when (val decision = NotificationPrivacyGate.evaluate(metadata.packageName, access)) {
      is NotificationGateDecision.Blocked ->
        return ReadOnlyNotificationResult.Blocked(decision.reason)
      NotificationGateDecision.Allowed -> Unit
    }

    if (
      deduplicationCache.isDuplicate(
        notificationKey = metadata.notificationKey,
        postTime = metadata.postTime,
        nowElapsedRealtimeMillis = metadata.receivedAtElapsedRealtimeMillis,
      )
    ) {
      return ReadOnlyNotificationResult.Duplicate
    }

    val notificationText =
      runCatching(contentProvider).getOrNull()
        ?: return ReadOnlyNotificationResult.ContentUnavailable
    val combined = notificationText.combined()
    if (combined.isBlank()) return ReadOnlyNotificationResult.Empty

    return ReadOnlyNotificationResult.Classified(
      NotificationClassifier.classify(
        content = combined,
        packageName = metadata.packageName.orEmpty(),
        timestamp = metadata.receivedAtWallClockMillis,
      ),
    )
  }
}
