package com.example.domain.repository

import com.example.domain.model.Budget
import com.example.domain.model.BudgetPeriod
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
  fun getBudgets(userId: String): Flow<List<Budget>>

  suspend fun getBudgetById(userId: String, budgetId: String): Budget?

  fun getBudgetByIdFlow(userId: String, budgetId: String): Flow<Budget?>

  suspend fun createBudget(authenticatedUserId: String, budget: Budget): Result<Budget>

  suspend fun updateBudget(authenticatedUserId: String, budget: Budget): Result<Unit>

  suspend fun deleteBudget(userId: String, budgetId: String): Result<Unit>

  /** Archived, closed periods for this user, newest first. */
  fun observeBudgetPeriods(userId: String): Flow<List<BudgetPeriod>>

  /**
   * Archives [closedPeriods] and moves the budget window to [nextStart]..[nextEnd] in one
   * transaction. Scoped to [authenticatedUserId]; a mismatched or blank UID writes nothing.
   */
  suspend fun applyRenewal(
    authenticatedUserId: String,
    budgetId: String,
    closedPeriods: List<BudgetPeriod>,
    nextStart: Long,
    nextEnd: Long,
    renewedAt: Long,
  ): Result<Unit>

  suspend fun findConflictingBudget(
    userId: String,
    category: String,
    currencyCode: String,
    startDate: Long,
    endDate: Long,
    excludeBudgetId: String? = null,
  ): Budget?
}
