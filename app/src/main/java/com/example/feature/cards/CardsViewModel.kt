package com.example.feature.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.parseAmountToMinor
import com.example.domain.model.AuthSessionState
import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardNetwork
import com.example.domain.model.CreditCardSummary
import com.example.domain.model.FinancialAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.FinancialAccountRepository
import com.example.domain.usecase.CreateCreditCardUseCase
import com.example.domain.usecase.ObserveCreditCardsUseCase
import com.example.domain.usecase.SetCreditCardArchivedUseCase
import com.example.domain.usecase.UpdateCreditCardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CardsViewModel
@Inject
constructor(
  private val authRepository: AuthRepository,
  private val accountRepository: FinancialAccountRepository,
  observeCreditCardsUseCase: ObserveCreditCardsUseCase,
  private val createCreditCardUseCase: CreateCreditCardUseCase,
  private val updateCreditCardUseCase: UpdateCreditCardUseCase,
  private val setCreditCardArchivedUseCase: SetCreditCardArchivedUseCase,
) : ViewModel() {

  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  private val _isErrorMessage = MutableStateFlow(false)
  val isErrorMessage: StateFlow<Boolean> = _isErrorMessage.asStateFlow()

  /** The UID the actions must write under, captured from the live session. */
  private var activeUid: String? = null

  @OptIn(ExperimentalCoroutinesApi::class)
  private val sessionFlow =
    authRepository.observeAuthSession().onEach { session ->
      activeUid = (session as? AuthSessionState.Authenticated)?.user?.uid
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  val cards: StateFlow<List<CreditCardSummary>> =
    sessionFlow
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) observeCreditCardsUseCase(uid) else flowOf(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  val accounts: StateFlow<List<FinancialAccount>> =
    sessionFlow
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) accountRepository.getAccounts(uid) else flowOf(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /** Credit-card accounts that do not have a card record yet, so they can be attached. */
  val linkableAccounts: StateFlow<List<FinancialAccount>> =
    combine(accounts, cards) { accountList, cardList ->
      val linked = cardList.map { it.card.accountId }.toSet()
      accountList.filter {
        it.type == com.example.domain.model.AccountType.CREDIT_CARD && it.id !in linked
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun dismissMessage() {
    _userMessage.value = null
    _isErrorMessage.value = false
  }

  fun createCard(
    accountId: String,
    issuerName: String,
    network: CreditCardNetwork,
    last4: String,
    limitDecimal: String,
    billingCycleDay: Int,
    paymentDueDayOffset: Int,
    aprPercent: String,
    onSuccess: () -> Unit,
  ) {
    val uid = currentUid() ?: return
    val account = accounts.value.firstOrNull { it.id == accountId } ?: return

    viewModelScope.launch {
      val limitMinor = parseAmountToMinor(limitDecimal, account.currencyCode)
      if (limitMinor == null) {
        _userMessage.value = "Enter a valid credit limit"
        _isErrorMessage.value = true
        return@launch
      }
      createCreditCardUseCase(
        authenticatedUserId = uid,
        accountId = accountId,
        issuerName = issuerName,
        network = network,
        last4 = last4,
        creditLimitMinor = limitMinor,
        currencyCode = account.currencyCode,
        billingCycleDay = billingCycleDay,
        paymentDueDayOffset = paymentDueDayOffset,
        aprPercent = aprPercent.ifBlank { "0" },
      )
        .onSuccess {
          _userMessage.value = "Card added"
          _isErrorMessage.value = false
          onSuccess()
        }
        .onFailure {
          _userMessage.value = it.message ?: "Could not add the card"
          _isErrorMessage.value = true
        }
    }
  }

  fun updateCard(
    card: CreditCard,
    limitDecimal: String,
    onSuccess: () -> Unit,
  ) {
    val uid = currentUid() ?: return
    viewModelScope.launch {
      val limitMinor = parseAmountToMinor(limitDecimal, card.currencyCode)
      if (limitMinor == null) {
        _userMessage.value = "Enter a valid credit limit"
        _isErrorMessage.value = true
        return@launch
      }
      updateCreditCardUseCase(uid, card.copy(creditLimitMinor = limitMinor))
        .onSuccess {
          _userMessage.value = "Card updated"
          _isErrorMessage.value = false
          onSuccess()
        }
        .onFailure {
          _userMessage.value = it.message ?: "Could not update the card"
          _isErrorMessage.value = true
        }
    }
  }

  fun setCardArchived(cardId: String, archived: Boolean, onSuccess: () -> Unit) {
    val uid = currentUid() ?: return
    viewModelScope.launch {
      setCreditCardArchivedUseCase(uid, cardId, archived)
        .onSuccess {
          _userMessage.value = if (archived) {
            "Card archived. Its outstanding balance and transaction history are preserved."
          } else {
            "Card restored."
          }
          _isErrorMessage.value = false
          onSuccess()
        }
        .onFailure {
          _userMessage.value = it.message ?: "Could not update the card archive"
          _isErrorMessage.value = true
        }
    }
  }

  private fun currentUid(): String? = activeUid
}
