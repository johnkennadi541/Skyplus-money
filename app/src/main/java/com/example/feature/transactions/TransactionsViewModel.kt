package com.example.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuthSessionState
import com.example.domain.model.FinancialAccount
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.Money
import com.example.domain.model.TransactionCategories
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.repository.AuthRepository
import com.example.domain.usecase.DeleteTransactionUseCase
import com.example.domain.usecase.GetAccountsUseCase
import com.example.domain.usecase.GetTransactionsUseCase
import com.example.domain.usecase.GetArchivedTransactionsUseCase
import com.example.domain.usecase.RestoreTransactionUseCase
import com.example.domain.usecase.RecordExpenseUseCase
import com.example.domain.usecase.RecordGeneralTransactionUseCase
import com.example.domain.usecase.RecordIncomeUseCase
import com.example.domain.usecase.RecordTransferUseCase
import com.example.domain.usecase.UpdateTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Calendar
import javax.inject.Inject

enum class TimePeriodFilter {
  ALL,
  TODAY,
  THIS_WEEK,
  THIS_MONTH,
  CUSTOM_RANGE,
}

data class TransactionsFilterState(
  val searchQuery: String = "",
  val selectedAccountId: String? = null,
  val selectedCategory: String? = null,
  val selectedCurrency: String? = null,
  val selectedType: TransactionType? = null,
  val selectedPeriod: TimePeriodFilter = TimePeriodFilter.ALL,
  val customStartDateMillis: Long? = null,
  val customEndDateMillis: Long? = null,
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  private val getTransactionsUseCase: GetTransactionsUseCase,
  private val getArchivedTransactionsUseCase: GetArchivedTransactionsUseCase,
  private val restoreTransactionUseCase: RestoreTransactionUseCase,
  private val getAccountsUseCase: GetAccountsUseCase,
  private val recordExpenseUseCase: RecordExpenseUseCase,
  private val recordIncomeUseCase: RecordIncomeUseCase,
  private val recordTransferUseCase: RecordTransferUseCase,
  private val recordGeneralTransactionUseCase: RecordGeneralTransactionUseCase,
  private val updateTransactionUseCase: UpdateTransactionUseCase,
  private val deleteTransactionUseCase: DeleteTransactionUseCase,
) : ViewModel() {

  private val _filterState = MutableStateFlow(TransactionsFilterState())
  val filterState: StateFlow<TransactionsFilterState> = _filterState.asStateFlow()

  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  init {
    viewModelScope.launch {
      authRepository.observeAuthSession()
        .distinctUntilChangedBy { (it as? AuthSessionState.Authenticated)?.user?.uid }
        .collect {
          _userMessage.value = null
          _filterState.value = TransactionsFilterState()
        }
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

  @OptIn(ExperimentalCoroutinesApi::class)
  val allTransactions: StateFlow<List<FinancialTransaction>> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) getTransactionsUseCase(uid) else flowOf(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val archivedTransactions: StateFlow<List<FinancialTransaction>> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) getArchivedTransactionsUseCase(uid) else flowOf(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val filteredTransactions: StateFlow<List<FinancialTransaction>> =
    combine(allTransactions, filterState) { txns, filters ->
      val now = System.currentTimeMillis()
      val cal = Calendar.getInstance()

      val startOfToday = cal.apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }.timeInMillis

      val weekCal = Calendar.getInstance().apply {
        timeInMillis = startOfToday
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
      }
      val startOfWeek = weekCal.timeInMillis

      val monthCal = Calendar.getInstance().apply {
        timeInMillis = startOfToday
        set(Calendar.DAY_OF_MONTH, 1)
      }
      val startOfMonth = monthCal.timeInMillis

      txns.filter { txn ->
        val matchesQuery =
          filters.searchQuery.isBlank() ||
            txn.category.contains(filters.searchQuery, ignoreCase = true) ||
            (txn.merchantOrPayee?.contains(filters.searchQuery, ignoreCase = true) == true) ||
            (txn.notes?.contains(filters.searchQuery, ignoreCase = true) == true) ||
            (txn.referenceId?.contains(filters.searchQuery, ignoreCase = true) == true)

        val matchesAccount =
          filters.selectedAccountId == null || txn.accountId == filters.selectedAccountId

        val matchesCategory =
          filters.selectedCategory == null || txn.category == filters.selectedCategory

        val matchesCurrency =
          filters.selectedCurrency == null || txn.currencyCode.equals(filters.selectedCurrency, ignoreCase = true)

        val matchesType =
          filters.selectedType == null || txn.type == filters.selectedType

        val matchesPeriod =
          when (filters.selectedPeriod) {
            TimePeriodFilter.ALL -> true
            TimePeriodFilter.TODAY -> txn.transactionDate >= startOfToday
            TimePeriodFilter.THIS_WEEK -> txn.transactionDate >= startOfWeek
            TimePeriodFilter.THIS_MONTH -> txn.transactionDate >= startOfMonth
            TimePeriodFilter.CUSTOM_RANGE -> {
              val startOk = filters.customStartDateMillis?.let { txn.transactionDate >= it } ?: true
              val endOk = filters.customEndDateMillis?.let { txn.transactionDate <= it } ?: true
              startOk && endOk
            }
          }

        matchesQuery && matchesAccount && matchesCategory && matchesCurrency && matchesType && matchesPeriod
      }
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  fun recordExpense(
    accountId: String,
    amountDecimal: String,
    category: String,
    merchant: String?,
    notes: String?,
    date: Long = System.currentTimeMillis(),
    status: TransactionStatus = TransactionStatus.COMPLETED,
  ) {
    recordTransaction(
      accountId = accountId,
      type = TransactionType.EXPENSE,
      status = status,
      amountDecimal = amountDecimal,
      category = category.ifBlank { "Other Expenses" },
      merchant = merchant,
      notes = notes,
      date = date,
    )
  }

  fun recordIncome(
    accountId: String,
    amountDecimal: String,
    category: String,
    notes: String?,
    merchant: String? = null,
    date: Long = System.currentTimeMillis(),
    status: TransactionStatus = TransactionStatus.COMPLETED,
  ) {
    recordTransaction(
      accountId = accountId,
      type = TransactionType.INCOME,
      status = status,
      amountDecimal = amountDecimal,
      category = category.ifBlank { "Other Income" },
      merchant = merchant,
      notes = notes,
      date = date,
    )
  }

  fun recordTransaction(
    accountId: String,
    type: TransactionType,
    status: TransactionStatus,
    amountDecimal: String,
    category: String,
    merchant: String?,
    notes: String?,
    date: Long = System.currentTimeMillis(),
  ) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Transaction failed: Authentication required"
        return@launch
      }
      val account =
        accounts.value.firstOrNull { it.id == accountId && it.userId == uid }
          ?: getAccountsUseCase(uid).first().firstOrNull { it.id == accountId }
          ?: run {
            _userMessage.value = "Transaction failed: Account ownership verification failed"
            return@launch
          }

      val parsedAmount = runCatching { BigDecimal(amountDecimal.trim()) }.getOrNull()
      if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) {
        _userMessage.value = "Enter a valid amount greater than zero"
        return@launch
      }

      val money = Money.fromBigDecimal(parsedAmount, account.currencyCode)
      val txn =
        FinancialTransaction(
          userId = uid,
          accountId = accountId,
          type = type,
          status = status,
          amountMinor = money.minorUnits,
          currencyCode = account.currencyCode,
          category = category,
          merchantOrPayee = merchant?.trim()?.ifBlank { null },
          notes = notes?.trim()?.ifBlank { null },
          transactionDate = date,
        )

      val result =
        when (type) {
          TransactionType.EXPENSE -> recordExpenseUseCase(uid, txn)
          TransactionType.INCOME -> recordIncomeUseCase(uid, txn)
          else -> recordGeneralTransactionUseCase(uid, txn)
        }

      result
        .onSuccess { _userMessage.value = "${type.name.lowercase().replaceFirstChar { it.uppercase() }} recorded" }
        .onFailure { error -> _userMessage.value = "Error: ${error.message}" }
    }
  }

  fun recordTransfer(
    sourceAccountId: String,
    destinationAccountId: String,
    amountDecimal: String,
    destinationAmountDecimal: String? = null,
    notes: String?,
    date: Long = System.currentTimeMillis(),
  ) {
    viewModelScope.launch {
      if (sourceAccountId == destinationAccountId) {
        _userMessage.value = "Source and destination accounts must be different"
        return@launch
      }

      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Transfer failed: Authentication required"
        return@launch
      }
      val uidAccounts =
        accounts.value.takeIf { list -> list.isNotEmpty() && list.all { it.userId == uid } }
          ?: getAccountsUseCase(uid).first()
      val sourceAcc =
        uidAccounts.firstOrNull { it.id == sourceAccountId && it.userId == uid } ?: run {
          _userMessage.value = "Transfer failed: Source account ownership verification failed"
          return@launch
        }
      val destAcc =
        uidAccounts.firstOrNull { it.id == destinationAccountId && it.userId == uid } ?: run {
          _userMessage.value = "Transfer failed: Destination account ownership verification failed"
          return@launch
        }

      val parsedAmount = runCatching { BigDecimal(amountDecimal.trim()) }.getOrNull()
      if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) {
        _userMessage.value = "Enter a valid amount greater than zero"
        return@launch
      }

      val sourceMoney = Money.fromBigDecimal(parsedAmount, sourceAcc.currencyCode)
      val destMinor: Long? =
        if (!sourceAcc.currencyCode.equals(destAcc.currencyCode, ignoreCase = true)) {
          val parsedDest = destinationAmountDecimal?.let { runCatching { BigDecimal(it.trim()) }.getOrNull() }
          if (parsedDest == null || parsedDest <= BigDecimal.ZERO) {
            _userMessage.value = "Cross-currency transfer requires converted ${destAcc.currencyCode} amount"
            return@launch
          }
          Money.fromBigDecimal(parsedDest, destAcc.currencyCode).minorUnits
        } else {
          null
        }

      val txn =
        FinancialTransaction(
          userId = uid,
          accountId = sourceAccountId,
          type = TransactionType.TRANSFER,
          status = TransactionStatus.COMPLETED,
          amountMinor = sourceMoney.minorUnits,
          currencyCode = sourceAcc.currencyCode,
          category = TransactionCategories.TRANSFER_OUT_CATEGORY,
          notes = notes?.trim()?.ifBlank { null },
          transactionDate = date,
        )

      recordTransferUseCase(uid, txn, destinationAccountId, destMinor)
        .onSuccess { _userMessage.value = "Transfer completed" }
        .onFailure { error -> _userMessage.value = "Transfer failed: ${error.message}" }
    }
  }

  fun updateTransactionFromEdit(
    original: FinancialTransaction,
    newAccountId: String,
    newType: TransactionType,
    newStatus: TransactionStatus,
    newAmountDecimal: String,
    newCategory: String,
    newMerchant: String?,
    newNotes: String?,
    newDate: Long,
  ) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Update failed: Authentication required"
        return@launch
      }
      if (original.userId != uid) {
        _userMessage.value = "Update failed: Transaction ownership verification failed"
        return@launch
      }
      val account =
        accounts.value.firstOrNull { it.id == newAccountId && it.userId == uid }
          ?: getAccountsUseCase(uid).first().firstOrNull { it.id == newAccountId }
          ?: run {
            _userMessage.value = "Update failed: Account ownership verification failed"
            return@launch
          }
      val parsedAmount = runCatching { BigDecimal(newAmountDecimal.trim()) }.getOrNull()
      if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) {
        _userMessage.value = "Enter a valid amount greater than zero"
        return@launch
      }

      val money = Money.fromBigDecimal(parsedAmount, account.currencyCode)
      val updated =
        original.copy(
          userId = uid,
          accountId = newAccountId,
          type = newType,
          status = newStatus,
          amountMinor = money.minorUnits,
          currencyCode = account.currencyCode,
          category = newCategory,
          merchantOrPayee = newMerchant?.trim()?.ifBlank { null },
          notes = newNotes?.trim()?.ifBlank { null },
          transactionDate = newDate,
        )

      updateTransactionUseCase(uid, updated, original)
        .onSuccess { _userMessage.value = "Transaction updated" }
        .onFailure { error -> _userMessage.value = "Update failed: ${error.message}" }
    }
  }

  fun deleteTransaction(transaction: FinancialTransaction) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Archive failed: Authentication required"
        return@launch
      }
      if (transaction.userId != uid) {
        _userMessage.value = "Archive failed: Transaction ownership verification failed"
        return@launch
      }
      deleteTransactionUseCase(uid, transaction.id)
        .onSuccess { _userMessage.value = "Transaction archived" }
        .onFailure { error -> _userMessage.value = "Archive failed: ${error.message}" }
    }
  }

  fun restoreTransaction(transaction: FinancialTransaction) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Restore failed: Authentication required"
        return@launch
      }
      if (transaction.userId != uid) {
        _userMessage.value = "Restore failed: Transaction ownership verification failed"
        return@launch
      }
      restoreTransactionUseCase(uid, transaction.id)
        .onSuccess { _userMessage.value = "Transaction restored" }
        .onFailure { error -> _userMessage.value = "Restore failed: ${error.message}" }
    }
  }

  fun setSearchQuery(query: String) {
    _filterState.value = _filterState.value.copy(searchQuery = query)
  }

  fun setAccountFilter(accountId: String?) {
    _filterState.value = _filterState.value.copy(selectedAccountId = accountId)
  }

  fun setCategoryFilter(category: String?) {
    _filterState.value = _filterState.value.copy(selectedCategory = category)
  }

  fun setCurrencyFilter(currencyCode: String?) {
    _filterState.value = _filterState.value.copy(selectedCurrency = currencyCode)
  }

  fun setTypeFilter(type: TransactionType?) {
    _filterState.value = _filterState.value.copy(selectedType = type)
  }

  fun setPeriodFilter(period: TimePeriodFilter) {
    _filterState.value = _filterState.value.copy(selectedPeriod = period)
  }

  fun setCustomDateRange(startMillis: Long?, endMillis: Long?) {
    _filterState.value =
      _filterState.value.copy(
        selectedPeriod = if (startMillis != null || endMillis != null) TimePeriodFilter.CUSTOM_RANGE else TimePeriodFilter.ALL,
        customStartDateMillis = startMillis,
        customEndDateMillis = endMillis,
      )
  }

  fun clearAllFilters() {
    _filterState.value = TransactionsFilterState()
  }

  fun dismissMessage() {
    _userMessage.value = null
  }
}
