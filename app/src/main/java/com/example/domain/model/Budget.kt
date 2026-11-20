package com.example.domain.model

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
