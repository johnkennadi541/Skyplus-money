package com.example.data.local.db.entity

import androidx.room.ColumnInfo
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
  /**
   * Column default 0 matches MIGRATION_6_7, which switches it to 1 only for eligible monthly
   * budgets. Room validates the migrated schema against this annotation, so the two must agree.
   */
  @ColumnInfo(defaultValue = "0") val isRecurring: Boolean = true,
  @ColumnInfo(defaultValue = "0") val lastRenewedAt: Long = 0L,
  val createdAt: Long,
  val updatedAt: Long,
  @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
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
      isRecurring = isRecurring,
      isArchived = isArchived,
      lastRenewedAt = lastRenewedAt,
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
        isRecurring = domain.isRecurring,
        isArchived = domain.isArchived,
        lastRenewedAt = domain.lastRenewedAt,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
      )
  }
}
