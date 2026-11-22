package com.example.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DashboardLayoutPreferencesDataSource
import com.example.domain.model.AuthSessionState
import com.example.domain.model.ReportPeriodType
import com.example.domain.repository.AuthRepository
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.domain.usecase.ObserveFinancialReportsUseCase
import com.example.domain.usecase.ReportsCalculationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Home dashboard state, built on the same [ObserveFinancialReportsUseCase] the Reports screen uses.
 *
 * That use case already scopes every query to the signed-in user's UID and is covered by the
 * existing calculation tests, so the dashboard shows real ledger data and never invents figures.
 *
 * The card layout is stored in [DashboardLayoutPreferencesDataSource], keyed by UID, so a custom
 * order survives app restarts and is never shared between accounts.
 */
@HiltViewModel
class HomeDashboardViewModel
@Inject
constructor(
  private val authRepository: AuthRepository,
  private val observeFinancialReportsUseCase: ObserveFinancialReportsUseCase,
  calculationEngine: ReportsCalculationEngine,
  private val formatMoneyUseCase: FormatMoneyUseCase,
  private val layoutPreferences: DashboardLayoutPreferencesDataSource,
) : ViewModel() {

  private val monthlyWindow = calculationEngine.buildDateWindow(ReportPeriodType.MONTHLY)

  /** The UID whose layout the actions should write to. Set by the session flow below. */
  private var activeUid: String? = null

  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<HomeDashboardUiState> =
    authRepository
      .observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        activeUid = uid
        if (uid == null) {
          flowOf(HomeDashboardUiState(isLoading = false, isEmpty = true))
        } else {
          combine(
            observeFinancialReportsUseCase(uid, flowOf(monthlyWindow)),
            layoutPreferences.observeLayout(uid),
          ) { snapshot, savedLayout ->
            val layout = DashboardLayoutPolicy.decode(savedLayout)
            HomeDashboardMapper.map(
              snapshot = snapshot,
              preferredCurrencyCode = null,
              formatMoney = { money -> formatMoneyUseCase(money) },
            )
              .copy(cards = layout.order, hiddenCards = layout.hidden)
          }
        }
      }
      .catch { emit(HomeDashboardUiState(isLoading = false, hasError = true)) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeDashboardUiState(),
      )

  fun toggleCardVisibility(card: HomeCardId) {
    val uid = activeUid ?: return
    persist(uid, DashboardLayoutPolicy.toggle(currentLayout(), card))
  }

  fun moveCard(card: HomeCardId, moveUp: Boolean) {
    val uid = activeUid ?: return
    persist(uid, DashboardLayoutPolicy.move(currentLayout(), card, moveUp))
  }

  /** Restores the default order, shows every card, and writes that reset to disk. */
  fun resetCardLayout() {
    val uid = activeUid ?: return
    persist(uid, DashboardLayoutPolicy.reset(currentLayout()))
  }

  private fun currentLayout(): DashboardLayout =
    DashboardLayout(order = uiState.value.cards, hidden = uiState.value.hiddenCards)

  private fun persist(uid: String, layout: DashboardLayout) {
    viewModelScope.launch {
      layoutPreferences.saveLayout(
        userId = uid,
        orderRaw = DashboardLayoutPolicy.encodeOrder(layout.order),
        hiddenRaw = DashboardLayoutPolicy.encodeHidden(layout.hidden),
      )
    }
  }
}
