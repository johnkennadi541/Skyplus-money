package com.example.domain.model

/**
 * An immutable snapshot of one closed budget window.
 *
 * Written once when a recurring budget rolls into the next month. Because the spend figures are
 * copied here at close time, later edits to old transactions can never rewrite the past.
 */
data class BudgetPeriod(
  val id: String,
  val userId: String,
  val budgetId: String,
  val category: String,
  val currencyCode: String,
  val periodType: BudgetPeriodType,
  val periodStart: Long,
  val periodEnd: Long,
  val allocatedMinor: Long,
  val spentMinor: Long,
  val remainingMinor: Long,
  val alertStatus: BudgetAlertStatus,
  val closedAt: Long,
)
