package com.example.domain.model

import java.math.BigDecimal
import java.util.UUID

enum class BudgetPeriodType {
  MONTHLY,
  CUSTOM,
}

enum class BudgetAlertStatus {
  NORMAL,
  APPROACHING_75,
  WARNING_90,
  EXCEEDED_100,

  /** The budget window has already ended, so its remaining figure no longer describes today. */
  PERIOD_ENDED,
}

data class Budget(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val name: String,
  val category: String,
  val amountMinor: Long,
  val currencyCode: String,
  val periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
  val startDate: Long,
  val endDate: Long,
  val warningThresholdPercent: Int = 75,
  /**
   * When true and [periodType] is MONTHLY, an elapsed period is archived and the budget moves on to
   * the current month with the same limit. Unused amount is never carried forward.
   */
  val isRecurring: Boolean = true,
  /** When the window was last moved forward by renewal; 0 means it never has been. */
  val lastRenewedAt: Long = 0L,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

data class BudgetStatus(
  val budget: Budget,
  val allocatedMoney: Money,
  val spentMoney: Money,
  val remainingMoney: Money,
  val percentageConsumed: Double,
  val overspendingMoney: Money,
  val alertStatus: BudgetAlertStatus,
  val transactionCount: Int,
  /** True when the window has ended; the remaining figure is then historical, not current. */
  val isStale: Boolean = false,
  /** Exact percentage, used for the alert bands so no threshold is decided by a rounded Double. */
  val percentageConsumedExact: BigDecimal = BigDecimal.ZERO,
)

data class BudgetSummary(
  val currencyCode: String,
  val totalAllocatedMinor: Long,
  val totalSpentMinor: Long,
  val totalRemainingMinor: Long,
  val averageUtilizationPercent: Double,
  val activeBudgetCount: Int,
  val overspentBudgetCount: Int,
)
