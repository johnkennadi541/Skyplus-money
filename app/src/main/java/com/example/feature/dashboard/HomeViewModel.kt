package com.example.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuthSessionState
import com.example.domain.model.FinancialReportSnapshot
import com.example.domain.model.Money
import com.example.domain.model.ReportDateWindow
import com.example.domain.model.ReportPeriodType
import com.example.domain.model.ThemePreference
import com.example.domain.repository.AuthRepository
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.domain.usecase.ObserveThemePreferenceUseCase
import com.example.domain.usecase.ReportsCalculationEngine
import com.example.domain.usecase.SetThemePreferenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One formatted monetary figure. Minor units stay available for tests and future use. */
data class HomeAmount(
  val currencyCode: String,
  val minorUnits: Long,
  val formatted: String,
)

data class HomeBudgetSection(
  val allocated: HomeAmount,
  val spent: HomeAmount,
  val remaining: HomeAmount,
  val activeCount: Int,
  val overspentCount: Int,
  val utilizationPercent: BigDecimal,
)

data class HomeLoanSection(
  val outstanding: HomeAmount,
  val monthlyEmi: HomeAmount,
  val activeCount: Int,
  val overdueCount: Int,
)

data class HomeInvestmentSection(
  val currentValue: HomeAmount,
  val invested: HomeAmount,
  val unrealisedPnl: HomeAmount,
  val holdingCount: Int,
  val returnPercent: BigDecimal,
)

/** One month of the income-versus-expense trend. Income is green, expenses are red. */
data class HomeTrendPoint(
  val monthKey: String,
  val label: String,
  val income: HomeAmount,
  val expenses: HomeAmount,
  val savings: HomeAmount,
)

/** One slice of the portfolio allocation ring. [colorKey] drives the stable colour. */
data class HomeAllocationSlice(
  val categoryName: String,
  val colorKey: String,
  val value: HomeAmount,
  val gainLoss: HomeAmount,
  val sharePercent: BigDecimal,
  val holdingCount: Int,
)

/** One row of the holdings breakdown. [colorKey] drives the stable colour. */
data class HomeHoldingRow(
  val id: String,
  val name: String,
  val categoryName: String,
  val colorKey: String,
  val value: HomeAmount,
  val invested: HomeAmount,
  val gainLoss: HomeAmount,
  val returnPercent: BigDecimal,
  val sharePercent: BigDecimal,
  val isStalePrice: Boolean,
)

/**
 * Everything the Home dashboard shows for ONE currency.
 * Currencies are never combined: each section carries exactly one currency code.
 */
