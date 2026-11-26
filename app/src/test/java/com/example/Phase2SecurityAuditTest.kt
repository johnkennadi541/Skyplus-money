package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DispatcherProvider
import com.example.core.security.DatabasePassphraseProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.entity.TransactionDetectionEventEntity
import com.example.data.repository.FinancialTransactionRepositoryImpl
import com.example.data.repository.SmartDetectionRepositoryImpl
import com.example.domain.model.DetectionConfidence
import com.example.domain.model.DetectionSettings
import com.example.domain.model.ParsedFinancialMessage
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.service.SkyplusNotificationListenerService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2SecurityAuditTest {

  private lateinit var context: Context
  private lateinit var database: AppDatabase
  private lateinit var detectionRepo: SmartDetectionRepositoryImpl

  private val testDispatchers =
    object : DispatcherProvider {
      private val d = UnconfinedTestDispatcher()
      override val main: CoroutineDispatcher = d
      override val io: CoroutineDispatcher = d
      override val default: CoroutineDispatcher = d
    }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    val txnRepo =
      FinancialTransactionRepositoryImpl(
        database = database,
        transactionDao = database.financialTransactionDao(),
        accountDao = database.financialAccountDao(),
        dispatchers = testDispatchers,
      )
    detectionRepo =
      SmartDetectionRepositoryImpl(
        context = context,
        database = database,
        eventDao = database.transactionDetectionEventDao(),
        mappingDao = database.accountMappingRuleDao(),
        accountDao = database.financialAccountDao(),
        transactionDao = database.financialTransactionDao(),
        transactionRepository = txnRepo,
        dispatchers = testDispatchers,
      )
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `manifest does not declare restricted SMS permissions or AccessibilityService`() {
    val pkgInfo =
      context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES,
      )

    val requestedPermissions = pkgInfo.requestedPermissions?.toSet().orEmpty()
    assertFalse("READ_SMS must NOT be declared", requestedPermissions.contains(Manifest.permission.READ_SMS))
    assertFalse("RECEIVE_SMS must NOT be declared", requestedPermissions.contains(Manifest.permission.RECEIVE_SMS))
    assertFalse(
      "AccessibilityService must NOT be used as SMS workaround",
      requestedPermissions.contains(Manifest.permission.BIND_ACCESSIBILITY_SERVICE),
    )

    val listenerService =
      pkgInfo.services?.firstOrNull {
        it.name == SkyplusNotificationListenerService::class.java.name
      }
    assertNotNull("SkyplusNotificationListenerService must be declared", listenerService)
    assertEquals(
      Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
      listenerService?.permission,
    )
  }

  @Test
  fun `detection settings lock SMS detection disabled per Play policy`() = runTest {
    val requested =
      DetectionSettings(
        userId = "uid_sec_test",
        isNotificationDetectionEnabled = false,
        isAutoSaveHighConfidenceEnabled = false,
        isSmsDetectionEnabled = false,
        isSmsPolicyRestricted = true,
      )
    detectionRepo.updateDetectionSettings("uid_sec_test", requested)

    val parsed =
      ParsedFinancialMessage(
        amountMinor = 50000L,
        currencyCode = "INR",
        type = TransactionType.EXPENSE,
        status = TransactionStatus.COMPLETED,
        merchantOrPayee = "Merchant",
        accountLast4Hint = "1234",
        referenceId = "REF999999",
        confidenceScore = 0.90f,
        confidenceLevel = DetectionConfidence.HIGH,
        confidenceReasons = listOf("test"),
      )

    val result =
      detectionRepo.processIncomingEvent(
        userId = "uid_sec_test",
        rawPackageName = "com.phonepe.app",
        senderOrTitle = "PhonePe Alert 9876543210",
        parsed = parsed,
      )
    assertTrue("Processing must fail when notification detection is disabled", result.isFailure)
  }

  @Test
  fun `detection events mask sensitive digit strings and store no raw message body`() = runTest {
    detectionRepo.updateDetectionSettings(
      "uid_privacy",
      DetectionSettings(
        userId = "uid_privacy",
        isNotificationDetectionEnabled = true,
        isAutoSaveHighConfidenceEnabled = false,
      ),
    )

    val parsed =
      ParsedFinancialMessage(
        amountMinor = 12300L,
        currencyCode = "INR",
        type = TransactionType.EXPENSE,
        status = TransactionStatus.COMPLETED,
        merchantOrPayee = "CoffeeShop",
        accountLast4Hint = "5555",
        referenceId = "REF123456",
        confidenceScore = 0.85f,
        confidenceLevel = DetectionConfidence.HIGH,
        confidenceReasons = listOf("parsed"),
      )

    val saved =
      detectionRepo.processIncomingEvent(
        userId = "uid_privacy",
        rawPackageName = "com.google.android.apps.nbu.paisa.user",
        senderOrTitle = "Bank Alert 99887766554433",
        parsed = parsed,
      ).getOrThrow()

    assertFalse(
      "Long digit sequences must be masked in senderOrTitle",
      saved.senderOrTitle.contains("99887766554433"),
    )
    assertTrue(saved.senderOrTitle.contains("••••"))

    // Verify Room entity schema contains no rawBody/rawText column
    val fieldNames = TransactionDetectionEventEntity::class.java.declaredFields.map { it.name }.toSet()
    assertFalse(fieldNames.contains("rawText"))
    assertFalse(fieldNames.contains("rawBody"))
    assertFalse(fieldNames.contains("smsBody"))
  }

  @Test
  fun `database passphrase provider fails closed without Keystore and creates 256-bit key with backend`() {
    val unbackedProvider = DatabasePassphraseProvider(context)
    var failedClosed = false
    try {
      unbackedProvider.getOrCreatePassphrase()
    } catch (_: com.example.core.security.DatabaseSecurityException) {
      failedClosed = true
    }
    assertTrue("Missing Keystore must fail closed instead of returning unpersisted key", failedClosed)

    val testBackend =
      object : com.example.core.security.KeyWrappingBackend {
        override fun encrypt(raw: ByteArray): Pair<String, String> =
          Pair(android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP), "dGVzdF9pdg==")

        override fun decrypt(encryptedBase64: String, ivBase64: String): ByteArray =
          android.util.Base64.decode(encryptedBase64, android.util.Base64.NO_WRAP)
      }
    val backedProvider = DatabasePassphraseProvider(context, keyBackend = testBackend)
    val passphrase = backedProvider.getOrCreatePassphrase()
    assertEquals(32, passphrase.size)
  }
}
