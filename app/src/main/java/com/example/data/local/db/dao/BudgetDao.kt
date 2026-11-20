package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

  @Query("SELECT * FROM budgets WHERE userId = :userId ORDER BY createdAt DESC")
  fun getAllBudgets(userId: String): Flow<List<BudgetEntity>>

  @Query("SELECT * FROM budgets WHERE userId = :userId AND id = :id LIMIT 1")
  suspend fun getBudgetById(userId: String, id: String): BudgetEntity?

  @Query("SELECT * FROM budgets WHERE userId = :userId AND id = :id LIMIT 1")
  fun getBudgetByIdFlow(userId: String, id: String): Flow<BudgetEntity?>

  @Query(
    """
    SELECT * FROM budgets 
    WHERE userId = :userId 
      AND category = :category 
      AND currencyCode = :currencyCode
      AND NOT (:endDate < startDate OR :startDate > endDate)
      AND (:excludeBudgetId IS NULL OR id != :excludeBudgetId)
    LIMIT 1
    """
  )
  suspend fun findConflictingBudget(
    userId: String,
    category: String,
    currencyCode: String,
    startDate: Long,
    endDate: Long,
    excludeBudgetId: String? = null,
  ): BudgetEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertBudget(budget: BudgetEntity)

  @Query(
    """
    UPDATE budgets
    SET name = :name,
        category = :category,
        amountMinor = :amountMinor,
        currencyCode = :currencyCode,
        periodType = :periodType,
        startDate = :startDate,
        endDate = :endDate,
        warningThresholdPercent = :warningThresholdPercent,
        updatedAt = :updatedAt
    WHERE id = :id AND userId = :userId
    """
  )
  suspend fun updateBudgetScoped(
    id: String,
    userId: String,
    name: String,
    category: String,
    amountMinor: Long,
    currencyCode: String,
    periodType: String,
    startDate: Long,
    endDate: Long,
    warningThresholdPercent: Int,
    updatedAt: Long,
  ): Int

  suspend fun updateBudget(budget: BudgetEntity, authenticatedUserId: String): Int {
    if (authenticatedUserId.isBlank() || budget.userId != authenticatedUserId) return 0
    return updateBudgetScoped(
      id = budget.id,
      userId = authenticatedUserId,
      name = budget.name,
      category = budget.category,
      amountMinor = budget.amountMinor,
      currencyCode = budget.currencyCode,
      periodType = budget.periodType,
      startDate = budget.startDate,
      endDate = budget.endDate,
      warningThresholdPercent = budget.warningThresholdPercent,
      updatedAt = budget.updatedAt,
    )
  }

  @Query("DELETE FROM budgets WHERE userId = :userId AND id = :id")
  suspend fun deleteBudget(userId: String, id: String): Int
}
