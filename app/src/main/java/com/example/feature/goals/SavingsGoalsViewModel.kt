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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
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

  private val _isLoading = MutableStateFlow(true)

  /** True until the first database result arrives, so the empty state is never shown too early. */
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _hasLoadError = MutableStateFlow(false)
  val hasLoadError: StateFlow<Boolean> = _hasLoadError.asStateFlow()

  // Incrementing this value re-subscribes to the current user's database Flow after a failure.
  private val retryLoadRequests = MutableStateFlow(0)

  @OptIn(ExperimentalCoroutinesApi::class)
  val goals: StateFlow<List<SavingsGoalSummary>> =
    combine(sessionFlow, retryLoadRequests) { session, _ ->
      (session as? AuthSessionState.Authenticated)?.user?.uid
    }
      .flatMapLatest { uid ->
        observeSavingsGoalsUseCase(uid)
          .onStart {
            _isLoading.value = true
            _hasLoadError.value = false
          }
          .onEach { _isLoading.value = false }
          .catch {
            // Do not expose exception details: they may contain implementation or storage data.
            _isLoading.value = false
            _hasLoadError.value = true
            _userMessage.value = "Couldn't load savings goals. Please try again."
            _isErrorMessage.value = true
            emit(emptyList())
          }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /** Retry the current user's goals query after a recoverable database observation failure. */
  fun retryGoalsLoad() {
    _userMessage.value = null
    _isErrorMessage.value = false
    _hasLoadError.value = false
    _isLoading.value = true
    retryLoadRequests.value += 1
  }

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

  fun updateGoal(
    goal: SavingsGoal,
    name: String,
    currencyCode: String,
    targetDecimal: String,
    note: String?,
    deadlineText: String,
    onSuccess: () -> Unit,
  ) {
    val uid = activeUid ?: return
    viewModelScope.launch {
      // A funded goal's currency is immutable. The form locks the field and the repository refuses
      // the change; checking here too means the user sees a clear message rather than a parse
      // failure from the old currency.
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
      // Parsed with the currency the user selected, so a switch from JPY to KWD picks up the new
      // fraction digits rather than reusing the old ones.
      val targetMinor = parseAmountToMinor(targetDecimal, code)
      if (targetMinor == null || targetMinor <= 0L) {
        fail("Enter a target greater than zero")
        return@launch
      }
      updateSavingsGoalUseCase(
        uid,
        goal.copy(
          name = name.trim(),
          currencyCode = code,
          targetMinor = targetMinor,
          note = note?.takeIf { it.isNotBlank() },
          deadlineMillis = deadlineMillis,
        ),
      )
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
