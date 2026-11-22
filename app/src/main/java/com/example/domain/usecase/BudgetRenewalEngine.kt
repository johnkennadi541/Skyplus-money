package com.example.domain.usecase

import com.example.domain.model.Budget
import com.example.domain.model.BudgetPeriod
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.FinancialTransaction
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** What renewal decided for one budget. */
sealed interface BudgetRenewalPlan {

  /** Nothing to do: not recurring, not monthly, not eligible, or the window is still running. */
  data object NotDue : BudgetRenewalPlan

  data class Due(
    val budgetId: String,
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
    var windowStart = budget.startDate
    var windowEnd = budget.endDate
    var guard = 0

    while (nowMillis > windowEnd && guard < MAX_PERIODS) {
      val windowStatus =
        calculationEngine.calculateBudgetStatus(
          budget = budget.copy(startDate = windowStart, endDate = windowEnd),
          transactions = transactions,
          nowMillis = nowMillis,
        )
      closed +=
        BudgetPeriod(
          id = UUID.randomUUID().toString(),
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
      windowStart = addMonthsClamped(windowStart, 1)
      windowEnd = endOfWindow(windowStart)
      guard++
    }

    if (closed.isEmpty()) return BudgetRenewalPlan.NotDue

    return BudgetRenewalPlan.Due(
      budgetId = budget.id,
      closedPeriods = closed,
      nextStart = windowStart,
      nextEnd = windowEnd,
      renewedAt = nowMillis,
    )
  }

  /** Last millisecond of the month that starts at [windowStart], keeping its day-of-month anchor. */
  fun endOfWindow(windowStart: Long): Long = addMonthsClamped(windowStart, 1) - 1L

  /**
   * Adds months while keeping the day of month, clamped to the target month's length
   * (31 January → 28 or 29 February). Time of day is preserved.
   */
  fun addMonthsClamped(timestampMillis: Long, months: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestampMillis
    val anchorDay = calendar.get(Calendar.DAY_OF_MONTH)
    calendar.add(Calendar.MONTH, months)
    val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    calendar.set(Calendar.DAY_OF_MONTH, min(anchorDay, maxDay))
    return calendar.timeInMillis
  }

  private companion object {
    /** Ten years of missed renewals is already pathological; this stops a runaway loop. */
    const val MAX_PERIODS = 120
  }
}
