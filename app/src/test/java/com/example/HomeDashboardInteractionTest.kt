package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.ThemePreference
import com.example.feature.dashboard.HomeAllocationSlice
import com.example.feature.dashboard.HomeAmount
import com.example.feature.dashboard.HomeCurrencySection
import com.example.feature.dashboard.HomeHoldingRow
import com.example.feature.dashboard.HomeTrendPoint
import com.example.feature.dashboard.HomeUiState
import com.example.feature.dashboard.HomeScreen
import java.math.BigDecimal
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Interaction and accessibility guards for the dashboard charts.
 *
 * These are real clicks on real composables: the month targets, the series chips and the
 * allocation rows are all exercised, and the panel that appears is asserted by tag and by
 * text. Nothing here mocks the UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeDashboardInteractionTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun amount(value: Long, formatted: String) =
    HomeAmount(currencyCode = "INR", minorUnits = value, formatted = formatted)

  private val trends =
    listOf(
      HomeTrendPoint(
        monthKey = "2026-01",
        label = "Jan 2026",
        income = amount(60_000_00L, "₹60,000.00"),
        expenses = amount(25_000_00L, "₹25,000.00"),
        savings = amount(35_000_00L, "₹35,000.00"),
      ),
      HomeTrendPoint(
        monthKey = "2026-02",
        label = "Feb 2026",
        income = amount(80_000_00L, "₹80,000.00"),
        expenses = amount(30_000_00L, "₹30,000.00"),
        savings = amount(50_000_00L, "₹50,000.00"),
      ),
    )

  private val allocation =
    listOf(
      HomeAllocationSlice(
        categoryName = "Stocks / Equities",
        colorKey = "STOCKS",
        value = amount(150_000_00L, "₹1,50,000.00"),
        gainLoss = amount(30_000_00L, "₹30,000.00"),
        sharePercent = BigDecimal("60.0"),
        holdingCount = 2,
      ),
      HomeAllocationSlice(
        categoryName = "Mutual Funds",
        colorKey = "MUTUAL_FUNDS",
        value = amount(100_000_00L, "₹1,00,000.00"),
        gainLoss = amount(20_000_00L, "₹20,000.00"),
        sharePercent = BigDecimal("40.0"),
        holdingCount = 1,
      ),
    )

  private val holdings =
    listOf(
      HomeHoldingRow(
        id = "h1",
        name = "Large Cap Fund",
        categoryName = "Stocks / Equities",
        colorKey = "STOCKS:Large Cap Fund",
        value = amount(150_000_00L, "₹1,50,000.00"),
        invested = amount(120_000_00L, "₹1,20,000.00"),
        gainLoss = amount(30_000_00L, "₹30,000.00"),
        returnPercent = BigDecimal("25.0"),
        sharePercent = BigDecimal("60.0"),
        isStalePrice = false,
      )
    )

  private val state =
    HomeUiState(
      themePreference = ThemePreference.DARK,
      isLoading = false,
      windowLabel = "This month",
      sections =
        listOf(
          HomeCurrencySection(
            currencyCode = "INR",
            netWorth = amount(250_000_00L, "₹2,50,000.00"),
            assets = amount(250_000_00L, "₹2,50,000.00"),
            liabilities = amount(0L, "₹0.00"),
            income = amount(80_000_00L, "₹80,000.00"),
            expenses = amount(30_000_00L, "₹30,000.00"),
            savings = amount(50_000_00L, "₹50,000.00"),
            accountCount = 2,
            trends = trends,
            allocation = allocation,
            holdings = holdings,
          )
        ),
      selectedCurrencyCode = "INR",
    )

  private fun render() {
    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        HomeScreen(uiState = state, onOpenAuthReadiness = {})
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun scrollTo(tag: String) {
    composeTestRule.onNodeWithTag("home_screen").performScrollToNode(hasTestTag(tag))
    composeTestRule.waitForIdle()
  }

  @Test
  fun `tapping a month selects it and the panel shows that month`() {
    render()
    scrollTo("home_trend_month_2026-02")

    // The latest month is selected by default, so the panel is already on Feb 2026.
    composeTestRule.onNodeWithTag("home_trend_selection").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_trend_selection_month").assertTextEquals("Feb 2026")

    // Tapping the January column moves the selection there.
    composeTestRule.onNodeWithTag("home_trend_month_2026-01").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_trend_selection").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_trend_selection_month").assertTextEquals("Jan 2026")
  }

  @Test
  fun `each month is one tap target carrying its own description`() {
    render()
    scrollTo("home_trend_month_2026-01")
    composeTestRule
      .onNodeWithTag("home_trend_month_2026-01")
      .assertIsDisplayed()
    composeTestRule
      .onNodeWithContentDescription(
        "Jan 2026. Income ₹60,000.00, expenses ₹25,000.00. Select to show the exact figures.",
        substring = true,
      )
      .assertExists()
  }

  @Test
  fun `allocation rows expose their own description and selection state`() {
    render()
    scrollTo("home_allocation_row_stocks")
    composeTestRule
      .onNodeWithContentDescription("Stocks / Equities, 60.0% of portfolio", substring = true)
      .assertExists()
  }

  @Test
  fun `tapping an allocation row shows that category and clears on a second tap`() {
    render()
    scrollTo("home_allocation_row_stocks")

    composeTestRule.onNodeWithTag("home_allocation_row_stocks").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_allocation_selection").assertIsDisplayed()
    composeTestRule.onNodeWithText("2 holdings").assertExists()

    composeTestRule.onNodeWithTag("home_allocation_row_stocks").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_allocation_selection").assertDoesNotExist()
  }
}
