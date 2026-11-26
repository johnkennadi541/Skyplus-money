package com.example

import com.example.domain.model.Budget
import com.example.domain.model.BudgetAlertStatus
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.usecase.BudgetCalculationEngine
import com.example.domain.usecase.BudgetRenewalEngine
import com.example.domain.usecase.BudgetRenewalPlan
import java.util.Calendar
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Renewal rules. Every case pins the clock explicitly, so nothing depends on when the suite runs.
 */
class BudgetRenewalEngineTest {

  private lateinit var engine: BudgetRenewalEngine
  private lateinit var calculationEngine: BudgetCalculationEngine

  private val uid = "user_renewal_1"
  private val otherUid = "user_renewal_2"

  @Before
  fun setUp() {
    calculationEngine = BudgetCalculationEngine()
    engine = BudgetRenewalEngine(calculationEngine)
  }

  private fun at(year: Int, monthZeroBased: Int, day: Int, hour: Int = 0): Long {
    val calendar = Calendar.getInstance()
    calendar.clear()
    calendar.set(year, monthZeroBased, day, hour, 0, 0)
    return calendar.timeInMillis
  }

  private fun lastMillisOfMonth(year: Int, monthZeroBased: Int): Long =
    engine.addMonthsClamped(at(year, monthZeroBased, 1), 1) - 1L

  private fun budget(
    start: Long,
    end: Long,
    amountMinor: Long = 100_00L,
    periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
    isRecurring: Boolean = true,
    userId: String = uid,
    category: String = "Food & Dining",
  ): Budget =
    Budget(
      id = UUID.randomUUID().toString(),
      userId = userId,
      name = "Groceries",
      category = category,
      amountMinor = amountMinor,
      currencyCode = "INR",
      periodType = periodType,
      startDate = start,
      endDate = end,
      isRecurring = isRecurring,
    )

  private fun expense(
    amountMinor: Long,
    timestamp: Long,
    category: String = "Food & Dining",
    userId: String = uid,
  ): FinancialTransaction =
    FinancialTransaction(
      id = UUID.randomUUID().toString(),
      userId = userId,
      accountId = "acc-1",
      type = TransactionType.EXPENSE,
      status = TransactionStatus.COMPLETED,
      amountMinor = amountMinor,
      currencyCode = "INR",
      category = category,
      transactionDate = timestamp,
    )

  @Test
  fun `a stale monthly budget renews into the current month`() {
    val january = budget(start = at(2026, 0, 1), end = lastMillisOfMonth(2026, 0))
    val plan = engine.plan(january, emptyList(), nowMillis = at(2026, 1, 20))

    assertTrue(plan is BudgetRenewalPlan.Due)
    plan as BudgetRenewalPlan.Due
    assertEquals(1, plan.closedPeriods.size)
    assertEquals(at(2026, 0, 1), plan.closedPeriods.first().periodStart)
    assertEquals(at(2026, 1, 1), plan.nextStart)
    assertEquals(lastMillisOfMonth(2026, 1), plan.nextEnd)
    assertTrue(plan.nextEnd > at(2026, 1, 20))
  }

  @Test
  fun `three elapsed months archive three periods in date order`() {
    val october = budget(start = at(2025, 9, 1), end = lastMillisOfMonth(2025, 9))
    val plan = engine.plan(october, emptyList(), nowMillis = at(2026, 0, 20)) as BudgetRenewalPlan.Due

    assertEquals(3, plan.closedPeriods.size)
    assertEquals(at(2025, 9, 1), plan.closedPeriods[0].periodStart)
    assertEquals(at(2025, 10, 1), plan.closedPeriods[1].periodStart)
    assertEquals(at(2025, 11, 1), plan.closedPeriods[2].periodStart)
    assertEquals(at(2026, 0, 1), plan.nextStart)
  }

  @Test
  fun `unused amount is never carried forward`() {
    val january = budget(start = at(2026, 0, 1), end = lastMillisOfMonth(2026, 0), amountMinor = 100_00L)
    val transactions = listOf(expense(20_00L, at(2026, 0, 10)))

    val plan = engine.plan(january, transactions, nowMillis = at(2026, 1, 5)) as BudgetRenewalPlan.Due

    val closed = plan.closedPeriods.first()
    assertEquals(100_00L, closed.allocatedMinor)
    assertEquals(20_00L, closed.spentMinor)
    assertEquals(80_00L, closed.remainingMinor)
    // The next window keeps the same limit; the 80.00 left over is not added to it.
    assertEquals(january.amountMinor, january.copy(startDate = plan.nextStart, endDate = plan.nextEnd).amountMinor)
  }

