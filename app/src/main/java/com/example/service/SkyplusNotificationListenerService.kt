package com.example.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.core.common.DispatcherProvider
import com.example.domain.model.AuthSessionState
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.ConsentRepository
import com.example.domain.repository.SmartDetectionRepository
import com.example.feature.smartdetection.engine.SmartTransactionParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
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
  private val serviceScope by lazy {
    CoroutineScope(serviceJob + dispatchers.io)
  }

  override fun onDestroy() {
    serviceJob.cancel()
    super.onDestroy()
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    super.onNotificationPosted(sbn)
    if (sbn == null) return

    val packageName = sbn.packageName ?: return

    serviceScope.launch {
      // Check authentication, current legal consent, detection toggle, and package allowlist
      // before reading any notification title or body supplied by another application.
      val userId = eligibleUserId(packageName) ?: return@launch
      val extras = sbn.notification.extras ?: return@launch

      val title = extras.getCharSequence("android.title")?.toString().orEmpty()
      val text = extras.getCharSequence("android.text")?.toString().orEmpty()
      val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
      val combinedContent = "$title $text $bigText".trim()
      if (combinedContent.isBlank()) return@launch

      processEligibleNotification(
        userId = userId,
        packageName = packageName,
        title = title,
        combinedContent = combinedContent,
        timestamp = sbn.postTime,
      )
    }
  }

  internal suspend fun processNotificationContent(
    packageName: String,
    title: String,
    combinedContent: String,
    timestamp: Long = System.currentTimeMillis(),
  ) {
    if (combinedContent.isBlank()) return
    val userId = eligibleUserId(packageName) ?: return
    processEligibleNotification(userId, packageName, title, combinedContent, timestamp)
  }

  private suspend fun eligibleUserId(packageName: String): String? {
    if (packageName.isBlank()) return null

    val session = authRepository.observeAuthSession().first()
    val userId = (session as? AuthSessionState.Authenticated)?.user?.uid ?: return null

    // Consent read errors and missing/outdated records fail closed, while coroutine cancellation
    // must still propagate instead of being converted into a normal "not eligible" result.
    val consent =
      try {
        consentRepository.getConsent(userId)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        return null
      }
    if (!consent.isUpToDate) return null

    val settings = detectionRepository.getDetectionSettings(userId).first()
    if (!settings.isNotificationDetectionEnabled) return null
    if (!settings.permittedPackages.contains(packageName)) return null
    return userId
  }

  private suspend fun processEligibleNotification(
    userId: String,
    packageName: String,
    title: String,
    combinedContent: String,
    timestamp: Long,
  ) {
    if (combinedContent.isBlank()) return
    val parsed = SmartTransactionParser.parse(combinedContent, packageName, timestamp) ?: return

    // Process event through deduplication, ambiguity review, and opt-in auto-save engine.
    detectionRepository.processIncomingEvent(
      userId = userId,
      rawPackageName = packageName,
      senderOrTitle = title.ifBlank { packageName },
      parsed = parsed,
    )
  }
}
