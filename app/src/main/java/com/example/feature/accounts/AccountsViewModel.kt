package com.example.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AccountType
import com.example.domain.model.AuthSessionState
import com.example.domain.model.FinancialAccount
import com.example.domain.model.Money
import com.example.domain.repository.AuthRepository
import com.example.domain.usecase.CreateAccountUseCase
import com.example.domain.usecase.DeleteAccountUseCase
import com.example.domain.usecase.GetAccountsUseCase
import com.example.domain.usecase.SetAccountActiveUseCase
import com.example.domain.usecase.UpdateAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class AccountsUiState(
  val accounts: List<FinancialAccount> = emptyList(),
  val balancesByCurrency: Map<String, Money> = emptyMap(),
  val isLoading: Boolean = false,
  val userMessage: String? = null,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  private val getAccountsUseCase: GetAccountsUseCase,
  private val createAccountUseCase: CreateAccountUseCase,
  private val updateAccountUseCase: UpdateAccountUseCase,
  private val deleteAccountUseCase: DeleteAccountUseCase,
  private val setAccountActiveUseCase: SetAccountActiveUseCase,
) : ViewModel() {

  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  init {
    viewModelScope.launch {
      authRepository.observeAuthSession()
        .distinctUntilChangedBy { (it as? AuthSessionState.Authenticated)?.user?.uid }
        .collect { _userMessage.value = null }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  val accounts: StateFlow<List<FinancialAccount>> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) getAccountsUseCase(uid) else flowOf(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  fun createAccount(
    name: String,
    type: AccountType,
    currencyCode: String,
    initialBalanceDecimal: String,
    institutionName: String?,
    accountNumberLast4: String?,
    colorHex: String,
  ) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Create failed: Authentication required"
        return@launch
      }

      val cleanCurrency = currencyCode.trim().uppercase()
      val initialAmount =
        if (initialBalanceDecimal.isBlank()) BigDecimal.ZERO
        else runCatching { BigDecimal(initialBalanceDecimal.trim()) }.getOrNull()
          ?: run {
            _userMessage.value = "Invalid opening balance format"
            return@launch
          }

      val initialMoney =
        runCatching { Money.fromBigDecimal(initialAmount, cleanCurrency) }.getOrElse {
          _userMessage.value = "Invalid ISO 4217 currency code: $cleanCurrency"
          return@launch
        }

      val account =
        FinancialAccount(
          userId = uid,
          name = name.trim(),
          type = type,
          currencyCode = cleanCurrency,
          initialBalanceMinor = initialMoney.minorUnits,
          currentBalanceMinor = initialMoney.minorUnits,
          institutionName = institutionName?.trim()?.ifBlank { null },
          accountNumberLast4 = accountNumberLast4?.trim()?.ifBlank { null },
          colorHex = colorHex,
          isActive = true,
        )

      createAccountUseCase(uid, account)
        .onSuccess { _userMessage.value = "Account '${account.name}' created" }
        .onFailure { error -> _userMessage.value = "Failed to create account: ${error.message}" }
    }
  }

  fun updateAccount(account: FinancialAccount) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Update failed: Authentication required"
        return@launch
      }
      if (account.userId != uid) {
        _userMessage.value = "Update failed: Account ownership verification failed"
        return@launch
      }
      updateAccountUseCase(uid, account)
        .onSuccess { _userMessage.value = "Account updated" }
        .onFailure { error -> _userMessage.value = "Update failed: ${error.message}" }
    }
  }

  fun toggleAccountActive(account: FinancialAccount) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Status update failed: Authentication required"
        return@launch
      }
      if (account.userId != uid) {
        _userMessage.value = "Status update failed: Account ownership verification failed"
        return@launch
      }
      val nextActive = !account.isActive
      setAccountActiveUseCase(uid, account.id, nextActive)
        .onSuccess {
          _userMessage.value =
            if (nextActive) "Account '${account.name}' activated"
            else "Account '${account.name}' deactivated"
        }
        .onFailure { error -> _userMessage.value = "Status update failed: ${error.message}" }
    }
  }

  fun deleteAccount(account: FinancialAccount, deleteLinkedTransactions: Boolean = false) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Delete failed: Authentication required"
        return@launch
      }
      if (account.userId != uid) {
        _userMessage.value = "Delete failed: Account ownership verification failed"
        return@launch
      }
      deleteAccountUseCase(uid, account.id, deleteLinkedTransactions)
        .onSuccess { _userMessage.value = "Account deleted" }
        .onFailure { error -> _userMessage.value = error.message ?: "Delete failed" }
    }
  }

  fun dismissMessage() {
    _userMessage.value = null
  }
}
