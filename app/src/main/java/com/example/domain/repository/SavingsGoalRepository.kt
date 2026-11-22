package com.example.domain.repository

import com.example.domain.model.SavingsContribution
import com.example.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

interface SavingsGoalRepository {

  fun observeGoals(userId: String): Flow<List<SavingsGoal>>

  fun observeContributions(userId: String): Flow<List<SavingsContribution>>

  fun observeContributionsForGoal(
    userId: String,
    goalId: String,
  ): Flow<List<SavingsContribution>>

  suspend fun getGoal(userId: String, goalId: String): SavingsGoal?

  suspend fun createGoal(
    authenticatedUserId: String,
    goal: SavingsGoal,
  ): Result<SavingsGoal>

  /** Rejects a currency change once the goal has any contribution. */
  suspend fun updateGoal(
    authenticatedUserId: String,
    goal: SavingsGoal,
  ): Result<Unit>

  /** Removes the goal and its contributions in one transaction. */
  suspend fun deleteGoal(userId: String, goalId: String): Result<Unit>

  /** Rejects a contribution whose currency differs from the goal's, and any non-positive amount. */
  suspend fun addContribution(
    authenticatedUserId: String,
    contribution: SavingsContribution,
  ): Result<SavingsContribution>

  suspend fun deleteContribution(userId: String, contributionId: String): Result<Unit>
}
