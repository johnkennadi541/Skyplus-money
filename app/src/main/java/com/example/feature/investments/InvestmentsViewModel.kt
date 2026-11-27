package com.example.feature.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.DispatcherProvider
import com.example.domain.model.AuthSessionState
import com.example.domain.model.FinancialAccount
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.InvestmentHoldingPerformance
import com.example.domain.model.MarketSyncSettings
import com.example.domain.model.PortfolioCurrencySummary
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.FinancialAccountRepository
import com.example.domain.usecase.CreateInvestmentUseCase
import com.example.domain.usecase.DeleteInvestmentUseCase
import com.example.domain.usecase.InvestmentCalculationEngine
import com.example.domain.usecase.InvestmentHoldingDetailState
import com.example.domain.usecase.InvestmentPortfolioSnapshot
import com.example.domain.usecase.ObserveHoldingDetailUseCase
import com.example.domain.usecase.ObservePortfolioUseCase
import com.example.domain.usecase.ObserveArchivedInvestmentsUseCase
import com.example.domain.usecase.RestoreInvestmentUseCase
import com.example.domain.usecase.RecordInvestmentBuyUseCase
import com.example.domain.usecase.RecordInvestmentSellUseCase
import com.example.domain.usecase.SetHoldingAutoPriceUseCase
import com.example.domain.usecase.SyncMarketPricesUseCase
import com.example.domain.usecase.UpdateInvestmentMetadataUseCase
import com.example.domain.usecase.UpdateManualMarketPriceUseCase
import com.example.worker.MarketPriceSyncScheduler
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
import javax.inject.Inject

