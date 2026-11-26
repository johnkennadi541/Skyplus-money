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
import com.example.data.repository.LoanRepositoryImpl
import com.example.domain.model.Loan
import com.example.domain.model.LoanStatus
import com.example.domain.model.LoanType
import com.example.domain.usecase.EmiCalculationEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3LoanDatabaseAndRepoTest {

  private lateinit var context: Context
  private lateinit var database: AppDatabase
  private lateinit var emiEngine: EmiCalculationEngine
  private lateinit var loanRepo: LoanRepositoryImpl

  private val testDispatchers =
    object : DispatcherProvider {
      private val d = UnconfinedTestDispatcher()
      override val main: CoroutineDispatcher = d
      override val io: CoroutineDispatcher = d
      override val default: CoroutineDispatcher = d
    }

  private val userA = "uid_loan_alpha"
  private val userB = "uid_loan_beta"

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    emiEngine = EmiCalculationEngine()
    loanRepo = LoanRepositoryImpl(database, database.loanDao(), emiEngine, testDispatchers)
  }

  @After
  fun tearDown() {
    database.close()
  }

  private fun buildLoan(
    userId: String = userA,
    name: String = "Car Loan",
    lender: String = "Axis Bank",
    type: LoanType = LoanType.VEHICLE,
    principalMinor: Long = 10_000_00L,
    currency: String = "USD",
    rate: String = "12",
    tenureMonths: Int = 12,
    startDate: Long = 1_700_000_000_000L,
    nextDueDate: Long = 1_702_592_000_000L,
  ): Loan =
    Loan(
      id = UUID.randomUUID().toString(),
      userId = userId,
      name = name,
      lenderName = lender,
      loanType = type,
      principalMinor = principalMinor,
      currencyCode = currency,
      annualInterestRatePercent = rate,
      tenureMonths = tenureMonths,
      startDate = startDate,
      nextDueDate = nextDueDate,
      monthlyEmiMinor = 0L,
      totalPayableMinor = 0L,
      totalInterestMinor = 0L,
      amountPaidMinor = 0L,
      outstandingPrincipalMinor = principalMinor,
      status = LoanStatus.ACTIVE,
    )

  @Test
  fun `loan creation and editing persist accurate values in encrypted repository`() = runTest {
    val loan = buildLoan(userId = userA, type = LoanType.HOME, name = "Villa Mortgage")
    val createResult = loanRepo.createLoan(userA, loan)
    assertTrue(createResult.isSuccess)

    val saved = loanRepo.getLoanById(userA, loan.id)!!
    assertEquals("Villa Mortgage", saved.name)
    assertEquals(LoanType.HOME, saved.loanType)
    assertEquals(888_49L, saved.monthlyEmiMinor)
    assertEquals(10_000_00L, saved.outstandingPrincipalMinor)

    val updateResult = loanRepo.updateLoan(userA, saved.copy(name = "Updated Mortgage", lenderName = "ICICI"))
    assertTrue(updateResult.isSuccess)

    val updated = loanRepo.getLoanById(userA, loan.id)!!
    assertEquals("Updated Mortgage", updated.name)
    assertEquals("ICICI", updated.lenderName)
  }

  @Test
  fun `recording payment updates outstanding principal, payment history, and marks completed at zero balance`() = runTest {
    val loan = buildLoan(userId = userA, principalMinor = 1_000_00L, rate = "0", tenureMonths = 2)
    loanRepo.createLoan(userA, loan)

    // Pay first 500.00
    val p1 = loanRepo.recordPayment(userA, loan.id, 1_701_000_000_000L, 500_00L)
    assertTrue(p1.isSuccess)
    assertEquals(500_00L, p1.getOrThrow().principalComponentMinor)
    assertEquals(0L, p1.getOrThrow().interestComponentMinor)
    assertEquals(500_00L, p1.getOrThrow().remainingBalanceMinor)

    val afterP1 = loanRepo.getLoanById(userA, loan.id)!!
    assertEquals(500_00L, afterP1.amountPaidMinor)
    assertEquals(500_00L, afterP1.outstandingPrincipalMinor)
    assertEquals(LoanStatus.ACTIVE, afterP1.status)

    // Pay remaining 500.00 on next day
    val p2 = loanRepo.recordPayment(userA, loan.id, 1_701_200_000_000L, 500_00L)
    assertTrue(p2.isSuccess)

    val afterP2 = loanRepo.getLoanById(userA, loan.id)!!
    assertEquals(1_000_00L, afterP2.amountPaidMinor)
    assertEquals(0L, afterP2.outstandingPrincipalMinor)
    assertEquals(LoanStatus.COMPLETED, afterP2.status)

    val history = loanRepo.getPaymentsForLoan(userA, loan.id).first()
    assertEquals(2, history.size)
  }

  @Test
  fun `loan financial terms are immutable after first payment but metadata remains editable`() = runTest {
    val loan = buildLoan(userId = userA, principalMinor = 10_000_00L, rate = "12", tenureMonths = 12)
    loanRepo.createLoan(userA, loan)
    loanRepo.recordPayment(userA, loan.id, 1_701_000_000_000L, 888_49L).getOrThrow()

    val afterPayment = loanRepo.getLoanById(userA, loan.id)!!
    val historyBefore = loanRepo.getPaymentsForLoan(userA, loan.id).first()
    assertEquals(1, historyBefore.size)

    val invalidTermEdits = listOf(
      afterPayment.copy(principalMinor = afterPayment.principalMinor + 100_00L),
      afterPayment.copy(annualInterestRatePercent = "15"),
      afterPayment.copy(tenureMonths = afterPayment.tenureMonths + 1),
      afterPayment.copy(currencyCode = "INR"),
      afterPayment.copy(startDate = afterPayment.startDate + 86_400_000L),
      afterPayment.copy(nextDueDate = afterPayment.nextDueDate + 86_400_000L),
      afterPayment.copy(loanType = LoanType.HOME),
      afterPayment.copy(amountPaidMinor = afterPayment.amountPaidMinor + 1L),
      afterPayment.copy(outstandingPrincipalMinor = afterPayment.outstandingPrincipalMinor - 1L),
      afterPayment.copy(status = LoanStatus.COMPLETED),
    )

    invalidTermEdits.forEach { attemptedEdit ->
      val result = loanRepo.updateLoan(userA, attemptedEdit)
      assertTrue("Financial term edits must be rejected after a payment", result.isFailure)
      assertEquals(afterPayment, loanRepo.getLoanById(userA, loan.id))
      assertEquals(historyBefore, loanRepo.getPaymentsForLoan(userA, loan.id).first())
    }

    val metadataUpdate = loanRepo.updateLoan(
      userA,
      afterPayment.copy(
        name = "Updated Car Loan",
        lenderName = "Updated Lender",
        notes = "Metadata only",
      ),
    )
    assertTrue("Name, lender and notes remain editable", metadataUpdate.isSuccess)

    val updated = loanRepo.getLoanById(userA, loan.id)!!
    assertEquals("Updated Car Loan", updated.name)
    assertEquals("Updated Lender", updated.lenderName)
    assertEquals("Metadata only", updated.notes)
    assertEquals(afterPayment.principalMinor, updated.principalMinor)
    assertEquals(afterPayment.annualInterestRatePercent, updated.annualInterestRatePercent)
    assertEquals(afterPayment.tenureMonths, updated.tenureMonths)
    assertEquals(afterPayment.currencyCode, updated.currencyCode)
    assertEquals(afterPayment.startDate, updated.startDate)
    assertEquals(afterPayment.nextDueDate, updated.nextDueDate)
    assertEquals(afterPayment.amountPaidMinor, updated.amountPaidMinor)
    assertEquals(afterPayment.outstandingPrincipalMinor, updated.outstandingPrincipalMinor)
    assertEquals("Payment-derived loan status must remain unchanged", afterPayment.status, updated.status)
    assertEquals(historyBefore, loanRepo.getPaymentsForLoan(userA, loan.id).first())
  }

  @Test
  fun `duplicate payment prevention blocks same day identical payment or repeated idempotency key`() = runTest {
    val loan = buildLoan(userId = userA, principalMinor = 10_000_00L)
    loanRepo.createLoan(userA, loan)

    val payTime = 1_701_000_000_000L
    val first = loanRepo.recordPayment(userA, loan.id, payTime, 888_49L, idempotencyKey = "emi_jan_1")
    assertTrue(first.isSuccess)

    // Attempt 1: identical idempotency key
    val dupKey = loanRepo.recordPayment(userA, loan.id, payTime + 86_400_000L, 888_49L, idempotencyKey = "emi_jan_1")
    assertTrue("Duplicate idempotency key must fail", dupKey.isFailure)

    // Attempt 2: same calendar day and same amount
    val dupDayAmount = loanRepo.recordPayment(userA, loan.id, payTime + 1_000L, 888_49L)
    assertTrue("Duplicate same-day identical amount must fail", dupDayAmount.isFailure)

    val history = loanRepo.getPaymentsForLoan(userA, loan.id).first()
    assertEquals("Only one payment must be persisted", 1, history.size)
  }

  @Test
  fun `markLoanCompleted rejects loan with remaining principal and overdue status triggers correctly`() = runTest {
    val loan = buildLoan(userId = userA, principalMinor = 5_000_00L, nextDueDate = 1_000L)
    loanRepo.createLoan(userA, loan)

    // Premature completion must be blocked
    val premature = loanRepo.markLoanCompleted(userA, loan.id)
    assertTrue("Marking incomplete loan completed must fail", premature.isFailure)

    // Evaluate status when current time is past nextDueDate
    val stored = loanRepo.getLoanById(userA, loan.id)!!
    val evaluated = emiEngine.evaluateEffectiveStatus(stored, nowMillis = 2_000L)
    assertEquals(LoanStatus.OVERDUE, evaluated)
  }

  @Test
  fun `strict UID isolation prevents cross-user query, update, payment, completion, and deletion`() = runTest {
    val loanA = buildLoan(userId = userA, name = "Alpha Private Loan")
    loanRepo.createLoan(userA, loanA)

    assertTrue(loanRepo.getLoans(userB).first().isEmpty())
    assertNull(loanRepo.getLoanById(userB, loanA.id))
    assertTrue(loanRepo.updateLoan(userB, loanA.copy(name = "Tampered", userId = userB)).isFailure)
    assertTrue(loanRepo.recordPayment(userB, loanA.id, 1_701_000_000_000L, 500_00L).isFailure)
    assertTrue(loanRepo.markLoanCompleted(userB, loanA.id).isFailure)
    assertTrue(loanRepo.deleteLoan(userB, loanA.id).isFailure)

    val preserved = loanRepo.getLoanById(userA, loanA.id)
    assertNotNull(preserved)
    assertEquals("Alpha Private Loan", preserved?.name)
  }

  @Test
  fun `deleteLoan removes loan and payment history atomically`() = runTest {
    val loan = buildLoan(userId = userA)
    loanRepo.createLoan(userA, loan)
    loanRepo.recordPayment(userA, loan.id, 1_701_000_000_000L, 888_49L)

    assertEquals(1, loanRepo.getPaymentsForLoan(userA, loan.id).first().size)
    assertTrue(loanRepo.deleteLoan(userA, loan.id).isSuccess)
    assertNull(loanRepo.getLoanById(userA, loan.id))
    assertTrue(loanRepo.getPaymentsForLoan(userA, loan.id).first().isEmpty())
  }

  @Test
  fun `migration 3 to 4 preserves existing budgets and creates loans tables non-destructively`() = runTest {
    val dbFile = File(context.cacheDir, "test_migration_3_4.db")
    if (dbFile.exists()) dbFile.delete()

    val helper =
      FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(dbFile.absolutePath)
          .callback(
            object : SupportSQLiteOpenHelper.Callback(3) {
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
                    updatedAt INTEGER NOT NULL,
                    normalizedMerchant TEXT
                  )
                  """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId ON financial_transactions(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_accountId ON financial_transactions(userId, accountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_transactionDate ON financial_transactions(userId, transactionDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_referenceId ON financial_transactions(userId, referenceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_linkedTransferId ON financial_transactions(userId, linkedTransferId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_normalizedMerchant ON financial_transactions(userId, normalizedMerchant)")
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
                  """
                  CREATE TABLE IF NOT EXISTS budgets (
                    id TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    amountMinor INTEGER NOT NULL,
                    currencyCode TEXT NOT NULL,
                    periodType TEXT NOT NULL,
                    startDate INTEGER NOT NULL,
                    endDate INTEGER NOT NULL,
                    warningThresholdPercent INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                  )
                  """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_userId_category ON budgets(userId, category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_userId_currencyCode ON budgets(userId, currencyCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_userId_startDate_endDate ON budgets(userId, startDate, endDate)")

                db.execSQL(
                  "INSERT INTO budgets VALUES ('b_v3', 'u_mig', 'Groceries', 'Food', 50000, 'USD', 'MONTHLY', 1000, 2000, 75, 1000, 1000)"
                )
              }

              override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }
          )
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
      val preservedBudget = migratedDb.budgetDao().getBudgetById("u_mig", "b_v3")
      assertNotNull("Existing v3 budget must survive migration 3->4", preservedBudget)
      assertEquals("Groceries", preservedBudget?.name)

      val loans = migratedDb.loanDao().getAllLoans("u_mig").first()
      assertTrue("Loans table must exist and be empty after migration", loans.isEmpty())
    } finally {
      migratedDb.close()
      dbFile.delete()
    }
  }
}
