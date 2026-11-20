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
    val extras = sbn.notification.extras ?: return

    val title = extras.getCharSequence("android.title")?.toString().orEmpty()
    val text = extras.getCharSequence("android.text")?.toString().orEmpty()
    val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()

    val combinedContent = "$title $text $bigText".trim()
    if (combinedContent.isBlank()) return

    serviceScope.launch {
      processNotificationContent(
        packageName = packageName,
        title = title,
        combinedContent = combinedContent,
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

    val session = authRepository.observeAuthSession().first()
    val userId = (session as? AuthSessionState.Authenticated)?.user?.uid ?: return

    // Enforce Phase 1D Legal Consent Gate in background ingestion: fail closed on missing,
    // outdated, or unreadable consent records.
    val consent = runCatching { consentRepository.getConsent(userId) }.getOrNull()
    if (consent == null || !consent.isUpToDate) return

    val settings = detectionRepository.getDetectionSettings(userId).first()
    if (!settings.isNotificationDetectionEnabled) return
    if (!settings.permittedPackages.contains(packageName)) return

    // On-device parsing with expanded sensitive token filter
    val parsed = SmartTransactionParser.parse(combinedContent, packageName, timestamp) ?: return

    // Process event through deduplication, ambiguity review, and auto-save engine
    detectionRepository.processIncomingEvent(
      userId = userId,
      rawPackageName = packageName,
      senderOrTitle = title.ifBlank { packageName },
      parsed = parsed,
    )
  }
}