  @Test
  fun `renewal is idempotent`() {
    val january = budget(start = at(2026, 0, 1), end = lastMillisOfMonth(2026, 0))
    val now = at(2026, 1, 20)

    val first = engine.plan(january, emptyList(), now) as BudgetRenewalPlan.Due
    val renewed = january.copy(startDate = first.nextStart, endDate = first.nextEnd, lastRenewedAt = first.renewedAt)

    assertEquals(BudgetRenewalPlan.NotDue, engine.plan(renewed, emptyList(), now))
  }

  @Test
  fun `a non-recurring budget never renews`() {
    val january = budget(start = at(2026, 0, 1), end = lastMillisOfMonth(2026, 0), isRecurring = false)

    assertEquals(BudgetRenewalPlan.NotDue, engine.plan(january, emptyList(), at(2026, 5, 1)))
  }

  @Test
  fun `a custom period never renews`() {
    val custom =
      budget(
        start = at(2026, 0, 1),
        end = at(2026, 0, 20),
        periodType = BudgetPeriodType.CUSTOM,
      )

    assertEquals(BudgetRenewalPlan.NotDue, engine.plan(custom, emptyList(), at(2026, 3, 1)))
  }

  @Test
  fun `a zero limit never renews`() {
    val zero = budget(start = at(2026, 0, 1), end = lastMillisOfMonth(2026, 0), amountMinor = 0L)

    assertEquals(BudgetRenewalPlan.NotDue, engine.plan(zero, emptyList(), at(2026, 3, 1)))
  }

  @Test
  fun `the anchor day is kept and clamped for short months`() {
    val january31 = at(2026, 0, 31, hour = 9)
    val february = engine.addMonthsClamped(january31, 1)

    val calendar = Calendar.getInstance()
    calendar.timeInMillis = february
    assertEquals(1, calendar.get(Calendar.MONTH))
    assertEquals(28, calendar.get(Calendar.DAY_OF_MONTH))
    assertEquals(9, calendar.get(Calendar.HOUR_OF_DAY))

    // Leap year: 31 January 2024 becomes 29 February 2024.
    val leap = engine.addMonthsClamped(at(2024, 0, 31, hour = 9), 1)
    calendar.timeInMillis = leap
    assertEquals(1, calendar.get(Calendar.MONTH))
    assertEquals(29, calendar.get(Calendar.DAY_OF_MONTH))
  }

  @Test
  fun `a stale budget reports the period-ended state instead of a live one`() {
    val january = budget(start = at(2026, 0, 1), end = lastMillisOfMonth(2026, 0))

    val stale = calculationEngine.calculateBudgetStatus(january, emptyList(), nowMillis = at(2026, 1, 10))
    assertTrue(stale.isStale)
    assertEquals(BudgetAlertStatus.PERIOD_ENDED, stale.alertStatus)

    val live = calculationEngine.calculateBudgetStatus(january, emptyList(), nowMillis = at(2026, 0, 15))
    assertFalse(live.isStale)
    assertEquals(BudgetAlertStatus.NORMAL, live.alertStatus)
  }

