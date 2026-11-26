package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.AccountType
import com.example.domain.model.Budget
import com.example.domain.model.BudgetAlertStatus
import com.example.domain.model.BudgetPeriod
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.BudgetStatus
import com.example.domain.model.BudgetSummary
import com.example.domain.model.FinancialAccount
import com.example.domain.model.Money
import com.example.domain.model.ThemePreference
import com.example.feature.budgets.BudgetsContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The stale budget must never look like a live one: the remaining amount is relabelled and the
 * period-ended note is shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BudgetsStaleDisplayTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val uid = "uid_stale"

  private fun budget(isRecurring: Boolean = true): Budget =
    Budget(
      id = "budget_stale_1",
      userId = uid,
      name = "Groceries",
      category = "Food & Dining",
      amountMinor = 100_00L,
      currencyCode = "INR",
      periodType = BudgetPeriodType.MONTHLY,
      startDate = 1_000L,
      endDate = 2_000L,
      isRecurring = isRecurring,
    )

  private fun status(stale: Boolean): BudgetStatus =
    BudgetStatus(
      budget = budget(),
      allocatedMoney = Money.fromMinorUnits(100_00L, "INR"),
      spentMoney = Money.fromMinorUnits(20_00L, "INR"),
      remainingMoney = Money.fromMinorUnits(80_00L, "INR"),
      percentageConsumed = 20.0,
      overspendingMoney = Money.fromMinorUnits(0L, "INR"),
      alertStatus = if (stale) BudgetAlertStatus.PERIOD_ENDED else BudgetAlertStatus.NORMAL,
      transactionCount = 1,
      isStale = stale,
    )

  private val account =
    FinancialAccount(
      id = "acc_inr",
      userId = uid,
      name = "Primary",
      type = AccountType.BANK,
      currencyCode = "INR",
      initialBalanceMinor = 0L,
      currentBalanceMinor = 0L,
    )

  private fun render(
    status: BudgetStatus,
    summaries: Map<String, BudgetSummary> = emptyMap(),
    periods: List<BudgetPeriod> = emptyList(),
  ) {
    val visibleCurrency = periods.firstOrNull()?.currencyCode ?: account.currencyCode
    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        BudgetsContent(
          budgets = listOf(status),
          summaries = summaries,
          periods = periods,
          accounts = listOf(account.copy(currencyCode = visibleCurrency)),
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun historyPeriod(currencyCode: String, spentMinor: Long, allocatedMinor: Long) =
    BudgetPeriod(
      id = "history_$currencyCode",
      userId = uid,
      budgetId = "budget_history",
      category = "Food & Dining",
      currencyCode = currencyCode,
      periodType = BudgetPeriodType.MONTHLY,
      periodStart = 1_000L,
      periodEnd = 2_000L,
      allocatedMinor = allocatedMinor,
      spentMinor = spentMinor,
      remainingMinor = allocatedMinor - spentMinor,
      alertStatus = BudgetAlertStatus.NORMAL,
      closedAt = 3_000L,
    )

  private fun assertHistoryCurrencyPrecision(currencyCode: String, spentMinor: Long, allocatedMinor: Long) {
    val currencyBudgetStatus =
      status(stale = true).copy(
        budget = budget().copy(currencyCode = currencyCode),
      )
    render(
      currencyBudgetStatus,
      periods = listOf(historyPeriod(currencyCode, spentMinor, allocatedMinor)),
    )

    val expectedSpent =
      com.example.domain.usecase.FormatMoneyUseCase()
        .format(Money.fromMinorUnits(spentMinor, currencyCode))
    val expectedAllocated =
      com.example.domain.usecase.FormatMoneyUseCase()
        .format(Money.fromMinorUnits(allocatedMinor, currencyCode))
    composeTestRule.onNodeWithTag("budgets_list")
      .performScrollToNode(hasTestTag("budget_period_history_$currencyCode"))
    composeTestRule
      .onNodeWithText("Spent $expectedSpent of $expectedAllocated", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun `INR budget history displays two fraction digits`() {
    assertHistoryCurrencyPrecision("INR", 1_234L, 2_000L)
  }

  @Test
  fun `a stale budget relabels the remaining amount and shows the period-ended note`() {
    render(status(stale = true))

    composeTestRule.onNodeWithTag("budget_card_budget_stale_1").assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("budget_remaining_budget_stale_1")
      .assertTextContains("Remaining at close", substring = true)
    composeTestRule.onNodeWithTag("budget_stale_note_budget_stale_1").assertIsDisplayed()
  }

  @Test
  fun `a live budget keeps the normal remaining label`() {
    render(status(stale = false))

    composeTestRule
      .onNodeWithTag("budget_remaining_budget_stale_1")
      .assertTextContains("Remaining:", substring = true)
    composeTestRule.onNodeWithTag("budget_stale_note_budget_stale_1").assertDoesNotExist()
  }
  @Test
  fun `when every budget is stale the screen shows no active summary`() {
    render(status(stale = true))

    composeTestRule.onNodeWithTag("budget_no_active_summary").assertIsDisplayed()
    composeTestRule.onNodeWithTag("budget_summary_card").assertDoesNotExist()
    // The stale card itself is still shown, with its history intact.
    composeTestRule.onNodeWithTag("budget_card_budget_stale_1").assertIsDisplayed()
  }

  @Test
  fun `a live budget still shows the active summary card`() {
    val live = status(stale = false)
    render(
      live,
      summaries =
        mapOf(
          "INR" to
            BudgetSummary(
              currencyCode = "INR",
              totalAllocatedMinor = 100_00L,
              totalSpentMinor = 20_00L,
              totalRemainingMinor = 80_00L,
              averageUtilizationPercent = 20.0,
              activeBudgetCount = 1,
              overspentBudgetCount = 0,
            )
        ),
    )

    composeTestRule.onNodeWithTag("budget_summary_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("budget_no_active_summary").assertDoesNotExist()
  }
}
