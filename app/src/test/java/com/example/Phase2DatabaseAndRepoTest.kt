package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.DatabaseMigrations
import com.example.data.repository.FinancialAccountRepositoryImpl
import com.example.data.repository.FinancialTransactionRepositoryImpl
import com.example.data.repository.SmartDetectionRepositoryImpl
import com.example.domain.model.AccountMappingRule
import com.example.domain.model.AccountType
import com.example.domain.model.DetectionEventStatus
import com.example.domain.model.DetectionSettings
import com.example.domain.model.FinancialAccount
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.TransactionCategories
import com.example.domain.model.TransactionType
import com.example.feature.smartdetection.engine.SmartTransactionParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2DatabaseAndRepoTest {

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
  fun `account lifecycle CRUD active toggle and safe deletion rules work end to end`() = runTest {
    val uid = "uid_accounts"
    val acc =
      FinancialAccount(
        id = "acc_1",
        userId = uid,
        name = "HDFC Salary",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 100000L,
        currentBalanceMinor = 100000L,
        accountNumberLast4 = "4321",
      )
    assertTrue(accountRepo.createAccount(uid, acc).isSuccess)

    val loaded = accountRepo.getAccountById(uid, "acc_1")
    assertEquals("HDFC Salary", loaded?.name)
    assertEquals(100000L, loaded?.currentBalanceMinor)

    accountRepo.setAccountActive(uid, "acc_1", false)
    assertFalse(accountRepo.getAccountById(uid, "acc_1")!!.isActive)

    // Record linked transaction
    txnRepo.recordExpense(
      uid,
      FinancialTransaction(
        id = "txn_link_1",
        userId = uid,
        accountId = "acc_1",
        type = TransactionType.EXPENSE,
        amountMinor = 25000L,
        currencyCode = "INR",
        category = "Food & Dining",
      ),
    ).getOrThrow()

    assertEquals(75000L, accountRepo.getAccountById(uid, "acc_1")!!.currentBalanceMinor)

    // Safe delete blocked without force flag
    val safeDeleteResult = accountRepo.deleteAccount(uid, "acc_1", deleteLinkedTransactions = false)
    assertTrue(safeDeleteResult.isFailure)
    assertNotNull(accountRepo.getAccountById(uid, "acc_1"))

    // Confirmed delete removes linked transactions and account
    val forceDeleteResult = accountRepo.deleteAccount(uid, "acc_1", deleteLinkedTransactions = true)
    assertTrue(forceDeleteResult.isSuccess)
    assertEquals(0, accountRepo.getAccounts(uid).first().size)
    assertEquals(0, txnRepo.getTransactions(uid).first().size)
  }

  @Test
  fun `account currency change is rejected and preserves ledger history and balances`() = runTest {
    val uid = "uid_account_currency_lock"
    val original = FinancialAccount(
      id = "currency_locked_account",
      userId = uid,
      name = "Primary Account",
      type = AccountType.BANK,
      currencyCode = "INR",
      initialBalanceMinor = 500_000L,
      currentBalanceMinor = 500_000L,
    )
    accountRepo.createAccount(uid, original).getOrThrow()
    txnRepo.recordExpense(
      uid,
      FinancialTransaction(
        id = "currency_locked_expense",
        userId = uid,
        accountId = original.id,
        type = TransactionType.EXPENSE,
        amountMinor = 25_000L,
        currencyCode = "INR",
        category = "Food",
      ),
    ).getOrThrow()

    val before = accountRepo.getAccountById(uid, original.id)!!
    val transactionsBefore = txnRepo.getTransactions(uid).first()
    val result = accountRepo.updateAccount(
      uid,
      before.copy(
        currencyCode = "USD",
        initialBalanceMinor = 50_000L,
        currentBalanceMinor = 47_500L,
      ),
    )

    assertTrue("An existing account currency must not be reinterpreted", result.isFailure)
    val after = accountRepo.getAccountById(uid, original.id)!!
    assertEquals("INR", after.currencyCode)
    assertEquals(before.initialBalanceMinor, after.initialBalanceMinor)
    assertEquals(before.currentBalanceMinor, after.currentBalanceMinor)
    assertEquals(transactionsBefore, txnRepo.getTransactions(uid).first())
  }

  @Test
  fun `account update cannot cross UID boundaries even when ownership is spoofed`() = runTest {
    val ownerUid = "uid_account_owner"
    val attackerUid = "uid_account_attacker"
    val original = FinancialAccount(
      id = "uid_scoped_account",
      userId = ownerUid,
      name = "Owner Savings",
      type = AccountType.BANK,
      currencyCode = "INR",
      initialBalanceMinor = 250_000L,
      currentBalanceMinor = 250_000L,
    )
    accountRepo.createAccount(ownerUid, original).getOrThrow()

    // Simulate a caller trying to relabel another user's account as their own.
    val attemptedUpdate = accountRepo.updateAccount(
      attackerUid,
      original.copy(
        userId = attackerUid,
        name = "Tampered Account",
        currencyCode = "USD",
        initialBalanceMinor = 1L,
        currentBalanceMinor = 1L,
      ),
    )

    assertTrue("A different UID must not update or relabel the account", attemptedUpdate.isFailure)
    val preserved = accountRepo.getAccountById(ownerUid, original.id)!!
    assertEquals(ownerUid, preserved.userId)
    assertEquals("Owner Savings", preserved.name)
    assertEquals("INR", preserved.currencyCode)
    assertEquals(250_000L, preserved.initialBalanceMinor)
    assertEquals(250_000L, preserved.currentBalanceMinor)
    assertEquals(null, accountRepo.getAccountById(attackerUid, original.id))
  }

  @Test
  fun `account currency is immutable even before the first transaction`() = runTest {
    val uid = "uid_account_currency_empty"
    val account = FinancialAccount(
      id = "empty_currency_locked_account",
      userId = uid,
      name = "Empty Account",
      type = AccountType.BANK,
      currencyCode = "KWD",
      initialBalanceMinor = 12_345L,
      currentBalanceMinor = 12_345L,
    )
    accountRepo.createAccount(uid, account).getOrThrow()

    val result = accountRepo.updateAccount(uid, account.copy(currencyCode = "INR"))

    assertTrue("Currency changes require a dedicated conversion workflow", result.isFailure)
    val preserved = accountRepo.getAccountById(uid, account.id)!!
    assertEquals("KWD", preserved.currencyCode)
    assertEquals(12_345L, preserved.initialBalanceMinor)
    assertEquals(12_345L, preserved.currentBalanceMinor)
  }

  @Test
  fun `income expense refund edit delete and pending failed status balance math are exact`() = runTest {
    val uid = "uid_math"
    accountRepo.createAccount(
      uid,
      FinancialAccount(
        id = "acc_kwd",
        userId = uid,
        name = "NBK KWD",
        type = AccountType.BANK,
        currencyCode = "KWD",
        initialBalanceMinor = 100_000L, // 100.000 KWD
        currentBalanceMinor = 100_000L,
      ),
    )

    val inc =
      txnRepo.recordIncome(
        uid,
        FinancialTransaction(
          id = "t_inc",
          userId = uid,
          accountId = "acc_kwd",
          type = TransactionType.INCOME,
          amountMinor = 50_000L, // +50.000
          currencyCode = "KWD",
          category = "Salary",
        ),
      ).getOrThrow()
    assertEquals(150_000L, accountRepo.getAccountById(uid, "acc_kwd")!!.currentBalanceMinor)

    val exp =
      txnRepo.recordExpense(
        uid,
        FinancialTransaction(
          id = "t_exp",
          userId = uid,
          accountId = "acc_kwd",
          type = TransactionType.EXPENSE,
          amountMinor = 12_345L, // -12.345
          currencyCode = "KWD",
          category = "Shopping",
        ),
      ).getOrThrow()
    assertEquals(137_655L, accountRepo.getAccountById(uid, "acc_kwd")!!.currentBalanceMinor)

    // Pending & Failed must NOT mutate balance
    txnRepo.recordGeneralTransaction(
      uid,
      FinancialTransaction(
        id = "t_pending",
        userId = uid,
        accountId = "acc_kwd",
        type = TransactionType.PENDING,
        amountMinor = 9_999L,
        currencyCode = "KWD",
        category = "Other Expenses",
      ),
    ).getOrThrow()
    txnRepo.recordGeneralTransaction(
      uid,
      FinancialTransaction(
        id = "t_failed",
        userId = uid,
        accountId = "acc_kwd",
        type = TransactionType.FAILED,
        amountMinor = 9_999L,
        currencyCode = "KWD",
        category = "Other Expenses",
      ),
    ).getOrThrow()
    assertEquals(137_655L, accountRepo.getAccountById(uid, "acc_kwd")!!.currentBalanceMinor)

    // Edit expense 12.345 -> 20.000 adjusts balance cleanly by -7.655
    txnRepo.updateTransaction(uid, exp.copy(amountMinor = 20_000L), exp).getOrThrow()
    assertEquals(130_000L, accountRepo.getAccountById(uid, "acc_kwd")!!.currentBalanceMinor)

    // Delete income reverses +50.000 cleanly
    txnRepo.deleteTransaction(uid, inc.id).getOrThrow()
    assertEquals(80_000L, accountRepo.getAccountById(uid, "acc_kwd")!!.currentBalanceMinor)
  }

  @Test
  fun `internal transfer is atomic across accounts, rolls back on delete, and blocks implicit cross currency`() = runTest {
    val uid = "uid_xfer"
    accountRepo.createAccount(
      uid,
      FinancialAccount(
        id = "src_inr",
        userId = uid,
        name = "HDFC",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 500_000L,
        currentBalanceMinor = 500_000L,
      ),
    )
    accountRepo.createAccount(
      uid,
      FinancialAccount(
        id = "dst_inr",
        userId = uid,
        name = "Wallet",
        type = AccountType.WALLET,
        currencyCode = "INR",
        initialBalanceMinor = 100_000L,
        currentBalanceMinor = 100_000L,
      ),
    )
    accountRepo.createAccount(
      uid,
      FinancialAccount(
        id = "dst_usd",
        userId = uid,
        name = "US Travel Card",
        type = AccountType.CREDIT_CARD,
        currencyCode = "USD",
        initialBalanceMinor = 0L,
        currentBalanceMinor = 0L,
      ),
    )

    // Same-currency transfer
    val (outLeg, _) =
      txnRepo.recordTransfer(
        authenticatedUserId = uid,
        sourceTransaction =
          FinancialTransaction(
            userId = uid,
            accountId = "src_inr",
            type = TransactionType.TRANSFER,
            amountMinor = 200_000L,
            currencyCode = "INR",
            category = TransactionCategories.TRANSFER_OUT_CATEGORY,
          ),
        destinationAccountId = "dst_inr",
      ).getOrThrow()

    assertEquals(300_000L, accountRepo.getAccountById(uid, "src_inr")!!.currentBalanceMinor)
    assertEquals(300_000L, accountRepo.getAccountById(uid, "dst_inr")!!.currentBalanceMinor)

    // Deleting source leg removes BOTH legs and restores BOTH balances
    txnRepo.deleteTransaction(uid, outLeg.id).getOrThrow()
    assertEquals(500_000L, accountRepo.getAccountById(uid, "src_inr")!!.currentBalanceMinor)
    assertEquals(100_000L, accountRepo.getAccountById(uid, "dst_inr")!!.currentBalanceMinor)
    assertEquals(0, txnRepo.getTransactions(uid).first().size)

    // Cross-currency without converted destination amount must fail
    val missingConv =
      txnRepo.recordTransfer(
        authenticatedUserId = uid,
        sourceTransaction =
          FinancialTransaction(
            userId = uid,
            accountId = "src_inr",
            type = TransactionType.TRANSFER,
            amountMinor = 85_000L,
            currencyCode = "INR",
            category = TransactionCategories.TRANSFER_OUT_CATEGORY,
          ),
        destinationAccountId = "dst_usd",
        destinationAmountMinor = null,
      )
    assertTrue(missingConv.isFailure)

    // Cross-currency with explicit converted destination amount succeeds
    val withConv =
      txnRepo.recordTransfer(
        authenticatedUserId = uid,
        sourceTransaction =
          FinancialTransaction(
            userId = uid,
            accountId = "src_inr",
            type = TransactionType.TRANSFER,
            amountMinor = 85_000L, // 850.00 INR
            currencyCode = "INR",
            category = TransactionCategories.TRANSFER_OUT_CATEGORY,
          ),
        destinationAccountId = "dst_usd",
        destinationAmountMinor = 1_000L, // 10.00 USD
      )
    assertTrue(withConv.isSuccess)
    assertEquals(415_000L, accountRepo.getAccountById(uid, "src_inr")!!.currentBalanceMinor)
    assertEquals(1_000L, accountRepo.getAccountById(uid, "dst_usd")!!.currentBalanceMinor)
  }

  @Test
  fun `smart detection auto-saves high confidence, deduplicates repeats, and handles review correction`() = runTest {
    val uid = "uid_detect"
    accountRepo.createAccount(
      uid,
      FinancialAccount(
        id = "acc_hdfc",
        userId = uid,
        name = "HDFC 4321",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 1_000_000L, // Rs 10,000.00
        currentBalanceMinor = 1_000_000L,
        accountNumberLast4 = "4321",
      ),
    )
    detectionRepo.addAccountMappingRule(
      uid,
      AccountMappingRule(userId = uid, accountId = "acc_hdfc", matchPattern = "4321"),
    )
    detectionRepo.updateDetectionSettings(uid, DetectionSettings(userId = uid, isNotificationDetectionEnabled = true, isAutoSaveHighConfidenceEnabled = true))

    val parsedHigh =
      SmartTransactionParser.parse(
        "Rs. 500.00 debited from A/c XX4321 paid to Swiggy Ref 9876543210",
        "com.google.android.apps.nbu.paisa.user",
        1_700_000_000_000L,
      )!!

    // First delivery -> AUTO_SAVED
    val event1 =
      detectionRepo.processIncomingEvent(
        userId = uid,
        rawPackageName = "com.google.android.apps.nbu.paisa.user",
        senderOrTitle = "Google Pay",
        parsed = parsedHigh,
      ).getOrThrow()
    assertEquals(DetectionEventStatus.AUTO_SAVED, event1.status)
    assertEquals(950_000L, accountRepo.getAccountById(uid, "acc_hdfc")!!.currentBalanceMinor)
    assertNotNull(detectionRepo.observeLatestAutoSavedNotice(uid).first())

    // Duplicate delivery with same ref / slightly different timestamp -> deduplicated, balance unchanged!
    val duplicateParsed =
      SmartTransactionParser.parse(
        "INR 500.00 spent via HDFC A/c 4321 at Swiggy UPI Ref 9876543210",
        "com.snapwork.hdfc",
        1_700_000_030_000L,
      )!!
    val event2 =
      detectionRepo.processIncomingEvent(
        userId = uid,
        rawPackageName = "com.snapwork.hdfc",
        senderOrTitle = "HDFC Bank",
        parsed = duplicateParsed,
      ).getOrThrow()
    assertEquals(DetectionEventStatus.DUPLICATE, event2.status)
    assertEquals(950_000L, accountRepo.getAccountById(uid, "acc_hdfc")!!.currentBalanceMinor)

    // Medium / Pending notification -> enters Review Queue, zero balance impact
    val parsedPending =
      SmartTransactionParser.parse(
        "Payment of Rs. 250.00 to Uber is pending Ref PND123456",
        "com.phonepe.app",
        1_700_001_000_000L,
      )!!
    val reviewEvent =
      detectionRepo.processIncomingEvent(
        userId = uid,
        rawPackageName = "com.phonepe.app",
        senderOrTitle = "PhonePe",
        parsed = parsedPending,
      ).getOrThrow()
    assertEquals(DetectionEventStatus.IN_REVIEW, reviewEvent.status)
    assertEquals(950_000L, accountRepo.getAccountById(uid, "acc_hdfc")!!.currentBalanceMinor)

    // Approve Review item with correction (type=EXPENSE, amount=300.00 INR)
    detectionRepo.approveReviewEvent(
      userId = uid,
      eventId = reviewEvent.id,
      selectedAccountId = "acc_hdfc",
      category = "Transport",
      merchant = "Uber India",
      correctedType = TransactionType.EXPENSE,
      correctedAmountMinor = 30_000L,
    ).getOrThrow()

    assertEquals(DetectionEventStatus.APPROVED, detectionRepo.getEventById(uid, reviewEvent.id)?.status)
    assertEquals(920_000L, accountRepo.getAccountById(uid, "acc_hdfc")!!.currentBalanceMinor)
  }

  @Test
  fun `financial data and detection events are strictly isolated by UID`() = runTest {
    accountRepo.createAccount(
      "uid_alice",
      FinancialAccount(
        id = "a_alice",
        userId = "uid_alice",
        name = "Alice Bank",
        type = AccountType.BANK,
        currencyCode = "USD",
        initialBalanceMinor = 50000L,
        currentBalanceMinor = 50000L,
      ),
    )
    assertEquals(1, accountRepo.getAccounts("uid_alice").first().size)
    assertEquals(0, accountRepo.getAccounts("uid_bob").first().size)
    assertEquals(0, txnRepo.getTransactions("uid_bob").first().size)
    assertEquals(0, detectionRepo.getDetectionEvents("uid_bob").first().size)
  }

  @Test
  fun `room migration 1 to 2 preserves existing accounts and transactions non destructively`() {
    val dbFile = File(context.cacheDir, "migration_test_${System.nanoTime()}.db")
    if (dbFile.exists()) dbFile.delete()

    val callback =
      object : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS financial_accounts (
              id TEXT NOT NULL PRIMARY KEY,
              userId TEXT NOT NULL,
              name TEXT NOT NULL,
              accountType TEXT NOT NULL,
              currencyCode TEXT NOT NULL,
              initialBalanceMinor INTEGER NOT NULL,
              currentBalanceMinor INTEGER NOT NULL,
              institutionName TEXT,
              accountNumberLast4 TEXT,
              colorHex TEXT NOT NULL,
              isActive INTEGER NOT NULL,
              createdAt INTEGER NOT NULL,
              updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_accounts_userId ON financial_accounts(userId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_accounts_userId_name ON financial_accounts(userId, name)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS financial_transactions (
              id TEXT NOT NULL PRIMARY KEY,
              userId TEXT NOT NULL,
              accountId TEXT NOT NULL,
              type TEXT NOT NULL,
              status TEXT NOT NULL,
              amountMinor INTEGER NOT NULL,
              currencyCode TEXT NOT NULL,
              category TEXT NOT NULL,
              merchantOrPayee TEXT,
              notes TEXT,
              transactionDate INTEGER NOT NULL,
              linkedTransferId TEXT,
              transferDestinationAccountId TEXT,
              isAutomaticallyDetected INTEGER NOT NULL,
              sourceApplication TEXT,
              referenceId TEXT,
              confidenceScore REAL,
              createdAt INTEGER NOT NULL,
              updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId ON financial_transactions(userId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_accountId ON financial_transactions(userId, accountId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_transactionDate ON financial_transactions(userId, transactionDate)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_referenceId ON financial_transactions(userId, referenceId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_linkedTransferId ON financial_transactions(userId, linkedTransferId)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transaction_detection_events (
              id TEXT NOT NULL PRIMARY KEY,
              userId TEXT NOT NULL,
              rawPackageName TEXT NOT NULL,
              senderOrTitle TEXT NOT NULL,
              detectedAmountMinor INTEGER NOT NULL,
              detectedCurrency TEXT NOT NULL,
              detectedType TEXT NOT NULL,
              detectedMerchant TEXT,
              detectedAccountLast4 TEXT,
              referenceId TEXT,
              confidence REAL NOT NULL,
              confidenceLevel TEXT NOT NULL,
              confidenceReasonsJoined TEXT NOT NULL,
              dedupFingerprint TEXT NOT NULL,
              status TEXT NOT NULL,
              mappedAccountId TEXT,
              createdTransactionId TEXT,
              receivedAt INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_detection_events_userId ON transaction_detection_events(userId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_detection_events_userId_status ON transaction_detection_events(userId, status)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_detection_events_userId_dedupFingerprint ON transaction_detection_events(userId, dedupFingerprint)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_detection_events_userId_referenceId ON transaction_detection_events(userId, referenceId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_detection_events_userId_receivedAt ON transaction_detection_events(userId, receivedAt)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS account_mapping_rules (
              id TEXT NOT NULL PRIMARY KEY,
              userId TEXT NOT NULL,
              accountId TEXT NOT NULL,
              matchPattern TEXT NOT NULL,
              institutionHint TEXT,
              isAutoMapped INTEGER NOT NULL,
              createdAt INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_account_mapping_rules_userId ON account_mapping_rules(userId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_account_mapping_rules_userId_accountId ON account_mapping_rules(userId, accountId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_account_mapping_rules_userId_matchPattern ON account_mapping_rules(userId, matchPattern)")

          db.execSQL(
            "INSERT INTO financial_accounts VALUES ('v1_acc', 'uid_m', 'Legacy Savings', 'SAVINGS', 'INR', 50000, 50000, 'SBI', '1111', '#008080', 1, 1000, 1000)"
          )
          db.execSQL(
            "INSERT INTO financial_transactions VALUES ('v1_txn', 'uid_m', 'v1_acc', 'EXPENSE', 'COMPLETED', 1500, 'INR', 'Food', 'Cafe', 'coffee', 1000, NULL, NULL, 0, NULL, NULL, NULL, 1000, 1000)"
          )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
      }

    val helper =
      FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(dbFile.absolutePath)
          .callback(callback)
          .build()
      )
    helper.writableDatabase.close()
    helper.close()

    val migratedDb =
      Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
        .allowMainThreadQueries()
        .addMigrations(
          DatabaseMigrations.MIGRATION_1_2,
          DatabaseMigrations.MIGRATION_2_3,
          DatabaseMigrations.MIGRATION_3_4,
          DatabaseMigrations.MIGRATION_4_5,
          DatabaseMigrations.MIGRATION_5_6,
          DatabaseMigrations.MIGRATION_6_7,
          DatabaseMigrations.MIGRATION_7_8,
          DatabaseMigrations.MIGRATION_8_9,
        )
        .build()

    try {
      runTest {
        val acc = migratedDb.financialAccountDao().getAccountById("uid_m", "v1_acc")
        assertNotNull(acc)
        assertEquals("Legacy Savings", acc?.name)

        val txn = migratedDb.financialTransactionDao().getTransactionById("uid_m", "v1_txn")
        assertNotNull(txn)
        assertEquals(1500L, txn?.amountMinor)
        assertEquals("Cafe", txn?.merchantOrPayee)
      }
    } finally {
      migratedDb.close()
      dbFile.delete()
    }
  }
}
