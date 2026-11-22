package com.example.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuthSessionState
import com.example.domain.model.ReportPeriodType
import com.example.domain.repository.AuthRepository
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.domain.usecase.ObserveFinancialReportsUseCase
import com.example.domain.usecase.ReportsCalculationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Home dashboard state, built on the same [ObserveFinancialReportsUseCase] the Reports screen uses.
 *
 * That use case already scopes every query to the signed-in user's UID and is covered by the
 * existing calculation tests, so the dashboard shows real ledger data and never invents figures.
 */
@HiltViewModel
class HomeDashboardViewModel
@Inject
constructor(
  private val authRepository: AuthRepository,
  private val observeFinancialReportsUseCase: ObserveFinancialReportsUseCase,
  calculationEngine: ReportsCalculationEngine,
  private val formatMoneyUseCase: FormatMoneyUseCase,
) : ViewModel() {

  private val monthlyWindow = calculationEngine.buildDateWindow(ReportPeriodType.MONTHLY)

  private val cardOrder = MutableStateFlow(HomeCardId.entries.toList())
  private val hiddenCards = MutableStateFlow<Set<HomeCardId>>(emptySet())

  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<HomeDashboardUiState> =
    authRepository
      .observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid == null) {
          flowOf(HomeDashboardUiState(isLoading = false, isEmpty = true))
        } else {
          observeFinancialReportsUseCase(uid, flowOf(monthlyWindow))
            .map { snapshot ->
              HomeDashboardMapper.map(
                snapshot = snapshot,
                preferredCurrencyCode = null,
                formatMoney = { money -> formatMoneyUseCase(money) },
              )
            }
        }
      }
      .catch { emit(HomeDashboardUiState(isLoading = false, hasError = true)) }
      .combine(cardOrder) { state, order -> state.copy(cards = order) }
      .combine(hiddenCards) { state, hidden -> state.copy(hiddenCards = hidden) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeDashboardUiState(),
      )

  fun toggleCardVisibility(card: HomeCardId) {
    hiddenCards.value =
      if (hiddenCards.value.contains(card)) {
        hiddenCards.value - card
      } else {
        // Never let the user hide every card: an empty dashboard looks broken.
        if (visibleCards().size <= 1 && !hiddenCards.value.contains(card)) hiddenCards.value
        else hiddenCards.value + card
      }
  }

  fun moveCard(card: HomeCardId, moveUp: Boolean) {
    val current = cardOrder.value
    val index = current.indexOf(card)
    if (index < 0) return
    val target = if (moveUp) index - 1 else index + 1
    if (target !in current.indices) return
    val reordered = current.toMutableList()
    val moved = reordered.removeAt(index)
    reordered.add(target, moved)
    cardOrder.value = reordered
  }

  fun resetCardLayout() {
    cardOrder.value = HomeCardId.entries.toList()
    hiddenCards.value = emptySet()
  }

  fun visibleCards(): List<HomeCardId> =
    cardOrder.value.filterNot { hiddenCards.value.contains(it) }
}
