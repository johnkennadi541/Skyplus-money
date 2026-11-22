package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.BudgetEntity
import com.example.data.local.db.entity.BudgetPeriodEntity
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
        isRecurring = :isRecurring,
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
    isRecurring: Boolean,
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
      isRecurring = budget.isRecurring,
      updatedAt = budget.updatedAt,
    )
  }

  @Query("DELETE FROM budgets WHERE userId = :userId AND id = :id")
  suspend fun deleteBudget(userId: String, id: String): Int
  // ---------------------------------------------------------------------------------------------
  // Stage 3 PR 1 — closed period archive and renewal
  // ---------------------------------------------------------------------------------------------

  @Query(
    "SELECT * FROM budget_periods WHERE userId = :userId ORDER BY periodEnd DESC, closedAt DESC"
  )
  fun observeAllPeriods(userId: String): Flow<List<BudgetPeriodEntity>>

  @Query(
    "SELECT * FROM budget_periods WHERE userId = :userId AND budgetId = :budgetId ORDER BY periodEnd DESC"
  )
  fun observePeriods(userId: String, budgetId: String): Flow<List<BudgetPeriodEntity>>

  @Query("SELECT * FROM budget_periods WHERE userId = :userId AND budgetId = :budgetId")
  suspend fun getPeriodsForBudget(userId: String, budgetId: String): List<BudgetPeriodEntity>

  /** IGNORE: archive ids are deterministic, so a retried insert can never duplicate history. */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertPeriods(periods: List<BudgetPeriodEntity>)

  /**
   * Moves a budget window forward. Always scoped to the owning user; returns 0 when the row does
   * not belong to [userId], so a mismatched call can never move someone else's budget.
   */
  @Query(
    """
    UPDATE budgets
    SET startDate = :startDate,
        endDate = :endDate,
        lastRenewedAt = :lastRenewedAt,
        updatedAt = :updatedAt
    WHERE id = :id
      AND userId = :userId
      AND startDate = :expectedStart
      AND endDate = :expectedEnd
    """
  )
  suspend fun applyRenewalScoped(
    id: String,
    userId: String,
    expectedStart: Long,
    expectedEnd: Long,
    startDate: Long,
    endDate: Long,
    lastRenewedAt: Long,
    updatedAt: Long,
  ): Int
}
