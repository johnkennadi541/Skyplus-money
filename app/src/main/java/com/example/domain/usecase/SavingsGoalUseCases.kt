package com.example.domain.usecase

import com.example.domain.model.SavingsContribution
import com.example.domain.model.SavingsGoal
import com.example.domain.model.SavingsGoalSummary
import com.example.domain.repository.SavingsGoalRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** Goals joined with the totals derived from their contributions, for the signed-in user. */
@Singleton
class ObserveSavingsGoalsUseCase
@Inject
constructor(
  private val repository: SavingsGoalRepository,
  private val calculationEngine: SavingsGoalCalculationEngine,
) {
  operator fun invoke(userId: String?): Flow<List<SavingsGoalSummary>> {
    if (userId.isNullOrBlank()) return flowOf(emptyList())
    return combine(
      repository.observeGoals(userId),
      repository.observeContributions(userId),
    ) { goals, contributions -> calculationEngine.summariseAll(goals, contributions) }
  }
}

@Singleton
class CreateSavingsGoalUseCase
@Inject
constructor(private val repository: SavingsGoalRepository) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    name: String,
    currencyCode: String,
    targetMinor: Long,
    deadlineMillis: Long? = null,
    note: String? = null,
  ): Result<SavingsGoal> =
    repository.createGoal(
      authenticatedUserId = authenticatedUserId,
      goal =
        SavingsGoal(
          userId = authenticatedUserId,
          name = name,
          currencyCode = currencyCode,
          targetMinor = targetMinor,
          deadlineMillis = deadlineMillis,
          note = note,
        ),
    )
}

@Singleton
class UpdateSavingsGoalUseCase
@Inject
constructor(private val repository: SavingsGoalRepository) {
  suspend operator fun invoke(authenticatedUserId: String, goal: SavingsGoal): Result<Unit> =
    repository.updateGoal(authenticatedUserId, goal)
}

@Singleton
class SetSavingsGoalArchivedUseCase
@Inject
constructor(private val repository: SavingsGoalRepository) {
  suspend operator fun invoke(userId: String, goalId: String, archived: Boolean): Result<Unit> =
    repository.setGoalArchived(userId, goalId, archived)
}

@Singleton
class AddSavingsContributionUseCase
@Inject
constructor(private val repository: SavingsGoalRepository) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    goalId: String,
    amountMinor: Long,
    currencyCode: String,
    note: String? = null,
    contributedAt: Long = System.currentTimeMillis(),
  ): Result<SavingsContribution> =
    repository.addContribution(
      authenticatedUserId = authenticatedUserId,
      contribution =
        SavingsContribution(
          userId = authenticatedUserId,
          goalId = goalId,
          amountMinor = amountMinor,
          currencyCode = currencyCode,
          contributedAt = contributedAt,
          note = note,
        ),
    )
}

@Singleton
class VoidSavingsContributionUseCase
@Inject
constructor(private val repository: SavingsGoalRepository) {
  suspend operator fun invoke(userId: String, contributionId: String): Result<Unit> =
    repository.voidContribution(userId, contributionId)
}
