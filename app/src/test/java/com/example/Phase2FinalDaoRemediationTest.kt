package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.entity.FinancialTransactionEntity
import com.example.data.local.db.entity.TransactionDetectionEventEntity
import com.example.data.repository.FinancialAccountRepositoryImpl
import com.example.data.repository.FinancialTransactionRepositoryImpl
import com.example.data.repository.SmartDetectionRepositoryImpl
import com.example.domain.model.AccountMappingRule
import com.example.domain.model.AccountType
import com.example.domain.model.AuthError
import com.example.domain.model.AuthProviderType
import com.example.domain.model.AuthResult
import com.example.domain.model.AuthSessionState
import com.example.domain.model.AuthenticatedUser
import com.example.domain.model.AutoSavedTransactionNotice
import com.example.domain.model.DetectionConfidence
import com.example.domain.model.DetectionEventStatus
import com.example.domain.model.DetectionSettings
import com.example.domain.model.FinancialAccount
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.ParsedFinancialMessage
import com.example.domain.model.TransactionDetectionEvent
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.FinancialAccountRepository
import com.example.domain.repository.FinancialTransactionRepository
import com.example.domain.repository.SmartDetectionRepository
import com.example.domain.usecase.ApproveReviewItemUseCase
import com.example.domain.usecase.CreateAccountUseCase
import com.example.domain.usecase.DeleteAccountUseCase
import com.example.domain.usecase.DeleteTransactionUseCase
import com.example.domain.usecase.DetectionSettingsUseCases
import com.example.domain.usecase.DismissReviewItemUseCase
import com.example.domain.usecase.GetAccountsUseCase
import com.example.domain.usecase.GetDetectionEventsUseCase
import com.example.domain.usecase.GetReviewQueueUseCase
import com.example.domain.usecase.GetTransactionsUseCase
import com.example.domain.usecase.RecordExpenseUseCase
import com.example.domain.usecase.RecordGeneralTransactionUseCase
import com.example.domain.usecase.RecordIncomeUseCase
import com.example.domain.usecase.RecordTransferUseCase
import com.example.domain.usecase.SetAccountActiveUseCase
import com.example.domain.usecase.UpdateAccountUseCase
import com.example.domain.usecase.UpdateTransactionUseCase
import com.example.feature.accounts.AccountsViewModel
import com.example.feature.smartdetection.SmartDetectionViewModel
import com.example.feature.smartdetection.engine.SmartTransactionParser
import com.example.feature.transactions.TransactionsViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2FinalDaoRemediationTest {

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

  @Test
  fun `FinancialTransactionDao and repository enforce UID ownership and immutable userId on update`() = runTest {
    val uidAlice = "uid_tx_alice"
    val uidBob = "uid_tx_bob"

    accountRepo.createAccount(
      uidAlice,
      FinancialAccount(
        id = "acc_tx_alice",
        userId = uidAlice,
        name = "Alice Bank",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 100_000L,
        currentBalanceMinor = 100_000L,
      ),
    ).getOrThrow()

    accountRepo.createAccount(
      uidBob,
      FinancialAccount(
        id = "acc_tx_bob",
        userId = uidBob,
        name = "Bob Bank",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 50_000L,
        currentBalanceMinor = 50_000L,
      ),
    ).getOrThrow()

    val aliceTxn =
      txnRepo.recordExpense(
        uidAlice,
        FinancialTransaction(
          id = "txn_owned_alice",
          userId = uidAlice,
          accountId = "acc_tx_alice",
          type = TransactionType.EXPENSE,
          amountMinor = 10_000L,
          currencyCode = "INR",
          category = "Food",
          merchantOrPayee = "Cafe Original",
          createdAt = 2_000L,
          updatedAt = 2_000L,
        ),
      ).getOrThrow()
    assertEquals(90_000L, accountRepo.getAccountById(uidAlice, "acc_tx_alice")!!.currentBalanceMinor)

    // 1. Same-user update succeeds
    val updatedLegit = aliceTxn.copy(amountMinor = 15_000L, merchantOrPayee = "Cafe Updated")
    assertTrue(txnRepo.updateTransaction(uidAlice, updatedLegit, aliceTxn).isSuccess)
    val afterLegit = txnRepo.getTransactionById(uidAlice, aliceTxn.id)!!
    assertEquals(15_000L, afterLegit.amountMinor)
    assertEquals("Cafe Updated", afterLegit.merchantOrPayee)
    assertEquals(uidAlice, afterLegit.userId)
    assertEquals(2_000L, afterLegit.createdAt)
    assertEquals(85_000L, accountRepo.getAccountById(uidAlice, "acc_tx_alice")!!.currentBalanceMinor)

    // 2. Cross-UID update rejected via repository & DAO (known transaction ID + attacker UID)
    val bobSpoofed = afterLegit.copy(userId = uidBob, accountId = "acc_tx_bob", amountMinor = 1_000L, merchantOrPayee = "Hijacked")
    assertTrue(txnRepo.updateTransaction(uidBob, bobSpoofed, afterLegit).isFailure)
    assertEquals(
      0,
      database.financialTransactionDao().updateTransaction(
        FinancialTransactionEntity.fromDomain(bobSpoofed),
        authenticatedUserId = uidBob,
      ),
    )

    // 3. Cross-UID update passing Alice's entity under Bob's authenticated UID is rejected
    assertTrue(txnRepo.updateTransaction(uidBob, afterLegit.copy(merchantOrPayee = "Bob Edit"), afterLegit).isFailure)
    assertEquals(
      0,
      database.financialTransactionDao().updateTransaction(
        FinancialTransactionEntity.fromDomain(afterLegit.copy(merchantOrPayee = "Bob Edit")),
        authenticatedUserId = uidBob,
      ),
    )

    // 4. Ownership cannot be transferred by owner through an update (DAO scoped + Repo)
    assertTrue(txnRepo.updateTransaction(uidAlice, afterLegit.copy(userId = uidBob), afterLegit).isFailure)
    assertEquals(
      0,
      database.financialTransactionDao().updateTransaction(
        FinancialTransactionEntity.fromDomain(afterLegit.copy(userId = uidBob)),
        authenticatedUserId = uidAlice,
      ),
    )
    val rowsScopedBypass =
      database.financialTransactionDao().updateTransactionScoped(
        id = aliceTxn.id,
        userId = uidBob,
        accountId = "acc_tx_bob",
        type = TransactionType.EXPENSE.name,
        status = TransactionStatus.COMPLETED.name,
        amountMinor = 999L,
        currencyCode = "INR",
        category = "Tamper",
        merchantOrPayee = "Tamper",
        notes = null,
        transactionDate = 3_000L,
        linkedTransferId = null,
        transferDestinationAccountId = null,
        isAutomaticallyDetected = false,
        sourceApplication = null,
        referenceId = null,
        confidenceScore = null,
        updatedAt = 3_000L,
        normalizedMerchant = "tamper",
      )
    assertEquals(0, rowsScopedBypass)

    // 5. Original owner's record & balances remain intact
    val intact = txnRepo.getTransactionById(uidAlice, aliceTxn.id)!!
    assertEquals(uidAlice, intact.userId)
    assertEquals(15_000L, intact.amountMinor)
    assertEquals("Cafe Updated", intact.merchantOrPayee)
    assertEquals(85_000L, accountRepo.getAccountById(uidAlice, "acc_tx_alice")!!.currentBalanceMinor)
    assertEquals(50_000L, accountRepo.getAccountById(uidBob, "acc_tx_bob")!!.currentBalanceMinor)
    assertNull(txnRepo.getTransactionById(uidBob, aliceTxn.id))
  }

  @Test
  fun `TransactionDetectionEventDao enforces UID ownership and immutable userId on update`() = runTest {
    val uidAlice = "uid_evt_alice"
    val uidBob = "uid_evt_bob"

    val aliceEvent =
      TransactionDetectionEventEntity(
        id = "evt_owned_1",
        userId = uidAlice,
        rawPackageName = "com.phonepe.app",
        senderOrTitle = "PhonePe",
        detectedAmountMinor = 25_000L,
        detectedCurrency = "INR",
        detectedType = TransactionType.EXPENSE.name,
        detectedMerchant = "Original Merchant",
        detectedAccountLast4 = "4321",
        referenceId = "REF_EVT_1",
        confidence = 0.75f,
        confidenceLevel = DetectionConfidence.MEDIUM.name,
        confidenceReasonsJoined = "parsed",
        dedupFingerprint = "fp_evt_1",
        status = DetectionEventStatus.IN_REVIEW.name,
        mappedAccountId = null,
        createdTransactionId = null,
        receivedAt = 5_000L,
      )
    val eventDao = database.transactionDetectionEventDao()
    eventDao.insertEvent(aliceEvent)

    // 1. Same-user event update succeeds
    val updatedRows =
      eventDao.updateEvent(
        aliceEvent.copy(detectedMerchant = "Confirmed Cafe"),
        authenticatedUserId = uidAlice,
      )
    assertEquals(1, updatedRows)
    assertEquals("Confirmed Cafe", eventDao.getEventById(uidAlice, aliceEvent.id)?.detectedMerchant)

    // 2. Cross-UID event updates rejected (spoofed userId and victim entity under attacker UID)
    assertEquals(
      0,
      eventDao.updateEvent(
        aliceEvent.copy(userId = uidBob, detectedMerchant = "Hijack", status = DetectionEventStatus.DISMISSED.name),
        authenticatedUserId = uidBob,
      ),
    )
    assertEquals(
      0,
      eventDao.updateEvent(
        aliceEvent.copy(detectedMerchant = "Bob Overwrite", status = DetectionEventStatus.DISMISSED.name),
        authenticatedUserId = uidBob,
      ),
    )

    // 3. Ownership cannot be transferred even by Alice
    assertEquals(
      0,
      eventDao.updateEvent(
        aliceEvent.copy(userId = uidBob),
        authenticatedUserId = uidAlice,
      ),
    )

    // 4. Blank authenticatedUserId is rejected
    assertEquals(0, eventDao.updateEvent(aliceEvent, authenticatedUserId = ""))
    assertEquals(0, eventDao.updateEvent(aliceEvent, authenticatedUserId = "   "))

    // 4. Original owner's event remains unchanged
    val intact = eventDao.getEventById(uidAlice, aliceEvent.id)!!
    assertEquals(uidAlice, intact.userId)
    assertEquals("Confirmed Cafe", intact.detectedMerchant)
    assertEquals(DetectionEventStatus.IN_REVIEW.name, intact.status)
    assertNull(eventDao.getEventById(uidBob, aliceEvent.id))
  }

  @Test
  fun `dismissReviewEvent enforces UID ownership atomically and handles repeated dismissal safely`() = runTest {
    val uidAlice = "uid_dismiss_alice"
    val uidBob = "uid_dismiss_bob"

    detectionRepo.updateDetectionSettings(
      uidAlice,
      DetectionSettings(userId = uidAlice, isNotificationDetectionEnabled = true, isAutoSaveHighConfidenceEnabled = false),
    )

    val parsed =
      SmartTransactionParser.parse(
        "Rs. 320.00 debited from A/c XX1122 paid to Metro Ref DSM123456",
        "com.phonepe.app",
        1_700_100_000_000L,
      )!!
    val event = detectionRepo.processIncomingEvent(uidAlice, "com.phonepe.app", "PhonePe", parsed).getOrThrow()
    assertEquals(DetectionEventStatus.IN_REVIEW, event.status)

    // 1. Cross-UID dismissal rejected & original event remains unchanged
    val crossResult = detectionRepo.dismissReviewEvent(uidBob, event.id)
    assertTrue(crossResult.isFailure)
    assertEquals(DetectionEventStatus.IN_REVIEW, detectionRepo.getEventById(uidAlice, event.id)?.status)

    // 2. Missing event dismissal fails cleanly
    assertTrue(detectionRepo.dismissReviewEvent(uidAlice, "nonexistent_event").isFailure)

    // 3. Owner can dismiss their own review event
    assertTrue(detectionRepo.dismissReviewEvent(uidAlice, event.id).isSuccess)
    assertEquals(DetectionEventStatus.DISMISSED, detectionRepo.getEventById(uidAlice, event.id)?.status)
    assertEquals(0, detectionRepo.getReviewQueue(uidAlice).first().size)

    // 4. Repeated dismissal is idempotent and handled safely without corrupting state
    assertTrue(detectionRepo.dismissReviewEvent(uidAlice, event.id).isSuccess)
    assertEquals(DetectionEventStatus.DISMISSED, detectionRepo.getEventById(uidAlice, event.id)?.status)
  }

  @Test
  fun `AccountsViewModel updateAccount enforces authenticated UID and blocks repository calls on rejection`() {
    val testDispatcher = StandardTestDispatcher()
    Dispatchers.setMain(testDispatcher)
    try {
      runTest(testDispatcher) {
        val uidAlice = "uid_vm_alice"
        val uidBob = "uid_vm_bob"
        val authFlow = MutableStateFlow<AuthSessionState>(AuthSessionState.Unauthenticated)
        val fakeAuthRepo =
          object : AuthRepository {
            override fun observeAuthSession(): Flow<AuthSessionState> = authFlow
            override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult<AuthenticatedUser> =
              AuthResult.Failure(AuthError.FirebaseAuthenticationFailed)
            override suspend fun refreshAndValidateSession(): AuthResult<AuthenticatedUser?> =
              AuthResult.Success(null)
            override suspend fun signOut(): AuthResult<Unit> = AuthResult.Success(Unit)
          }

        val recordedUpdates = mutableListOf<Pair<String, FinancialAccount>>()
        val spyAccountRepo =
          object : FinancialAccountRepository {
            override fun getAccounts(userId: String): Flow<List<FinancialAccount>> = flowOf(emptyList())
            override suspend fun getAccountById(userId: String, accountId: String): FinancialAccount? = null
            override fun getAccountByIdFlow(userId: String, accountId: String): Flow<FinancialAccount?> = flowOf(null)
            override suspend fun createAccount(authenticatedUserId: String, account: FinancialAccount) =
              Result.success(account.copy(userId = authenticatedUserId))
            override suspend fun updateAccount(authenticatedUserId: String, account: FinancialAccount): Result<Unit> {
              recordedUpdates += (authenticatedUserId to account)
              return Result.success(Unit)
            }
            override suspend fun setAccountActive(userId: String, accountId: String, isActive: Boolean) = Result.success(Unit)
            override suspend fun getAccountTransactionCount(userId: String, accountId: String) = 0
            override suspend fun deleteAccount(userId: String, accountId: String, deleteLinkedTransactions: Boolean) = Result.success(Unit)
            override suspend fun adjustAccountBalance(userId: String, accountId: String, deltaMinor: Long) = Result.success(Unit)
          }

        val viewModel =
          AccountsViewModel(
            authRepository = fakeAuthRepo,
            getAccountsUseCase = GetAccountsUseCase(spyAccountRepo),
            createAccountUseCase = CreateAccountUseCase(spyAccountRepo),
            updateAccountUseCase = UpdateAccountUseCase(spyAccountRepo),
            deleteAccountUseCase = DeleteAccountUseCase(spyAccountRepo),
            setAccountActiveUseCase = SetAccountActiveUseCase(spyAccountRepo),
          )

        val aliceAccount =
          FinancialAccount(
            id = "acc_vm_1",
            userId = uidAlice,
            name = "Alice Primary",
            type = AccountType.BANK,
            currencyCode = "INR",
            initialBalanceMinor = 50_000L,
            currentBalanceMinor = 50_000L,
          )

        // 1. Missing authentication is rejected and repository is NOT called
        viewModel.updateAccount(aliceAccount.copy(name = "Unauth Edit"))
        advanceUntilIdle()
        assertEquals(0, recordedUpdates.size)
        assertTrue(viewModel.userMessage.value.orEmpty().contains("Authentication required"))

        // 2. Mismatched UID is rejected and repository is NOT called
        authFlow.value =
          AuthSessionState.Authenticated(
            AuthenticatedUser(uidBob, "Bob", "bob@test.com", null, true, setOf(AuthProviderType.GOOGLE))
          )
        viewModel.updateAccount(aliceAccount.copy(name = "Bob Tamper"))
        advanceUntilIdle()
        assertEquals(0, recordedUpdates.size)
        assertTrue(viewModel.userMessage.value.orEmpty().contains("ownership verification failed"))

        // 3. Authenticated matching UID succeeds and forwards authenticated UID to repository
        authFlow.value =
          AuthSessionState.Authenticated(
            AuthenticatedUser(uidAlice, "Alice", "alice@test.com", null, true, setOf(AuthProviderType.GOOGLE))
          )
        val validEdit = aliceAccount.copy(name = "Alice Verified")
        viewModel.updateAccount(validEdit)
        advanceUntilIdle()
        assertEquals(1, recordedUpdates.size)
        assertEquals(uidAlice, recordedUpdates.single().first)
        assertEquals("Alice Verified", recordedUpdates.single().second.name)
        assertEquals("Account updated", viewModel.userMessage.value)
      }
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `concurrent duplicate notifications save at most once and never overwrite cross-UID records`() = runTest {
    val tempDbFile = File(context.cacheDir, "concurrent_security_test_${System.nanoTime()}.db")
    val fileDb =
      Room.databaseBuilder(context, AppDatabase::class.java, tempDbFile.absolutePath)
        .allowMainThreadQueries()
        .build()

    try {
      val fileAccountRepo =
        FinancialAccountRepositoryImpl(
          database = fileDb,
          accountDao = fileDb.financialAccountDao(),
          transactionDao = fileDb.financialTransactionDao(),
          mappingRuleDao = fileDb.accountMappingRuleDao(),
          dispatchers =
            object : DispatcherProvider {
              override val main: CoroutineDispatcher = Dispatchers.IO
              override val io: CoroutineDispatcher = Dispatchers.IO
              override val default: CoroutineDispatcher = Dispatchers.IO
            },
        )
      val fileTxnRepo =
        FinancialTransactionRepositoryImpl(
          database = fileDb,
          transactionDao = fileDb.financialTransactionDao(),
          accountDao = fileDb.financialAccountDao(),
          dispatchers =
            object : DispatcherProvider {
              override val main: CoroutineDispatcher = Dispatchers.IO
              override val io: CoroutineDispatcher = Dispatchers.IO
              override val default: CoroutineDispatcher = Dispatchers.IO
            },
        )
      val fileDetectionRepo =
        SmartDetectionRepositoryImpl(
          context = context,
          database = fileDb,
          eventDao = fileDb.transactionDetectionEventDao(),
          mappingDao = fileDb.accountMappingRuleDao(),
          accountDao = fileDb.financialAccountDao(),
          transactionDao = fileDb.financialTransactionDao(),
          transactionRepository = fileTxnRepo,
          dispatchers =
            object : DispatcherProvider {
              override val main: CoroutineDispatcher = Dispatchers.IO
              override val io: CoroutineDispatcher = Dispatchers.IO
              override val default: CoroutineDispatcher = Dispatchers.IO
            },
        )

      val uidAlice = "uid_conc_alice"
      val uidBob = "uid_conc_bob"
      fileAccountRepo.createAccount(
        uidAlice,
        FinancialAccount(
          id = "acc_conc_alice",
          userId = uidAlice,
          name = "Alice HDFC",
          type = AccountType.BANK,
          currencyCode = "INR",
          initialBalanceMinor = 500_000L,
          currentBalanceMinor = 500_000L,
          accountNumberLast4 = "4321",
        ),
      ).getOrThrow()
      fileAccountRepo.createAccount(
        uidBob,
        FinancialAccount(
          id = "acc_conc_bob",
          userId = uidBob,
          name = "Bob ICICI",
          type = AccountType.BANK,
          currencyCode = "INR",
          initialBalanceMinor = 300_000L,
          currentBalanceMinor = 300_000L,
          accountNumberLast4 = "9999",
        ),
      ).getOrThrow()

      fileDetectionRepo.updateDetectionSettings(
        uidAlice,
        DetectionSettings(userId = uidAlice, isNotificationDetectionEnabled = true, isAutoSaveHighConfidenceEnabled = true),
      )
      fileDetectionRepo.updateDetectionSettings(
        uidBob,
        DetectionSettings(userId = uidBob, isNotificationDetectionEnabled = true, isAutoSaveHighConfidenceEnabled = true),
      )

      val aliceAlert =
        ParsedFinancialMessage(
          amountMinor = 25_000L,
          currencyCode = "INR",
          type = TransactionType.EXPENSE,
          status = TransactionStatus.COMPLETED,
          merchantOrPayee = "Swiggy",
          accountLast4Hint = "4321",
          referenceId = "CONC_REF_778899",
          confidenceScore = 0.95f,
          confidenceLevel = DetectionConfidence.HIGH,
          confidenceReasons = listOf("concurrent_test"),
          timestamp = 1_700_200_000_000L,
        )

      // Parallel duplicate deliveries for Alice + concurrent independent alert for Bob
      val bobAlert =
        aliceAlert.copy(
          amountMinor = 10_000L,
          merchantOrPayee = "Zomato",
          accountLast4Hint = "9999",
          referenceId = "CONC_REF_BOB_1",
        )

      val results =
        coroutineScope {
          listOf(
            async { fileDetectionRepo.processIncomingEvent(uidAlice, "com.phonepe.app", "PhonePe", aliceAlert).getOrThrow() },
            async { fileDetectionRepo.processIncomingEvent(uidAlice, "com.snapwork.hdfc", "HDFC Bank", aliceAlert).getOrThrow() },
            async { fileDetectionRepo.processIncomingEvent(uidBob, "com.phonepe.app", "PhonePe", bobAlert).getOrThrow() },
          ).awaitAll()
        }

      val aliceStatuses = results.take(2).map { it.status }.toSet()
      assertEquals(setOf(DetectionEventStatus.AUTO_SAVED, DetectionEventStatus.DUPLICATE), aliceStatuses)
      assertEquals(DetectionEventStatus.AUTO_SAVED, results[2].status)

      // Alice charged exactly once, Bob charged independently once, zero cross-UID leakage
      assertEquals(475_000L, fileAccountRepo.getAccountById(uidAlice, "acc_conc_alice")!!.currentBalanceMinor)
      assertEquals(290_000L, fileAccountRepo.getAccountById(uidBob, "acc_conc_bob")!!.currentBalanceMinor)
      assertEquals(1, fileTxnRepo.getTransactions(uidAlice).first().size)
      assertEquals(1, fileTxnRepo.getTransactions(uidBob).first().size)
    } finally {
      fileDb.close()
      tempDbFile.delete()
      File("${tempDbFile.absolutePath}-wal").delete()
      File("${tempDbFile.absolutePath}-shm").delete()
    }
  }

  @Test
  fun `blank authenticatedUserId and cross-UID mutations are rejected across DAOs and repositories`() = runTest {
    val uidAlice = "uid_blank_alice"
    val uidBob = "uid_blank_bob"

    val aliceAcc =
      FinancialAccount(
        id = "acc_blank_alice",
        userId = uidAlice,
        name = "Alice Vault",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 100_000L,
        currentBalanceMinor = 100_000L,
      )
    assertTrue(accountRepo.createAccount("", aliceAcc).isFailure)
    assertTrue(accountRepo.createAccount("   ", aliceAcc).isFailure)
    assertTrue(accountRepo.createAccount(uidBob, aliceAcc).isFailure)
    accountRepo.createAccount(uidAlice, aliceAcc).getOrThrow()

    // Blank UID rejected at account DAO & repository
    assertEquals(0, database.financialAccountDao().updateAccount(com.example.data.local.db.entity.FinancialAccountEntity.fromDomain(aliceAcc), ""))
    assertEquals(0, database.financialAccountDao().updateAccount(com.example.data.local.db.entity.FinancialAccountEntity.fromDomain(aliceAcc), "   "))
    assertTrue(accountRepo.updateAccount("", aliceAcc).isFailure)
    assertTrue(accountRepo.setAccountActive("", aliceAcc.id, false).isFailure)
    assertTrue(accountRepo.setAccountActive(uidBob, aliceAcc.id, false).isFailure)
    assertTrue(accountRepo.deleteAccount("", aliceAcc.id, true).isFailure)
    assertTrue(accountRepo.deleteAccount(uidBob, aliceAcc.id, true).isFailure)
    assertTrue(accountRepo.adjustAccountBalance("", aliceAcc.id, -5_000L).isFailure)

    // Blank UID & cross-UID rejected at transaction DAO & repository
    val aliceTxn =
      FinancialTransaction(
        id = "txn_blank_1",
        userId = uidAlice,
        accountId = aliceAcc.id,
        type = TransactionType.EXPENSE,
        amountMinor = 5_000L,
        currencyCode = "INR",
        category = "Food",
      )
    assertTrue(txnRepo.recordExpense("", aliceTxn).isFailure)
    assertTrue(txnRepo.recordExpense(uidBob, aliceTxn).isFailure)
    assertTrue(txnRepo.recordExpense(uidBob, aliceTxn.copy(userId = uidBob)).isFailure)
    txnRepo.recordExpense(uidAlice, aliceTxn).getOrThrow()

    assertEquals(0, database.financialTransactionDao().updateTransaction(FinancialTransactionEntity.fromDomain(aliceTxn), ""))
    assertEquals(0, database.financialTransactionDao().updateTransaction(FinancialTransactionEntity.fromDomain(aliceTxn), "  "))
    assertTrue(txnRepo.updateTransaction("", aliceTxn, aliceTxn).isFailure)
    assertTrue(txnRepo.deleteTransaction("", aliceTxn.id).isFailure)
    assertTrue(txnRepo.deleteTransaction(uidBob, aliceTxn.id).isFailure)

    // Mapping rule & detection settings reject blank and cross-UID callers
    val rule = AccountMappingRule(id = "rule_b1", userId = uidAlice, accountId = aliceAcc.id, matchPattern = "1234")
    assertTrue(detectionRepo.addAccountMappingRule("", rule).isFailure)
    assertTrue(detectionRepo.addAccountMappingRule(uidBob, rule).isFailure)
    assertTrue(detectionRepo.addAccountMappingRule(uidAlice, rule).isSuccess)
    assertTrue(detectionRepo.deleteAccountMappingRule("", "rule_b1").isFailure)

    val settings = DetectionSettings(userId = uidAlice, isNotificationDetectionEnabled = false)
    assertTrue(detectionRepo.updateDetectionSettings("", settings).isFailure)
    assertTrue(detectionRepo.updateDetectionSettings(uidBob, settings).isFailure)
    assertTrue(detectionRepo.updateDetectionSettings(uidAlice, settings).isSuccess)
    assertTrue(detectionRepo.clearDetectionHistory("").isFailure)

    // Original records & balance remain intact
    val reloadedAcc = accountRepo.getAccountById(uidAlice, aliceAcc.id)!!
    assertTrue(reloadedAcc.isActive)
    assertEquals(95_000L, reloadedAcc.currentBalanceMinor)
    assertEquals(1, detectionRepo.getAccountMappingRules(uidAlice).first().size)
    assertFalse(detectionRepo.getDetectionSettings(uidAlice).first().isNotificationDetectionEnabled)
  }

  @Test
  fun `TransactionsViewModel and SmartDetectionViewModel enforce active authenticated UID from AuthRepository`() {
    val testDispatcher = StandardTestDispatcher()
    Dispatchers.setMain(testDispatcher)
    try {
      runTest(testDispatcher) {
        val uidAlice = "uid_tvm_alice"
        val uidBob = "uid_tvm_bob"
        val authFlow = MutableStateFlow<AuthSessionState>(AuthSessionState.Unauthenticated)
        val fakeAuthRepo =
          object : AuthRepository {
            override fun observeAuthSession(): Flow<AuthSessionState> = authFlow
            override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult<AuthenticatedUser> =
              AuthResult.Failure(AuthError.FirebaseAuthenticationFailed)
            override suspend fun refreshAndValidateSession(): AuthResult<AuthenticatedUser?> =
              AuthResult.Success(null)
            override suspend fun signOut(): AuthResult<Unit> = AuthResult.Success(Unit)
          }

        val aliceAcc =
          FinancialAccount(
            id = "acc_tvm_alice",
            userId = uidAlice,
            name = "Alice Cash",
            type = AccountType.WALLET,
            currencyCode = "INR",
            initialBalanceMinor = 50_000L,
            currentBalanceMinor = 50_000L,
          )
        val recordedTxnCalls = mutableListOf<String>()
        val spyAccountRepo =
          object : FinancialAccountRepository {
            override fun getAccounts(userId: String): Flow<List<FinancialAccount>> =
              flowOf(if (userId == uidAlice) listOf(aliceAcc) else emptyList())
            override suspend fun getAccountById(userId: String, accountId: String) = null
            override fun getAccountByIdFlow(userId: String, accountId: String) = flowOf<FinancialAccount?>(null)
            override suspend fun createAccount(authenticatedUserId: String, account: FinancialAccount) = Result.success(account)
            override suspend fun updateAccount(authenticatedUserId: String, account: FinancialAccount) = Result.success(Unit)
            override suspend fun setAccountActive(userId: String, accountId: String, isActive: Boolean) = Result.success(Unit)
            override suspend fun getAccountTransactionCount(userId: String, accountId: String) = 0
            override suspend fun deleteAccount(userId: String, accountId: String, deleteLinkedTransactions: Boolean) = Result.success(Unit)
            override suspend fun adjustAccountBalance(userId: String, accountId: String, deltaMinor: Long) = Result.success(Unit)
          }
        val spyTxnRepo =
          object : FinancialTransactionRepository {
            override fun getTransactions(userId: String) = flowOf(emptyList<FinancialTransaction>())
            override fun getTransactionsByAccount(userId: String, accountId: String) = flowOf(emptyList<FinancialTransaction>())
            override suspend fun getTransactionById(userId: String, transactionId: String) = null
            override suspend fun recordIncome(authenticatedUserId: String, transaction: FinancialTransaction) =
              Result.success(transaction).also { recordedTxnCalls += "inc:$authenticatedUserId" }
            override suspend fun recordExpense(authenticatedUserId: String, transaction: FinancialTransaction) =
              Result.success(transaction).also { recordedTxnCalls += "exp:$authenticatedUserId" }
            override suspend fun recordTransfer(authenticatedUserId: String, sourceTransaction: FinancialTransaction, destinationAccountId: String, destinationAmountMinor: Long?) =
              Result.success(sourceTransaction to sourceTransaction).also { recordedTxnCalls += "xfer:$authenticatedUserId" }
            override suspend fun recordGeneralTransaction(authenticatedUserId: String, transaction: FinancialTransaction) =
              Result.success(transaction).also { recordedTxnCalls += "gen:$authenticatedUserId" }
            override suspend fun updateTransaction(authenticatedUserId: String, updatedTransaction: FinancialTransaction, oldTransaction: FinancialTransaction) =
              Result.success(Unit).also { recordedTxnCalls += "upd:$authenticatedUserId" }
            override suspend fun deleteTransaction(userId: String, transactionId: String) =
              Result.success(Unit).also { recordedTxnCalls += "del:$userId" }
            override suspend fun findRecentDuplicate(userId: String, amountMinor: Long, currencyCode: String, referenceId: String?, timeWindowMs: Long) = null
          }

        val txnVm =
          TransactionsViewModel(
            authRepository = fakeAuthRepo,
            getTransactionsUseCase = GetTransactionsUseCase(spyTxnRepo),
            getAccountsUseCase = GetAccountsUseCase(spyAccountRepo),
            recordExpenseUseCase = RecordExpenseUseCase(spyTxnRepo),
            recordIncomeUseCase = RecordIncomeUseCase(spyTxnRepo),
            recordTransferUseCase = RecordTransferUseCase(spyTxnRepo),
            recordGeneralTransactionUseCase = RecordGeneralTransactionUseCase(spyTxnRepo),
            updateTransactionUseCase = UpdateTransactionUseCase(spyTxnRepo),
            deleteTransactionUseCase = DeleteTransactionUseCase(spyTxnRepo),
          )

        val aliceTxn =
          FinancialTransaction(
            id = "t1",
            userId = uidAlice,
            accountId = aliceAcc.id,
            type = TransactionType.EXPENSE,
            amountMinor = 1_000L,
            currencyCode = "INR",
            category = "Food",
          )

        // Unauthenticated transaction mutations must never reach repository
        txnVm.recordExpense(aliceAcc.id, "10.00", "Food", "Cafe", null)
        txnVm.updateTransactionFromEdit(aliceTxn, aliceAcc.id, TransactionType.EXPENSE, TransactionStatus.COMPLETED, "20.00", "Food", "Cafe", null, 1000L)
        txnVm.deleteTransaction(aliceTxn)
        advanceUntilIdle()
        assertEquals(0, recordedTxnCalls.size)

        // Bob cannot record on Alice's accountId or edit/delete Alice's transaction
        authFlow.value =
          AuthSessionState.Authenticated(
            AuthenticatedUser(uidBob, "Bob", "bob@test.com", null, true, setOf(AuthProviderType.GOOGLE))
          )
        txnVm.recordExpense(aliceAcc.id, "10.00", "Food", "Cafe", null)
        txnVm.updateTransactionFromEdit(aliceTxn, aliceAcc.id, TransactionType.EXPENSE, TransactionStatus.COMPLETED, "20.00", "Food", "Cafe", null, 1000L)
        txnVm.deleteTransaction(aliceTxn)
        advanceUntilIdle()
        assertEquals(0, recordedTxnCalls.size)

        // Alice succeeds and passes uidAlice explicitly
        authFlow.value =
          AuthSessionState.Authenticated(
            AuthenticatedUser(uidAlice, "Alice", "alice@test.com", null, true, setOf(AuthProviderType.GOOGLE))
          )
        txnVm.recordExpense(aliceAcc.id, "10.00", "Food", "Cafe", null)
        txnVm.updateTransactionFromEdit(aliceTxn, aliceAcc.id, TransactionType.EXPENSE, TransactionStatus.COMPLETED, "20.00", "Food", "Cafe", null, 1000L)
        txnVm.deleteTransaction(aliceTxn)
        advanceUntilIdle()
        assertEquals(listOf("exp:$uidAlice", "upd:$uidAlice", "del:$uidAlice"), recordedTxnCalls)

        // SmartDetectionViewModel checks active session UID for settings & mapping rules
        val recordedSettingsUpdates = mutableListOf<String>()
        val spyDetectionRepo =
          object : SmartDetectionRepository {
            override fun getDetectionEvents(userId: String) = flowOf(emptyList<TransactionDetectionEvent>())
            override fun getReviewQueue(userId: String) = flowOf(emptyList<TransactionDetectionEvent>())
            override fun observeLatestAutoSavedNotice(userId: String) = flowOf<AutoSavedTransactionNotice?>(null)
            override fun clearAutoSavedNotice() = Unit
            override suspend fun getEventById(userId: String, eventId: String) = null
            override suspend fun processIncomingEvent(userId: String, rawPackageName: String, senderOrTitle: String, parsed: ParsedFinancialMessage) =
              Result.failure<TransactionDetectionEvent>(IllegalStateException("unused"))
            override suspend fun approveReviewEvent(userId: String, eventId: String, selectedAccountId: String, category: String, merchant: String?, correctedType: TransactionType?, correctedAmountMinor: Long?) =
              Result.success(Unit)
            override suspend fun dismissReviewEvent(userId: String, eventId: String) = Result.success(Unit)
            override suspend fun clearDetectionHistory(userId: String) = Result.success(Unit)
            override fun getAccountMappingRules(userId: String) = flowOf(emptyList<AccountMappingRule>())
            override suspend fun addAccountMappingRule(authenticatedUserId: String, rule: AccountMappingRule) =
              Result.success(Unit).also { recordedSettingsUpdates += "rule:$authenticatedUserId:${rule.userId}" }
            override suspend fun deleteAccountMappingRule(userId: String, ruleId: String) = Result.success(Unit)
            override fun getDetectionSettings(userId: String) = flowOf(DetectionSettings(userId = userId))
            override suspend fun updateDetectionSettings(authenticatedUserId: String, settings: DetectionSettings) =
              Result.success(Unit).also { recordedSettingsUpdates += "settings:$authenticatedUserId:${settings.userId}" }
          }

        authFlow.value = AuthSessionState.Unauthenticated
        val detectionVm =
          SmartDetectionViewModel(
            context = context,
            authRepository = fakeAuthRepo,
            getReviewQueueUseCase = GetReviewQueueUseCase(spyDetectionRepo),
            getDetectionEventsUseCase = GetDetectionEventsUseCase(spyDetectionRepo),
            approveReviewItemUseCase = ApproveReviewItemUseCase(spyDetectionRepo),
            dismissReviewItemUseCase = DismissReviewItemUseCase(spyDetectionRepo),
            settingsUseCases = DetectionSettingsUseCases(spyDetectionRepo),
            getAccountsUseCase = GetAccountsUseCase(spyAccountRepo),
          )
        detectionVm.toggleNotificationDetection(false)
        detectionVm.addMappingRule(aliceAcc.id, "4321", "HDFC")
        advanceUntilIdle()
        assertEquals(0, recordedSettingsUpdates.size)

        authFlow.value =
          AuthSessionState.Authenticated(
            AuthenticatedUser(uidAlice, "Alice", "alice@test.com", null, true, setOf(AuthProviderType.GOOGLE))
          )
        detectionVm.toggleNotificationDetection(false)
        detectionVm.addMappingRule(aliceAcc.id, "4321", "HDFC")
        advanceUntilIdle()
        assertEquals(
          listOf("settings:$uidAlice:$uidAlice", "rule:$uidAlice:$uidAlice"),
          recordedSettingsUpdates,
        )
      }
    } finally {
      Dispatchers.resetMain()
    }
  }
}
