package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.AccountType
import com.example.domain.model.Budget
import com.example.domain.model.BudgetAlertStatus
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.BudgetStatus
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

  private fun render(status: BudgetStatus) {
    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        BudgetsContent(
          budgets = listOf(status),
          summaries = emptyMap(),
          accounts = listOf(account),
        )
      }
    }
    composeTestRule.waitForIdle()
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
}
