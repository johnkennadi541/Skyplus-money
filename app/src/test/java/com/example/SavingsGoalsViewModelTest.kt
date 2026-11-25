package com.example

import com.example.domain.model.AuthError
import com.example.domain.model.AuthProviderType
import com.example.domain.model.AuthResult
import com.example.domain.model.AuthSessionState
import com.example.domain.model.AuthenticatedUser
import com.example.domain.model.SavingsContribution
import com.example.domain.model.SavingsGoal
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.SavingsGoalRepository
import com.example.domain.usecase.AddSavingsContributionUseCase
import com.example.domain.usecase.CreateSavingsGoalUseCase
import com.example.domain.usecase.ObserveSavingsGoalsUseCase
import com.example.domain.usecase.SetSavingsGoalArchivedUseCase
import com.example.domain.usecase.VoidSavingsContributionUseCase
import com.example.domain.usecase.SavingsGoalCalculationEngine
import com.example.domain.usecase.UpdateSavingsGoalUseCase
import com.example.feature.goals.SavingsGoalsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SavingsGoalsViewModelTest {

  @Test
  fun `database observation failure can recover after retry`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val goal =
        SavingsGoal(
          userId = "uid_retry_test",
          name = "Emergency fund",
          currencyCode = "INR",
          targetMinor = 100_000L,
        )
      val repository = RetryAwareSavingsGoalRepository(goal)
      val viewModel = createViewModel(repository)

      // StateFlow is WhileSubscribed, so collect it as the screen would.
      val collector =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          viewModel.goals.collect { }
        }

      advanceUntilIdle()

      assertEquals(1, repository.goalObservationAttempts)
      assertTrue(viewModel.hasLoadError.value)
      assertFalse(viewModel.isLoading.value)
      assertTrue(viewModel.goals.value.isEmpty())
      assertEquals("Couldn't load savings goals. Please try again.", viewModel.userMessage.value)

      viewModel.retryGoalsLoad()
      advanceUntilIdle()

      assertEquals(2, repository.goalObservationAttempts)
      assertFalse(viewModel.hasLoadError.value)
      assertFalse(viewModel.isLoading.value)
      assertEquals(listOf(goal.id), viewModel.goals.value.map { it.goal.id })
      assertEquals(null, viewModel.userMessage.value)

      collector.cancel()
      advanceUntilIdle()
    } finally {
      Dispatchers.resetMain()
    }
  }

  private fun createViewModel(repository: SavingsGoalRepository): SavingsGoalsViewModel {
    val observe = ObserveSavingsGoalsUseCase(repository, SavingsGoalCalculationEngine())
    return SavingsGoalsViewModel(
      authRepository = TestAuthRepository(),
      observeSavingsGoalsUseCase = observe,
      createSavingsGoalUseCase = CreateSavingsGoalUseCase(repository),
      updateSavingsGoalUseCase = UpdateSavingsGoalUseCase(repository),
      setSavingsGoalArchivedUseCase = SetSavingsGoalArchivedUseCase(repository),
      addSavingsContributionUseCase = AddSavingsContributionUseCase(repository),
      voidSavingsContributionUseCase = VoidSavingsContributionUseCase(repository),
    )
  }

  private class TestAuthRepository : AuthRepository {
    private val user =
      AuthenticatedUser(
        uid = "uid_retry_test",
        displayName = "Test User",
        email = "test@example.invalid",
        photoUrl = null,
        isEmailVerified = true,
        providers = setOf(AuthProviderType.GOOGLE),
      )

    override fun observeAuthSession(): Flow<AuthSessionState> =
      flowOf(AuthSessionState.Authenticated(user))

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult<AuthenticatedUser> =
      AuthResult.Failure(AuthError.SessionExpiredOrInvalid)

    override suspend fun refreshAndValidateSession(): AuthResult<AuthenticatedUser?> =
      AuthResult.Failure(AuthError.SessionExpiredOrInvalid)

    override suspend fun signOut(): AuthResult<Unit> =
      AuthResult.Failure(AuthError.SessionExpiredOrInvalid)
  }

  private class RetryAwareSavingsGoalRepository(
    private val goal: SavingsGoal,
  ) : SavingsGoalRepository {
    var goalObservationAttempts: Int = 0
      private set

    override fun observeGoals(userId: String): Flow<List<SavingsGoal>> = flow {
      goalObservationAttempts += 1
      if (goalObservationAttempts == 1) {
        throw IOException("simulated database observation failure")
      }
      emit(listOf(goal).filter { it.userId == userId })
    }

    override fun observeContributions(userId: String): Flow<List<SavingsContribution>> =
      flowOf(emptyList())

    override fun observeContributionsForGoal(
      userId: String,
      goalId: String,
    ): Flow<List<SavingsContribution>> = flowOf(emptyList())

    override suspend fun getGoal(userId: String, goalId: String): SavingsGoal? = null

    override suspend fun createGoal(
      authenticatedUserId: String,
      goal: SavingsGoal,
    ): Result<SavingsGoal> = Result.failure(UnsupportedOperationException("Not used in this test"))

    override suspend fun updateGoal(
      authenticatedUserId: String,
      goal: SavingsGoal,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Not used in this test"))

    override suspend fun setGoalArchived(userId: String, goalId: String, archived: Boolean): Result<Unit> =
      Result.failure(UnsupportedOperationException("Not used in this test"))

    override suspend fun addContribution(
      authenticatedUserId: String,
      contribution: SavingsContribution,
    ): Result<SavingsContribution> =
      Result.failure(UnsupportedOperationException("Not used in this test"))

    override suspend fun voidContribution(userId: String, contributionId: String): Result<Unit> =
      Result.failure(UnsupportedOperationException("Not used in this test"))
  }
}
