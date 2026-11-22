package com.example.domain.usecase

import com.example.domain.model.Budget
import com.example.domain.model.BudgetPeriod
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.FinancialTransaction
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** What renewal decided for one budget. */
sealed interface BudgetRenewalPlan {

  /** Nothing to do: not recurring, not monthly, not eligible, or the window is still running. */
  data object NotDue : BudgetRenewalPlan

  data class Due(
    val budgetId: String,
    /** The window this plan expects to find in the database. Used for the compare-and-set. */
    val expectedStart: Long,
    val expectedEnd: Long,
    val closedPeriods: List<BudgetPeriod>,
    val nextStart: Long,
    val nextEnd: Long,
    val renewedAt: Long,
  ) : BudgetRenewalPlan
}

/**
 * Decides whether a monthly recurring budget should roll into the current month.
 *
 * Pure: no Android, no database, no clock of its own — the caller supplies `nowMillis`, so every
 * rule here is deterministic and unit tested.
 *
 * Rules, exactly as approved:
 *  - only MONTHLY recurring budgets renew;
 *  - each fully elapsed window is archived with its own spend snapshot;
 *  - the limit carries forward, unused amount never does;
 *  - the day-of-month anchor is preserved and clamped for short months;
 *  - after renewal the window ends in the future, so a second run is a no-op.
 */
@Singleton
class BudgetRenewalEngine @Inject constructor(private val calculationEngine: BudgetCalculationEngine) {

  fun plan(
    budget: Budget,
    transactions: List<FinancialTransaction>,
    nowMillis: Long,
  ): BudgetRenewalPlan {
    if (!budget.isRecurring) return BudgetRenewalPlan.NotDue
    if (budget.periodType != BudgetPeriodType.MONTHLY) return BudgetRenewalPlan.NotDue
    if (budget.amountMinor <= 0L) return BudgetRenewalPlan.NotDue
    if (budget.endDate <= budget.startDate) return BudgetRenewalPlan.NotDue
    if (nowMillis <= budget.endDate) return BudgetRenewalPlan.NotDue

    val closed = mutableListOf<BudgetPeriod>()
    // The anchor day comes from the original window and never changes, so 31 January gives
    // 28 February and then 31 March rather than drifting to 28 March.
    val anchorDay = dayOfMonth(budget.startDate)
    var windowStart = budget.startDate
    var windowEnd = budget.endDate
    var guard = 0

    while (nowMillis > windowEnd && guard < MAX_PERIODS) {
      val windowStatus =
        calculationEngine.calculateBudgetStatus(
          budget = budget.copy(startDate = windowStart, endDate = windowEnd),
          transactions = transactions,
          // Evaluate at the window's own end so the archive records the band the budget actually
          // had while it was running, not PERIOD_ENDED.
          nowMillis = windowEnd,
        )
      closed +=
        BudgetPeriod(
          // Deterministic identity: one archive row per budget and window, whatever the retry.
          id = archiveId(budget.id, windowStart, windowEnd),
          userId = budget.userId,
          budgetId = budget.id,
          category = budget.category,
          currencyCode = budget.currencyCode,
          periodType = budget.periodType,
          periodStart = windowStart,
          periodEnd = windowEnd,
          allocatedMinor = budget.amountMinor,
          spentMinor = windowStatus.spentMoney.minorUnits,
          remainingMinor = windowStatus.remainingMoney.minorUnits,
          alertStatus = windowStatus.alertStatus,
          closedAt = windowEnd,
        )
      windowStart = addMonthsWithAnchor(windowStart, 1, anchorDay)
      windowEnd = endOfWindow(windowStart, anchorDay)
      guard++
    }

    if (closed.isEmpty()) return BudgetRenewalPlan.NotDue

    return BudgetRenewalPlan.Due(
      budgetId = budget.id,
      expectedStart = budget.startDate,
      expectedEnd = budget.endDate,
      closedPeriods = closed,
      nextStart = windowStart,
      nextEnd = windowEnd,
      renewedAt = nowMillis,
    )
  }

  /** Stable archive identity for one budget window. */
  fun archiveId(budgetId: String, periodStart: Long, periodEnd: Long): String =
    "$budgetId:$periodStart:$periodEnd"

  /** Last millisecond of the window that starts at [windowStart], using the original anchor day. */
  fun endOfWindow(windowStart: Long, anchorDay: Int = dayOfMonth(windowStart)): Long =
    addMonthsWithAnchor(windowStart, 1, anchorDay) - 1L

  /**
   * Adds months keeping [anchorDay], clamped only for the target month's length. Because the anchor
   * is passed in rather than read back from the clamped date, the sequence 31 Jan → 28 Feb →
   * 31 Mar is preserved instead of drifting to 28 Mar.
   */
  fun addMonthsWithAnchor(timestampMillis: Long, months: Int, anchorDay: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestampMillis
    calendar.add(Calendar.MONTH, months)
    val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    calendar.set(Calendar.DAY_OF_MONTH, min(anchorDay, maxDay))
    return calendar.timeInMillis
  }

  /** Convenience overload that takes the anchor from the timestamp itself. */
  fun addMonthsClamped(timestampMillis: Long, months: Int): Long =
    addMonthsWithAnchor(timestampMillis, months, dayOfMonth(timestampMillis))

  fun dayOfMonth(timestampMillis: Long): Int {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestampMillis
    return calendar.get(Calendar.DAY_OF_MONTH)
  }

  private companion object {
    /** Ten years of missed renewals is already pathological; this stops a runaway loop. */
    const val MAX_PERIODS = 120
  }
}
