package com.example.feature.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.parseAmountToMinor
import com.example.domain.model.Money
import com.example.domain.model.AuthSessionState
import com.example.domain.model.SavingsGoal
import com.example.domain.model.SavingsGoalSummary
import com.example.domain.repository.AuthRepository
import com.example.domain.usecase.AddSavingsContributionUseCase
import com.example.domain.usecase.CreateSavingsGoalUseCase
import com.example.domain.usecase.DeleteSavingsContributionUseCase
import com.example.domain.usecase.DeleteSavingsGoalUseCase
import com.example.domain.usecase.ObserveSavingsGoalsUseCase
import com.example.domain.usecase.UpdateSavingsGoalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SavingsGoalsViewModel
@Inject
constructor(
  authRepository: AuthRepository,
  observeSavingsGoalsUseCase: ObserveSavingsGoalsUseCase,
  private val createSavingsGoalUseCase: CreateSavingsGoalUseCase,
  private val updateSavingsGoalUseCase: UpdateSavingsGoalUseCase,
  private val deleteSavingsGoalUseCase: DeleteSavingsGoalUseCase,
  private val addSavingsContributionUseCase: AddSavingsContributionUseCase,
  private val deleteSavingsContributionUseCase: DeleteSavingsContributionUseCase,
) : ViewModel() {

  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  private val _isErrorMessage = MutableStateFlow(false)
  val isErrorMessage: StateFlow<Boolean> = _isErrorMessage.asStateFlow()

  /** The UID every write must use, captured from the live session. */
  private var activeUid: String? = null

  @OptIn(ExperimentalCoroutinesApi::class)
  private val sessionFlow =
    authRepository.observeAuthSession().onEach { session ->
      activeUid = (session as? AuthSessionState.Authenticated)?.user?.uid
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  val goals: StateFlow<List<SavingsGoalSummary>> =
    sessionFlow
      .map { session -> (session as? AuthSessionState.Authenticated)?.user?.uid }
      .flatMapLatest { uid -> observeSavingsGoalsUseCase(uid) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun dismissMessage() {
    _userMessage.value = null
    _isErrorMessage.value = false
  }

  fun createGoal(
    name: String,
    currencyCode: String,
    targetDecimal: String,
    note: String?,
    deadlineText: String = "",
    onSuccess: () -> Unit,
  ) {
    val uid = activeUid ?: return
    viewModelScope.launch {
      val code = currencyCode.trim().uppercase()
      if (!Money.isSupportedCurrency(code)) {
        fail("'$code' is not a supported currency")
        return@launch
      }
      val deadlineMillis =
        runCatching { GoalDeadlineParser.parse(deadlineText) }
          .getOrElse {
            fail(it.message ?: "Enter a valid deadline")
            return@launch
          }
      val targetMinor = parseAmountToMinor(targetDecimal, code)
      if (targetMinor == null || targetMinor <= 0L) {
        fail("Enter a target greater than zero")
        return@launch
      }
      createSavingsGoalUseCase(
        authenticatedUserId = uid,
        name = name,
        currencyCode = code,
        targetMinor = targetMinor,
        deadlineMillis = deadlineMillis,
        note = note?.takeIf { it.isNotBlank() },
      )
        .onSuccess {
          succeed("Goal created")
          onSuccess()
        }
        .onFailure { fail(it.message ?: "Could not create the goal") }
    }
  }

  fun updateGoal(goal: SavingsGoal, name: String, targetDecimal: String, onSuccess: () -> Unit) {
    val uid = activeUid ?: return
    viewModelScope.launch {
      val targetMinor = parseAmountToMinor(targetDecimal, goal.currencyCode)
      if (targetMinor == null || targetMinor <= 0L) {
        fail("Enter a target greater than zero")
        return@launch
      }
      updateSavingsGoalUseCase(uid, goal.copy(name = name, targetMinor = targetMinor))
        .onSuccess {
          succeed("Goal updated")
          onSuccess()
        }
        .onFailure { fail(it.message ?: "Could not update the goal") }
    }
  }

  fun deleteGoal(goalId: String, onSuccess: () -> Unit) {
    val uid = activeUid ?: return
    viewModelScope.launch {
      deleteSavingsGoalUseCase(uid, goalId)
        .onSuccess {
          succeed("Goal and its contributions were removed")
          onSuccess()
        }
        .onFailure { fail(it.message ?: "Could not remove the goal") }
    }
  }

  fun addContribution(
    summary: SavingsGoalSummary,
    amountDecimal: String,
    note: String?,
    onSuccess: () -> Unit,
  ) {
    val uid = activeUid ?: return
    viewModelScope.launch {
      // The amount is parsed with the goal's own currency, so a JPY goal accepts no decimals and
      // a KWD goal accepts three.
      val amountMinor = parseAmountToMinor(amountDecimal, summary.goal.currencyCode)
      if (amountMinor == null || amountMinor <= 0L) {
        fail("Enter an amount greater than zero")
        return@launch
      }
      addSavingsContributionUseCase(
        authenticatedUserId = uid,
        goalId = summary.goal.id,
        amountMinor = amountMinor,
        currencyCode = summary.goal.currencyCode,
        note = note?.takeIf { it.isNotBlank() },
      )
        .onSuccess {
          succeed("Contribution added")
          onSuccess()
        }
        .onFailure { fail(it.message ?: "Could not add the contribution") }
    }
  }

  fun deleteContribution(contributionId: String, onSuccess: () -> Unit) {
    val uid = activeUid ?: return
    viewModelScope.launch {
      deleteSavingsContributionUseCase(uid, contributionId)
        .onSuccess {
          succeed("Contribution removed")
          onSuccess()
        }
        .onFailure { fail(it.message ?: "Could not remove the contribution") }
    }
  }

  private fun succeed(message: String) {
    _userMessage.value = message
    _isErrorMessage.value = false
  }

  private fun fail(message: String) {
    _userMessage.value = message
    _isErrorMessage.value = true
  }
}
