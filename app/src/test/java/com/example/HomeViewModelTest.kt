package com.example

import com.example.domain.model.AuthResult
import com.example.domain.model.AuthSessionState
import com.example.domain.model.AuthenticatedUser
import com.example.domain.model.CategoryAllocationItem
import com.example.domain.model.CurrencyFinancialReport
import com.example.domain.model.FinancialReportSnapshot
import com.example.domain.model.IncomeExpenseCurrencyReport
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentCurrencyReport
import com.example.domain.model.InvestmentHoldingReportRow
import com.example.domain.model.MonthlyTrendRow
import com.example.domain.model.NetWorthCurrencyBreakdown
import com.example.domain.model.PortfolioCurrencySummary
import com.example.domain.model.ReportDateWindow
import com.example.domain.model.ReportPeriodType
import com.example.domain.model.ThemePreference
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.UserPreferencesRepository
import com.example.domain.usecase.BudgetCalculationEngine
import com.example.domain.usecase.EmiCalculationEngine
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.domain.usecase.InvestmentCalculationEngine
import com.example.domain.usecase.ObserveThemePreferenceUseCase
import com.example.domain.usecase.ReportsCalculationEngine
import com.example.domain.usecase.SetThemePreferenceUseCase
import com.example.domain.usecase.ShoppingCalculationEngine
import com.example.feature.dashboard.HomeFinancialReportsSource
import com.example.feature.dashboard.HomeViewModel
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 2 — Home dashboard state tests.
 *
 * Covers the four mandatory UI states (loading, empty, error, data) and, most
 * importantly, that currencies are never combined.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val window =
    ReportDateWindow(
      periodType = ReportPeriodType.MONTHLY,
      startDateMillis = 0L,
      endDateMillis = 1_000L,
      label = "This month",
    )

  private class FakePreferencesRepository : UserPreferencesRepository {
    private val state = MutableStateFlow(ThemePreference.DARK)

    override fun observeThemePreference(): Flow<ThemePreference> = state

    override suspend fun setThemePreference(preference: ThemePreference) {
      state.value = preference
    }
  }

  private class FakeAuthRepository(private val session: AuthSessionState) : AuthRepository {
    override fun observeAuthSession(): Flow<AuthSessionState> = flowOf(session)

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult<AuthenticatedUser> =
      AuthResult.Failure(com.example.domain.model.AuthError.MissingIdToken)

    override suspend fun refreshAndValidateSession(): AuthResult<AuthenticatedUser?> =
      AuthResult.Success(null)

    override suspend fun signOut(): AuthResult<Unit> = AuthResult.Success(Unit)
  }

  private class FakeReportsSource(
    private val snapshot: FinancialReportSnapshot? = null,
    private val failure: Throwable? = null,
    private val emitNothing: Boolean = false,
  ) : HomeFinancialReportsSource {
    override fun invoke(
      userId: String,
      windowFlow: Flow<ReportDateWindow>,
    ): Flow<FinancialReportSnapshot> {
      if (failure != null) {
        return flow { throw failure }
      }
      if (emitNothing) {
        return flow { /* never emits: the dashboard must stay in its loading state */ }
      }
      return flowOf(requireNotNull(snapshot) { "snapshot or failure must be supplied" })
    }
  }

  private val authenticatedSession =
    AuthSessionState.Authenticated(
      AuthenticatedUser(
        uid = "user-1",
        displayName = "John",
        email = "john@example.com",
        photoUrl = null,
        isEmailVerified = true,
        providers = emptySet(),
      )
    )

  private val calculationEngine =
    ReportsCalculationEngine(
      BudgetCalculationEngine(),
      EmiCalculationEngine(),
      InvestmentCalculationEngine(),
      ShoppingCalculationEngine(),
    )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(
    reportsSource: HomeFinancialReportsSource,
    session: AuthSessionState = authenticatedSession,
  ): HomeViewModel {
    val preferences = FakePreferencesRepository()
    return HomeViewModel(
      observeThemePreferenceUseCase = ObserveThemePreferenceUseCase(preferences),
      setThemePreferenceUseCase = SetThemePreferenceUseCase(preferences),
      formatMoneyUseCase = FormatMoneyUseCase(),
      authRepository = FakeAuthRepository(session),
      reportsSource = reportsSource,
      reportsCalculationEngine = calculationEngine,
    )
  }

  @Test
  fun `state is loading before the first snapshot arrives`() = runTest {
    val viewModel = viewModel(FakeReportsSource(emitNothing = true))
    val collector = launch { viewModel.uiState.collect() }
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.isEmpty)
    assertNull(viewModel.uiState.value.errorMessage)

    collector.cancel()
  }

  @Test
  fun `an empty snapshot produces the empty state`() = runTest {
    val viewModel = viewModel(FakeReportsSource(snapshot = emptySnapshot()))
    val collector = launch { viewModel.uiState.collect() }
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertTrue(state.isEmpty)
    assertTrue(state.sections.isEmpty())
    assertNull(state.selectedSection)

    collector.cancel()
  }

  @Test
  fun `a failure produces the error state and no financial figures`() = runTest {
    val viewModel = viewModel(FakeReportsSource(failure = IllegalStateException("disk unavailable")))
    val collector = launch { viewModel.uiState.collect() }
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertNotNull(state.errorMessage)
    assertTrue(state.sections.isEmpty())
    assertFalse(state.isEmpty)

    collector.cancel()
  }

  @Test
  fun `an unauthenticated session shows no figures`() = runTest {
    val viewModel =
      viewModel(
        reportsSource = FakeReportsSource(snapshot = twoCurrencySnapshot()),
        session = AuthSessionState.Unauthenticated,
      )
    val collector = launch { viewModel.uiState.collect() }
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertTrue(state.sections.isEmpty())

    collector.cancel()
  }

  @Test
  fun `currencies are kept separate and never summed`() = runTest {
    val viewModel = viewModel(FakeReportsSource(snapshot = twoCurrencySnapshot()))
    val collector = launch { viewModel.uiState.collect() }
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(2, state.sections.size)
    assertTrue(state.hasMultipleCurrencies)

    val inr = state.sections.first { it.currencyCode == "INR" }
    val kwd = state.sections.first { it.currencyCode == "KWD" }

    assertEquals("INR", inr.netWorth.currencyCode)
    assertEquals("KWD", kwd.netWorth.currencyCode)

    // Original minor units are preserved per currency, with no conversion.
    assertEquals(125_000_00L, inr.netWorth.minorUnits)
    assertEquals(900_000L, kwd.netWorth.minorUnits)

    // The first currency is selected by default and is a single section, not a total.
    assertEquals("INR", state.selectedSection?.currencyCode)
    assertFalse(state.selectedSection?.netWorth?.minorUnits == inr.netWorth.minorUnits + kwd.netWorth.minorUnits)

    collector.cancel()
  }

  @Test
  fun `selecting a currency switches the visible section`() = runTest {
    val viewModel = viewModel(FakeReportsSource(snapshot = twoCurrencySnapshot()))
    val collector = launch { viewModel.uiState.collect() }
    advanceUntilIdle()

    viewModel.selectCurrency("KWD")
    advanceUntilIdle()

    assertEquals("KWD", viewModel.uiState.value.selectedCurrencyCode)
    assertEquals("KWD", viewModel.uiState.value.selectedSection?.currencyCode)

    collector.cancel()
  }

  @Test
  fun `income expense and budget figures are mapped from the existing snapshot`() = runTest {
    val viewModel = viewModel(FakeReportsSource(snapshot = twoCurrencySnapshot()))
    val collector = launch { viewModel.uiState.collect() }
    advanceUntilIdle()

    val inr = viewModel.uiState.value.sections.first { it.currencyCode == "INR" }

    assertEquals(80_000_00L, inr.income.minorUnits)
    assertEquals(30_000_00L, inr.expenses.minorUnits)
    assertEquals(50_000_00L, inr.savings.minorUnits)
    assertEquals(2, inr.accountCount)

    // No budgets, loans or investments in this snapshot: those cards must be absent.
    assertNull(inr.budget)
    assertNull(inr.loans)
    assertNull(inr.investments)

    // Expense share is derived only from this currency's own figures.
    val expected = 30_000_00.0 / (80_000_00.0 + 30_000_00.0)
    assertEquals(expected.toFloat(), inr.expenseShare, 0.0001f)

    collector.cancel()
  }

  @Test
  fun `theme preference is exposed and can be toggled`() = runTest {
    val viewModel = viewModel(FakeReportsSource(snapshot = twoCurrencySnapshot()))
    val collector = launch { viewModel.uiState.collect() }
    advanceUntilIdle()

    assertEquals(ThemePreference.DARK, viewModel.uiState.value.themePreference)

    viewModel.toggleDarkLightTheme()
    advanceUntilIdle()

    assertEquals(ThemePreference.LIGHT, viewModel.uiState.value.themePreference)

    collector.cancel()
  }

  @Test
  fun `portfolio allocation trends and holdings are mapped from the snapshot`() = runTest {
    val viewModel = viewModel(FakeReportsSource(snapshot = portfolioSnapshot()))
    val collector = launch { viewModel.uiState.collect() }
    advanceUntilIdle()

    val inr = viewModel.uiState.value.sections.first { it.currencyCode == "INR" }

    // Trends: the existing monthly rows, newest last, formatted per currency.
    assertEquals(2, inr.trends.size)
    assertEquals("Jan 2026", inr.trends[0].label)
    assertEquals(60_000_00L, inr.trends[0].income.minorUnits)
    assertEquals(25_000_00L, inr.trends[0].expenses.minorUnits)
    assertEquals("INR", inr.trends[0].income.currencyCode)

    // Allocation: sorted by value, zero-value categories dropped, colours keyed by category.
    assertEquals(2, inr.allocation.size)
    assertEquals("Stocks / Equities", inr.allocation[0].categoryName)
    assertEquals("STOCKS", inr.allocation[0].colorKey)
    assertEquals(150_000_00L, inr.allocation[0].value.minorUnits)
    assertEquals("Mutual Funds", inr.allocation[1].categoryName)

    // Holdings: sorted by value, share derived from the real total, stale flag kept.
    assertEquals(2, inr.holdings.size)
    assertEquals("Large Cap Fund", inr.holdings[0].name)
    assertEquals(150_000_00L, inr.holdings[0].value.minorUnits)
    assertEquals(BigDecimal("60.0"), inr.holdings[0].sharePercent)
    assertTrue(inr.holdings[0].isStalePrice)
    assertEquals(BigDecimal("40.0"), inr.holdings[1].sharePercent)

    // Portfolio summary figures still come from the existing portfolio model.
    assertEquals(250_000_00L, inr.investments?.currentValue?.minorUnits)
    assertEquals(200_000_00L, inr.investments?.invested?.minorUnits)
    assertTrue(inr.hasPortfolio)

    collector.cancel()
  }

  @Test
  fun `a currency without investments shows no portfolio sections`() = runTest {
    val viewModel = viewModel(FakeReportsSource(snapshot = twoCurrencySnapshot()))
    val collector = launch { viewModel.uiState.collect() }
    advanceUntilIdle()

    val inr = viewModel.uiState.value.sections.first { it.currencyCode == "INR" }

    assertTrue(inr.trends.isEmpty())
    assertTrue(inr.allocation.isEmpty())
    assertTrue(inr.holdings.isEmpty())
    assertFalse(inr.hasPortfolio)

    collector.cancel()
  }

  // ---- fixtures -------------------------------------------------------------

  private fun emptySnapshot(): FinancialReportSnapshot =
    FinancialReportSnapshot(
      window = window,
      generatedAtMillis = 1L,
      currencies = emptyList(),
      reportsByCurrency = emptyMap(),
    )

  private fun twoCurrencySnapshot(): FinancialReportSnapshot {
    val inr =
      CurrencyFinancialReport(
        currencyCode = "INR",
        netWorth =
          NetWorthCurrencyBreakdown(
            currencyCode = "INR",
            cashAndBankAssetsMinor = 125_000_00L,
            investmentAssetsMinor = 0L,
            totalAssetsMinor = 125_000_00L,
            loanLiabilitiesMinor = 0L,
            accountOrCardLiabilitiesMinor = 0L,
            totalLiabilitiesMinor = 0L,
            netWorthMinor = 125_000_00L,
            activeAccountCount = 2,
            activeInvestmentCount = 0,
            activeOrOverdueLoanCount = 0,
          ),
        incomeExpense = incomeExpense("INR", income = 80_000_00L, expenses = 30_000_00L),
        budgets = null,
        loans = null,
        investments = null,
        shopping = null,
      )

    val kwd =
      CurrencyFinancialReport(
        currencyCode = "KWD",
        netWorth =
          NetWorthCurrencyBreakdown(
            currencyCode = "KWD",
            cashAndBankAssetsMinor = 900_000L,
            investmentAssetsMinor = 0L,
            totalAssetsMinor = 900_000L,
            loanLiabilitiesMinor = 0L,
            accountOrCardLiabilitiesMinor = 0L,
            totalLiabilitiesMinor = 0L,
            netWorthMinor = 900_000L,
            activeAccountCount = 1,
            activeInvestmentCount = 0,
            activeOrOverdueLoanCount = 0,
          ),
        incomeExpense = incomeExpense("KWD", income = 400_000L, expenses = 150_000L),
        budgets = null,
        loans = null,
        investments = null,
        shopping = null,
      )

    return FinancialReportSnapshot(
      window = window,
      generatedAtMillis = 1L,
      currencies = listOf("INR", "KWD"),
      reportsByCurrency = mapOf("INR" to inr, "KWD" to kwd),
    )
  }

  private fun incomeExpense(
    currencyCode: String,
    income: Long,
    expenses: Long,
    trends: List<MonthlyTrendRow> = emptyList(),
  ): IncomeExpenseCurrencyReport =
    IncomeExpenseCurrencyReport(
      currencyCode = currencyCode,
      totalIncomeMinor = income,
      grossExpensesMinor = expenses,
      totalRefundsAndReversalsMinor = 0L,
      netExpensesMinor = expenses,
      netSavingsMinor = income - expenses,
      savingsPercentage = BigDecimal.ZERO,
      completedIncomeCount = 1,
      completedExpenseCount = 1,
      completedRefundCount = 0,
      excludedTransferCount = 0,
      excludedNonCompletedCount = 0,
      expenseByCategory = emptyList(),
      incomeByCategory = emptyList(),
      monthlyTrends = trends,
      transactionsInRange = emptyList(),
    )

  /** One currency with a real portfolio: two categories, two holdings, two months. */
  private fun portfolioSnapshot(): FinancialReportSnapshot {
    val portfolio =
      PortfolioCurrencySummary(
        currencyCode = "INR",
        totalInvestedMinor = 200_000_00L,
        totalBuyCostMinor = 200_000_00L,
        totalValuationMinor = 250_000_00L,
        unrealizedPnlMinor = 50_000_00L,
        realizedPnlMinor = 0L,
        absoluteReturnMinor = 50_000_00L,
        returnPercent = BigDecimal("25.0"),
        lastMarketUpdateMillis = 1L,
        staleHoldingCount = 1,
        activeHoldingCount = 2,
        categoryAllocations =
          listOf(
            CategoryAllocationItem(
              category = InvestmentCategory.MUTUAL_FUNDS,
              investedMinor = 80_000_00L,
              valuationMinor = 100_000_00L,
              unrealizedPnlMinor = 20_000_00L,
              realizedPnlMinor = 0L,
              allocationPercent = BigDecimal("40.0"),
              holdingCount = 1,
            ),
            CategoryAllocationItem(
              category = InvestmentCategory.STOCKS,
              investedMinor = 120_000_00L,
              valuationMinor = 150_000_00L,
              unrealizedPnlMinor = 30_000_00L,
              realizedPnlMinor = 0L,
              allocationPercent = BigDecimal("60.0"),
              holdingCount = 1,
            ),
            CategoryAllocationItem(
              category = InvestmentCategory.GOLD,
              investedMinor = 0L,
              valuationMinor = 0L,
              unrealizedPnlMinor = 0L,
              realizedPnlMinor = 0L,
              allocationPercent = BigDecimal.ZERO,
              holdingCount = 0,
            ),
          ),
        growthPoints = emptyList(),
      )

    val investments =
      InvestmentCurrencyReport(
        currencyCode = "INR",
        portfolioSummary = portfolio,
        periodBuyTotalMinor = 0L,
        periodSellTotalMinor = 0L,
        periodRealizedPnlMinor = 0L,
        holdingRows =
          listOf(
            InvestmentHoldingReportRow(
              holdingId = "h2",
              name = "Index ETF",
              category = InvestmentCategory.MUTUAL_FUNDS,
              currencyCode = "INR",
              investedMinor = 80_000_00L,
              valuationMinor = 100_000_00L,
              unrealizedPnlMinor = 20_000_00L,
              realizedPnlMinor = 0L,
              returnPercent = BigDecimal("25.0"),
              lastPriceUpdateDate = 1L,
              isStalePrice = false,
            ),
            InvestmentHoldingReportRow(
              holdingId = "h1",
              name = "Large Cap Fund",
              category = InvestmentCategory.STOCKS,
              currencyCode = "INR",
              investedMinor = 120_000_00L,
              valuationMinor = 150_000_00L,
              unrealizedPnlMinor = 30_000_00L,
              realizedPnlMinor = 0L,
              returnPercent = BigDecimal("25.0"),
              lastPriceUpdateDate = 1L,
              isStalePrice = true,
            ),
          ),
      )

    val report =
      CurrencyFinancialReport(
        currencyCode = "INR",
        netWorth =
          NetWorthCurrencyBreakdown(
            currencyCode = "INR",
            cashAndBankAssetsMinor = 125_000_00L,
            investmentAssetsMinor = 250_000_00L,
            totalAssetsMinor = 375_000_00L,
            loanLiabilitiesMinor = 0L,
            accountOrCardLiabilitiesMinor = 0L,
            totalLiabilitiesMinor = 0L,
            netWorthMinor = 375_000_00L,
            activeAccountCount = 2,
            activeInvestmentCount = 2,
            activeOrOverdueLoanCount = 0,
          ),
        incomeExpense =
          incomeExpense(
            currencyCode = "INR",
            income = 80_000_00L,
            expenses = 30_000_00L,
            trends =
              listOf(
                MonthlyTrendRow(
                  monthKey = "2026-01",
                  monthLabel = "Jan 2026",
                  incomeMinor = 60_000_00L,
                  grossExpenseMinor = 25_000_00L,
                  refundMinor = 0L,
                  netExpenseMinor = 25_000_00L,
                  netSavingsMinor = 35_000_00L,
                ),
                MonthlyTrendRow(
                  monthKey = "2026-02",
                  monthLabel = "Feb 2026",
                  incomeMinor = 80_000_00L,
                  grossExpenseMinor = 30_000_00L,
                  refundMinor = 0L,
                  netExpenseMinor = 30_000_00L,
                  netSavingsMinor = 50_000_00L,
                ),
              ),
          ),
        budgets = null,
        loans = null,
        investments = investments,
        shopping = null,
      )

    return FinancialReportSnapshot(
      window = window,
      generatedAtMillis = 1L,
      currencies = listOf("INR"),
      reportsByCurrency = mapOf("INR" to report),
    )
  }
}