@HiltViewModel
class InvestmentsViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  private val accountRepository: FinancialAccountRepository,
  private val observePortfolioUseCase: ObservePortfolioUseCase,
  private val observeArchivedInvestmentsUseCase: ObserveArchivedInvestmentsUseCase,
  private val restoreInvestmentUseCase: RestoreInvestmentUseCase,
  private val observeHoldingDetailUseCase: ObserveHoldingDetailUseCase,
  private val createInvestmentUseCase: CreateInvestmentUseCase,
  private val updateInvestmentMetadataUseCase: UpdateInvestmentMetadataUseCase,
  private val recordInvestmentBuyUseCase: RecordInvestmentBuyUseCase,
  private val recordInvestmentSellUseCase: RecordInvestmentSellUseCase,
  private val updateManualMarketPriceUseCase: UpdateManualMarketPriceUseCase,
  private val setHoldingAutoPriceUseCase: SetHoldingAutoPriceUseCase,
  private val syncMarketPricesUseCase: SyncMarketPricesUseCase,
  private val deleteInvestmentUseCase: DeleteInvestmentUseCase,
  private val syncScheduler: MarketPriceSyncScheduler,
  val calculationEngine: InvestmentCalculationEngine,
  private val dispatchers: DispatcherProvider,
) : ViewModel() {

  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  private val _isErrorMessage = MutableStateFlow(false)
  val isErrorMessage: StateFlow<Boolean> = _isErrorMessage.asStateFlow()

  private val _selectedCurrency = MutableStateFlow<String?>(null)
  val selectedCurrency: StateFlow<String?> = _selectedCurrency.asStateFlow()

  private val _selectedCategory = MutableStateFlow<InvestmentCategory?>(null)
  val selectedCategory: StateFlow<InvestmentCategory?> = _selectedCategory.asStateFlow()

  private val _isSyncing = MutableStateFlow(false)
  val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

  init {
    viewModelScope.launch {
      authRepository
        .observeAuthSession()
        .distinctUntilChangedBy { (it as? AuthSessionState.Authenticated)?.user?.uid }
        .collect {
          _userMessage.value = null
          _isErrorMessage.value = false
          _selectedCurrency.value = null
          _selectedCategory.value = null
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
  private val portfolioSnapshot: StateFlow<InvestmentPortfolioSnapshot> =
    authRepository
      .observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) observePortfolioUseCase(uid) else flowOf(InvestmentPortfolioSnapshot())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InvestmentPortfolioSnapshot(),
      )

  val holdings: StateFlow<List<InvestmentHolding>> =
    portfolioSnapshot
      .map { it.holdings }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val archivedHoldings: StateFlow<List<InvestmentHolding>> =
    authRepository
      .observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) observeArchivedInvestmentsUseCase(uid) else flowOf(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val performances: StateFlow<List<InvestmentHoldingPerformance>> =
    portfolioSnapshot
      .map { it.performances }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val summariesByCurrency: StateFlow<Map<String, PortfolioCurrencySummary>> =
    portfolioSnapshot
      .map { it.summariesByCurrency }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap(),
      )

  val syncSettings: StateFlow<MarketSyncSettings> =
    syncMarketPricesUseCase
      .observeSettings()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MarketSyncSettings(),
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeHoldingDetail(holdingId: String): Flow<InvestmentHoldingDetailState> =
    authRepository.observeAuthSession().flatMapLatest { session ->
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid != null) observeHoldingDetailUseCase(uid, holdingId)
      else flowOf(InvestmentHoldingDetailState())
    }

  fun selectCurrency(currencyCode: String?) {
    _selectedCurrency.value = currencyCode
  }

  fun selectCategory(category: InvestmentCategory?) {
    _selectedCategory.value = category
  }

  fun dismissMessage() {
    _userMessage.value = null
    _isErrorMessage.value = false
  }

  fun setAutoUpdateConsent(enabled: Boolean) {
    viewModelScope.launch(dispatchers.io) {
      syncMarketPricesUseCase.setAutoUpdateConsent(enabled)
      val wifiOnly = syncSettings.value.syncOnlyOnWifi
      syncScheduler.updateSchedule(autoUpdateConsentEnabled = enabled, syncOnlyOnWifi = wifiOnly)
      _isErrorMessage.value = false
      _userMessage.value =
        if (enabled) {
          "Opt-in EOD Mutual Fund NAV sync enabled. Manual prices remain protected unless opted in per holding."
        } else {
          "Automatic market sync disabled. Manual pricing remains 100% active."
        }
    }
  }

  fun setSyncOnlyOnWifi(wifiOnly: Boolean) {
    viewModelScope.launch(dispatchers.io) {
      syncMarketPricesUseCase.setSyncOnlyOnWifi(wifiOnly)
      syncScheduler.updateSchedule(
        autoUpdateConsentEnabled = syncSettings.value.autoUpdateConsentEnabled,
        syncOnlyOnWifi = wifiOnly,
      )
    }
  }

  fun syncMarketPricesNow(allowOverwriteManual: Boolean = false) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      _isSyncing.value = true
      try {
        val result =
          syncMarketPricesUseCase.syncNow(
            authenticatedUserId = uid,
            allowOverwriteManual = allowOverwriteManual,
            forceBypassCooldown = true,
          )
        result.fold(
          onSuccess = { summary ->
            _isErrorMessage.value = summary.errorCount > 0 && summary.updatedCount == 0
            _userMessage.value = summary.message
          },
          onFailure = { error ->
            _isErrorMessage.value = true
            _userMessage.value = error.message ?: "Unable to sync market prices"
          },
        )
      } finally {
        _isSyncing.value = false
      }
    }
  }

  fun createInvestment(
    name: String,
    category: InvestmentCategory,
    symbolOrTicker: String?,
    isin: String?,
    brokerOrPlatform: String?,
    currencyCode: String,
    purchaseDate: Long,
    quantityDecimal: String,
    purchasePriceDecimal: String,
    chargesDecimal: String,
    currentMarketPriceDecimal: String?,
    autoPriceUpdateEnabled: Boolean,
    notes: String?,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result =
        createInvestmentUseCase(
          authenticatedUserId = uid,
          name = name,
          category = category,
          symbolOrTicker = symbolOrTicker,
          isin = isin,
          brokerOrPlatform = brokerOrPlatform,
          currencyCode = currencyCode,
          purchaseDate = purchaseDate,
          quantityDecimal = quantityDecimal,
          purchasePriceDecimal = purchasePriceDecimal,
          chargesDecimal = chargesDecimal,
          currentMarketPriceDecimal = currentMarketPriceDecimal,
          autoPriceUpdateEnabled = autoPriceUpdateEnabled,
          notes = notes,
        )
      result.fold(
        onSuccess = { created ->
          _isErrorMessage.value = false
          _userMessage.value = "Investment '${created.name}' added"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to add investment"
        },
      )
    }
  }

  fun updateHoldingMetadata(
    holding: InvestmentHolding,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result = updateInvestmentMetadataUseCase(uid, holding)
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Investment '${holding.name}' updated"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to update investment"
        },
      )
    }
  }

  fun setHoldingAutoPriceUpdate(holdingId: String, enabled: Boolean) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result = setHoldingAutoPriceUseCase(uid, holdingId, enabled)
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value =
            if (enabled) "Auto NAV updates opted in for this holding"
            else "Manual pricing locked for this holding"
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to update holding preference"
        },
      )
    }
  }

  fun recordAdditionalPurchase(
    holding: InvestmentHolding,
    transactionDate: Long,
    quantityDecimal: String,
    unitPriceDecimal: String,
    chargesDecimal: String,
    notes: String?,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result =
        recordInvestmentBuyUseCase(
          authenticatedUserId = uid,
          holdingId = holding.id,
          currencyCode = holding.currencyCode,
          transactionDate = transactionDate,
          quantityDecimal = quantityDecimal,
          unitPriceDecimal = unitPriceDecimal,
          chargesDecimal = chargesDecimal,
          notes = notes,
        )
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Additional purchase recorded for '${holding.name}'"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to record purchase"
        },
      )
    }
  }

  fun recordSale(
    holding: InvestmentHolding,
    transactionDate: Long,
    quantityDecimal: String,
    unitPriceDecimal: String,
    chargesDecimal: String,
    notes: String?,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result =
        recordInvestmentSellUseCase(
          authenticatedUserId = uid,
          holdingId = holding.id,
          currencyCode = holding.currencyCode,
          transactionDate = transactionDate,
          quantityDecimal = quantityDecimal,
          unitPriceDecimal = unitPriceDecimal,
          chargesDecimal = chargesDecimal,
          notes = notes,
        )
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Sale recorded using Weighted Average Cost"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to record sale"
        },
      )
    }
  }

  fun updateManualMarketPrice(
    holdingId: String,
    newUnitPriceDecimal: String,
    priceDate: Long,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result =
        updateManualMarketPriceUseCase(
          authenticatedUserId = uid,
          holdingId = holdingId,
          newUnitPriceDecimal = newUnitPriceDecimal,
          priceDate = priceDate,
        )
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Manual market price updated"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to update market price"
        },
      )
    }
  }

  fun restoreInvestment(
    holdingId: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      restoreInvestmentUseCase(uid, holdingId).fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Investment restored to active portfolio. Its history is unchanged."
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to restore investment"
        },
      )
    }
  }

  fun deleteInvestment(
    holdingId: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result = deleteInvestmentUseCase(uid, holdingId)
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Investment holding archived. Transaction and price history are preserved."
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to delete investment"
        },
      )
    }
  }

  private suspend fun requireAuthenticatedUid(): String? {
    val session = authRepository.observeAuthSession().first()
    val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
    if (uid.isNullOrBlank()) {
      _isErrorMessage.value = true
      _userMessage.value = "Authentication session required"
      return null
    }
    return uid
  }
}
