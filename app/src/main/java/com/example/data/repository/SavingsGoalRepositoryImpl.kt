package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.dao.SavingsGoalDao
import com.example.data.local.db.entity.SavingsContributionEntity
import com.example.data.local.db.entity.SavingsGoalEntity
import com.example.domain.model.SavingsContribution
import com.example.domain.model.SavingsGoal
import com.example.domain.model.SavingsGoalConflictException
import com.example.domain.model.SavingsGoalValidation
import com.example.domain.repository.SavingsGoalRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class SavingsGoalRepositoryImpl
@Inject
constructor(
  private val database: AppDatabase,
  private val savingsGoalDao: SavingsGoalDao,
  private val dispatchers: DispatcherProvider,
) : SavingsGoalRepository {

  override fun observeGoals(userId: String): Flow<List<SavingsGoal>> =
    savingsGoalDao
      .observeGoals(userId)
      .map { rows -> rows.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun observeContributions(userId: String): Flow<List<SavingsContribution>> =
    savingsGoalDao
      .observeContributions(userId)
      .map { rows -> rows.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun observeContributionsForGoal(
    userId: String,
    goalId: String,
  ): Flow<List<SavingsContribution>> =
    savingsGoalDao
      .observeContributionsForGoal(userId, goalId)
      .map { rows -> rows.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override suspend fun getGoal(userId: String, goalId: String): SavingsGoal? =
    withContext(dispatchers.io) { savingsGoalDao.getGoalById(userId, goalId)?.toDomain() }

  override suspend fun createGoal(
    authenticatedUserId: String,
    goal: SavingsGoal,
  ): Result<SavingsGoal> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        SavingsGoalValidation.requireValidGoal(goal, authenticatedUserId)

        database.withTransaction {
          val now = System.currentTimeMillis()
          val scoped =
            goal.copy(
              userId = authenticatedUserId,
              name = goal.name.trim(),
              currencyCode = goal.currencyCode.uppercase().trim(),
              createdAt = now,
              updatedAt = now,
            )
          savingsGoalDao.insertGoal(SavingsGoalEntity.fromDomain(scoped))
          scoped
        }
      }
    }

  override suspend fun updateGoal(
    authenticatedUserId: String,
    goal: SavingsGoal,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        SavingsGoalValidation.requireValidGoal(
          goal = goal,
          authenticatedUserId = authenticatedUserId,
          ownershipMessage = "Goal ownership cannot be modified or transferred across users",
        )

        database.withTransaction {
          val existing =
            savingsGoalDao.getGoalById(authenticatedUserId, goal.id)
              ?: throw SecurityException("Goal does not belong to authenticated user")

          // A currency change would silently re-denominate every contribution, so it is refused
          // as soon as one exists.
          val funded = savingsGoalDao.countContributions(authenticatedUserId, existing.id) > 0
          SavingsGoalValidation.requireCurrencyUnchangedWhenFunded(
            goal = goal,
            existing = existing.toDomain(),
            funded = funded,
          )

          val rows =
            savingsGoalDao.updateGoalScoped(
              id = existing.id,
              userId = authenticatedUserId,
              name = goal.name.trim(),
              currencyCode = goal.currencyCode.uppercase().trim(),
              targetMinor = goal.targetMinor,
              deadlineMillis = goal.deadlineMillis,
              note = goal.note,
              updatedAt = System.currentTimeMillis(),
            )
          check(rows == 1) { "Goal update rejected for authenticated user" }
        }
      }
    }

  override suspend fun deleteGoal(userId: String, goalId: String): Result<Unit> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        require(userId.isNotBlank()) { "Authenticated user ID is required" }
        require(goalId.isNotBlank()) { "Goal ID is required" }

        database.withTransaction {
          val existing =
            savingsGoalDao.getGoalById(userId, goalId)
              ?: throw SecurityException("Goal does not belong to authenticated user")
          require(existing.userId == userId) { "Goal ownership verification failed" }

          // Contributions are meaningless without their goal, so both go in one transaction.
          savingsGoalDao.deleteContributionsForGoal(userId, existing.id)
          val rows = savingsGoalDao.deleteGoal(userId, existing.id)
          check(rows == 1) { "Goal deletion rejected for authenticated user" }
        }
      }
    }

  override suspend fun addContribution(
    authenticatedUserId: String,
    contribution: SavingsContribution,
  ): Result<SavingsContribution> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }

        database.withTransaction {
          val goal =
            savingsGoalDao.getGoalById(authenticatedUserId, contribution.goalId)
              ?: throw SavingsGoalConflictException("Goal does not exist for this user")

          SavingsGoalValidation.requireValidContribution(
            contribution = contribution,
            goal = goal.toDomain(),
            authenticatedUserId = authenticatedUserId,
          )

          val now = System.currentTimeMillis()
          val scoped =
            contribution.copy(
              userId = authenticatedUserId,
              currencyCode = goal.currencyCode,
              createdAt = now,
            )
          savingsGoalDao.insertContribution(SavingsContributionEntity.fromDomain(scoped))
          scoped
        }
      }
    }

  override suspend fun deleteContribution(
    userId: String,
    contributionId: String,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        require(userId.isNotBlank()) { "Authenticated user ID is required" }
        require(contributionId.isNotBlank()) { "Contribution ID is required" }

        database.withTransaction {
          val existing =
            savingsGoalDao.getContributionById(userId, contributionId)
              ?: throw SecurityException("Contribution does not belong to authenticated user")
          require(existing.userId == userId) { "Contribution ownership verification failed" }
          val rows = savingsGoalDao.deleteContribution(userId, contributionId)
          check(rows == 1) { "Contribution deletion rejected for authenticated user" }
        }
      }
    }
}
