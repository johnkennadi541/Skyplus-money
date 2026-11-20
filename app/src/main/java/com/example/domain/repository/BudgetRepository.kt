package com.example.domain.repository

import com.example.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
  fun getBudgets(userId: String): Flow<List<Budget>>

  suspend fun getBudgetById(userId: String, budgetId: String): Budget?

  fun getBudgetByIdFlow(userId: String, budgetId: String): Flow<Budget?>

  suspend fun createBudget(authenticatedUserId: String, budget: Budget): Result<Budget>

  suspend fun updateBudget(authenticatedUserId: String, budget: Budget): Result<Unit>

  suspend fun deleteBudget(userId: String, budgetId: String): Result<Unit>

  suspend fun findConflictingBudget(
    userId: String,
    category: String,
    currencyCode: String,
    startDate: Long,
    endDate: Long,
    excludeBudgetId: String? = null,
  ): Budget?
}
