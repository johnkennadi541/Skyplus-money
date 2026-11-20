package com.example.domain.usecase

import com.example.domain.model.Budget
import com.example.domain.model.BudgetAlertStatus
import com.example.domain.model.BudgetStatus
import com.example.domain.model.BudgetSummary
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.Money
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
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

    val percentageConsumed =
      if (budget.amountMinor > 0L) {
        (netSpentMinor.toDouble() / budget.amountMinor.toDouble()) * 100.0
      } else {
        0.0
      }

    val alertStatus =
      when {
        percentageConsumed >= 100.0 -> BudgetAlertStatus.EXCEEDED_100
        percentageConsumed >= 90.0 -> BudgetAlertStatus.WARNING_90
        percentageConsumed >= 75.0 -> BudgetAlertStatus.APPROACHING_75
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
    )
  }

  /**
   * Generates per-currency summaries for all active budgets.
   * Currencies are NEVER combined into a single total.
   */
  fun calculateSummaries(budgetStatuses: List<BudgetStatus>): Map<String, BudgetSummary> {
    return budgetStatuses
      .groupBy { it.budget.currencyCode }
      .mapValues { (currency, statuses) ->
        val totalAllocated = statuses.sumOf { it.budget.amountMinor }
        val totalSpent = statuses.sumOf { it.spentMoney.minorUnits }
        val totalRemaining = totalAllocated - totalSpent
        val avgUtilization =
          if (totalAllocated > 0L) {
            (totalSpent.toDouble() / totalAllocated.toDouble()) * 100.0
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
