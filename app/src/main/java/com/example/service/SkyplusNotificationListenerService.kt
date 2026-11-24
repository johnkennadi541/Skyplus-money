package com.example.service

import android.app.Notification
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.core.common.DispatcherProvider
import com.example.domain.model.AuthSessionState
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.ConsentRepository
import com.example.domain.repository.SmartDetectionRepository
import com.example.feature.smartdetection.engine.NotificationAccessContext
import com.example.feature.smartdetection.engine.NotificationMetadata
import com.example.feature.smartdetection.engine.NotificationText
import com.example.feature.smartdetection.engine.ReadOnlyNotificationProcessor
import com.example.feature.smartdetection.engine.ReadOnlyNotificationResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SkyplusNotificationListenerService : NotificationListenerService() {

  @Inject lateinit var detectionRepository: SmartDetectionRepository
  @Inject lateinit var authRepository: AuthRepository
  @Inject lateinit var consentRepository: ConsentRepository
  @Inject lateinit var dispatchers: DispatcherProvider

  private val serviceJob = SupervisorJob()
  private val readOnlyProcessor = ReadOnlyNotificationProcessor()

  private val serviceScope by lazy {
    CoroutineScope(serviceJob + dispatchers.io)
  }

  override fun onDestroy() {
    serviceJob.cancel()
    readOnlyProcessor.clearTransientState()
    super.onDestroy()
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    super.onNotificationPosted(sbn)
    if (sbn == null) return

    // Read only OS-provided identity metadata here. Do not access notification.extras yet.
    val metadata =
      NotificationMetadata(
        packageName = sbn.packageName,
        notificationKey = sbn.key,
        postTime = sbn.postTime,
        receivedAtWallClockMillis = System.currentTimeMillis(),
        receivedAtElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
      )

    serviceScope.launch {
      processNotification(metadata) {
        // This lazy provider is invoked only after authentication, consent, settings,
        // source exclusion and exact-redelivery checks have passed.
        val extras = sbn.notification.extras
        if (extras == null) {
          null
        } else {
          NotificationText(
            title = boundedNotificationField(extras.getCharSequence(Notification.EXTRA_TITLE)),
            text = boundedNotificationField(extras.getCharSequence(Notification.EXTRA_TEXT)),
            bigText = boundedNotificationField(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),
          )
        }
      }
    }
  }

  /**
   * Kept as an internal test seam for legacy consent tests. The supplied content is already
   * in memory, so production callbacks must use [processNotification] with a lazy provider.
   */
  private fun boundedNotificationField(value: CharSequence?): String {
    if (value == null) return ""
    val maxChars = NotificationText.MAX_NOTIFICATION_CHARS + 1
    return value.subSequence(0, minOf(value.length, maxChars)).toString()
  }

  internal suspend fun processNotificationContent(
    packageName: String,
    title: String,
    combinedContent: String,
    timestamp: Long = System.currentTimeMillis(),
  ): ReadOnlyNotificationResult {
    val metadata =
      NotificationMetadata(
        packageName = packageName,
        notificationKey = null,
        postTime = timestamp,
        receivedAtWallClockMillis = timestamp,
        receivedAtElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
      )
    return processNotification(metadata) {
      NotificationText(title = title, text = combinedContent)
    }
  }

  internal suspend fun processNotification(
    metadata: NotificationMetadata,
    contentProvider: () -> NotificationText?,
  ): ReadOnlyNotificationResult {
    val access = resolveNotificationAccess()
    return readOnlyProcessor.process(metadata, access, contentProvider)
  }

  private suspend fun resolveNotificationAccess(): NotificationAccessContext {
    val session =
      runCatching { authRepository.observeAuthSession().first() }.getOrNull()
    val userId =
      (session as? AuthSessionState.Authenticated)?.user?.uid
    if (userId.isNullOrBlank()) {
      return NotificationAccessContext(
        isAuthenticated = false,
        hasCurrentConsent = false,
        isDetectionEnabled = false,
      )
    }

    val consent = runCatching { consentRepository.getConsent(userId) }.getOrNull()
    val settings =
      runCatching { detectionRepository.getDetectionSettings(userId).first() }.getOrNull()

    // All-app eligibility is intentional for this read-only feature branch. The old
    // permittedPackages allowlist is not used here; future source exclusions are explicit.
    return NotificationAccessContext(
      isAuthenticated = true,
      hasCurrentConsent = consent?.isUpToDate == true,
      hasAllAppNotificationConsent = settings?.hasAllAppNotificationConsent == true,
      isDetectionEnabled = settings?.isNotificationDetectionEnabled == true,
      excludedPackages = settings?.excludedPackages ?: emptySet(),
    )
  }
}
