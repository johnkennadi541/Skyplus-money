package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.AccountType
import com.example.domain.model.AutoSavedTransactionNotice
import com.example.domain.model.DetectionConfidence
import com.example.domain.model.DetectionEventStatus
import com.example.domain.model.FinancialAccount
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.ThemePreference
import com.example.domain.model.TransactionDetectionEvent
import com.example.domain.model.TransactionType
import com.example.feature.accounts.AccountsContent
import com.example.feature.dashboard.HomeUiState
import com.example.feature.dashboard.HomeScreen
import com.example.feature.smartdetection.SmsPolicyStatusScreen
import com.example.feature.smartdetection.TransactionCorrectionDialog
import com.example.feature.transactions.TransactionsContent
import com.example.feature.transactions.TransactionsFilterState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2UiRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `accounts content renders empty state, account cards, and opens add account dialog`() {
    val sampleAccount =
      FinancialAccount(
        id = "acc_ui_1",
        userId = "uid_1",
        name = "Kuwait Salary",
        type = AccountType.BANK,
        currencyCode = "KWD",
        initialBalanceMinor = 500_000L,
        currentBalanceMinor = 500_000L,
      )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        AccountsContent(accounts = listOf(sampleAccount))
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("accounts_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("account_item_acc_ui_1").assertIsDisplayed()

    composeTestRule.onNodeWithTag("add_account_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("account_name_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("currency_chip_KWD").assertIsDisplayed()
    composeTestRule.onNodeWithTag("save_account_button").assertIsDisplayed()
  }

  @Test
  fun `transactions content opens add transaction sheet with expense income transfer tabs`() {
    val acc =
      FinancialAccount(
        id = "acc_tx_1",
        userId = "uid_1",
        name = "HDFC Bank",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 100_000L,
        currentBalanceMinor = 100_000L,
      )
    val txn =
      FinancialTransaction(
        id = "txn_ui_1",
        userId = "uid_1",
        accountId = "acc_tx_1",
        type = TransactionType.EXPENSE,
        amountMinor = 25_000L,
        currencyCode = "INR",
        category = "Food & Dining",
        merchantOrPayee = "Swiggy",
      )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        TransactionsContent(
          transactions = listOf(txn),
          accounts = listOf(acc),
          filterState = TransactionsFilterState(),
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("transactions_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("transactions_search_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("transaction_item_txn_ui_1").assertIsDisplayed()

    composeTestRule.onNodeWithTag("add_transaction_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("add_transaction_bottom_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tab_expense").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tab_income").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tab_transfer").assertIsDisplayed()
    composeTestRule.onNodeWithTag("transaction_amount_input").assertIsDisplayed()
  }

  @Test
  fun `home screen displays auto-saved banner and phase 2 action shortcuts`() {
    var reviewClicked = false
    val notice =
      AutoSavedTransactionNotice(
        eventId = "ev_1",
        transactionId = "tx_1",
        accountName = "HDFC Bank",
        merchantOrPayee = "Amazon",
        amountMinor = 129_900L,
        currencyCode = "INR",
        type = TransactionType.EXPENSE,
        sourceApplication = "com.snapwork.hdfc",
      )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        HomeScreen(
          uiState = HomeUiState(),
          onOpenAuthReadiness = {},
          autoSavedNotice = notice,
          onOpenReviewQueue = { reviewClicked = true },
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("auto_saved_notice_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_accounts_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_smart_detection_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_review_queue_button").performClick()
    assertTrue(reviewClicked)
  }

  @Test
  fun `sms policy screen and transaction correction dialog render accurately`() {
    var confirmed = false
    val event =
      TransactionDetectionEvent(
        id = "ev_corr",
        userId = "uid_1",
        rawPackageName = "com.phonepe.app",
        senderOrTitle = "PhonePe",
        detectedAmountMinor = 45_000L,
        detectedCurrency = "INR",
        detectedType = TransactionType.PENDING,
        detectedMerchant = "Uber",
        confidence = 0.65f,
        confidenceLevel = DetectionConfidence.MEDIUM,
        dedupFingerprint = "fp_test",
        status = DetectionEventStatus.IN_REVIEW,
      )
    val acc =
      FinancialAccount(
        id = "acc_c",
        userId = "uid_1",
        name = "Primary Bank",
        type = AccountType.BANK,
        currencyCode = "INR",
        initialBalanceMinor = 500_000L,
        currentBalanceMinor = 500_000L,
      )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        SmsPolicyStatusScreen(onOpenNotificationSetup = {}, onNavigateBack = {})
        TransactionCorrectionDialog(
          event = event,
          accounts = listOf(acc),
          onDismiss = {},
          onConfirm = { _, _, _, _, _ -> confirmed = true },
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("sms_policy_status_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("correction_amount_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("confirm_correction_button").performClick()
    assertTrue(confirmed)
  }
}
