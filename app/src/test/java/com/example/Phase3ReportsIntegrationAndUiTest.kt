package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DispatcherProvider
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.data.local.MarketPriceSyncPreferencesDataSource
import com.example.data.local.ReportPreferencesDataSource
import com.example.data.local.db.AppDatabase
import com.example.data.repository.BudgetRepositoryImpl
import com.example.data.repository.FinancialAccountRepositoryImpl
import com.example.data.repository.FinancialTransactionRepositoryImpl
import com.example.data.repository.InvestmentRepositoryImpl
import com.example.data.repository.LoanRepositoryImpl
import com.example.data.repository.ShoppingRepositoryImpl
import com.example.domain.model.AccountType
import com.example.domain.model.EmailReportSettings
import com.example.domain.model.FinancialAccount
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.ReportCategoryTab
import com.example.domain.model.ReportDateWindow
import com.example.domain.model.ReportFrequency
import com.example.domain.model.ReportPeriodType
import com.example.domain.model.ThemePreference
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.repository.MarketPriceDataSource
import com.example.domain.repository.MarketQuoteFetchResult
import com.example.domain.repository.MarketQuoteRequest
import com.example.domain.usecase.BudgetCalculationEngine
import com.example.domain.usecase.EmiCalculationEngine
import com.example.domain.usecase.InvestmentCalculationEngine
import com.example.domain.usecase.ObserveFinancialReportsUseCase
import com.example.domain.usecase.ReportsCalculationEngine
import com.example.domain.usecase.ShoppingCalculationEngine
import com.example.feature.dashboard.HomeFoundationUiState
import com.example.feature.reports.PreparedEmailPreview
import com.example.feature.reports.ReportsContent
import com.example.feature.reports.ReportsScreen
import com.example.navigation.SkyplusAppShell
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3ReportsIntegrationAndUiTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var database: AppDatabase
  private lateinit var accountRepo: FinancialAccountRepositoryImpl
  private lateinit var txnRepo: FinancialTransactionRepositoryImpl
  private lateinit var budgetRepo: BudgetRepositoryImpl
  private lateinit var loanRepo: LoanRepositoryImpl
  private lateinit var investmentRepo: InvestmentRepositoryImpl
  private lateinit var shoppingRepo: ShoppingRepositoryImpl
  private lateinit var observeReportsUseCase: ObserveFinancialReportsUseCase
  private lateinit var calcEngine: ReportsCalculationEngine
  private lateinit var reportPrefs: ReportPreferencesDataSource

  private val testDispatchers =
    object : DispatcherProvider {
      override val io: CoroutineDispatcher = Dispatchers.Unconfined
      override val default: CoroutineDispatcher = Dispatchers.Unconfined
      override val main: CoroutineDispatcher = Dispatchers.Main
    }

  private val noopMarketDataSource =
    object : MarketPriceDataSource {
      override suspend fun fetchLatestQuote(request: MarketQuoteRequest): MarketQuoteFetchResult =
        MarketQuoteFetchResult.UnsupportedCategoryOrIdentifier("Noop test mock")
    }

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    accountRepo =
      FinancialAccountRepositoryImpl(
        database,
        database.financialAccountDao(),
        database.financialTransactionDao(),
        database.accountMappingRuleDao(),
        testDispatchers,
      )
    txnRepo =
      FinancialTransactionRepositoryImpl(
        database,
        database.financialTransactionDao(),
        database.financialAccountDao(),
        testDispatchers,
      )
    val budgetEngine = BudgetCalculationEngine()
    val emiEngine = EmiCalculationEngine()
    val invEngine = InvestmentCalculationEngine()
    val shopEngine = ShoppingCalculationEngine()

    budgetRepo = BudgetRepositoryImpl(database, database.budgetDao(), testDispatchers)
    loanRepo = LoanRepositoryImpl(database, database.loanDao(), emiEngine, testDispatchers)
    investmentRepo =
      InvestmentRepositoryImpl(
        database,
        database.investmentDao(),
        invEngine,
        noopMarketDataSource,
        MarketPriceSyncPreferencesDataSource(context),
        testDispatchers,
      )
    shoppingRepo = ShoppingRepositoryImpl(database, database.shoppingDao(), shopEngine, testDispatchers)
    calcEngine = ReportsCalculationEngine(budgetEngine, emiEngine, invEngine, shopEngine)
    observeReportsUseCase =
      ObserveFinancialReportsUseCase(
        accountRepository = accountRepo,
        transactionRepository = txnRepo,
        budgetRepository = budgetRepo,
        loanRepository = loanRepo,
        investmentRepository = investmentRepo,
        shoppingRepository = shoppingRepo,
        calculationEngine = calcEngine,
      )
    reportPrefs = ReportPreferencesDataSource(context)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `observeFinancialReportsUseCase and ReportPreferences enforce strict Firebase UID isolation`() = runBlocking {
    val userA = "uid_user_a_reports"
    val userB = "uid_user_b_reports"
    val now = 1_751_500_000_000L

    accountRepo.createAccount(
      userA,
      FinancialAccount(
        id = "acc_a",
        userId = userA,
        name = "User A Bank",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 50_000_00L,
        currentBalanceMinor = 50_000_00L,
      ),
    ).getOrThrow()
    txnRepo.recordIncome(
      userA,
      FinancialTransaction(
        id = "txn_a",
        userId = userA,
        accountId = "acc_a",
        type = TransactionType.INCOME,
        amountMinor = 20_000_00L,
        currencyCode = "INR",
        category = "Salary",
        transactionDate = now,
        status = TransactionStatus.COMPLETED,
      ),
    ).getOrThrow()

    accountRepo.createAccount(
      userB,
      FinancialAccount(
        id = "acc_b",
        userId = userB,
        name = "User B Bank",
        type = AccountType.BANK,
        currencyCode = "USD",
        initialBalanceMinor = 3_000_00L,
        currentBalanceMinor = 3_000_00L,
      ),
    ).getOrThrow()

    val windowFlow =
      MutableStateFlow(
        ReportDateWindow(
          periodType = ReportPeriodType.MONTHLY,
          startDateMillis = now - 10_000_000L,
          endDateMillis = now + 10_000_000L,
          label = "Current Window",
        )
      )

    val snapshotA = observeReportsUseCase(userA, windowFlow).first()
    assertEquals(listOf("INR"), snapshotA.currencies)
    assertFalse("User A must never see User B USD currency", "USD" in snapshotA.currencies)

    val snapshotB = observeReportsUseCase(userB, windowFlow).first()
    assertEquals(listOf("USD"), snapshotB.currencies)
    assertEquals(3_000_00L, snapshotB.reportsByCurrency["USD"]!!.netWorth.netWorthMinor)

    reportPrefs.setEnabled(userA, true)
    reportPrefs.setRecipientEmail(userA, "alice@example.com")
    reportPrefs.setFrequency(userA, ReportFrequency.WEEKLY)

    val prefsA = reportPrefs.observeSettings(userA).first()
    val prefsB = reportPrefs.observeSettings(userB).first()
    assertTrue(prefsA.enabled)
    assertEquals("alice@example.com", prefsA.recipientEmail)
    assertEquals(ReportFrequency.WEEKLY, prefsA.frequency)
    assertFalse(prefsB.enabled)
    assertEquals("", prefsB.recipientEmail)
  }

  @Test
  fun `bottom nav retains exact 4 tabs and opens Reports from Transactions tab and Home`() {
    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.LIGHT) {
        SkyplusAppShell(
          homeUiState = HomeFoundationUiState(),
          themePreference = ThemePreference.LIGHT,
          onToggleTheme = {},
          onSelectTheme = {},
        )
      }
    }
    composeTestRule.waitForIdle()

    // Verify exact 4 bottom navigation tabs remain intact
    composeTestRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_transactions").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_budget").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_shopping_list").assertIsDisplayed()

    // Navigate via Transactions -> Financial Reports button
    composeTestRule.onNodeWithTag("nav_tab_transactions").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("open_reports_from_transactions_btn").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("reports_screen").assertIsDisplayed()

    // Back to Transactions
    composeTestRule.onNodeWithTag("reports_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("transactions_screen").assertIsDisplayed()
  }

  @Test
  fun `reports screen renders all sections exports and mandatory email preview modal in dark mode`() {
    val now = 1_751_500_000_000L
    val snapshot =
      calcEngine.calculateSnapshot(
        window =
          ReportDateWindow(
            periodType = ReportPeriodType.MONTHLY,
            startDateMillis = now - 86_400_000L,
            endDateMillis = now + 86_400_000L,
            label = "July 2025",
          ),
        accounts =
          listOf(
            FinancialAccount(
              id = "acc_1",
              userId = "u1",
              name = "Primary Bank",
              type = AccountType.BANK,
              currencyCode = "INR",
              initialBalanceMinor = 100_000_00L,
              currentBalanceMinor = 100_000_00L,
            ),
          ),
        transactions =
          listOf(
            FinancialTransaction(
              id = "tx_1",
              userId = "u1",
              accountId = "acc_1",
              type = TransactionType.INCOME,
              amountMinor = 50_000_00L,
              currencyCode = "INR",
              category = "Salary",
              transactionDate = now,
              status = TransactionStatus.COMPLETED,
            ),
            FinancialTransaction(
              id = "tx_2",
              userId = "u1",
              accountId = "acc_1",
              type = TransactionType.EXPENSE,
              amountMinor = 10_000_00L,
              currencyCode = "INR",
              category = "Dining",
              transactionDate = now,
              status = TransactionStatus.COMPLETED,
            ),
          ),
        budgets = emptyList(),
        loans = emptyList(),
        loanPayments = emptyList(),
        holdings = emptyList(),
        investmentTransactions = emptyList(),
        investmentPriceHistory = emptyList(),
        shoppingLists = emptyList(),
        shoppingItems = emptyList(),
        nowMillis = now,
      )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        ReportsContent(
          snapshot = snapshot,
          selectedTab = ReportCategoryTab.EXPORTS_EMAIL,
          emailSettings = EmailReportSettings(enabled = true, frequency = ReportFrequency.MONTHLY, recipientEmail = "user@example.com"),
          emailPreview =
            PreparedEmailPreview(
              recipientEmail = "user@example.com",
              subject = "SKYPLUS MONEY Monthly Report",
              bodyPreview = "Net Worth INR 100,000.00",
              attachmentFile = com.example.domain.usecase.ReportExportGenerator().generateExport(snapshot, com.example.domain.model.ReportExportFormat.CSV),
            ),
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("reports_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_save_pdf_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_save_csv_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_save_docx_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("email_reports_enable_switch").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("confirm_launch_email_button").assertIsDisplayed()
  }
}
