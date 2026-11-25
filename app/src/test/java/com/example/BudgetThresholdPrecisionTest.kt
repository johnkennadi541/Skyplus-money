package com.example

import com.example.domain.model.Budget
import com.example.domain.model.BudgetAlertStatus
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.usecase.BudgetCalculationEngine
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Alert bands must be classified exactly, never from a rounded percentage.
 *
 * The bands are decided by cross-multiplying (spent x 100 versus allocated x 75/90/100), so a value
 * that is a hair below a threshold cannot round up into the band above it.
 */
class BudgetThresholdPrecisionTest {

  private lateinit var engine: BudgetCalculationEngine

  private val uid = "user_threshold_1"
  private val start = 1_000_000L
  private val end = 2_000_000L
  private val inside = 1_500_000L

  @Before
  fun setUp() {
    engine = BudgetCalculationEngine()
  }

  private fun budget(allocatedMinor: Long): Budget =
    Budget(
      id = UUID.randomUUID().toString(),
      userId = uid,
      name = "Groceries",
      category = "Food & Dining",
      amountMinor = allocatedMinor,
      currencyCode = "INR",
      periodType = BudgetPeriodType.MONTHLY,
      startDate = start,
      endDate = end,
    )

  private fun transaction(
    type: TransactionType,
    amountMinor: Long,
  ): FinancialTransaction =
    FinancialTransaction(
      id = UUID.randomUUID().toString(),
      userId = uid,
      accountId = "acc-1",
      type = type,
      status = TransactionStatus.COMPLETED,
      amountMinor = amountMinor,
      currencyCode = "INR",
      category = "Food & Dining",
      transactionDate = inside,
    )

  private fun bandFor(
    allocatedMinor: Long,
    spentMinor: Long,
    thresholdPercent: Int = 75,
  ): BudgetAlertStatus =
    engine
      .calculateBudgetStatus(
        budget = budget(allocatedMinor).copy(warningThresholdPercent = thresholdPercent),
        transactions = listOf(transaction(TransactionType.EXPENSE, spentMinor)),
        nowMillis = inside,
      )
      .alertStatus

  // ---------------------------------------------------------------- 75 %

  @Test
  fun `just below 75 percent stays normal`() {
    assertEquals(BudgetAlertStatus.NORMAL, bandFor(10_000L, 7_499L))
  }

  @Test
  fun `exactly 75 percent enters the 75 band`() {
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, bandFor(10_000L, 7_500L))
  }

  @Test
  fun `just above 75 percent stays in the 75 band`() {
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, bandFor(10_000L, 7_501L))
  }

  // ---------------------------------------------------------------- 90 %

  @Test
  fun `just below 90 percent stays in the 75 band`() {
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, bandFor(10_000L, 8_999L))
  }

  @Test
  fun `exactly 90 percent enters the 90 band`() {
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, bandFor(10_000L, 9_000L))
  }

  @Test
  fun `just above 90 percent stays in the 90 band`() {
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, bandFor(10_000L, 9_001L))
  }

  // --------------------------------------------------------------- 100 %

  @Test
  fun `just below 100 percent stays in the 90 band`() {
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, bandFor(10_000L, 9_999L))
  }

  @Test
  fun `exactly 100 percent reaches configured threshold without overspending`() {
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, bandFor(10_000L, 10_000L))
  }

  @Test
  fun `just above 100 percent is overspent`() {
    assertEquals(BudgetAlertStatus.EXCEEDED_100, bandFor(10_000L, 10_001L))
  }

  // ------------------------------------------------- values that would round up

  @Test
  fun `a value that would round up to 75 is still normal`() {
    val status =
      engine.calculateBudgetStatus(
        budget = budget(2_000_000L),
        transactions = listOf(transaction(TransactionType.EXPENSE, 1_499_999L)),
        nowMillis = inside,
      )

    // The display percentage does round to 75.0, which is exactly why it must not decide the band.
    assertEquals(75.0, status.percentageConsumed, 0.00001)
    assertEquals(BudgetAlertStatus.NORMAL, status.alertStatus)
  }

  @Test
  fun `a value that would round up to 90 remains at the configured threshold`() {
    val status =
      engine.calculateBudgetStatus(
        budget = budget(2_000_000L),
        transactions = listOf(transaction(TransactionType.EXPENSE, 1_799_999L)),
        nowMillis = inside,
      )

    assertEquals(90.0, status.percentageConsumed, 0.00001)
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, status.alertStatus)
  }

  @Test
  fun `a value that would round up to 100 remains at the configured threshold`() {
    val status =
      engine.calculateBudgetStatus(
        budget = budget(2_000_000L),
        transactions = listOf(transaction(TransactionType.EXPENSE, 1_999_999L)),
        nowMillis = inside,
      )

    assertEquals(100.0, status.percentageConsumed, 0.00001)
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, status.alertStatus)
  }

  // ---------------------------------------------------- configured thresholds

  @Test
  fun `90 percent setting is the sole warning threshold`() {
    assertEquals(BudgetAlertStatus.NORMAL, bandFor(10_000L, 8_999L, thresholdPercent = 90))
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, bandFor(10_000L, 9_000L, thresholdPercent = 90))
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, bandFor(10_000L, 9_999L, thresholdPercent = 90))
  }

  @Test
  fun `100 percent setting warns at limit and overspends only above limit`() {
    assertEquals(BudgetAlertStatus.NORMAL, bandFor(10_000L, 9_999L, thresholdPercent = 100))
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, bandFor(10_000L, 10_000L, thresholdPercent = 100))
    assertEquals(BudgetAlertStatus.EXCEEDED_100, bandFor(10_000L, 10_001L, thresholdPercent = 100))
  }

  // ------------------------------------------------------------- preservation

  @Test
  fun `a stale budget is still period ended even when overspent`() {
    val status =
      engine.calculateBudgetStatus(
        budget = budget(10_000L),
        transactions = listOf(transaction(TransactionType.EXPENSE, 20_000L)),
        nowMillis = end + 1L,
      )

    assertTrue(status.isStale)
    assertEquals(BudgetAlertStatus.PERIOD_ENDED, status.alertStatus)
  }

  @Test
  fun `a zero allocation stays normal and never divides by zero`() {
    val status =
      engine.calculateBudgetStatus(
        budget = budget(0L),
        transactions = listOf(transaction(TransactionType.EXPENSE, 5_000L)),
        nowMillis = inside,
      )

    assertEquals(BudgetAlertStatus.NORMAL, status.alertStatus)
    assertEquals(0.0, status.percentageConsumed, 0.0)
    assertEquals(5_000L, status.spentMoney.minorUnits)
  }

  @Test
  fun `refunds reduce the net spend before the band is chosen`() {
    val status =
      engine.calculateBudgetStatus(
        budget = budget(10_000L),
        transactions =
          listOf(
            transaction(TransactionType.EXPENSE, 10_000L),
            transaction(TransactionType.REFUND, 2_500L),
          ),
        nowMillis = inside,
      )

    assertEquals(7_500L, status.spentMoney.minorUnits)
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, status.alertStatus)
  }
}
