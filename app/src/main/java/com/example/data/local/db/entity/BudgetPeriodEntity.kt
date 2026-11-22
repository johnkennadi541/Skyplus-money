package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.BudgetAlertStatus
import com.example.domain.model.BudgetPeriod
import com.example.domain.model.BudgetPeriodType

@Entity(
  tableName = "budget_periods",
  indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "budgetId"]),
    Index(value = ["userId", "periodEnd"]),
  ],
)
data class BudgetPeriodEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val budgetId: String,
  val category: String,
  val currencyCode: String,
  val periodType: String,
  val periodStart: Long,
  val periodEnd: Long,
  val allocatedMinor: Long,
  val spentMinor: Long,
  val remainingMinor: Long,
  val alertStatus: String,
  val closedAt: Long,
) {
  fun toDomain(): BudgetPeriod =
    BudgetPeriod(
      id = id,
      userId = userId,
      budgetId = budgetId,
      category = category,
      currencyCode = currencyCode,
      periodType = runCatching { BudgetPeriodType.valueOf(periodType) }.getOrDefault(BudgetPeriodType.MONTHLY),
      periodStart = periodStart,
      periodEnd = periodEnd,
      allocatedMinor = allocatedMinor,
      spentMinor = spentMinor,
      remainingMinor = remainingMinor,
      alertStatus = runCatching { BudgetAlertStatus.valueOf(alertStatus) }.getOrDefault(BudgetAlertStatus.NORMAL),
      closedAt = closedAt,
    )

  companion object {
    fun fromDomain(domain: BudgetPeriod): BudgetPeriodEntity =
      BudgetPeriodEntity(
        id = domain.id,
        userId = domain.userId,
        budgetId = domain.budgetId,
        category = domain.category,
        currencyCode = domain.currencyCode,
        periodType = domain.periodType.name,
        periodStart = domain.periodStart,
        periodEnd = domain.periodEnd,
        allocatedMinor = domain.allocatedMinor,
        spentMinor = domain.spentMinor,
        remainingMinor = domain.remainingMinor,
        alertStatus = domain.alertStatus.name,
        closedAt = domain.closedAt,
      )
  }
}