data class HomeCurrencySection(
  val currencyCode: String,
  val netWorth: HomeAmount,
  val assets: HomeAmount,
  val liabilities: HomeAmount,
  val income: HomeAmount,
  val expenses: HomeAmount,
  val savings: HomeAmount,
  val accountCount: Int,
  val budget: HomeBudgetSection? = null,
  val loans: HomeLoanSection? = null,
  val investments: HomeInvestmentSection? = null,
  val trends: List<HomeTrendPoint> = emptyList(),
  val allocation: List<HomeAllocationSlice> = emptyList(),
  val holdings: List<HomeHoldingRow> = emptyList(),
) {
  /** True when this currency has anything portfolio-related to show. */
  val hasPortfolio: Boolean
    get() = investments != null || allocation.isNotEmpty() || holdings.isNotEmpty()

  /** Share of the income-versus-expense bar taken by expenses, 0f..1f. */
  val expenseShare: Float
    get() {
      val total = income.minorUnits + expenses.minorUnits
      if (total <= 0L) return 0f
      return (expenses.minorUnits.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }
}

data class HomeUiState(
  val themePreference: ThemePreference = ThemePreference.DARK,
  val isLoading: Boolean = true,
  val errorMessage: String? = null,
  val windowLabel: String = "",
  val lastUpdatedMillis: Long? = null,
  val sections: List<HomeCurrencySection> = emptyList(),
  val selectedCurrencyCode: String? = null,
) {
  val isEmpty: Boolean
    get() = !isLoading && errorMessage == null && sections.isEmpty()

  val hasMultipleCurrencies: Boolean
    get() = sections.size > 1

  /** The section currently on screen; never a sum across currencies. */
  val selectedSection: HomeCurrencySection?
    get() =
      sections.firstOrNull { it.currencyCode == selectedCurrencyCode } ?: sections.firstOrNull()
}

/**
 * Home dashboard state.
 *
 * The figures come from the existing ObserveFinancialReportsUseCase, which already
 * aggregates accounts, transactions, budgets, loans, investments and shopping into
 * [FinancialReportSnapshot] keyed by currency. Nothing is recalculated here and no
 * repository, database or calculation engine is modified — this ViewModel only maps
 * an existing snapshot into presentation state and formats it.
 *
 * Currencies are kept strictly separate: one [HomeCurrencySection] per currency code,
 * no cross-currency totals and no exchange-rate conversion.
 */
@HiltViewModel
class HomeViewModel
@Inject
constructor(
  observeThemePreferenceUseCase: ObserveThemePreferenceUseCase,
  private val setThemePreferenceUseCase: SetThemePreferenceUseCase,
  private val formatMoneyUseCase: FormatMoneyUseCase,
  private val authRepository: AuthRepository,
  private val reportsSource: HomeFinancialReportsSource,
  private val reportsCalculationEngine: ReportsCalculationEngine,
) : ViewModel() {

  private companion object {
    /** Bars stay legible with at most six months on a phone width. */
    const val MAX_TREND_MONTHS = 6

    /** The breakdown shows the largest eight holdings; the full list lives in Investments. */
    const val MAX_HOLDING_ROWS = 8
  }

  private val window = MutableStateFlow(buildWindow())
  private val selectedCurrency = MutableStateFlow<String?>(null)

  private sealed interface ReportsResult {
    data object NoUser : ReportsResult

    data class Success(val snapshot: FinancialReportSnapshot) : ReportsResult

    data class Failure(val message: String?) : ReportsResult
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val reportsFlow: Flow<ReportsResult> =
    authRepository
      .observeAuthSession()
      .map { session -> (session as? AuthSessionState.Authenticated)?.user?.uid }
      .distinctUntilChanged()
      .flatMapLatest { userId ->
        if (userId == null) {
          flowOf(ReportsResult.NoUser)
        } else {
          reportsSource(userId, window)
            .map<FinancialReportSnapshot, ReportsResult> { ReportsResult.Success(it) }
            .catch { error -> emit(ReportsResult.Failure(error.message)) }
        }
      }

  val uiState: StateFlow<HomeUiState> =
    combine(
        observeThemePreferenceUseCase(),
        window,
        reportsFlow,
        selectedCurrency,
      ) { preference, currentWindow, result, selected ->
        when (result) {
          ReportsResult.NoUser ->
            HomeUiState(
              themePreference = preference,
              isLoading = false,
              windowLabel = currentWindow.label,
            )
          is ReportsResult.Failure ->
            HomeUiState(
              themePreference = preference,
              isLoading = false,
              errorMessage = result.message,
              windowLabel = currentWindow.label,
            )
          is ReportsResult.Success -> mapSnapshot(result.snapshot, preference, selected)
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(windowLabel = window.value.label),
      )

  /** Recomputes the reporting window and re-subscribes. Used by the error-state retry. */
  fun refresh() {
    window.value = buildWindow()
  }

  fun selectCurrency(currencyCode: String) {
    selectedCurrency.value = currencyCode
  }

  fun toggleDarkLightTheme() {
    viewModelScope.launch {
      val next =
        when (uiState.value.themePreference) {
          ThemePreference.DARK -> ThemePreference.LIGHT
          ThemePreference.LIGHT -> ThemePreference.DARK
          ThemePreference.SYSTEM -> ThemePreference.LIGHT
        }
      setThemePreferenceUseCase(next)
    }
  }

  private fun buildWindow(): ReportDateWindow =
    reportsCalculationEngine.buildDateWindow(periodType = ReportPeriodType.MONTHLY)

  private fun mapSnapshot(
    snapshot: FinancialReportSnapshot,
    preference: ThemePreference,
    selected: String?,
  ): HomeUiState {
    val sections =
      snapshot.currencies.mapNotNull { currencyCode ->
        val report = snapshot.reportsByCurrency[currencyCode] ?: return@mapNotNull null
        val netWorth = report.netWorth
        val incomeExpense = report.incomeExpense

        val portfolioSummary = report.investments?.portfolioSummary

        HomeCurrencySection(
          currencyCode = currencyCode,
          netWorth = amount(netWorth.netWorthMinor, currencyCode),
          assets = amount(netWorth.totalAssetsMinor, currencyCode),
          liabilities = amount(netWorth.totalLiabilitiesMinor, currencyCode),
          income = amount(incomeExpense.totalIncomeMinor, currencyCode),
          expenses = amount(incomeExpense.netExpensesMinor, currencyCode),
          savings = amount(incomeExpense.netSavingsMinor, currencyCode),
          accountCount = netWorth.activeAccountCount,
          budget =
            report.budgets?.let { budget ->
              HomeBudgetSection(
                allocated = amount(budget.totalAllocatedMinor, currencyCode),
                spent = amount(budget.totalSpentMinor, currencyCode),
                remaining = amount(budget.totalRemainingMinor, currencyCode),
                activeCount = budget.activeBudgetCount,
                overspentCount = budget.overspentBudgetCount,
                utilizationPercent = budget.utilizationPercent,
              )
            },
          loans =
            report.loans?.let { loan ->
              HomeLoanSection(
                outstanding = amount(loan.totalOutstandingMinor, currencyCode),
                monthlyEmi = amount(loan.totalMonthlyEmiMinor, currencyCode),
                activeCount = loan.activeCount,
                overdueCount = loan.overdueCount,
              )
            },
          investments =
            portfolioSummary?.let { portfolio ->
              HomeInvestmentSection(
                currentValue = amount(portfolio.totalValuationMinor, currencyCode),
                invested = amount(portfolio.totalInvestedMinor, currencyCode),
                unrealisedPnl = amount(portfolio.unrealizedPnlMinor, currencyCode),
                holdingCount = portfolio.activeHoldingCount,
                returnPercent = portfolio.returnPercent,
              )
            },
          trends = mapTrends(incomeExpense.monthlyTrends, currencyCode),
          allocation = mapAllocation(portfolioSummary, currencyCode),
          holdings =
            mapHoldings(
              rows = report.investments?.holdingRows.orEmpty(),
              totalValuationMinor = portfolioSummary?.totalValuationMinor ?: 0L,
              currencyCode = currencyCode,
            ),
        )
      }

    val selectedStillPresent = sections.any { it.currencyCode == selected }

    return HomeUiState(
      themePreference = preference,
      isLoading = false,
      windowLabel = snapshot.window.label,
      lastUpdatedMillis = snapshot.generatedAtMillis,
      sections = sections,
      selectedCurrencyCode =
        when {
          selectedStillPresent -> selected
          else -> sections.firstOrNull()?.currencyCode
        },
    )
  }

  /** Last [MAX_TREND_MONTHS] months only, so the bars stay readable on a phone. */
  private fun mapTrends(
    rows: List<com.example.domain.model.MonthlyTrendRow>,
    currencyCode: String,
  ): List<HomeTrendPoint> =
    rows.takeLast(MAX_TREND_MONTHS).map { row ->
      HomeTrendPoint(
        monthKey = row.monthKey,
        label = row.monthLabel,
        income = amount(row.incomeMinor, currencyCode),
        expenses = amount(row.netExpenseMinor, currencyCode),
        savings = amount(row.netSavingsMinor, currencyCode),
      )
    }

  private fun mapAllocation(
    portfolio: com.example.domain.model.PortfolioCurrencySummary?,
    currencyCode: String,
  ): List<HomeAllocationSlice> =
    portfolio?.categoryAllocations.orEmpty()
      .filter { it.valuationMinor > 0L }
      .sortedByDescending { it.valuationMinor }
      .map { item ->
        HomeAllocationSlice(
          categoryName = item.category.displayName,
          colorKey = item.category.name,
          value = amount(item.valuationMinor, currencyCode),
          gainLoss = amount(item.unrealizedPnlMinor, currencyCode),
          sharePercent = item.allocationPercent,
          holdingCount = item.holdingCount,
        )
      }

  /**
   * Holdings are ordered by value, largest first. [sharePercent] is derived here from
   * the existing total valuation; when that total is zero the share is zero rather
   * than an invented number.
   */
  private fun mapHoldings(
    rows: List<com.example.domain.model.InvestmentHoldingReportRow>,
    totalValuationMinor: Long,
    currencyCode: String,
  ): List<HomeHoldingRow> =
    rows.sortedByDescending { it.valuationMinor }
      .take(MAX_HOLDING_ROWS)
      .map { row ->
        HomeHoldingRow(
          id = row.holdingId,
          name = row.name,
          categoryName = row.category.displayName,
          colorKey = row.category.name + ":" + row.name,
          value = amount(row.valuationMinor, currencyCode),
          invested = amount(row.investedMinor, currencyCode),
          gainLoss = amount(row.unrealizedPnlMinor, currencyCode),
          returnPercent = row.returnPercent,
          sharePercent =
            if (totalValuationMinor > 0L) {
              BigDecimal(row.valuationMinor)
                .multiply(BigDecimal(100))
                .divide(BigDecimal(totalValuationMinor), 1, java.math.RoundingMode.HALF_UP)
            } else {
              BigDecimal.ZERO
            },
          isStalePrice = row.isStalePrice,
        )
      }

  private fun amount(minorUnits: Long, currencyCode: String): HomeAmount =
    HomeAmount(
      currencyCode = currencyCode,
      minorUnits = minorUnits,
      formatted = formatMoneyUseCase(Money.fromMinorUnits(minorUnits, currencyCode)),
    )
}
