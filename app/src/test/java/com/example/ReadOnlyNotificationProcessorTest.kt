package com.example

import com.example.feature.smartdetection.engine.NotificationAccessContext
import com.example.feature.smartdetection.engine.NotificationClassification
import com.example.feature.smartdetection.engine.NotificationGateBlockReason
import com.example.feature.smartdetection.engine.NotificationMetadata
import com.example.feature.smartdetection.engine.NotificationText
import com.example.feature.smartdetection.engine.ReadOnlyNotificationProcessor
import com.example.feature.smartdetection.engine.ReadOnlyNotificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyNotificationProcessorTest {
  private val access =
    NotificationAccessContext(
      isAuthenticated = true,
      hasCurrentConsent = true,
      isDetectionEnabled = true,
      hasAllAppNotificationConsent = true,
    )

  private fun metadata(
    packageName: String = "com.unlisted.wallet",
    key: String? = "notification-key",
    postTime: Long = 100L,
    elapsed: Long = 500L,
  ) = NotificationMetadata(
    packageName = packageName,
    notificationKey = key,
    postTime = postTime,
    receivedAtWallClockMillis = 1_700_000_000_000L,
    receivedAtElapsedRealtimeMillis = elapsed,
  )

  @Test
  fun `blocked notification never invokes content provider`() {
    val processor = ReadOnlyNotificationProcessor()
    var contentRead = false
    val result =
      processor.process(
        metadata(),
        access.copy(hasCurrentConsent = false),
      ) {
        contentRead = true
        NotificationText(text = "INR 100 debited")
      }

    assertEquals(
      ReadOnlyNotificationResult.Blocked(NotificationGateBlockReason.CONSENT_REQUIRED),
      result,
    )
    assertFalse(contentRead)
  }

  @Test
  fun `missing all-app opt-in prevents notification text extraction`() {
    val processor = ReadOnlyNotificationProcessor()
    var contentRead = false
    val result =
      processor.process(
        metadata(),
        access.copy(hasAllAppNotificationConsent = false),
      ) {
        contentRead = true
        NotificationText(text = "INR 100.00 debited from A/c XX1234")
      }

    assertEquals(
      ReadOnlyNotificationResult.Blocked(NotificationGateBlockReason.ALL_APP_CONSENT_REQUIRED),
      result,
    )
    assertFalse(contentRead)
  }

  @Test
  fun `excluded source prevents notification text extraction`() {
    val processor = ReadOnlyNotificationProcessor()
    var contentRead = false
    val result =
      processor.process(
        metadata(packageName = "com.private.chat"),
        access.copy(excludedPackages = setOf("com.private.chat")),
      ) {
        contentRead = true
        NotificationText(text = "INR 100.00 debited from A/c XX1234")
      }

    assertEquals(
      ReadOnlyNotificationResult.Blocked(NotificationGateBlockReason.SOURCE_EXCLUDED),
      result,
    )
    assertFalse(contentRead)
  }

  @Test
  fun `unlisted app source can be classified after all user gates pass`() {
    val processor = ReadOnlyNotificationProcessor()
    val result =
      processor.process(metadata(), access) {
        NotificationText(
          text = "INR 1,250.00 debited from A/c XX1234 paid to Swiggy Ref TXN123456",
        )
      }

    assertTrue(result is ReadOnlyNotificationResult.Classified)
    assertTrue(
      (result as ReadOnlyNotificationResult.Classified).classification is
        NotificationClassification.FinancialCandidate,
    )
  }

  @Test
  fun `sensitive notification is rejected as a whole`() {
    val processor = ReadOnlyNotificationProcessor()
    val result =
      processor.process(metadata(), access) {
        NotificationText(text = "INR 1,250 debited. Your OTP is 123456.")
      }

    assertEquals(
      NotificationClassification.Sensitive,
      (result as ReadOnlyNotificationResult.Classified).classification,
    )
  }

  @Test
  fun `exact redelivery is stopped before content provider runs twice`() {
    val processor = ReadOnlyNotificationProcessor()
    var reads = 0
    val provider = {
      reads += 1
      NotificationText(text = "INR 100.00 debited from A/c XX1234 paid to Shop Ref TXN123456")
    }

    assertTrue(processor.process(metadata(), access, provider) is ReadOnlyNotificationResult.Classified)
    assertEquals(ReadOnlyNotificationResult.Duplicate, processor.process(metadata(elapsed = 700L), access, provider))
    assertEquals(1, reads)
  }

  @Test
  fun `processor has no persistence or Android context dependency`() {
    val forbiddenNames = listOf("Repository", "Dao", "Database", "Context")
    val fieldTypes =
      ReadOnlyNotificationProcessor::class.java.declaredFields.map { it.type.name }
    assertFalse(fieldTypes.any { type -> forbiddenNames.any { forbidden -> type.contains(forbidden) } })
  }
}
