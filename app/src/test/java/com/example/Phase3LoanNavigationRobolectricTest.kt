package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.ThemePreference
import com.example.feature.dashboard.HomeFoundationUiState
import com.example.navigation.SkyplusAppShell
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3LoanNavigationRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `home quick actions open emi calculator and loans without altering 4-tab bottom nav`() {
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

    // Verify exact 4 bottom navigation tabs exist
    composeTestRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_transactions").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_portfolio").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_tools").assertIsDisplayed()

    // Scroll to Quick Actions and open EMI Calculator directly
    composeTestRule.onNodeWithTag("home_emi_calculator_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("emi_calculator_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("emi_calculator_list").performScrollToNode(hasTestTag("emi_results_card"))
    composeTestRule.onNodeWithTag("emi_results_card").assertIsDisplayed()

    // Explicit Save as Loan opens Loans screen (never auto-saves)
    composeTestRule.onNodeWithTag("emi_calculator_list").performScrollToNode(hasTestTag("emi_save_as_loan_button"))
    composeTestRule.onNodeWithTag("emi_save_as_loan_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("loans_screen").assertIsDisplayed()

    // Navigate back twice to return to Home and verify Loans shortcut works too
    composeTestRule.onNodeWithTag("loans_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("emi_back_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("home_loans_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("loans_screen").assertIsDisplayed()
    composeTestRule.onNodeWithText("No Loans Tracked").assertIsDisplayed()
  }

  @Test
  fun `loan detail displays active loan metrics and payment history in dark theme`() {
    val sampleLoan =
      com.example.domain.model.Loan(
        id = "loan_ui_1",
        userId = "uid_ui",
        name = "Home Mortgage",
        lenderName = "HDFC Bank",
        loanType = com.example.domain.model.LoanType.HOME,
        principalMinor = 500_000_00L,
        currencyCode = "INR",
        annualInterestRatePercent = "8.5",
        tenureMonths = 240,
        startDate = 1_700_000_000_000L,
        nextDueDate = 1_800_000_000_000L,
        monthlyEmiMinor = 4_339_12L,
        totalPayableMinor = 1_041_388_80L,
        totalInterestMinor = 541_388_80L,
        amountPaidMinor = 4_339_12L,
        outstandingPrincipalMinor = 499_202_55L,
        status = com.example.domain.model.LoanStatus.ACTIVE,
      )
    val samplePayment =
      com.example.domain.model.LoanPayment(
        id = "pay_ui_1",
        loanId = "loan_ui_1",
        userId = "uid_ui",
        paymentDate = 1_702_592_000_000L,
        amountMinor = 4_339_12L,
        principalComponentMinor = 797_45L,
        interestComponentMinor = 3_541_67L,
        remainingBalanceMinor = 499_202_55L,
        currencyCode = "INR",
        status = com.example.domain.model.LoanPaymentStatus.COMPLETED,
        idempotencyKey = "k1",
      )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        com.example.feature.loans.LoanDetailContent(
          loan = sampleLoan,
          payments = listOf(samplePayment),
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("loan_detail_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("loan_overview_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("loan_outstanding_balance").assertIsDisplayed()
    composeTestRule.onNodeWithTag("loan_detail_list").performScrollToNode(hasTestTag("record_payment_button"))
    composeTestRule.onNodeWithTag("record_payment_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("loan_detail_list").performScrollToNode(hasTestTag("loan_payment_item_pay_ui_1"))
    composeTestRule.onNodeWithTag("loan_payment_item_pay_ui_1").assertIsDisplayed()
  }
}