  @Test
  fun `alert bands are decided by the exact percentage`() {
    val start = at(2026, 0, 1)
    val end = lastMillisOfMonth(2026, 0)
    val now = at(2026, 0, 15)

    fun status(spent: Long) =
      calculationEngine.calculateBudgetStatus(
        budget(start = start, end = end, amountMinor = 100_00L),
        listOf(expense(spent, at(2026, 0, 10))),
        nowMillis = now,
      )

    assertEquals(BudgetAlertStatus.NORMAL, status(74_99L).alertStatus)
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, status(75_00L).alertStatus)
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, status(90_00L).alertStatus)
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, status(100_00L).alertStatus)
  }

  @Test
  fun `another user's transactions never count towards a renewal snapshot`() {
    val january = budget(start = at(2026, 0, 1), end = lastMillisOfMonth(2026, 0))
    val foreign = listOf(expense(90_00L, at(2026, 0, 10), userId = otherUid))

    val plan = engine.plan(january, foreign, nowMillis = at(2026, 1, 5)) as BudgetRenewalPlan.Due

    assertEquals(0L, plan.closedPeriods.first().spentMinor)
  }
  @Test
  fun `the anchor day survives a short month instead of drifting`() {
    val january31 = at(2026, 0, 31, hour = 9)
    val anchor = engine.dayOfMonth(january31)

    val february = engine.addMonthsWithAnchor(january31, 1, anchor)
    val march = engine.addMonthsWithAnchor(february, 1, anchor)

    val calendar = Calendar.getInstance()
    calendar.timeInMillis = february
    assertEquals(1, calendar.get(Calendar.MONTH))
    assertEquals(28, calendar.get(Calendar.DAY_OF_MONTH))

    calendar.timeInMillis = march
    assertEquals(2, calendar.get(Calendar.MONTH))
    assertEquals(31, calendar.get(Calendar.DAY_OF_MONTH))
  }

  @Test
  fun `a plan built from a month-end budget keeps the 31st`() {
    val budget =
      budget(
        start = at(2026, 0, 31, hour = 9),
        end = engine.endOfWindow(at(2026, 0, 31, hour = 9)),
      )

    val plan = engine.plan(budget, emptyList(), nowMillis = at(2026, 3, 5)) as BudgetRenewalPlan.Due

    assertEquals(2, plan.closedPeriods.size)

    val calendar = Calendar.getInstance()
    calendar.timeInMillis = plan.closedPeriods[0].periodStart
    assertEquals(0, calendar.get(Calendar.MONTH))
    assertEquals(31, calendar.get(Calendar.DAY_OF_MONTH))

    calendar.timeInMillis = plan.closedPeriods[1].periodStart
    assertEquals(1, calendar.get(Calendar.MONTH))
    assertEquals(28, calendar.get(Calendar.DAY_OF_MONTH))

    calendar.timeInMillis = plan.nextStart
    assertEquals(2, calendar.get(Calendar.MONTH))
    assertEquals(31, calendar.get(Calendar.DAY_OF_MONTH))
  }

  @Test
  fun `the anchor day is 29 February in a leap year and back to 31 in March`() {
    val january31 = at(2024, 0, 31, hour = 9)
    val anchor = engine.dayOfMonth(january31)

    val february = engine.addMonthsWithAnchor(january31, 1, anchor)
    val march = engine.addMonthsWithAnchor(february, 1, anchor)

    val calendar = Calendar.getInstance()
    calendar.timeInMillis = february
    assertEquals(1, calendar.get(Calendar.MONTH))
    assertEquals(29, calendar.get(Calendar.DAY_OF_MONTH))

    calendar.timeInMillis = march
    assertEquals(2, calendar.get(Calendar.MONTH))
    assertEquals(31, calendar.get(Calendar.DAY_OF_MONTH))
  }

  @Test
  fun `an archived period keeps the alert band it had while running`() {
    val january = budget(start = at(2026, 0, 1), end = lastMillisOfMonth(2026, 0), amountMinor = 100_00L)
    val transactions = listOf(expense(80_00L, at(2026, 0, 10)))

    val plan = engine.plan(january, transactions, nowMillis = at(2026, 1, 20)) as BudgetRenewalPlan.Due

    val archived = plan.closedPeriods.single()
    assertEquals(BudgetAlertStatus.THRESHOLD_REACHED, archived.alertStatus)
    assertEquals(80_00L, archived.spentMinor)
    assertEquals(20_00L, archived.remainingMinor)
  }

  @Test
  fun `archive ids are stable for the same window`() {
    val january = budget(start = at(2026, 0, 1), end = lastMillisOfMonth(2026, 0))

    val first = engine.plan(january, emptyList(), at(2026, 1, 20)) as BudgetRenewalPlan.Due
    val second = engine.plan(january, emptyList(), at(2026, 1, 20)) as BudgetRenewalPlan.Due

    assertEquals(
      first.closedPeriods.map { it.id },
      second.closedPeriods.map { it.id },
    )
    assertEquals(january.id + ":" + january.startDate + ":" + january.endDate, first.closedPeriods.single().id)
  }
}
