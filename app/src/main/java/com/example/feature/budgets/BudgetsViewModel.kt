package com.example.feature.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.DispatcherProvider
import com.example.domain.model.AuthSessionState
import com.example.domain.model.Budget
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.BudgetPeriod
import com.example.domain.model.BudgetStatus
import com.example.domain.model.BudgetSummary
import com.example.domain.model.FinancialAccount
import com.example.domain.model.Money
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.BudgetRepository
import com.example.domain.repository.FinancialAccountRepository
import com.example.domain.usecase.CreateBudgetUseCase
import com.example.domain.usecase.DeleteBudgetUseCase
import com.example.domain.usecase.ObserveBudgetsWithStatusUseCase
import com.example.domain.usecase.RenewBudgetsUseCase
import com.example.domain.usecase.UpdateBudgetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class BudgetsUiState(
  val isLoading: Boolean = false,
  val budgets: List<BudgetStatus> = emptyList(),
  val summariesByCurrency: Map<String, BudgetSummary> = emptyMap(),
  val accounts: List<FinancialAccount> = emptyList(),
  val availableCurrencies: List<String> = emptyList(),
  val selectedCurrency: String? = null,
  val userMessage: String? = null,
  val isErrorMessage: Boolean = false,
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  private val observeBudgetsWithStatusUseCase: ObserveBudgetsWithStatusUseCase,
  private val createBudgetUseCase: CreateBudgetUseCase,
  private val updateBudgetUseCase: UpdateBudgetUseCase,
  private val deleteBudgetUseCase: DeleteBudgetUseCase,
  private val accountRepository: FinancialAccountRepository,
  private val budgetRepository: BudgetRepository,
  private val renewBudgetsUseCase: RenewBudgetsUseCase,
  private val dispatchers: DispatcherProvider,
) : ViewModel() {

  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  private val _isErrorMessage = MutableStateFlow(false)
  val isErrorMessage: StateFlow<Boolean> = _isErrorMessage.asStateFlow()

  private val _selectedCurrency = MutableStateFlow<String?>(null)
  val selectedCurrency: StateFlow<String?> = _selectedCurrency.asStateFlow()

  init {
    viewModelScope.launch {
      authRepository.observeAuthSession()
        .distinctUntilChangedBy { (it as? AuthSessionState.Authenticated)?.user?.uid }
        .collect { session ->
          _userMessage.value = null
          _isErrorMessage.value = false

          // Roll due recurring budgets into the current month. Idempotent, and a failure here must
          // never block the screen: the stale-budget state still shows correctly without it.
          val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
          if (uid != null) {
            runCatching { renewBudgetsUseCase(uid) }
          }
        }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  val accounts: StateFlow<List<FinancialAccount>> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) accountRepository.getAccounts(uid) else flowOf(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  private val rawBudgetData =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) {
          observeBudgetsWithStatusUseCase(uid)
        } else {
          flowOf(Pair(emptyList(), emptyMap()))
        }
      }
      .catch {
        // Fail closed: never expose partial or misleading totals when exact arithmetic fails.
        _isErrorMessage.value = true
        _userMessage.value =
          "Budget totals could not be calculated safely. Please review the transaction amounts and try again."
        emit(Pair(emptyList(), emptyMap()))
      }

  val budgets: StateFlow<List<BudgetStatus>> =
    rawBudgetData.map { it.first }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val budgetPeriods: StateFlow<List<BudgetPeriod>> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) budgetRepository.observeBudgetPeriods(uid) else flowOf(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val summariesByCurrency: StateFlow<Map<String, BudgetSummary>> =
    rawBudgetData.map { it.second }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap(),
      )

  fun selectCurrency(currency: String?) {
    _selectedCurrency.value = currency
  }

  fun dismissMessage() {
    _userMessage.value = null
    _isErrorMessage.value = false
  }

  fun createBudget(
    name: String,
    category: String,
    amountDecimal: String,
    currencyCode: String,
    periodType: BudgetPeriodType,
    customStartDate: Long? = null,
    customEndDate: Long? = null,
    warningThresholdPercent: Int = 75,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid == null) {
        _isErrorMessage.value = true
        _userMessage.value = "Authentication session required"
        return@launch
      }

      val minorUnits =
        try {
          val parsed = BigDecimal(amountDecimal.trim())
          Money.fromBigDecimal(parsed, currencyCode).minorUnits
        } catch (e: Exception) {
          _isErrorMessage.value = true
          _userMessage.value = "Please enter a valid numeric budget amount"
          return@launch
        }

      if (minorUnits <= 0L) {
        _isErrorMessage.value = true
        _userMessage.value = "Budget amount must be greater than zero"
        return@launch
      }

      val result =
        createBudgetUseCase(
          authenticatedUserId = uid,
          name = name,
          category = category,
          amountMinor = minorUnits,
          currencyCode = currencyCode,
          periodType = periodType,
          customStartDate = customStartDate,
          customEndDate = customEndDate,
          warningThresholdPercent = warningThresholdPercent,
        )

      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Budget '${it.name}' created successfully"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to create budget"
        },
      )
    }
  }

  fun updateBudget(
    budget: Budget,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid == null) {
        _isErrorMessage.value = true
        _userMessage.value = "Authentication session required"
        return@launch
      }

      val result = updateBudgetUseCase(uid, budget)
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Budget updated successfully"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to update budget"
        },
      )
    }
  }

  fun deleteBudget(
    budgetId: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid == null) {
        _isErrorMessage.value = true
        _userMessage.value = "Authentication session required"
        return@launch
      }

      val result = deleteBudgetUseCase(uid, budgetId)
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Budget deleted successfully"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to delete budget"
        },
      )
    }
  }
}
