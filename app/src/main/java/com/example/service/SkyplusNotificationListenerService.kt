package com.example.service

import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.core.common.DispatcherProvider
import com.example.domain.model.AuthSessionState
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.ConsentRepository
import com.example.domain.repository.SmartDetectionRepository
import com.example.feature.smartdetection.engine.SensitiveNotificationFilter
import com.example.feature.smartdetection.engine.SmartTransactionParser
import com.example.feature.smartdetection.engine.SmsNotificationAccessContext
import com.example.feature.smartdetection.engine.SmsNotificationPrivacyGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SmsNotificationContent(
  val title: String,
  val text: String,
  val bigText: String,
)

@AndroidEntryPoint
class SkyplusNotificationListenerService : NotificationListenerService() {

  private companion object {
    const val MAX_TITLE_CHARS = 256
    const val MAX_NOTIFICATION_TEXT_CHARS = 2_048
    const val MAX_COMBINED_CONTENT_CHARS = 4_096
  }

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
    val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this) ?: return

    // Check source identity using metadata only. Never inspect extras for any other app.
    if (packageName != defaultSmsPackage) return

    serviceScope.launch {
      // Re-check the current default SMS app immediately before any notification body access.
      val currentDefaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this@SkyplusNotificationListenerService)
        ?: return@launch

      processNotification(
        packageName = packageName,
        defaultSmsPackage = currentDefaultSmsPackage,
        timestamp = sbn.postTime,
      ) {
        // This provider is invoked only after source, authentication, legal consent,
        // and detection-setting checks all pass.
        val extras = sbn.notification.extras
        if (extras == null) {
          null
        } else {
          SmsNotificationContent(
            title = boundedNotificationField(extras.getCharSequence(Notification.EXTRA_TITLE), MAX_TITLE_CHARS),
            text = boundedNotificationField(extras.getCharSequence(Notification.EXTRA_TEXT), MAX_NOTIFICATION_TEXT_CHARS),
            bigText = boundedNotificationField(extras.getCharSequence(Notification.EXTRA_BIG_TEXT), MAX_NOTIFICATION_TEXT_CHARS),
          )
        }
      }
    }
  }

  internal suspend fun processNotification(
    packageName: String?,
    defaultSmsPackage: String?,
    timestamp: Long = System.currentTimeMillis(),
    contentProvider: () -> SmsNotificationContent?,
  ) {
    // Fail closed on source mismatch before authentication or content access.
    if (!SmsNotificationPrivacyGate.isSourceAllowed(packageName, defaultSmsPackage)) return

    val session =
      try {
        authRepository.observeAuthSession().first()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        return
      }
    val userId = (session as? AuthSessionState.Authenticated)?.user?.uid
      ?.takeIf { it.isNotBlank() } ?: return

    val consent =
      try {
        consentRepository.getConsent(userId)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        null
      }

    val settings =
      try {
        detectionRepository.getDetectionSettings(userId).first()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        null
      }

    val accessDecision =
      SmsNotificationPrivacyGate.evaluate(
        SmsNotificationAccessContext(
          packageName = packageName,
          defaultSmsPackage = defaultSmsPackage,
          isAuthenticated = true,
          hasCurrentConsent = consent?.isUpToDate == true,
          isDetectionEnabled = settings?.isNotificationDetectionEnabled == true,
        )
      )
    if (accessDecision !is SmsNotificationGateDecision.Allowed) return

    val content =
      try {
        contentProvider()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        null
      } ?: return

    val combinedContent =
      "${content.title} ${content.text} ${content.bigText}"
        .take(MAX_COMBINED_CONTENT_CHARS)
        .trim()
    if (combinedContent.isBlank()) return

    // Reject OTP/PIN/CVV/password/security notifications in English, Tamil and Arabic.
    if (SensitiveNotificationFilter.containsSensitiveContent(combinedContent)) return

    val parsed = SmartTransactionParser.parse(combinedContent, packageName, timestamp) ?: return

    detectionRepository.processIncomingEvent(
      userId = userId,
      rawPackageName = packageName.orEmpty(),
      senderOrTitle = content.title.ifBlank { packageName.orEmpty() },
      parsed = parsed,
    )
  }

  private fun boundedNotificationField(value: CharSequence?, maxChars: Int): String {
    if (value == null) return ""
    return value.subSequence(0, minOf(value.length, maxChars)).toString()
  }
}
