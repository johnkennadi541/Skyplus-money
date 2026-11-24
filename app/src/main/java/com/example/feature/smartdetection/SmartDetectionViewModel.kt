package com.example.feature.smartdetection

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AccountMappingRule
import com.example.domain.model.AuthSessionState
import com.example.domain.model.AutoSavedTransactionNotice
import com.example.domain.model.DetectionSettings
import com.example.domain.model.FinancialAccount
import com.example.domain.model.Money
import com.example.domain.model.MerchantCategoryOverridePersistenceException
import com.example.domain.model.TransactionDetectionEvent
import com.example.domain.model.TransactionType
import com.example.domain.repository.AuthRepository
import com.example.domain.usecase.ApproveReviewItemUseCase
import com.example.domain.usecase.DetectionSettingsUseCases
import com.example.domain.usecase.DismissReviewItemUseCase
import com.example.domain.usecase.GetAccountsUseCase
import com.example.domain.usecase.GetDetectionEventsUseCase
import com.example.domain.usecase.GetReviewQueueUseCase
import com.example.service.NotificationPermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

@HiltViewModel
class SmartDetectionViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val authRepository: AuthRepository,
  private val getReviewQueueUseCase: GetReviewQueueUseCase,
  private val getDetectionEventsUseCase: GetDetectionEventsUseCase,
  private val approveReviewItemUseCase: ApproveReviewItemUseCase,
  private val dismissReviewItemUseCase: DismissReviewItemUseCase,
  private val settingsUseCases: DetectionSettingsUseCases,
  private val getAccountsUseCase: GetAccountsUseCase,
) : ViewModel() {

  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  private val _merchantCategoryOverride = MutableStateFlow<String?>(null)
  val merchantCategoryOverride: StateFlow<String?> = _merchantCategoryOverride.asStateFlow()
  private var merchantOverrideRequestId: Long = 0L

  private val _isPermissionGranted = MutableStateFlow(NotificationPermissionHelper.isNotificationAccessGranted(context))
  val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

  init {
    viewModelScope.launch {
      authRepository.observeAuthSession()
        .distinctUntilChangedBy { (it as? AuthSessionState.Authenticated)?.user?.uid }
        .collect { session ->
          _userMessage.value = null
          if (session !is AuthSessionState.Authenticated) {
            merchantOverrideRequestId++
            _merchantCategoryOverride.value = null
            settingsUseCases.clearAutoSavedNotice()
          }
        }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  val latestAutoSavedNotice: StateFlow<AutoSavedTransactionNotice?> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) settingsUseCases.observeAutoSavedNotice(uid)
        else flowOf(null)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val reviewQueue: StateFlow<List<TransactionDetectionEvent>> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) getReviewQueueUseCase(uid) else flowOf(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val detectionEvents: StateFlow<List<TransactionDetectionEvent>> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) getDetectionEventsUseCase(uid) else flowOf(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

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
  val mappingRules: StateFlow<List<AccountMappingRule>> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) settingsUseCases.getMappingRules(uid) else flowOf(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val settings: StateFlow<DetectionSettings> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) settingsUseCases.getSettings(uid)
        else flowOf(DetectionSettings(userId = ""))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DetectionSettings(userId = ""),
      )

  fun refreshPermissionStatus() {
    _isPermissionGranted.value = NotificationPermissionHelper.isNotificationAccessGranted(context)
  }

  fun openNotificationSettings() {
    NotificationPermissionHelper.openNotificationAccessSettings(context)
  }

  fun dismissAutoSavedNotice() {
    settingsUseCases.clearAutoSavedNotice()
  }

  fun loadMerchantCategoryOverride(merchant: String?) {
    val requestId = ++merchantOverrideRequestId
    _merchantCategoryOverride.value = null
    val safeMerchant = merchant?.trim()?.takeIf { it.isNotBlank() } ?: return
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) return@launch
      val savedCategory = settingsUseCases.getMerchantCategoryOverride(uid, safeMerchant)
      val latestSession = authRepository.observeAuthSession().first()
      val latestUid = (latestSession as? AuthSessionState.Authenticated)?.user?.uid
      if (requestId == merchantOverrideRequestId && latestUid == uid) {
        _merchantCategoryOverride.value = savedCategory
      }
    }
  }

  fun approveReviewItem(
    eventId: String,
    selectedAccountId: String,
    category: String,
    merchant: String?,
    correctedType: TransactionType? = null,
    correctedAmountDecimal: String? = null,
    currencyCode: String = "INR",
    rememberMerchantCategory: Boolean = false,
  ) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Approval failed: Authentication required"
        return@launch
      }

      val correctedMinor: Long? =
        correctedAmountDecimal?.trim()?.takeIf { it.isNotBlank() }?.let { txt ->
          val bd = runCatching { BigDecimal(txt) }.getOrNull() ?: return@let null
          Money.fromBigDecimal(bd, currencyCode).minorUnits
        }

      approveReviewItemUseCase(
        userId = uid,
        eventId = eventId,
        selectedAccountId = selectedAccountId,
        category = category,
        merchant = merchant,
        correctedType = correctedType,
        correctedAmountMinor = correctedMinor,
        rememberMerchantCategory = rememberMerchantCategory,
      )
        .onSuccess { _userMessage.value = "Transaction approved and saved" }
        .onFailure { error ->
          _userMessage.value =
            if (error is MerchantCategoryOverridePersistenceException) {
              "Transaction saved; merchant category preference could not be saved. You can still correct future entries manually."
            } else {
              "Approval failed: ${error.message}"
            }
        }
    }
  }

  fun dismissReviewItem(eventId: String) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Dismiss failed: Authentication required"
        return@launch
      }

      dismissReviewItemUseCase(uid, eventId)
        .onSuccess { _userMessage.value = "Transaction dismissed" }
        .onFailure { error -> _userMessage.value = "Dismiss failed: ${error.message}" }
    }
  }

  fun toggleNotificationDetection(enabled: Boolean) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Update failed: Authentication required"
        return@launch
      }
      val current = settingsUseCases.getSettings(uid).first()
      settingsUseCases.updateSettings(uid, current.copy(userId = uid, isNotificationDetectionEnabled = enabled))
        .onSuccess { _userMessage.value = if (enabled) "Detection enabled" else "Detection paused" }
        .onFailure { error -> _userMessage.value = "Update failed: ${error.message}" }
    }
  }

  fun toggleAutoSave(enabled: Boolean) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Update failed: Authentication required"
        return@launch
      }
      val current = settingsUseCases.getSettings(uid).first()
      settingsUseCases.updateSettings(uid, current.copy(userId = uid, isAutoSaveHighConfidenceEnabled = enabled))
        .onSuccess {
          _userMessage.value =
            if (enabled) "High-confidence auto-save enabled"
            else "Auto-save disabled; all items go to Review Queue"
        }
        .onFailure { error -> _userMessage.value = "Update failed: ${error.message}" }
    }
  }

  fun togglePermittedPackage(packageName: String, permitted: Boolean) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Update failed: Authentication required"
        return@launch
      }
      val current = settingsUseCases.getSettings(uid).first()
      val newPackages = current.permittedPackages.toMutableSet()
      if (permitted) newPackages.add(packageName) else newPackages.remove(packageName)
      settingsUseCases.updateSettings(uid, current.copy(userId = uid, permittedPackages = newPackages))
        .onFailure { error -> _userMessage.value = "Update failed: ${error.message}" }
    }
  }

  fun addMappingRule(accountId: String, pattern: String, hint: String?) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Save failed: Authentication required"
        return@launch
      }

      val rule =
        AccountMappingRule(
          userId = uid,
          accountId = accountId,
          matchPattern = pattern.trim(),
          institutionHint = hint?.trim()?.ifBlank { null },
        )
      settingsUseCases.addMappingRule(uid, rule)
        .onSuccess { _userMessage.value = "Account mapping saved" }
        .onFailure { error -> _userMessage.value = "Save failed: ${error.message}" }
    }
  }

  fun deleteMappingRule(ruleId: String) {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Delete failed: Authentication required"
        return@launch
      }

      settingsUseCases.deleteMappingRule(uid, ruleId)
        .onSuccess { _userMessage.value = "Mapping rule removed" }
        .onFailure { error -> _userMessage.value = "Delete failed: ${error.message}" }
    }
  }

  fun clearHistory() {
    viewModelScope.launch {
      val session = authRepository.observeAuthSession().first()
      val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
      if (uid.isNullOrBlank()) {
        _userMessage.value = "Clear failed: Authentication required"
        return@launch
      }

      settingsUseCases.clearHistory(uid)
        .onSuccess { _userMessage.value = "Detection history cleared" }
        .onFailure { error -> _userMessage.value = "Clear failed: ${error.message}" }
    }
  }

  fun dismissMessage() {
    _userMessage.value = null
  }
}
