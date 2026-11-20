package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.Budget
import com.example.domain.model.BudgetPeriodType

@Entity(
  tableName = "budgets",
  indices = [
    Index(value = ["userId", "category"]),
    Index(value = ["userId", "currencyCode"]),
    Index(value = ["userId", "startDate", "endDate"]),
  ],
)
data class BudgetEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val name: String,
  val category: String,
  val amountMinor: Long,
  val currencyCode: String,
  val periodType: String,
  val startDate: Long,
  val endDate: Long,
  val warningThresholdPercent: Int,
  val createdAt: Long,
  val updatedAt: Long,
) {
  fun toDomain(): Budget =
    Budget(
      id = id,
      userId = userId,
      name = name,
      category = category,
      amountMinor = amountMinor,
      currencyCode = currencyCode,
      periodType = runCatching { BudgetPeriodType.valueOf(periodType) }.getOrDefault(BudgetPeriodType.MONTHLY),
      startDate = startDate,
      endDate = endDate,
      warningThresholdPercent = warningThresholdPercent,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )

  companion object {
    fun fromDomain(domain: Budget): BudgetEntity =
      BudgetEntity(
        id = domain.id,
        userId = domain.userId,
        name = domain.name,
        category = domain.category,
        amountMinor = domain.amountMinor,
        currencyCode = domain.currencyCode,
        periodType = domain.periodType.name,
        startDate = domain.startDate,
        endDate = domain.endDate,
        warningThresholdPercent = domain.warningThresholdPercent,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
      )
  }
}
