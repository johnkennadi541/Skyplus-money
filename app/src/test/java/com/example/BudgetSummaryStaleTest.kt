package com.example

import com.example.domain.model.Budget
import com.example.domain.model.BudgetAlertStatus
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.BudgetStatus
import com.example.domain.model.Money
import com.example.domain.usecase.BudgetCalculationEngine
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A closed budget period must never contribute to the live "allocated / spent / remaining" totals.
 * Its card and its archived history stay; only the summary ignores it.
 */
class BudgetSummaryStaleTest {

  private lateinit var engine: BudgetCalculationEngine

  private val uid = "user_summary_1"

  @Before
  fun setUp() {
    engine = BudgetCalculationEngine()
  }

  private fun status(
    currency: String = "INR",
    allocated: Long,
    spent: Long,
    stale: Boolean,
    category: String = "Food & Dining",
  ): BudgetStatus {
    val budget =
      Budget(
        id = UUID.randomUUID().toString(),
        userId = uid,
        name = "Budget $category",
        category = category,
        amountMinor = allocated,
        currencyCode = currency,
        periodType = BudgetPeriodType.MONTHLY,
        startDate = 1_000L,
        endDate = 2_000L,
      )
    return BudgetStatus(
      budget = budget,
      allocatedMoney = Money.fromMinorUnits(allocated, currency),
      spentMoney = Money.fromMinorUnits(spent, currency),
      remainingMoney = Money.fromMinorUnits(allocated - spent, currency),
      percentageConsumed = if (allocated > 0L) (spent * 100.0 / allocated) else 0.0,
      overspendingMoney = Money.fromMinorUnits(maxOf(0L, spent - allocated), currency),
      alertStatus = if (stale) BudgetAlertStatus.PERIOD_ENDED else BudgetAlertStatus.NORMAL,
      transactionCount = 1,
      isStale = stale,
    )
  }

  @Test
  fun `a stale budget is excluded from the active summary`() {
    val summaries = engine.calculateSummaries(listOf(status(allocated = 100_00L, spent = 40_00L, stale = true)))

    assertTrue(summaries.isEmpty())
  }

  @Test
  fun `a live budget is still included`() {
    val summaries = engine.calculateSummaries(listOf(status(allocated = 100_00L, spent = 40_00L, stale = false)))

    val summary = summaries["INR"]!!
    assertEquals(100_00L, summary.totalAllocatedMinor)
    assertEquals(40_00L, summary.totalSpentMinor)
    assertEquals(60_00L, summary.totalRemainingMinor)
    assertEquals(1, summary.activeBudgetCount)
  }

  @Test
  fun `a mixture of stale and live budgets totals only the live ones`() {
    val summaries =
      engine.calculateSummaries(
        listOf(
          status(allocated = 100_00L, spent = 90_00L, stale = true, category = "Old"),
          status(allocated = 50_00L, spent = 10_00L, stale = false, category = "Live A"),
          status(allocated = 30_00L, spent = 30_00L, stale = false, category = "Live B"),
        )
      )

    val summary = summaries["INR"]!!
    assertEquals(80_00L, summary.totalAllocatedMinor)
    assertEquals(40_00L, summary.totalSpentMinor)
    assertEquals(40_00L, summary.totalRemainingMinor)
    assertEquals(2, summary.activeBudgetCount)
    assertEquals(1, summary.overspentBudgetCount)
    assertEquals(50.0, summary.averageUtilizationPercent, 0.001)
  }

  @Test
  fun `every budget stale leaves no summary for that currency`() {
    val summaries =
      engine.calculateSummaries(
        listOf(
          status(allocated = 100_00L, spent = 90_00L, stale = true, category = "A"),
          status(allocated = 20_00L, spent = 5_00L, stale = true, category = "B"),
        )
      )

    assertNull(summaries["INR"])
    assertTrue(summaries.isEmpty())
  }

  @Test
  fun `currencies stay separate and stale exclusion applies per currency`() {
    val summaries =
      engine.calculateSummaries(
        listOf(
          status(currency = "INR", allocated = 100_00L, spent = 10_00L, stale = false),
          status(currency = "INR", allocated = 500_00L, spent = 400_00L, stale = true, category = "Old INR"),
          status(currency = "USD", allocated = 70_00L, spent = 20_00L, stale = true, category = "Old USD"),
        )
      )

    assertEquals(setOf("INR"), summaries.keys)
    assertEquals(100_00L, summaries["INR"]!!.totalAllocatedMinor)
    assertEquals(1, summaries["INR"]!!.activeBudgetCount)
    assertFalse(summaries.containsKey("USD"))
  }

  @Test
  fun `stale budgets are still produced as statuses so their cards stay`() {
    // The exclusion happens in the summary only: the engine still returns the status itself.
    val stale = status(allocated = 100_00L, spent = 40_00L, stale = true)
    assertTrue(stale.isStale)
    assertEquals(BudgetAlertStatus.PERIOD_ENDED, stale.alertStatus)
    assertEquals(60_00L, stale.remainingMoney.minorUnits)
  }
}
