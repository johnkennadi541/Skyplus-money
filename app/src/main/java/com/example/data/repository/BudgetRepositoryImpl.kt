package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.dao.BudgetDao
import com.example.data.local.db.entity.BudgetEntity
import com.example.domain.model.Budget
import com.example.domain.model.Money
import com.example.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepositoryImpl @Inject constructor(
  private val database: AppDatabase,
  private val budgetDao: BudgetDao,
  private val dispatchers: DispatcherProvider,
) : BudgetRepository {

  override fun getBudgets(userId: String): Flow<List<Budget>> =
    budgetDao.getAllBudgets(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override suspend fun getBudgetById(userId: String, budgetId: String): Budget? =
    withContext(dispatchers.io) {
      budgetDao.getBudgetById(userId, budgetId)?.toDomain()
    }

  override fun getBudgetByIdFlow(userId: String, budgetId: String): Flow<Budget?> =
    budgetDao.getBudgetByIdFlow(userId, budgetId)
      .map { it?.toDomain() }
      .flowOn(dispatchers.io)

  override suspend fun createBudget(
    authenticatedUserId: String,
    budget: Budget,
  ): Result<Budget> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(budget.userId == authenticatedUserId) {
          "Budget ownership cannot be assigned across users"
        }
        require(budget.name.isNotBlank()) { "Budget name cannot be blank" }
        require(budget.category.isNotBlank()) { "Budget category cannot be blank" }
        require(budget.amountMinor > 0L) { "Budget amount must be strictly positive" }
        Money.resolveFractionDigits(budget.currencyCode)
        require(budget.endDate >= budget.startDate) { "Budget end date must be on or after start date" }

        database.withTransaction {
          val conflicting =
            budgetDao.findConflictingBudget(
              userId = authenticatedUserId,
              category = budget.category,
              currencyCode = budget.currencyCode,
              startDate = budget.startDate,
              endDate = budget.endDate,
            )
          if (conflicting != null) {
            throw IllegalArgumentException(
              "An active budget ('${conflicting.name}') already exists for category '${budget.category}' in this date period."
            )
          }

          val now = System.currentTimeMillis()
          val scoped =
            budget.copy(
              userId = authenticatedUserId,
              createdAt = now,
              updatedAt = now,
            )
          budgetDao.insertBudget(BudgetEntity.fromDomain(scoped))
          scoped
        }
      }
    }

  override suspend fun updateBudget(
    authenticatedUserId: String,
    budget: Budget,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(budget.userId == authenticatedUserId) {
          "Budget ownership cannot be modified or transferred across users"
        }
        require(budget.name.isNotBlank()) { "Budget name cannot be blank" }
        require(budget.category.isNotBlank()) { "Budget category cannot be blank" }
        require(budget.amountMinor > 0L) { "Budget amount must be strictly positive" }
        Money.resolveFractionDigits(budget.currencyCode)
        require(budget.endDate >= budget.startDate) { "Budget end date must be on or after start date" }

        database.withTransaction {
          val existing =
            budgetDao.getBudgetById(authenticatedUserId, budget.id)
              ?: throw SecurityException("Budget does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) { "Budget ownership verification failed" }

          val conflicting =
            budgetDao.findConflictingBudget(
              userId = authenticatedUserId,
              category = budget.category,
              currencyCode = budget.currencyCode,
              startDate = budget.startDate,
              endDate = budget.endDate,
              excludeBudgetId = budget.id,
            )
          if (conflicting != null) {
            throw IllegalArgumentException(
              "Another budget ('${conflicting.name}') already covers category '${budget.category}' in this date period."
            )
          }

          val now = System.currentTimeMillis()
          val entity =
            BudgetEntity.fromDomain(
              budget.copy(
                userId = existing.userId,
                createdAt = existing.createdAt,
                updatedAt = now,
              )
            )
          val rowsUpdated = budgetDao.updateBudget(entity, authenticatedUserId)
          check(rowsUpdated == 1) { "Budget update rejected for authenticated user" }
        }
      }
    }

  override suspend fun deleteBudget(userId: String, budgetId: String): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(userId.isNotBlank()) { "Authenticated user ID is required" }
        require(budgetId.isNotBlank()) { "Budget ID is required" }
        database.withTransaction {
          val existing =
            budgetDao.getBudgetById(userId, budgetId)
              ?: throw SecurityException("Budget does not belong to authenticated user")
          require(existing.userId == userId) { "Budget ownership verification failed" }
          val rowsDeleted = budgetDao.deleteBudget(userId, budgetId)
          check(rowsDeleted == 1) { "Budget deletion rejected for authenticated user" }
        }
      }
    }

  override suspend fun findConflictingBudget(
    userId: String,
    category: String,
    currencyCode: String,
    startDate: Long,
    endDate: Long,
    excludeBudgetId: String?,
  ): Budget? =
    withContext(dispatchers.io) {
      budgetDao.findConflictingBudget(
        userId = userId,
        category = category,
        currencyCode = currencyCode,
        startDate = startDate,
        endDate = endDate,
        excludeBudgetId = excludeBudgetId,
      )?.toDomain()
    }
}
