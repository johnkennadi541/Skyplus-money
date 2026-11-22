package com.example

import com.example.domain.model.Budget
import com.example.domain.model.BudgetAlertStatus
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.usecase.BudgetCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class BudgetCalculationEngineTest {

  private lateinit var engine: BudgetCalculationEngine

  private val testUserId = "user_test_123"
  private val otherUserId = "user_other_999"
  private val startTime = 1_000_000L
  private val endTime = 2_000_000L
  private val withinRangeTime = 1_500_000L
  private val outOfRangeTime = 3_000_000L

  @Before
  fun setUp() {
    engine = BudgetCalculationEngine()
  }

  private fun createBudget(
    amountMinor: Long = 100_00L, // $100.00
    currency: String = "USD",
    category: String = "Food & Dining",
    userId: String = testUserId,
  ): Budget =
    Budget(
      id = UUID.randomUUID().toString(),
      userId = userId,
      name = "Groceries & Dining",
      category = category,
      amountMinor = amountMinor,
      currencyCode = currency,
      periodType = BudgetPeriodType.MONTHLY,
      startDate = startTime,
      endDate = endTime,
    )

  private fun createTxn(
    type: TransactionType,
    status: TransactionStatus = TransactionStatus.COMPLETED,
    amountMinor: Long = 20_00L,
    currency: String = "USD",
    category: String = "Food & Dining",
    timestamp: Long = withinRangeTime,
    userId: String = testUserId,
  ): FinancialTransaction =
    FinancialTransaction(
      id = UUID.randomUUID().toString(),
      userId = userId,
      accountId = "acc_1",
      type = type,
      status = status,
      amountMinor = amountMinor,
      currencyCode = currency,
      category = category,
      transactionDate = timestamp,
    )

  @Test
  fun `calculates spending correctly for eligible completed expenses`() {
    val budget = createBudget(amountMinor = 100_00L)
    val transactions =
      listOf(
        createTxn(type = TransactionType.EXPENSE, amountMinor = 30_00L),
        createTxn(type = TransactionType.EXPENSE, amountMinor = 20_00L),
      )

    val status = engine.calculateBudgetStatus(budget, transactions, nowMillis = withinRangeTime)
    assertEquals(50_00L, status.spentMoney.minorUnits)
    assertEquals(50_00L, status.remainingMoney.minorUnits)
    assertEquals(50.0, status.percentageConsumed, 0.001)
    assertEquals(0L, status.overspendingMoney.minorUnits)
    assertEquals(BudgetAlertStatus.NORMAL, status.alertStatus)
    assertEquals(2, status.transactionCount)
  }

  @Test
  fun `excludes income, transfers, pending, and failed transactions from budget spending`() {
    val budget = createBudget(amountMinor = 100_00L)
    val transactions =
      listOf(
        createTxn(type = TransactionType.EXPENSE, amountMinor = 25_00L),
        createTxn(type = TransactionType.INCOME, amountMinor = 1000_00L),
        createTxn(type = TransactionType.TRANSFER, amountMinor = 200_00L),
        createTxn(type = TransactionType.EXPENSE, status = TransactionStatus.PENDING, amountMinor = 40_00L),
        createTxn(type = TransactionType.EXPENSE, status = TransactionStatus.FAILED, amountMinor = 50_00L),
        createTxn(type = TransactionType.PENDING, amountMinor = 60_00L),
        createTxn(type = TransactionType.FAILED, amountMinor = 70_00L),
      )

    val status = engine.calculateBudgetStatus(budget, transactions, nowMillis = withinRangeTime)
    assertEquals(25_00L, status.spentMoney.minorUnits)
    assertEquals(75_00L, status.remainingMoney.minorUnits)
    assertEquals(25.0, status.percentageConsumed, 0.001)
    assertEquals(1, status.transactionCount)
  }

  @Test
  fun `refunds and reversals reduce budget spending correctly`() {
    val budget = createBudget(amountMinor = 100_00L)
    val transactions =
      listOf(
        createTxn(type = TransactionType.EXPENSE, amountMinor = 50_00L),
        createTxn(type = TransactionType.REFUND, amountMinor = 15_00L),
        createTxn(type = TransactionType.REVERSAL, amountMinor = 5_00L),
      )

    val status = engine.calculateBudgetStatus(budget, transactions, nowMillis = withinRangeTime)
    assertEquals(30_00L, status.spentMoney.minorUnits) // 50 - 15 - 5 = 30
    assertEquals(70_00L, status.remainingMoney.minorUnits)
    assertEquals(30.0, status.percentageConsumed, 0.001)
    assertEquals(3, status.transactionCount)
  }

  @Test
  fun `spending cannot become negative when refunds exceed expenses`() {
    val budget = createBudget(amountMinor = 100_00L)
    val transactions =
      listOf(
        createTxn(type = TransactionType.EXPENSE, amountMinor = 10_00L),
        createTxn(type = TransactionType.REFUND, amountMinor = 50_00L),
      )

    val status = engine.calculateBudgetStatus(budget, transactions, nowMillis = withinRangeTime)
    assertEquals(0L, status.spentMoney.minorUnits)
    assertEquals(100_00L, status.remainingMoney.minorUnits)
    assertEquals(0.0, status.percentageConsumed, 0.001)
  }

  @Test
  fun `excludes transactions outside date range, other categories, other users, and other currencies`() {
    val budget = createBudget(amountMinor = 100_00L, currency = "USD", category = "Food & Dining")
    val transactions =
      listOf(
        createTxn(type = TransactionType.EXPENSE, amountMinor = 20_00L), // MATCH
        createTxn(type = TransactionType.EXPENSE, amountMinor = 30_00L, timestamp = outOfRangeTime), // OUT OF DATE
        createTxn(type = TransactionType.EXPENSE, amountMinor = 40_00L, category = "Entertainment"), // WRONG CATEGORY
        createTxn(type = TransactionType.EXPENSE, amountMinor = 50_00L, currency = "EUR"), // WRONG CURRENCY
        createTxn(type = TransactionType.EXPENSE, amountMinor = 60_00L, userId = otherUserId), // OTHER USER
      )

    val status = engine.calculateBudgetStatus(budget, transactions, nowMillis = withinRangeTime)
    assertEquals(20_00L, status.spentMoney.minorUnits)
    assertEquals(80_00L, status.remainingMoney.minorUnits)
    assertEquals(1, status.transactionCount)
  }

  @Test
  fun `triggers 75 percent approaching threshold correctly`() {
    val budget = createBudget(amountMinor = 100_00L)
    val transactions = listOf(createTxn(type = TransactionType.EXPENSE, amountMinor = 75_00L))

    val status = engine.calculateBudgetStatus(budget, transactions, nowMillis = withinRangeTime)
    assertEquals(75.0, status.percentageConsumed, 0.001)
    assertEquals(BudgetAlertStatus.APPROACHING_75, status.alertStatus)
  }

  @Test
  fun `triggers 90 percent warning threshold correctly`() {
    val budget = createBudget(amountMinor = 100_00L)
    val transactions = listOf(createTxn(type = TransactionType.EXPENSE, amountMinor = 92_50L))

    val status = engine.calculateBudgetStatus(budget, transactions, nowMillis = withinRangeTime)
    assertEquals(92.5, status.percentageConsumed, 0.001)
    assertEquals(BudgetAlertStatus.WARNING_90, status.alertStatus)
  }

  @Test
  fun `triggers 100 percent exceeded threshold and calculates overspending`() {
    val budget = createBudget(amountMinor = 100_00L)
    val transactions = listOf(createTxn(type = TransactionType.EXPENSE, amountMinor = 135_00L))

    val status = engine.calculateBudgetStatus(budget, transactions, nowMillis = withinRangeTime)
    assertEquals(135.0, status.percentageConsumed, 0.001)
    assertEquals(BudgetAlertStatus.EXCEEDED_100, status.alertStatus)
    assertEquals(-35_00L, status.remainingMoney.minorUnits)
    assertEquals(35_00L, status.overspendingMoney.minorUnits)
  }

  @Test
  fun `multi-currency summaries are strictly isolated without combining currencies`() {
    val usdBudget = createBudget(amountMinor = 200_00L, currency = "USD", category = "Food & Dining")
    val inrBudget = createBudget(amountMinor = 5000_00L, currency = "INR", category = "Transport")
    val kwdBudget = createBudget(amountMinor = 100_000L, currency = "KWD", category = "Utilities")

    val transactions =
      listOf(
        createTxn(type = TransactionType.EXPENSE, amountMinor = 150_00L, currency = "USD", category = "Food & Dining"),
        createTxn(type = TransactionType.EXPENSE, amountMinor = 6000_00L, currency = "INR", category = "Transport"), // Overspent
        createTxn(type = TransactionType.EXPENSE, amountMinor = 40_000L, currency = "KWD", category = "Utilities"),
      )

    val statuses =
      listOf(
        engine.calculateBudgetStatus(usdBudget, transactions, nowMillis = withinRangeTime),
        engine.calculateBudgetStatus(inrBudget, transactions, nowMillis = withinRangeTime),
        engine.calculateBudgetStatus(kwdBudget, transactions, nowMillis = withinRangeTime),
      )

    val summaries = engine.calculateSummaries(statuses)
    assertEquals(3, summaries.size)

    val usdSummary = summaries["USD"]!!
    assertEquals(200_00L, usdSummary.totalAllocatedMinor)
    assertEquals(150_00L, usdSummary.totalSpentMinor)
    assertEquals(50_00L, usdSummary.totalRemainingMinor)
    assertEquals(75.0, usdSummary.averageUtilizationPercent, 0.001)
    assertEquals(0, usdSummary.overspentBudgetCount)

    val inrSummary = summaries["INR"]!!
    assertEquals(5000_00L, inrSummary.totalAllocatedMinor)
    assertEquals(6000_00L, inrSummary.totalSpentMinor)
    assertEquals(-1000_00L, inrSummary.totalRemainingMinor)
    assertEquals(120.0, inrSummary.averageUtilizationPercent, 0.001)
    assertEquals(1, inrSummary.overspentBudgetCount)

    val kwdSummary = summaries["KWD"]!!
    assertEquals(100_000L, kwdSummary.totalAllocatedMinor)
    assertEquals(40_000L, kwdSummary.totalSpentMinor)
    assertEquals(60_000L, kwdSummary.totalRemainingMinor)
    assertEquals(40.0, kwdSummary.averageUtilizationPercent, 0.001)
    assertEquals(0, kwdSummary.overspentBudgetCount)
  }
}
