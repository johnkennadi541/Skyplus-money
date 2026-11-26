package com.example.feature.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.DispatcherProvider
import com.example.domain.model.AuthSessionState
import com.example.domain.model.EmiCalculationResult
import com.example.domain.model.EmiPrefillDraft
import com.example.domain.model.FinancialAccount
import com.example.domain.model.Loan
import com.example.domain.model.LoanCurrencySummary
import com.example.domain.model.LoanPayment
import com.example.domain.model.LoanType
import com.example.domain.model.Money
import com.example.domain.model.PaymentBreakdown
import com.example.domain.model.TenureUnit
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.FinancialAccountRepository
import com.example.domain.usecase.CreateLoanUseCase
import com.example.domain.usecase.DeleteLoanUseCase
import com.example.domain.usecase.EmiCalculationEngine
import com.example.domain.usecase.MarkLoanCompletedUseCase
import com.example.domain.usecase.ObserveLoanDetailUseCase
import com.example.domain.usecase.ObserveLoansWithSummariesUseCase
import com.example.domain.usecase.RecordLoanPaymentUseCase
import com.example.domain.usecase.UpdateLoanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
class LoansViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  private val accountRepository: FinancialAccountRepository,
  private val observeLoansWithSummariesUseCase: ObserveLoansWithSummariesUseCase,
  private val observeLoanDetailUseCase: ObserveLoanDetailUseCase,
  private val createLoanUseCase: CreateLoanUseCase,
  private val updateLoanUseCase: UpdateLoanUseCase,
  private val recordLoanPaymentUseCase: RecordLoanPaymentUseCase,
  private val markLoanCompletedUseCase: MarkLoanCompletedUseCase,
  private val deleteLoanUseCase: DeleteLoanUseCase,
  val emiCalculationEngine: EmiCalculationEngine,
  private val dispatchers: DispatcherProvider,
) : ViewModel() {

  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  private val _isErrorMessage = MutableStateFlow(false)
  val isErrorMessage: StateFlow<Boolean> = _isErrorMessage.asStateFlow()

  private val _selectedCurrency = MutableStateFlow<String?>(null)
  val selectedCurrency: StateFlow<String?> = _selectedCurrency.asStateFlow()

  private val _emiPrefillDraft = MutableStateFlow<EmiPrefillDraft?>(null)
  val emiPrefillDraft: StateFlow<EmiPrefillDraft?> = _emiPrefillDraft.asStateFlow()

  init {
    viewModelScope.launch {
      authRepository
        .observeAuthSession()
        .distinctUntilChangedBy { (it as? AuthSessionState.Authenticated)?.user?.uid }
        .collect {
          _userMessage.value = null
          _isErrorMessage.value = false
          _emiPrefillDraft.value = null
        }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  val accounts: StateFlow<List<FinancialAccount>> =
    authRepository
      .observeAuthSession()
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
  private val rawLoansData =
    authRepository.observeAuthSession().flatMapLatest { session ->
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid != null) observeLoansWithSummariesUseCase(uid) else flowOf(emptyList<Loan>() to emptyMap())
    }

  val loans: StateFlow<List<Loan>> =
    rawLoansData
      .map { it.first }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val summariesByCurrency: StateFlow<Map<String, LoanCurrencySummary>> =
    rawLoansData
      .map { it.second }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap(),
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeLoanDetail(loanId: String): Flow<Pair<Loan?, List<LoanPayment>>> {
    return authRepository.observeAuthSession().flatMapLatest { session ->
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid != null) observeLoanDetailUseCase(uid, loanId) else flowOf(null to emptyList())
    }
  }

  fun selectCurrency(currencyCode: String?) {
    _selectedCurrency.value = currencyCode
  }

  fun dismissMessage() {
    _userMessage.value = null
    _isErrorMessage.value = false
  }

  fun stageEmiDraftForLoanCreation(draft: EmiPrefillDraft) {
    _emiPrefillDraft.value = draft
  }

  fun consumeEmiDraft() {
    _emiPrefillDraft.value = null
  }

  fun calculateEmi(
    principalDecimal: String,
    annualInterestRateText: String,
    tenureValueText: String,
    tenureUnit: TenureUnit,
    currencyCode: String,
  ): Result<EmiCalculationResult> =
    emiCalculationEngine.calculateEmiFromDecimal(
      principalDecimal = principalDecimal,
      annualInterestRateText = annualInterestRateText,
      tenureValueText = tenureValueText,
      tenureUnit = tenureUnit,
      currencyCode = currencyCode,
    )

  fun previewPaymentBreakdown(
    loan: Loan,
    amountDecimal: String,
  ): Result<PaymentBreakdown> =
    runCatching {
      val amountBd = BigDecimal(amountDecimal.trim())
      val money = Money.fromBigDecimal(amountBd, loan.currencyCode, RoundingMode.HALF_UP)
      emiCalculationEngine.calculatePaymentBreakdown(
        outstandingPrincipalMinor = loan.outstandingPrincipalMinor,
        annualInterestRatePercent = loan.annualInterestRatePercent,
        paymentAmountMinor = money.minorUnits,
        currencyCode = loan.currencyCode,
      )
    }

  fun createLoan(
    name: String,
    lenderName: String,
    loanType: LoanType,
    principalDecimal: String,
    currencyCode: String,
    annualInterestRateText: String,
    tenureMonths: Int,
    startDate: Long,
    alreadyPaidDecimal: String = "0",
    nextDueDate: Long? = null,
    notes: String? = null,
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

      val result =
        createLoanUseCase(
          authenticatedUserId = uid,
          name = name,
          lenderName = lenderName,
          loanType = loanType,
          principalDecimal = principalDecimal,
          currencyCode = currencyCode,
          annualInterestRateText = annualInterestRateText,
          tenureMonths = tenureMonths,
          startDate = startDate,
          alreadyPaidDecimal = alreadyPaidDecimal,
          nextDueDate = nextDueDate,
          notes = notes,
        )

      result.fold(
        onSuccess = { created ->
          _isErrorMessage.value = false
          _userMessage.value = "Loan '${created.name}' saved"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to create loan"
        },
      )
    }
  }

  fun updateLoan(
    loan: Loan,
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

      val result = updateLoanUseCase(uid, loan)
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Loan '${loan.name}' updated"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to update loan"
        },
      )
    }
  }

  fun recordPayment(
    loan: Loan,
    paymentDate: Long,
    amountDecimal: String,
    notes: String? = null,
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

      val amountMoney =
        try {
          Money.fromBigDecimal(
            BigDecimal(amountDecimal.trim()),
            loan.currencyCode,
            RoundingMode.HALF_UP,
          )
        } catch (_: Exception) {
          _isErrorMessage.value = true
          _userMessage.value = "Enter a valid numeric payment amount"
          return@launch
        }

      if (amountMoney.minorUnits <= 0L) {
        _isErrorMessage.value = true
        _userMessage.value = "Payment amount must be greater than zero"
        return@launch
      }

      val result =
        recordLoanPaymentUseCase(
          authenticatedUserId = uid,
          loanId = loan.id,
          paymentDate = paymentDate,
          amountMinor = amountMoney.minorUnits,
          notes = notes,
        )

      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "EMI payment recorded successfully"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to record loan payment"
        },
      )
    }
  }

  fun markLoanCompleted(
    loanId: String,
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

      val result = markLoanCompletedUseCase(uid, loanId)
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Loan marked as completed"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Unable to mark loan as completed"
        },
      )
    }
  }

  fun deleteLoan(
    loanId: String,
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

      val result = deleteLoanUseCase(uid, loanId)
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Loan deleted"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to delete loan"
        },
      )
    }
  }
}
