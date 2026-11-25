package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.repository.FinancialAccountRepositoryImpl
import com.example.data.repository.FinancialTransactionRepositoryImpl
import com.example.data.repository.SmartDetectionRepositoryImpl
import com.example.domain.model.AccountType
import com.example.domain.model.DetectionConfidence
import com.example.domain.model.DetectionEventStatus
import com.example.domain.model.DetectionSettings
import com.example.domain.model.FinancialAccount
import com.example.domain.model.ParsedFinancialMessage
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.feature.smartdetection.engine.SmartTransactionParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2DeduplicationRemediationTest {

  private lateinit var context: Context
  private lateinit var database: AppDatabase
  private lateinit var accountRepo: FinancialAccountRepositoryImpl
  private lateinit var txnRepo: FinancialTransactionRepositoryImpl
  private lateinit var detectionRepo: SmartDetectionRepositoryImpl

  private val uid = "uid_dedup_test"

  private val testDispatchers =
    object : DispatcherProvider {
      private val d = UnconfinedTestDispatcher()
      override val main: CoroutineDispatcher = d
      override val io: CoroutineDispatcher = d
      override val default: CoroutineDispatcher = d
    }

  @Before
  fun setUp() = runTest {
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

    accountRepo.createAccount(
      uid,
      FinancialAccount(
        id = "acc_1",
        userId = uid,
        name = "HDFC 4321",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 500_000L,
        currentBalanceMinor = 500_000L,
        accountNumberLast4 = "4321",
      ),
    )
    accountRepo.createAccount(
      uid,
      FinancialAccount(
        id = "acc_2",
        userId = uid,
        name = "ICICI 9876",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 500_000L,
        currentBalanceMinor = 500_000L,
        accountNumberLast4 = "9876",
      ),
    )
    detectionRepo.updateDetectionSettings(
      uid,
      DetectionSettings(userId = uid, isNotificationDetectionEnabled = true, isAutoSaveHighConfidenceEnabled = true),
    )
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `same merchant and amount with different reference IDs remain in Review Queue`() = runTest {
    val t1 = SmartTransactionParser.parse("Rs. 150.00 debited from A/c XX4321 paid to Starbucks Ref REF111111", "com.phonepe.app", 1_700_000_000_000L)!!
    val t2 = SmartTransactionParser.parse("Rs. 150.00 debited from A/c XX4321 paid to Starbucks Ref REF222222", "com.phonepe.app", 1_700_000_120_000L)!!

    val ev1 = detectionRepo.processIncomingEvent(uid, "com.phonepe.app", "PhonePe", t1).getOrThrow()
    val ev2 = detectionRepo.processIncomingEvent(uid, "com.phonepe.app", "PhonePe", t2).getOrThrow()

    assertEquals(DetectionEventStatus.IN_REVIEW, ev1.status)
    assertEquals(DetectionEventStatus.IN_REVIEW, ev2.status)
    assertEquals(500_000L, accountRepo.getAccountById(uid, "acc_1")!!.currentBalanceMinor)
  }

  @Test
  fun `same merchant and amount on different accounts remain in Review Queue`() = runTest {
    val msgAcc1 = ParsedFinancialMessage(
      amountMinor = 30_000L,
      currencyCode = "INR",
      type = TransactionType.EXPENSE,
      status = TransactionStatus.COMPLETED,
      merchantOrPayee = "MetroRail",
      accountLast4Hint = "4321",
      referenceId = null,
      confidenceScore = 0.90f,
      confidenceLevel = DetectionConfidence.HIGH,
      confidenceReasons = listOf("test"),
      timestamp = 1_700_000_000_000L,
    )
    val msgAcc2 = msgAcc1.copy(accountLast4Hint = "9876", timestamp = 1_700_000_060_000L)

    val ev1 = detectionRepo.processIncomingEvent(uid, "com.phonepe.app", "PhonePe", msgAcc1).getOrThrow()
    val ev2 = detectionRepo.processIncomingEvent(uid, "com.phonepe.app", "PhonePe", msgAcc2).getOrThrow()

    assertEquals(DetectionEventStatus.AUTO_SAVED, ev1.status)
    assertEquals(DetectionEventStatus.AUTO_SAVED, ev2.status)
    assertEquals(470_000L, accountRepo.getAccountById(uid, "acc_1")!!.currentBalanceMinor)
    assertEquals(500_000L, accountRepo.getAccountById(uid, "acc_2")!!.currentBalanceMinor)
  }

  @Test
  fun `repeated NOREF same merchant and amount inside 10 minutes routes to Review Queue instead of silent DUPLICATE`() = runTest {
    val firstCoffee = ParsedFinancialMessage(
      amountMinor = 15_000L,
      currencyCode = "INR",
      type = TransactionType.EXPENSE,
      status = TransactionStatus.COMPLETED,
      merchantOrPayee = "Starbucks",
      accountLast4Hint = "4321",
      referenceId = null,
      confidenceScore = 0.85f,
      confidenceLevel = DetectionConfidence.HIGH,
      confidenceReasons = listOf("test"),
      timestamp = 1_700_000_000_000L,
    )
    // Exact OS re-delivery (same package, same timestamp) -> safe DUPLICATE
    val exactRedelivery = firstCoffee.copy(timestamp = 1_700_000_000_300L)
    // Legitimate second coffee 2 minutes later without Ref ID -> ambiguous, must route to IN_REVIEW
    val secondCoffee = firstCoffee.copy(timestamp = 1_700_000_120_000L)

    val ev1 = detectionRepo.processIncomingEvent(uid, "com.phonepe.app", "PhonePe", firstCoffee).getOrThrow()
    val evRedelivered = detectionRepo.processIncomingEvent(uid, "com.phonepe.app", "PhonePe", exactRedelivery).getOrThrow()
    val ev2 = detectionRepo.processIncomingEvent(uid, "com.phonepe.app", "PhonePe", secondCoffee).getOrThrow()

    assertEquals(DetectionEventStatus.AUTO_SAVED, ev1.status)
    assertEquals(DetectionEventStatus.DUPLICATE, evRedelivered.status)
    assertEquals(DetectionEventStatus.IN_REVIEW, ev2.status)
    assertEquals(500_000L, accountRepo.getAccountById(uid, "acc_1")!!.currentBalanceMinor)

    // Explicit approval is the only operation that changes the balance.
    detectionRepo.approveReviewEvent(uid, ev2.id, "acc_1", "Food & Dining", "Starbucks").getOrThrow()
    assertEquals(485_000L, accountRepo.getAccountById(uid, "acc_1")!!.currentBalanceMinor)
  }

  @Test
  fun `null merchant does not act as wildcard to discard distinct transactions within 10 minutes`() = runTest {
    val firstNamed = ParsedFinancialMessage(
      amountMinor = 20_000L,
      currencyCode = "INR",
      type = TransactionType.EXPENSE,
      status = TransactionStatus.COMPLETED,
      merchantOrPayee = "LocalShop",
      accountLast4Hint = "4321",
      referenceId = null,
      confidenceScore = 0.85f,
      confidenceLevel = DetectionConfidence.HIGH,
      confidenceReasons = listOf("test"),
      timestamp = 1_700_000_000_000L,
    )
    val secondNullMerchant = firstNamed.copy(
      merchantOrPayee = null,
      timestamp = 1_700_000_120_000L,
    )

    val ev1 = detectionRepo.processIncomingEvent(uid, "com.phonepe.app", "PhonePe", firstNamed).getOrThrow()
    val ev2 = detectionRepo.processIncomingEvent(uid, "com.snapwork.hdfc", "HDFC Bank", secondNullMerchant).getOrThrow()

    assertEquals(DetectionEventStatus.AUTO_SAVED, ev1.status)
    assertEquals(DetectionEventStatus.AUTO_SAVED, ev2.status)
    assertEquals(500_000L, accountRepo.getAccountById(uid, "acc_1")!!.currentBalanceMinor)
    assertEquals(0, txnRepo.getTransactions(uid).first().size)
  }
}
