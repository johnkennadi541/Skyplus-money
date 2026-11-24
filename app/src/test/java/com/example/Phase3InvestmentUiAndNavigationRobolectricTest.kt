package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.InvestmentPriceHistory
import com.example.domain.model.InvestmentTransaction
import com.example.domain.model.InvestmentTransactionType
import com.example.domain.model.MarketSyncSettings
import com.example.domain.model.PriceSourceType
import com.example.domain.model.ThemePreference
import com.example.domain.usecase.InvestmentCalculationEngine
import com.example.feature.dashboard.HomeFoundationUiState
import com.example.feature.investments.InvestmentDetailContent
import com.example.feature.investments.InvestmentsContent
import com.example.navigation.SkyplusAppShell
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3InvestmentUiAndNavigationRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val engine = InvestmentCalculationEngine()

  @Test
  fun `home quick action and profile entry navigate to investments while preserving 4-tab bottom nav`() {
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

    // Exact 4 bottom nav tabs unchanged
    composeTestRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_transactions").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_portfolio").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_tools").assertIsDisplayed()

    // Open Investments from Home Quick Action
    composeTestRule.onNodeWithTag("home_investments_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("investments_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("investments_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_menu_button").assertIsDisplayed()

    // Open Investments from Profile menu
    composeTestRule.onNodeWithTag("profile_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_investments_item").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("investments_screen").assertIsDisplayed()
  }

  @Test
  fun `investments dashboard renders allocation chart authentic growth empty state and stale warning in light theme`() {
    val now = 1_755_000_000_000L
    val tenDaysAgo = now - (10L * 86_400_000L)
    val holdings =
      listOf(
        InvestmentHolding(
          id = "h_1",
          userId = "u1",
          name = "Tata Consultancy Services",
          category = InvestmentCategory.STOCKS,
          symbolOrTicker = "TCS",
          currencyCode = "INR",
          purchaseDate = tenDaysAgo,
          quantityDecimal = "10",
          totalInvestedMinor = 35000_00L,
          totalBuyCostMinor = 35000_00L,
          realizedPnlMinor = 1500_00L,
          currentMarketPriceDecimal = "4000",
          currentValuationMinor = 40000_00L,
          lastPriceUpdateDate = tenDaysAgo,
        ),
        InvestmentHolding(
          id = "h_2",
          userId = "u1",
          name = "HDFC Corporate FD",
          category = InvestmentCategory.FIXED_DEPOSITS,
          currencyCode = "INR",
          purchaseDate = tenDaysAgo,
          quantityDecimal = "1",
          totalInvestedMinor = 50000_00L,
          totalBuyCostMinor = 50000_00L,
          currentMarketPriceDecimal = "50000",
          currentValuationMinor = 50000_00L,
          lastPriceUpdateDate = 0L,
          lastPriceProviderLabel = "Awaiting current price",
        ),
      )

    val history =
      listOf(
        InvestmentPriceHistory(
          id = "p1",
          holdingId = "h_1",
          userId = "u1",
          priceDate = tenDaysAgo,
          unitPriceDecimal = "3500",
          valuationMinor = 35000_00L,
          totalInvestedMinor = 35000_00L,
          realizedPnlMinor = 0L,
          currencyCode = "INR",
          source = PriceSourceType.MANUAL,
          providerLabel = "Manual",
        ),
        InvestmentPriceHistory(
          id = "p2",
          holdingId = "h_1",
          userId = "u1",
          priceDate = now,
          unitPriceDecimal = "4000",
          valuationMinor = 40000_00L,
          totalInvestedMinor = 35000_00L,
          realizedPnlMinor = 1500_00L,
          currencyCode = "INR",
          source = PriceSourceType.MANUAL,
          providerLabel = "Manual",
        ),
      )

    val perfs = holdings.map { engine.evaluateHoldingPerformance(it, now) }
    val summaries = engine.calculateCurrencySummaries(holdings, history, now)

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.LIGHT) {
        InvestmentsContent(
          performances = perfs,
          summaries = summaries,
          accounts = emptyList(),
          syncSettings = MarketSyncSettings(autoUpdateConsentEnabled = false),
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("portfolio_summary_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Estimated Valuation (INR)").assertIsDisplayed()
    composeTestRule.onNodeWithTag("awaiting_price_summary_note").assertIsDisplayed()
    composeTestRule.onNodeWithTag("stale_price_warning_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("investments_list").performScrollToNode(hasTestTag("asset_allocation_chart_card"))
    composeTestRule.onNodeWithTag("asset_allocation_canvas").assertIsDisplayed()
    composeTestRule.onNodeWithTag("investments_list").performScrollToNode(hasTestTag("portfolio_growth_chart_card"))
    composeTestRule.onNodeWithTag("portfolio_growth_empty_state").assertIsDisplayed()
    composeTestRule.onNodeWithTag("investments_list").performScrollToNode(hasTestTag("holding_card_h_1"))
    composeTestRule.onNodeWithText("Tata Consultancy Services").assertIsDisplayed()

    // Open Market Sync settings dialog and verify opt-in switch defaults OFF and provider policy shown
    composeTestRule.onNodeWithTag("open_market_sync_settings_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("auto_sync_consent_switch").assertIsOff()
    composeTestRule.onNodeWithText("MFapi.in third-party API • AMFI-sourced EOD NAV").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `growth chart shows authentic empty state when fewer than two dated snapshots exist`() {
    val now = 1_755_000_000_000L
    val holding =
      InvestmentHolding(
        id = "h_single",
        userId = "u1",
        name = "Gold ETF",
        category = InvestmentCategory.GOLD,
        currencyCode = "USD",
        purchaseDate = now,
        quantityDecimal = "5",
        totalInvestedMinor = 1000_00L,
        totalBuyCostMinor = 1000_00L,
        currentMarketPriceDecimal = "210",
        currentValuationMinor = 1050_00L,
        lastPriceUpdateDate = now,
      )
    val perfs = listOf(engine.evaluateHoldingPerformance(holding, now))
    val summaries = engine.calculateCurrencySummaries(listOf(holding), emptyList(), now)

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        InvestmentsContent(
          performances = perfs,
          summaries = summaries,
          accounts = emptyList(),
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("investments_list").performScrollToNode(hasTestTag("portfolio_growth_empty_state"))
    composeTestRule.onNodeWithTag("portfolio_growth_empty_state").assertIsDisplayed()
  }

  @Test
  fun `manual only holding shows awaiting current price and does not prefill trade price`() {
    val now = 1_755_000_000_000L
    val stock = InvestmentHolding(
      id = "stock_waiting",
      userId = "u1",
      name = "Manual-only Stock",
      category = InvestmentCategory.STOCKS,
      symbolOrTicker = "TEST",
      currencyCode = "INR",
      purchaseDate = now,
      quantityDecimal = "1",
      totalInvestedMinor = 100_00L,
      totalBuyCostMinor = 100_00L,
      currentMarketPriceDecimal = "100",
      currentValuationMinor = 100_00L,
      lastPriceUpdateDate = 0L,
      lastPriceProviderLabel = "Awaiting current price",
    )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.LIGHT) {
        InvestmentDetailContent(
          holding = stock,
          performance = engine.evaluateHoldingPerformance(stock, now),
          transactions = emptyList(),
          priceHistory = emptyList(),
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Awaiting current price").assertIsDisplayed()
    composeTestRule.onNodeWithTag("investment_detail_list").performScrollToNode(hasTestTag("detail_buy_more_button"))
    composeTestRule.onNodeWithTag("detail_buy_more_button").performClick()
    composeTestRule.waitForIdle()
    // Inspect the editable-text semantic value directly; TextField's visible label is separate.
    composeTestRule.onNodeWithTag("trade_price_input")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    composeTestRule.onNodeWithTag("cancel_trade_dialog").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("detail_update_price_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("manual_price_input")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
  }

  @Test
  fun `investment detail screen renders metrics transaction history and auto opt-in toggle in dark theme`() {
    val now = 1_755_000_000_000L
    val mf =
      InvestmentHolding(
        id = "mf_1",
        userId = "u1",
        name = "SBI Bluechip Fund",
        category = InvestmentCategory.MUTUAL_FUNDS,
        symbolOrTicker = "119598",
        currencyCode = "INR",
        purchaseDate = now,
        quantityDecimal = "100",
        totalInvestedMinor = 9000_00L,
        totalBuyCostMinor = 9000_00L,
        realizedPnlMinor = 500_00L,
        currentMarketPriceDecimal = "95.00",
        currentValuationMinor = 9500_00L,
        lastPriceUpdateDate = now,
        autoPriceUpdateEnabled = false,
      )
    val tx =
      InvestmentTransaction(
        id = "tx_1",
        holdingId = "mf_1",
        userId = "u1",
        type = InvestmentTransactionType.BUY,
        transactionDate = now,
        quantityDecimal = "100",
        unitPriceDecimal = "90.00",
        grossAmountMinor = 9000_00L,
        chargesMinor = 0L,
        netAmountMinor = 9000_00L,
      )
    val history =
      InvestmentPriceHistory(
        id = "ph_1",
        holdingId = "mf_1",
        userId = "u1",
        priceDate = now,
        unitPriceDecimal = "95.00",
        valuationMinor = 9500_00L,
        totalInvestedMinor = 9000_00L,
        realizedPnlMinor = 500_00L,
        currencyCode = "INR",
        source = PriceSourceType.MANUAL,
        providerLabel = "Manual",
      )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        InvestmentDetailContent(
          holding = mf,
          performance = engine.evaluateHoldingPerformance(mf, now),
          transactions = listOf(tx),
          priceHistory = listOf(history),
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("investment_detail_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("holding_detail_summary_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("investment_detail_list").performScrollToNode(hasTestTag("holding_auto_price_switch"))
    composeTestRule.onNodeWithTag("holding_auto_price_switch").assertIsOff()
    composeTestRule.onNodeWithTag("investment_detail_list").performScrollToNode(hasTestTag("investment_tx_tx_1"))
    composeTestRule.onNodeWithTag("investment_tx_tx_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("investment_detail_list").performScrollToNode(hasTestTag("price_history_ph_1"))
    composeTestRule.onNodeWithTag("price_history_ph_1").assertIsDisplayed()
  }
}
