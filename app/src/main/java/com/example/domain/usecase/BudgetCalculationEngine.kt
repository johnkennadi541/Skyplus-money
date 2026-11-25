package com.example.domain.usecase

import com.example.domain.model.Budget
import com.example.domain.model.BudgetAlertStatus
import com.example.domain.model.BudgetStatus
import com.example.domain.model.BudgetSummary
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.Money
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class BudgetCalculationEngine @Inject constructor() {

  /**
   * Calculates the real-time financial status of a single budget against the verified transaction ledger.
   */
  fun calculateBudgetStatus(
    budget: Budget,
    transactions: List<FinancialTransaction>,
    nowMillis: Long = System.currentTimeMillis(),
  ): BudgetStatus {
    var totalExpensesMinor = 0L
    var totalRefundsMinor = 0L
    var matchingTxnCount = 0

    for (txn in transactions) {
      if (txn.userId != budget.userId) continue
      if (!txn.currencyCode.equals(budget.currencyCode, ignoreCase = true)) continue
      if (!txn.category.equals(budget.category, ignoreCase = true)) continue
      if (txn.transactionDate < budget.startDate || txn.transactionDate > budget.endDate) continue
      if (txn.status != TransactionStatus.COMPLETED) continue

      when (txn.type) {
        TransactionType.EXPENSE -> {
          totalExpensesMinor += txn.amountMinor
          matchingTxnCount++
        }
        TransactionType.REFUND, TransactionType.REVERSAL -> {
          totalRefundsMinor += txn.amountMinor
          matchingTxnCount++
        }
        TransactionType.INCOME,
        TransactionType.TRANSFER,
        TransactionType.PENDING,
        TransactionType.FAILED -> {
          // Strictly excluded from budget consumption
        }
      }
    }

    val netSpentMinor = max(0L, totalExpensesMinor - totalRefundsMinor)
    val remainingMinor = budget.amountMinor - netSpentMinor
    val overspendingMinor = max(0L, netSpentMinor - budget.amountMinor)

    // Display percentage only. It is rounded, so it must never decide an alert band.
    val percentageExact =
      if (budget.amountMinor > 0L) {
        BigDecimal.valueOf(netSpentMinor)
          .multiply(HUNDRED)
          .divide(BigDecimal.valueOf(budget.amountMinor), 4, RoundingMode.HALF_EVEN)
      } else {
        BigDecimal.ZERO
      }
    val percentageConsumed = percentageExact.toDouble()

    val isStale = nowMillis > budget.endDate

    // Band classification is done by cross-multiplying, with no division and no rounding:
    //   spent x 100  compared with  allocated x 75 / 90 / 100.
    // A value just below a threshold can therefore never round up into the band above it.
    val spentScaled = BigDecimal.valueOf(netSpentMinor).multiply(HUNDRED)
    val allocated = BigDecimal.valueOf(budget.amountMinor)

    val alertStatus =
      when {
        isStale -> BudgetAlertStatus.PERIOD_ENDED
        budget.amountMinor <= 0L -> BudgetAlertStatus.NORMAL
        spentScaled > allocated.multiply(HUNDRED) -> BudgetAlertStatus.EXCEEDED_100
        spentScaled >= allocated.multiply(BigDecimal.valueOf(budget.warningThresholdPercent.coerceIn(1, 100))) ->
          BudgetAlertStatus.THRESHOLD_REACHED
        else -> BudgetAlertStatus.NORMAL
      }

    return BudgetStatus(
      budget = budget,
      allocatedMoney = Money.fromMinorUnits(budget.amountMinor, budget.currencyCode),
      spentMoney = Money.fromMinorUnits(netSpentMinor, budget.currencyCode),
      remainingMoney = Money.fromMinorUnits(remainingMinor, budget.currencyCode),
      percentageConsumed = percentageConsumed,
      overspendingMoney = Money.fromMinorUnits(overspendingMinor, budget.currencyCode),
      alertStatus = alertStatus,
      transactionCount = matchingTxnCount,
      isStale = isStale,
      percentageConsumedExact = percentageExact,
    )
  }

  private companion object {
    val HUNDRED: BigDecimal = BigDecimal.valueOf(100)
  }

  /**
   * Generates per-currency summaries for all active budgets.
   * Currencies are NEVER combined into a single total.
   */
  fun calculateSummaries(budgetStatuses: List<BudgetStatus>): Map<String, BudgetSummary> {
    // A closed period's remaining amount is history, so it must never be added to the totals a
    // user reads as "available now". Stale budgets keep their cards and their archive; they are
    // only excluded from the live summary.
    val activeStatuses = budgetStatuses.filterNot { it.isStale }
    return activeStatuses
      .groupBy { it.budget.currencyCode }
      .mapValues { (currency, statuses) ->
        val totalAllocated = statuses.sumOf { it.budget.amountMinor }
        val totalSpent = statuses.sumOf { it.spentMoney.minorUnits }
        val totalRemaining = totalAllocated - totalSpent
        val avgUtilization =
          if (totalAllocated > 0L) {
            BigDecimal.valueOf(totalSpent)
              .multiply(HUNDRED)
              .divide(BigDecimal.valueOf(totalAllocated), 4, RoundingMode.HALF_EVEN)
              .toDouble()
          } else {
            0.0
          }

        BudgetSummary(
          currencyCode = currency,
          totalAllocatedMinor = totalAllocated,
          totalSpentMinor = totalSpent,
          totalRemainingMinor = totalRemaining,
          averageUtilizationPercent = avgUtilization,
          activeBudgetCount = statuses.size,
          overspentBudgetCount = statuses.count { it.alertStatus == BudgetAlertStatus.EXCEEDED_100 },
        )
      }
  }
}
