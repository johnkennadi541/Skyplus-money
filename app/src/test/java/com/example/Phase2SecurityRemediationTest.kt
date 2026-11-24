package com.example

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DispatcherProvider
import com.example.core.security.DatabasePassphraseProvider
import com.example.core.security.DatabaseSecurityException
import com.example.core.security.DatabaseSecurityMonitor
import com.example.core.security.KeyWrappingBackend
import com.example.core.security.PassphraseFailureReason
import com.example.data.di.DatabaseModule
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.entity.AccountMappingRuleEntity
import com.example.data.local.db.entity.FinancialAccountEntity
import com.example.data.local.db.entity.FinancialTransactionEntity
import com.example.data.local.db.entity.TransactionDetectionEventEntity
import com.example.data.remote.FirebaseAuthGateway
import com.example.data.remote.GoogleCredentialClient
import com.example.data.remote.GoogleCredentialResult
import com.example.data.repository.AuthRepositoryImpl
import com.example.data.repository.FinancialAccountRepositoryImpl
import com.example.data.repository.FinancialTransactionRepositoryImpl
import com.example.data.repository.SmartDetectionRepositoryImpl
import com.example.domain.model.AccountMappingRule
import com.example.domain.model.AccountType
import com.example.domain.model.AuthProviderType
import com.example.domain.model.AuthResult
import com.example.domain.model.AuthSessionState
import com.example.domain.model.AuthenticatedUser
import com.example.domain.model.ConsentRecord
import com.example.domain.model.DetectionConfidence
import com.example.domain.model.DetectionEventStatus
import com.example.domain.model.DetectionSettings
import com.example.domain.model.FinancialAccount
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.repository.ConsentRepository
import com.example.domain.usecase.CheckUserConsentUseCase
import com.example.domain.usecase.ObserveAuthSessionUseCase
import com.example.domain.usecase.RefreshAuthSessionUseCase
import com.example.domain.usecase.SaveUserConsentUseCase
import com.example.domain.usecase.SignInWithGoogleIdTokenUseCase
import com.example.domain.usecase.SignOutUseCase
import com.example.feature.authentication.AuthViewModel
import com.example.feature.smartdetection.engine.SmartTransactionParser
import com.example.service.SkyplusNotificationListenerService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2SecurityRemediationTest {

  private lateinit var context: Context
  private lateinit var database: AppDatabase
  private lateinit var accountRepo: FinancialAccountRepositoryImpl
  private lateinit var txnRepo: FinancialTransactionRepositoryImpl
  private lateinit var detectionRepo: SmartDetectionRepositoryImpl

  private val testDispatchers =
    object : DispatcherProvider {
      private val d = UnconfinedTestDispatcher()
      override val main: CoroutineDispatcher = d
      override val io: CoroutineDispatcher = d
      override val default: CoroutineDispatcher = d
    }

  private val simpleBackend =
    object : KeyWrappingBackend {
      override fun encrypt(raw: ByteArray): Pair<String, String> =
        Pair(Base64.encodeToString(raw, Base64.NO_WRAP), Base64.encodeToString(byteArrayOf(1, 2, 3, 4), Base64.NO_WRAP))

      override fun decrypt(encryptedBase64: String, ivBase64: String): ByteArray =
        Base64.decode(encryptedBase64, Base64.NO_WRAP)
    }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    accountRepo =
      FinancialAccountRepositoryImpl(
        database = database,
        accountDao = database.financialAccountDao(),
        transactionDao = database.financialTransactionDao(),
        mappingRuleDao = database.accountMappingRuleDao(),
        dispatchers = testDispatchers,
      )
    txnRepo =
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

  // --- Requirement 7.8: Release builds cannot use plaintext database fallback ---

  @Test
  fun `release builds and real-device paths prohibit plaintext database fallback`() {
    val prefs = context.getSharedPreferences("release_fallback_test_${System.nanoTime()}", Context.MODE_PRIVATE)
    val dbFile = File(context.cacheDir, "nonexistent_${System.nanoTime()}.db")
    val provider = DatabasePassphraseProvider(context, simpleBackend, prefs) { dbFile }

    // Release build + native SQLCipher failure -> must fail closed with DatabaseSecurityException
    var releaseException: DatabaseSecurityException? = null
    try {
      DatabaseModule.buildAppDatabase(
        context = context,
        passphraseProvider = provider,
        isReleaseBuild = true,
        isRobolectricRunner = true,
        nativeLibsLoader = { throw UnsatisfiedLinkError("Simulated missing native sqlcipher") },
      )
    } catch (e: DatabaseSecurityException) {
      releaseException = e
    }
    assertNotNull("Release build must throw DatabaseSecurityException instead of plaintext fallback", releaseException)
    assertEquals(PassphraseFailureReason.SQLCIPHER_INIT_FAILED, releaseException?.reason)

    // Non-Robolectric real-device path (even if not flagged release) -> must also fail closed
    var deviceException: DatabaseSecurityException? = null
    try {
      DatabaseModule.buildAppDatabase(
        context = context,
        passphraseProvider = provider,
        isReleaseBuild = false,
        isRobolectricRunner = false,
        nativeLibsLoader = { throw UnsatisfiedLinkError("Simulated device native init failure") },
      )
    } catch (e: DatabaseSecurityException) {
      deviceException = e
    }
    assertNotNull("Real device path must never fall back to plaintext SQLite", deviceException)
  }

  // --- Requirement 7.5: Key persistence failure and temporary Keystore failure ---

  @Test
  fun `key persistence commit failure fails closed and does not return unpersisted key`() {
    val failingCommitPrefs =
      FailingCommitSharedPreferences(
        context.getSharedPreferences("failing_prefs_${System.nanoTime()}", Context.MODE_PRIVATE),
        failCommit = true,
      )
    val dbFile = File(context.cacheDir, "no_db_${System.nanoTime()}.db")
    val provider = DatabasePassphraseProvider(context, simpleBackend, failingCommitPrefs) { dbFile }

    var caught: DatabaseSecurityException? = null
    try {
      provider.getOrCreatePassphrase()
    } catch (e: DatabaseSecurityException) {
      caught = e
    }
    assertNotNull(caught)
    assertEquals(PassphraseFailureReason.PERSISTENCE_FAILED, caught?.reason)
    assertNull(failingCommitPrefs.getString(DatabasePassphraseProvider.KEY_ENCRYPTED_PASSPHRASE, null))
  }

  @Test
  fun `round trip verification buffer is zeroed after passphrase comparison`() {
    val prefs = context.getSharedPreferences("passphrase_zero_${System.nanoTime()}", Context.MODE_PRIVATE)
    val dbFile = File(context.cacheDir, "zero_key_${System.nanoTime()}.db")
    var verificationBuffer: ByteArray? = null
    val observingBackend =
      object : KeyWrappingBackend {
        override fun encrypt(raw: ByteArray): Pair<String, String> = simpleBackend.encrypt(raw)
        override fun decrypt(encryptedBase64: String, ivBase64: String): ByteArray {
          return simpleBackend.decrypt(encryptedBase64, ivBase64).also { verificationBuffer = it }
        }
      }

    val returnedPassphrase =
      DatabasePassphraseProvider(context, observingBackend, prefs) { dbFile }.getOrCreatePassphrase()

    assertEquals(32, returnedPassphrase.size)
    assertNotNull(verificationBuffer)
    assertTrue("Temporary verification copy must be zeroed", verificationBuffer!!.all { it == 0.toByte() })
    assertTrue("Returned passphrase remains owned by the SQLCipher caller", returnedPassphrase.any { it != 0.toByte() })
    returnedPassphrase.fill(0)
  }

  @Test
  fun `invalid decrypted passphrase buffer is zeroed before failure`() {
    val prefs = context.getSharedPreferences("invalid_key_length_${System.nanoTime()}", Context.MODE_PRIVATE)
    prefs.edit()
      .putString(DatabasePassphraseProvider.KEY_ENCRYPTED_PASSPHRASE, "wrapped")
      .putString(DatabasePassphraseProvider.KEY_IV, "iv")
      .commit()
    val invalidPlaintext = ByteArray(16) { 7 }
    val invalidLengthBackend =
      object : KeyWrappingBackend {
        override fun encrypt(raw: ByteArray): Pair<String, String> = simpleBackend.encrypt(raw)
        override fun decrypt(encryptedBase64: String, ivBase64: String): ByteArray = invalidPlaintext
      }
    val provider = DatabasePassphraseProvider(
      context,
      invalidLengthBackend,
      prefs,
    ) { File(context.cacheDir, "unused_${System.nanoTime()}.db") }

    var caught: DatabaseSecurityException? = null
    try {
      provider.getOrCreatePassphrase()
    } catch (error: DatabaseSecurityException) {
      caught = error
    }

    assertEquals(PassphraseFailureReason.KEY_CORRUPTED, caught?.reason)
    assertTrue("Rejected decrypted passphrase must be zeroed", invalidPlaintext.all { it == 0.toByte() })
  }

  @Test
  fun `temporary Keystore decrypt failure preserves wrapped key and recovers on retry`() {
    val prefs = context.getSharedPreferences("keystore_temp_fail_${System.nanoTime()}", Context.MODE_PRIVATE)
    val dbFile = File(context.cacheDir, "existing_db_${System.nanoTime()}.db")

    var failDecryptTemporarily = false
    val flakyBackend =
      object : KeyWrappingBackend {
        override fun encrypt(raw: ByteArray): Pair<String, String> = simpleBackend.encrypt(raw)
        override fun decrypt(encryptedBase64: String, ivBase64: String): ByteArray {
          if (failDecryptTemporarily) {
            throw GeneralSecurityException("Transient TEE / Keystore IPC unavailable")
          }
          return simpleBackend.decrypt(encryptedBase64, ivBase64)
        }
      }

    val provider = DatabasePassphraseProvider(context, flakyBackend, prefs) { dbFile }
    val originalKey = provider.getOrCreatePassphrase()
    val savedEncBefore = prefs.getString(DatabasePassphraseProvider.KEY_ENCRYPTED_PASSPHRASE, null)
    val savedIvBefore = prefs.getString(DatabasePassphraseProvider.KEY_IV, null)

    // Simulate temporary Keystore failure
    failDecryptTemporarily = true
    var tempError: DatabaseSecurityException? = null
    try {
      provider.getOrCreatePassphrase()
    } catch (e: DatabaseSecurityException) {
      tempError = e
    }
    assertNotNull(tempError)
    assertEquals(PassphraseFailureReason.KEYSTORE_DECRYPT_FAILED, tempError?.reason)
    assertTrue(tempError!!.isRecoverable)
    // Verify wrapped key was NEVER overwritten or deleted
    assertEquals(savedEncBefore, prefs.getString(DatabasePassphraseProvider.KEY_ENCRYPTED_PASSPHRASE, null))
    assertEquals(savedIvBefore, prefs.getString(DatabasePassphraseProvider.KEY_IV, null))

    // Keystore recovers -> returns exact original passphrase
    failDecryptTemporarily = false
    val recoveredKey = provider.getOrCreatePassphrase()
    assertArrayEquals(originalKey, recoveredKey)
  }

  @Test
  fun `key corruption and existing DB with missing key both fail closed without key replacement`() {
    val prefs = context.getSharedPreferences("corrupt_key_${System.nanoTime()}", Context.MODE_PRIVATE)
    val dbFile = File(context.cacheDir, "existing_real_${System.nanoTime()}.db").apply {
      writeText("non-empty-sqlcipher-bytes")
    }
    try {
      // Case 1: existing DB on disk without stored key
      val missingKeyProvider = DatabasePassphraseProvider(context, simpleBackend, prefs) { dbFile }
      var missingErr: DatabaseSecurityException? = null
      try {
        missingKeyProvider.getOrCreatePassphrase()
      } catch (e: DatabaseSecurityException) {
        missingErr = e
      }
      assertEquals(PassphraseFailureReason.MISSING_KEY_FOR_EXISTING_DATABASE, missingErr?.reason)
      assertFalse(missingErr!!.isRecoverable)
      assertNull(prefs.getString(DatabasePassphraseProvider.KEY_ENCRYPTED_PASSPHRASE, null))

      // Case 2: AEADBadTagException on existing wrapped key
      prefs.edit()
        .putString(DatabasePassphraseProvider.KEY_ENCRYPTED_PASSPHRASE, "corrupted_cipher")
        .putString(DatabasePassphraseProvider.KEY_IV, "corrupted_iv")
        .commit()
      val corruptBackend =
        object : KeyWrappingBackend {
          override fun encrypt(raw: ByteArray) = simpleBackend.encrypt(raw)
          override fun decrypt(encryptedBase64: String, ivBase64: String): ByteArray =
            throw AEADBadTagException("Invalid GCM tag")
        }
      val corruptProvider = DatabasePassphraseProvider(context, corruptBackend, prefs) { dbFile }
      var corruptErr: DatabaseSecurityException? = null
      try {
        corruptProvider.getOrCreatePassphrase()
      } catch (e: DatabaseSecurityException) {
        corruptErr = e
      }
      assertEquals(PassphraseFailureReason.KEY_CORRUPTED, corruptErr?.reason)
      assertEquals("corrupted_cipher", prefs.getString(DatabasePassphraseProvider.KEY_ENCRYPTED_PASSPHRASE, null))
    } finally {
      dbFile.delete()
    }
  }

  @Test
  fun `nonempty database sidecar without key refuses passphrase regeneration`() {
    val prefs = context.getSharedPreferences("missing_sidecar_key_${System.nanoTime()}", Context.MODE_PRIVATE)
    val dbFile = File(context.cacheDir, "missing_main_db_${System.nanoTime()}.db")
    val sidecarFile = File("${dbFile.path}-wal").apply { writeText("sidecar data") }
    try {
      val provider = DatabasePassphraseProvider(context, simpleBackend, prefs) { dbFile }
      var caught: DatabaseSecurityException? = null
      try {
        provider.getOrCreatePassphrase()
      } catch (error: DatabaseSecurityException) {
        caught = error
      }
      assertEquals(PassphraseFailureReason.MISSING_KEY_FOR_EXISTING_DATABASE, caught?.reason)
      assertNull(prefs.getString(DatabasePassphraseProvider.KEY_ENCRYPTED_PASSPHRASE, null))
    } finally {
      sidecarFile.delete()
      dbFile.delete()
    }
  }

  @Test
  fun `security monitor tracks fail-closed state and recovers when retry succeeds`() {
    val monitor = DatabaseSecurityMonitor()
    var shouldFail = true
    monitor.registerRetryHandler {
      if (shouldFail) {
        monitor.reportIssue(
          DatabaseSecurityException(
            reason = PassphraseFailureReason.KEYSTORE_DECRYPT_FAILED,
            isRecoverable = true,
            message = "Locked",
          )
        )
        false
      } else {
        monitor.clearIssue()
        true
      }
    }
    assertFalse(monitor.retryInitialization())
    assertNotNull(monitor.securityIssue.value)

    shouldFail = false
    assertTrue(monitor.retryInitialization())
    assertNull(monitor.securityIssue.value)
  }

  // --- Requirement 7.6: Logout / account-switch state isolation & DAO UID predicates ---

  @Test
  fun `duplicate account mapping rules never choose an arbitrary account`() = runTest {
    val uid = "uid_duplicate_mapping"
    val accounts = listOf(
      FinancialAccount(
        id = "acc_dup_a",
        userId = uid,
        name = "Bank A",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 100_000L,
        currentBalanceMinor = 100_000L,
        accountNumberLast4 = "4321",
      ),
      FinancialAccount(
        id = "acc_dup_b",
        userId = uid,
        name = "Bank B",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 200_000L,
        currentBalanceMinor = 200_000L,
        accountNumberLast4 = "4321",
      ),
    )
    accounts.forEach { accountRepo.createAccount(uid, it) }
    detectionRepo.updateDetectionSettings(
      uid,
      DetectionSettings(
        userId = uid,
        isNotificationDetectionEnabled = true,
        isAutoSaveHighConfidenceEnabled = true,
      ),
    )
    database.accountMappingRuleDao().insertRule(
      AccountMappingRuleEntity(
        id = "rule_dup_a",
        userId = uid,
        accountId = "acc_dup_a",
        matchPattern = "4321",
        institutionHint = null,
        isAutoMapped = false,
        createdAt = 1L,
      )
    )
    database.accountMappingRuleDao().insertRule(
      AccountMappingRuleEntity(
        id = "rule_dup_b",
        userId = uid,
        accountId = "acc_dup_b",
        matchPattern = "4321",
        institutionHint = null,
        isAutoMapped = false,
        createdAt = 2L,
      )
    )

    val parsed = SmartTransactionParser.parse(
      "Rs. 250.00 debited from A/c XX4321 paid to Swiggy Ref 5566778899",
      "com.android.messaging",
      1_700_000_000_000L,
    )!!
    val event = detectionRepo.processIncomingEvent(uid, "com.android.messaging", "Bank SMS", parsed).getOrThrow()

    assertEquals(DetectionEventStatus.IN_REVIEW, event.status)
    assertEquals(100_000L, accountRepo.getAccountById(uid, "acc_dup_a")!!.currentBalanceMinor)
    assertEquals(200_000L, accountRepo.getAccountById(uid, "acc_dup_b")!!.currentBalanceMinor)
    assertTrue(
      "The API must reject a second rule for the same account reference",
      detectionRepo.addAccountMappingRule(
        uid,
        AccountMappingRule(
          id = "rule_dup_c",
          userId = uid,
          accountId = "acc_dup_a",
          matchPattern = "4321",
          institutionHint = null,
          isAutoMapped = false,
          createdAt = 3L,
        ),
      ).isFailure,
    )
  }

  @Test
  fun `logout and account switch isolate auto-saved notices and DAOs enforce UID predicates`() = runTest {
    val uidAlice = "uid_alice"
    val uidBob = "uid_bob"

    accountRepo.createAccount(
      uidAlice,
      FinancialAccount(
        id = "acc_alice",
        userId = uidAlice,
        name = "Alice HDFC",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 500_000L,
        currentBalanceMinor = 500_000L,
        accountNumberLast4 = "4321",
      ),
    )
    detectionRepo.addAccountMappingRule(
      uidAlice,
      AccountMappingRule(id = "rule_alice", userId = uidAlice, accountId = "acc_alice", matchPattern = "4321"),
    )
    detectionRepo.updateDetectionSettings(
      uidAlice,
      DetectionSettings(userId = uidAlice, isNotificationDetectionEnabled = true, isAutoSaveHighConfidenceEnabled = true),
    )

    val parsed =
      SmartTransactionParser.parse(
        "Rs. 250.00 debited from A/c XX4321 paid to Swiggy Ref 5566778899",
        "com.google.android.apps.nbu.paisa.user",
        1_700_000_000_000L,
      )!!

    detectionRepo.processIncomingEvent(uidAlice, "com.google.android.apps.nbu.paisa.user", "GPay", parsed).getOrThrow()

    // Notice is visible ONLY for Alice, NEVER for Bob
    assertNotNull(detectionRepo.observeLatestAutoSavedNotice(uidAlice).first())
    assertNull(detectionRepo.observeLatestAutoSavedNotice(uidBob).first())

    // Cross-user DAO operations must NOT affect Alice's balance or mapping rules
    accountRepo.adjustAccountBalance(uidBob, "acc_alice", -100_000L).getOrThrow()
    assertEquals(475_000L, accountRepo.getAccountById(uidAlice, "acc_alice")!!.currentBalanceMinor)

    detectionRepo.deleteAccountMappingRule(uidBob, "rule_alice").getOrThrow()
    assertEquals(1, detectionRepo.getAccountMappingRules(uidAlice).first().size)

    // Sign out clears in-memory auto-saved notice
    val gateway = FakeGateway()
    val credClient = FakeCredClient()
    val authRepo = AuthRepositoryImpl(gateway, credClient, detectionRepo)
    authRepo.signOut()
    assertNull(detectionRepo.observeLatestAutoSavedNotice(uidAlice).first())
  }

  // --- Requirement 7.7: Notification processing with missing / outdated legal consent ---

  @Test
  fun `notification listener blocks processing when legal consent is missing or outdated`() = runTest {
    val uid = "uid_notif_consent"
    accountRepo.createAccount(
      uid,
      FinancialAccount(
        id = "acc_n1",
        userId = uid,
        name = "ICICI 8899",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 200_000L,
        currentBalanceMinor = 200_000L,
        accountNumberLast4 = "8899",
      ),
    )
    detectionRepo.updateDetectionSettings(
      uid,
      DetectionSettings(userId = uid, isNotificationDetectionEnabled = true, isAutoSaveHighConfidenceEnabled = true),
    )

    val gateway = FakeGateway().apply {
      sessionFlow.value = AuthSessionState.Authenticated(
        AuthenticatedUser(uid, "User", "u@test.com", null, true, setOf(AuthProviderType.GOOGLE))
      )
    }
    val fakeConsentRepo = MutableFakeConsentRepo()

    val service = SkyplusNotificationListenerService().apply {
      authRepository = AuthRepositoryImpl(gateway, FakeCredClient(), detectionRepo)
      consentRepository = fakeConsentRepo
      detectionRepository = detectionRepo
      dispatchers = testDispatchers
    }

    val smsLikeNotif = "Rs. 400.00 debited from A/c XX8899 paid to Uber Ref N1234567"

    // 1. Missing consent -> MUST be blocked
    service.processNotificationContent("com.phonepe.app", "PhonePe", smsLikeNotif, 1_700_000_000_000L)
    assertEquals(0, detectionRepo.getDetectionEvents(uid).first().size)

    // 2. Outdated consent (0.9.0 != 1.0.0) -> MUST be blocked
    fakeConsentRepo.records[uid] = ConsentRecord("0.9.0", ConsentRecord.CURRENT_PRIVACY_VERSION, 1000L)
    service.processNotificationContent("com.phonepe.app", "PhonePe", smsLikeNotif, 1_700_000_010_000L)
    assertEquals(0, detectionRepo.getDetectionEvents(uid).first().size)

    // 3. Up-to-date consent -> allowed and processed
    fakeConsentRepo.records[uid] =
      ConsentRecord(ConsentRecord.CURRENT_TERMS_VERSION, ConsentRecord.CURRENT_PRIVACY_VERSION, 2000L)
    service.processNotificationContent("com.phonepe.app", "PhonePe", smsLikeNotif, 1_700_000_020_000L)
    assertEquals(1, detectionRepo.getDetectionEvents(uid).first().size)
    assertEquals(160_000L, accountRepo.getAccountById(uid, "acc_n1")!!.currentBalanceMinor)
  }

  // --- Requirement 5: Consent DataStore error handling + spaced OTP/2FA/digit masking ---

  @Test
  fun `consent DataStore load and save errors fail closed and preserve retry state`() {
    val testDispatcher = StandardTestDispatcher()
    Dispatchers.setMain(testDispatcher)
    try {
      runTest(testDispatcher) {
        val gateway = FakeGateway()
        val credClient = FakeCredClient()
        val authRepo = AuthRepositoryImpl(gateway, credClient, detectionRepo)
        val flakyConsentRepo = MutableFakeConsentRepo().apply { failReads = true; failWrites = true }

        val viewModel =
          AuthViewModel(
            observeAuthSessionUseCase = ObserveAuthSessionUseCase(authRepo),
            signInWithGoogleIdTokenUseCase = SignInWithGoogleIdTokenUseCase(authRepo),
            refreshAuthSessionUseCase = RefreshAuthSessionUseCase(authRepo),
            signOutUseCase = SignOutUseCase(authRepo),
            googleCredentialClient = credClient,
            checkUserConsentUseCase = CheckUserConsentUseCase(flakyConsentRepo),
            saveUserConsentUseCase = SaveUserConsentUseCase(flakyConsentRepo),
            smartDetectionRepository = detectionRepo,
          )

        gateway.sessionFlow.value = AuthSessionState.Authenticated(
          AuthenticatedUser("uid_err", "Err", "err@test.com", null, true, setOf(AuthProviderType.GOOGLE))
        )
        advanceUntilIdle()

        // Read error fails closed: requiresConsent stays true and load error is exposed
        assertTrue(viewModel.uiState.value.requiresConsent)
        assertEquals(R.string.legal_consent_load_error, viewModel.uiState.value.consentErrorMessageRes)

        // Save error fails closed: requiresConsent stays true and save error is exposed
        viewModel.onAcceptConsent()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.requiresConsent)
        assertEquals(R.string.legal_consent_save_error, viewModel.uiState.value.consentErrorMessageRes)

        // Recovery on retry
        flakyConsentRepo.failReads = false
        flakyConsentRepo.failWrites = false
        viewModel.onRetryConsentCheck()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.consentErrorMessageRes)
        assertTrue(viewModel.uiState.value.requiresConsent)

        viewModel.onAcceptConsent()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.requiresConsent)
        assertNull(viewModel.uiState.value.consentErrorMessageRes)
      }
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `parser rejects spaced OTP passcode 2FA and masks long numeric sequences in merchant`() {
    val spacedAlerts =
      listOf(
        "Your O T P for Rs. 5,000.00 purchase is 992211.",
        "Enter O.T.P 445566 to pay USD 120.00.",
        "Use pass code 887766 to approve INR 900.00 debit.",
        "Your 2FA code for INR 1,500.00 transfer is 123456.",
        "Your P I N 1234 was requested for Rs 300.00.",
      )
    for (msg in spacedAlerts) {
      assertNull("Spaced sensitive alert must be rejected: $msg", SmartTransactionParser.parse(msg))
    }

    val numericMerchant =
      SmartTransactionParser.parse(
        "Rs. 500.00 paid to Account 987654321012 via UPI Ref 4455667788",
        "com.google.android.apps.nbu.paisa.user",
      )!!
    assertFalse(numericMerchant.merchantOrPayee.orEmpty().contains("987654321012"))
  }

  @Test
  fun `account update succeeds for owner and rejects cross-UID and ownership transfer attempts`() = runTest {
    val uidAlice = "uid_alice_owner"
    val uidBob = "uid_bob_attacker"

    val aliceAccount =
      FinancialAccount(
        id = "acc_shared_id_check",
        userId = uidAlice,
        name = "Alice Primary",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 250_000L,
        currentBalanceMinor = 250_000L,
        institutionName = "HDFC",
        accountNumberLast4 = "4321",
        createdAt = 1000L,
        updatedAt = 1000L,
      )
    accountRepo.createAccount(uidAlice, aliceAccount).getOrThrow()

    // 1. Same-user update succeeds
    val legitUpdate = aliceAccount.copy(name = "Alice Salary Renamed", institutionName = "HDFC Imperia")
    assertTrue(accountRepo.updateAccount(uidAlice, legitUpdate).isSuccess)
    val afterLegit = accountRepo.getAccountById(uidAlice, aliceAccount.id)!!
    assertEquals("Alice Salary Renamed", afterLegit.name)
    assertEquals("HDFC Imperia", afterLegit.institutionName)
    assertEquals(uidAlice, afterLegit.userId)
    assertEquals(1000L, afterLegit.createdAt)

    // 2. Cross-user update with known account ID + attacker UID in entity is rejected (repo & DAO)
    val spoofedBobAccount = afterLegit.copy(userId = uidBob, name = "Stolen By Bob", currentBalanceMinor = 0L)
    assertTrue(accountRepo.updateAccount(uidBob, spoofedBobAccount).isFailure)
    assertEquals(
      0,
      database.financialAccountDao().updateAccount(
        FinancialAccountEntity.fromDomain(spoofedBobAccount),
        authenticatedUserId = uidBob,
      ),
    )

    // 3. Cross-user update passing Alice's unmodified userId under Bob's authenticated UID is rejected
    assertTrue(accountRepo.updateAccount(uidBob, afterLegit.copy(name = "Bob Tamper")).isFailure)
    assertEquals(
      0,
      database.financialAccountDao().updateAccount(
        FinancialAccountEntity.fromDomain(afterLegit.copy(name = "Bob Tamper")),
        authenticatedUserId = uidBob,
      ),
    )

    // 4. Ownership transfer by owner (Alice trying to assign userId = uidBob) is rejected
    assertTrue(accountRepo.updateAccount(uidAlice, afterLegit.copy(userId = uidBob)).isFailure)
    assertEquals(
      0,
      database.financialAccountDao().updateAccount(
        FinancialAccountEntity.fromDomain(afterLegit.copy(userId = uidBob)),
        authenticatedUserId = uidAlice,
      ),
    )

    // 5. Verify original owner's record remains completely unchanged
    val intact = accountRepo.getAccountById(uidAlice, aliceAccount.id)!!
    assertEquals(uidAlice, intact.userId)
    assertEquals("Alice Salary Renamed", intact.name)
    assertEquals("HDFC Imperia", intact.institutionName)
    assertEquals(250_000L, intact.currentBalanceMinor)
    assertNull(accountRepo.getAccountById(uidBob, aliceAccount.id))
  }

  @Test
  fun `DAO insertTransaction insertEvent and insertRule abort on conflicting IDs across UIDs`() = runTest {
    val uidAlice = "uid_alice_pk"
    val uidBob = "uid_bob_pk"

    accountRepo.createAccount(
      uidAlice,
      FinancialAccount(
        id = "acc_a_pk",
        userId = uidAlice,
        name = "Alice Acc",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 100_000L,
        currentBalanceMinor = 100_000L,
      ),
    ).getOrThrow()
    accountRepo.createAccount(
      uidBob,
      FinancialAccount(
        id = "acc_b_pk",
        userId = uidBob,
        name = "Bob Acc",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 100_000L,
        currentBalanceMinor = 100_000L,
      ),
    ).getOrThrow()

    // Transaction insert conflict
    val aliceTxn =
      FinancialTransactionEntity(
        id = "txn_conflict_1",
        userId = uidAlice,
        accountId = "acc_a_pk",
        type = TransactionType.EXPENSE.name,
        status = TransactionStatus.COMPLETED.name,
        amountMinor = 5_000L,
        currencyCode = "INR",
        category = "Food",
        merchantOrPayee = "Alice Cafe",
        notes = null,
        transactionDate = 1000L,
        linkedTransferId = null,
        transferDestinationAccountId = null,
        isAutomaticallyDetected = false,
        sourceApplication = null,
        referenceId = null,
        confidenceScore = null,
        createdAt = 1000L,
        updatedAt = 1000L,
      )
    database.financialTransactionDao().insertTransaction(aliceTxn)
    val bobTxnResult =
      runCatching {
        database.financialTransactionDao().insertTransaction(
          aliceTxn.copy(userId = uidBob, accountId = "acc_b_pk", merchantOrPayee = "Bob Hijack")
        )
      }
    assertTrue("insertTransaction must abort on existing PK across UIDs", bobTxnResult.isFailure)
    assertEquals("Alice Cafe", database.financialTransactionDao().getTransactionById(uidAlice, "txn_conflict_1")?.merchantOrPayee)
    assertNull(database.financialTransactionDao().getTransactionById(uidBob, "txn_conflict_1"))

    // Detection event insert conflict
    val aliceEvent =
      TransactionDetectionEventEntity(
        id = "evt_conflict_1",
        userId = uidAlice,
        rawPackageName = "com.phonepe.app",
        senderOrTitle = "PhonePe",
        detectedAmountMinor = 5_000L,
        detectedCurrency = "INR",
        detectedType = TransactionType.EXPENSE.name,
        detectedMerchant = "Alice Merchant",
        detectedAccountLast4 = "4321",
        referenceId = "REF_ALICE",
        confidence = 0.9f,
        confidenceLevel = DetectionConfidence.HIGH.name,
        confidenceReasonsJoined = "parsed",
        dedupFingerprint = "fp_alice",
        status = DetectionEventStatus.IN_REVIEW.name,
        mappedAccountId = "acc_a_pk",
        createdTransactionId = null,
        receivedAt = 1000L,
      )
    database.transactionDetectionEventDao().insertEvent(aliceEvent)
    val bobEventResult =
      runCatching {
        database.transactionDetectionEventDao().insertEvent(
          aliceEvent.copy(userId = uidBob, detectedMerchant = "Bob Hijack")
        )
      }
    assertTrue("insertEvent must abort on existing PK across UIDs", bobEventResult.isFailure)
    assertEquals("Alice Merchant", database.transactionDetectionEventDao().getEventById(uidAlice, "evt_conflict_1")?.detectedMerchant)
    assertNull(database.transactionDetectionEventDao().getEventById(uidBob, "evt_conflict_1"))

    // Account mapping rule insert conflict + cross-UID target account protection in repo
    val aliceRule =
      AccountMappingRuleEntity(
        id = "rule_conflict_1",
        userId = uidAlice,
        accountId = "acc_a_pk",
        matchPattern = "4321",
        institutionHint = "HDFC",
        isAutoMapped = false,
        createdAt = 1000L,
      )
    database.accountMappingRuleDao().insertRule(aliceRule)
    val bobRuleDaoResult =
      runCatching {
        database.accountMappingRuleDao().insertRule(
          aliceRule.copy(userId = uidBob, accountId = "acc_b_pk")
        )
      }
    assertTrue("insertRule must abort on existing PK across UIDs", bobRuleDaoResult.isFailure)
    val bobRuleRepoResult =
      detectionRepo.addAccountMappingRule(
        uidBob,
        AccountMappingRule(userId = uidBob, accountId = "acc_a_pk", matchPattern = "4321"),
      )
    assertTrue("addAccountMappingRule must reject targeting another user's accountId", bobRuleRepoResult.isFailure)
    assertEquals(1, detectionRepo.getAccountMappingRules(uidAlice).first().size)
    assertEquals(0, detectionRepo.getAccountMappingRules(uidBob).first().size)
  }

  private class FakeCredClient : GoogleCredentialClient {
    override suspend fun requestGoogleIdToken(context: Context) = GoogleCredentialResult.Cancelled
    override suspend fun clearCredentialState() = Unit
  }

  private class FakeGateway : FirebaseAuthGateway {
    val sessionFlow = MutableStateFlow<AuthSessionState>(AuthSessionState.Unauthenticated)
    override fun observeSession(): Flow<AuthSessionState> = sessionFlow
    override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult<AuthenticatedUser> =
      AuthResult.Failure(com.example.domain.model.AuthError.FirebaseAuthenticationFailed)
    override suspend fun reloadCurrentUser(): AuthResult<AuthenticatedUser?> = AuthResult.Success(null)
    override suspend fun signOut() {
      sessionFlow.value = AuthSessionState.Unauthenticated
    }
  }

  private class MutableFakeConsentRepo : ConsentRepository {
    val records = mutableMapOf<String, ConsentRecord>()
    var failReads = false
    var failWrites = false

    override suspend fun getConsent(uid: String): ConsentRecord? {
      if (failReads) throw IOException("Simulated DataStore read failure")
      return records[uid]
    }

    override suspend fun saveConsent(uid: String, termsVersion: String, privacyVersion: String) {
      if (failWrites) throw IOException("Simulated DataStore write failure")
      records[uid] = ConsentRecord(termsVersion, privacyVersion, System.currentTimeMillis())
    }

    override suspend fun clearConsent(uid: String) {
      records.remove(uid)
    }
  }

  private class FailingCommitSharedPreferences(
    private val delegate: SharedPreferences,
    private val failCommit: Boolean,
  ) : SharedPreferences by delegate {
    override fun edit(): SharedPreferences.Editor {
      val realEditor = delegate.edit()
      return object : SharedPreferences.Editor by realEditor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
          realEditor.putString(key, value)
          return this
        }

        override fun commit(): Boolean {
          return if (failCommit) false else realEditor.commit()
        }
      }
    }
  }
}
