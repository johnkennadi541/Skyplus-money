package com.example.feature.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.parseAmountToMinor
import com.example.domain.model.AuthSessionState
import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardNetwork
import com.example.domain.model.CreditCardSummary
import com.example.domain.model.CreditCardStatement
import com.example.domain.model.CreditCardRewardEvent
import com.example.domain.model.CreditCardSpendingAnalytics
import com.example.domain.model.CreditCardStatementSummary
import com.example.domain.model.FinancialAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.FinancialAccountRepository
import com.example.domain.repository.CreditCardRepository
import com.example.domain.repository.FinancialTransactionRepository
import com.example.domain.usecase.CreateCreditCardUseCase
import com.example.domain.usecase.ObserveCreditCardStatementSummariesUseCase
import com.example.domain.usecase.ObserveCreditCardSpendingAnalyticsUseCase
import com.example.domain.usecase.DeleteCreditCardUseCase
import com.example.domain.usecase.ObserveCreditCardsUseCase
import com.example.domain.usecase.UpdateCreditCardUseCase
import com.example.worker.CreditCardReminderScheduler
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
  private val creditCardRepository: CreditCardRepository,
  private val financialTransactionRepository: FinancialTransactionRepository,
  observeCreditCardsUseCase: ObserveCreditCardsUseCase,
  private val observeStatementSummariesUseCase: ObserveCreditCardStatementSummariesUseCase,
  private val observeSpendingAnalyticsUseCase: ObserveCreditCardSpendingAnalyticsUseCase,
  private val creditCardReminderScheduler: CreditCardReminderScheduler,
  private val createCreditCardUseCase: CreateCreditCardUseCase,
  private val updateCreditCardUseCase: UpdateCreditCardUseCase,
  private val deleteCreditCardUseCase: DeleteCreditCardUseCase,
) : ViewModel() {

  private val _selectedCardId = MutableStateFlow<String?>(null)

  init {
    viewModelScope.launch {
      authRepository.observeAuthSession().collect { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (!uid.isNullOrBlank()) creditCardReminderScheduler.updateSchedule(uid)
      }
    }
  }

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

  private val selectedCard =
    combine(cards, _selectedCardId) { summaries, selectedId ->
      summaries.firstOrNull { it.card.id == selectedId }?.card
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  val selectedCardStatements: StateFlow<List<CreditCardStatementSummary>> =
    combine(sessionFlow, selectedCard) { session, card ->
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      uid to card
    }
      .flatMapLatest { (uid, card) ->
        if (uid != null && card != null) observeStatementSummariesUseCase(uid, card)
        else flowOf(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  val selectedCardRewardEvents: StateFlow<List<CreditCardRewardEvent>> =
    combine(sessionFlow, selectedCard) { session, card ->
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      uid to card
    }
      .flatMapLatest { (uid, card) ->
        if (uid != null && card != null) creditCardRepository.observeRewardEvents(uid, card.id)
        else flowOf(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  val selectedCardTransactions: StateFlow<List<com.example.domain.model.FinancialTransaction>> =
    combine(sessionFlow, selectedCard) { session, card ->
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      uid to card
    }
      .flatMapLatest { (uid, card) ->
        if (uid != null && card != null) financialTransactionRepository.getTransactionsByAccount(uid, card.accountId)
        else flowOf(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  val selectedCardAnalytics: StateFlow<CreditCardSpendingAnalytics?> =
    combine(sessionFlow, selectedCard) { session, card ->
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      uid to card
    }
      .flatMapLatest { (uid, card) ->
        if (uid != null && card != null) observeSpendingAnalyticsUseCase(uid, card)
        else flowOf(null)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  fun selectCardDetails(cardId: String) {
    _selectedCardId.value = cardId
  }

  fun clearCardDetails() {
    _selectedCardId.value = null
  }

  fun dismissMessage() {
    _userMessage.value = null
    _isErrorMessage.value = false
  }

  fun createCard(
    accountId: String,
    issuerName: String,
    network: CreditCardNetwork,
    isActive: Boolean = true,
    last4: String,
    limitDecimal: String,
    billingCycleDay: Int,
    paymentDueDayOffset: Int,
    aprPercent: String,
    joiningFeeDecimal: String = "0",
    annualFeeDecimal: String = "0",
    annualFeeMonth: Int = 1,
    rewardProgramName: String? = null,
    reminderEnabled: Boolean = true,
    reminderDaysBefore: Int = 3,
    notes: String? = null,
    onSuccess: () -> Unit,
  ) {
    val uid = currentUid() ?: return
    val account = accounts.value.firstOrNull { it.id == accountId } ?: return

    viewModelScope.launch {
      val limitMinor = parseAmountToMinor(limitDecimal, account.currencyCode)
      val joiningFeeMinor = parseAmountToMinor(joiningFeeDecimal.ifBlank { "0" }, account.currencyCode)
      val annualFeeMinor = parseAmountToMinor(annualFeeDecimal.ifBlank { "0" }, account.currencyCode)
      if (limitMinor == null || joiningFeeMinor == null || annualFeeMinor == null) {
        _userMessage.value = "Enter valid credit limit, joining fee and annual fee amounts"
        _isErrorMessage.value = true
        return@launch
      }
      createCreditCardUseCase(
        authenticatedUserId = uid,
        accountId = accountId,
        issuerName = issuerName,
        network = network,
        isActive = isActive,
        last4 = last4,
        creditLimitMinor = limitMinor,
        currencyCode = account.currencyCode,
        billingCycleDay = billingCycleDay,
        paymentDueDayOffset = paymentDueDayOffset,
        aprPercent = aprPercent.ifBlank { "0" },
        joiningFeeMinor = joiningFeeMinor,
        annualFeeMinor = annualFeeMinor,
        annualFeeMonth = annualFeeMonth,
        rewardProgramName = rewardProgramName,
        reminderEnabled = reminderEnabled,
        reminderDaysBefore = reminderDaysBefore,
        notes = notes,
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
    joiningFeeDecimal: String,
    annualFeeDecimal: String,
    onSuccess: () -> Unit,
  ) {
    val uid = currentUid() ?: return
    viewModelScope.launch {
      val limitMinor = parseAmountToMinor(limitDecimal, card.currencyCode)
      val joiningFeeMinor = parseAmountToMinor(joiningFeeDecimal.ifBlank { "0" }, card.currencyCode)
      val annualFeeMinor = parseAmountToMinor(annualFeeDecimal.ifBlank { "0" }, card.currencyCode)
      if (limitMinor == null || joiningFeeMinor == null || annualFeeMinor == null) {
        _userMessage.value = "Enter valid credit limit, joining fee and annual fee amounts"
        _isErrorMessage.value = true
        return@launch
      }
      updateCreditCardUseCase(uid, card.copy(creditLimitMinor = limitMinor, joiningFeeMinor = joiningFeeMinor, annualFeeMinor = annualFeeMinor))
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

  fun createStatement(statement: CreditCardStatement, onSuccess: () -> Unit = {}) {
    val uid = currentUid() ?: return
    viewModelScope.launch {
      creditCardRepository.createStatement(uid, statement)
        .onSuccess {
          _userMessage.value = "Statement saved"
          _isErrorMessage.value = false
          onSuccess()
        }
        .onFailure {
          _userMessage.value = it.message ?: "Could not save statement"
          _isErrorMessage.value = true
        }
    }
  }

  fun addRewardEvent(event: CreditCardRewardEvent, onSuccess: () -> Unit = {}) {
    val uid = currentUid() ?: return
    viewModelScope.launch {
      creditCardRepository.addRewardEvent(uid, event)
        .onSuccess {
          _userMessage.value = "Rewards updated"
          _isErrorMessage.value = false
          onSuccess()
        }
        .onFailure {
          _userMessage.value = it.message ?: "Could not update rewards"
          _isErrorMessage.value = true
        }
    }
  }

  fun setCardFrozen(cardId: String, frozen: Boolean) {
    val uid = currentUid() ?: return
    viewModelScope.launch {
      creditCardRepository.setCardFrozen(uid, cardId, frozen)
        .onSuccess {
          _userMessage.value = if (frozen) {
            "Marked as frozen in SKYPLUS MONEY. This does not lock the card with your bank."
          } else {
            "Card marked as active in SKYPLUS MONEY."
          }
          _isErrorMessage.value = false
        }
        .onFailure {
          _userMessage.value = it.message ?: "Could not update card status"
          _isErrorMessage.value = true
        }
    }
  }

  fun deleteCard(cardId: String, onSuccess: () -> Unit) {
    val uid = currentUid() ?: return
    viewModelScope.launch {
      deleteCreditCardUseCase(uid, cardId)
        .onSuccess {
          _userMessage.value = "Card removed. The account and its transactions are unchanged."
          _isErrorMessage.value = false
          onSuccess()
        }
        .onFailure {
          _userMessage.value = it.message ?: "Could not remove the card"
          _isErrorMessage.value = true
        }
    }
  }

  private fun currentUid(): String? = activeUid
}
