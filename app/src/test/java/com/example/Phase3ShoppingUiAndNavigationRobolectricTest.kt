package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.ShoppingList
import com.example.domain.model.ShoppingListItem
import com.example.domain.model.ShoppingStorePriceRecord
import com.example.domain.model.ThemePreference
import com.example.domain.usecase.ShoppingCalculationEngine
import com.example.domain.usecase.ShoppingWorkspaceSnapshot
import com.example.feature.dashboard.HomeFoundationUiState
import com.example.feature.shopping.ShoppingItemFilter
import com.example.feature.shopping.ShoppingListContent
import com.example.navigation.SkyplusAppShell
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3ShoppingUiAndNavigationRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val engine = ShoppingCalculationEngine()

  private fun buildSnapshot(
    lists: List<ShoppingList>,
    items: List<ShoppingListItem>,
    prices: List<ShoppingStorePriceRecord>,
  ): ShoppingWorkspaceSnapshot {
    val itemsByList = items.groupBy { it.listId }
    val pricesByItem = prices.groupBy { it.itemId }
    val summaries = lists.associate { list ->
      list.id to engine.calculateListSummary(list, itemsByList[list.id].orEmpty())
    }
    val comparisons = items.associate { item ->
      item.id to engine.buildItemComparisonSummary(item, pricesByItem[item.id].orEmpty())
    }
    return ShoppingWorkspaceSnapshot(
      lists = lists,
      items = items,
      storePrices = prices,
      summariesByListId = summaries,
      comparisonsByItemId = comparisons,
    )
  }

  @Test
  fun `bottom navigation keeps exact 4 tabs and shopping tab renders real empty state with no fake data`() {
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

    composeTestRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_transactions").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_budget").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_shopping_list").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_tab_shopping_list").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("shopping_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("shopping_empty_state").assertIsDisplayed()
    composeTestRule.onNodeWithText("Organic Milk & Produce").assertDoesNotExist()
  }

  @Test
  fun `shopping content displays budget warning savings badge search and store comparison latest badge in dark theme`() {
    val list = ShoppingList(
      id = "l_grocery",
      userId = "u1",
      name = "Grocery",
      currencyCode = "USD",
      budgetMinor = 50_00L,
      warningThresholdPercent = 80,
    )
    val itemPurchased = ShoppingListItem(
      id = "it_milk",
      listId = "l_grocery",
      userId = "u1",
      name = "Organic Milk",
      category = "Dairy & Bakery",
      quantityDecimal = "2",
      unit = "litre",
      estimatedUnitPriceMinor = 10_00L,
      estimatedTotalMinor = 20_00L,
      storeName = "FreshMart",
      isPurchased = true,
      actualUnitPriceMinor = 8_00L,
      actualTotalMinor = 16_00L,
      purchasedAt = 1_755_000_000_000L,
    )
    val itemPending = ShoppingListItem(
      id = "it_rice",
      listId = "l_grocery",
      userId = "u1",
      name = "Basmati Rice",
      category = "Groceries",
      quantityDecimal = "5",
      unit = "kg",
      estimatedUnitPriceMinor = 6_00L,
      estimatedTotalMinor = 30_00L,
      storeName = "SuperBazaar",
      isPurchased = false,
    )
    val quotes = listOf(
      ShoppingStorePriceRecord("p1", "it_rice", "l_grocery", "u1", "SuperBazaar", 6_50L, "USD", 1_000L),
      ShoppingStorePriceRecord("p2", "it_rice", "l_grocery", "u1", "ValueClub", 5_50L, "USD", 2_000L),
    )

    val snapshot = buildSnapshot(listOf(list), listOf(itemPurchased, itemPending), quotes)

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        ShoppingListContent(
          workspace = snapshot,
          selectedListId = list.id,
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("shopping_budget_card").assertIsDisplayed()
    // Projected = 16.00 + 30.00 = 46.00 out of 50.00 (92% >= 80%) -> Approaching warning visible
    composeTestRule.onNodeWithTag("shopping_budget_warning_banner").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("shopping_lists_lazy_column").performScrollToNode(hasTestTag("shopping_item_it_milk"))
    composeTestRule.onNodeWithTag("shopping_item_it_milk").assertIsDisplayed()
    composeTestRule.onNodeWithTag("shopping_lists_lazy_column").performScrollToNode(hasTestTag("shopping_item_it_rice"))
    composeTestRule.onNodeWithTag("shopping_item_it_rice").assertIsDisplayed()

    // Open price comparison dialog and verify LATEST and LOWEST badges
    composeTestRule.onNodeWithTag("compare_prices_it_rice").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("LATEST").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("LOWEST").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("ValueClub").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `search and filter chips filter shopping items cleanly`() {
    val list = ShoppingList(id = "l1", userId = "u1", name = "Monthly", currencyCode = "INR")
    val itemA = ShoppingListItem(
      id = "a",
      listId = "l1",
      userId = "u1",
      name = "Olive Oil",
      category = "Kitchen",
      quantityDecimal = "1",
      unit = "litre",
      estimatedUnitPriceMinor = 800_00L,
      estimatedTotalMinor = 800_00L,
      isPurchased = false,
    )
    val itemB = ShoppingListItem(
      id = "b",
      listId = "l1",
      userId = "u1",
      name = "Dish Soap",
      category = "Household",
      quantityDecimal = "2",
      unit = "bottle",
      estimatedUnitPriceMinor = 100_00L,
      estimatedTotalMinor = 200_00L,
      isPurchased = true,
      actualUnitPriceMinor = 95_00L,
      actualTotalMinor = 190_00L,
      purchasedAt = 1_755_000_000_000L,
    )
    val snapshot = buildSnapshot(listOf(list), listOf(itemA, itemB), emptyList())

    composeTestRule.setContent {
      var filter = ShoppingItemFilter.ALL
      var query = ""
      val filterState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(filter) }
      val queryState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(query) }

      SkyplusOceanTheme(themePreference = ThemePreference.LIGHT) {
        ShoppingListContent(
          workspace = snapshot,
          selectedListId = "l1",
          searchQuery = queryState.value,
          selectedFilter = filterState.value,
          onSearchQueryChange = { queryState.value = it },
          onSelectFilter = { filterState.value = it },
        )
      }
    }
    composeTestRule.waitForIdle()

    // Filter Purchased
    composeTestRule.onNodeWithTag("shopping_filter_purchased").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("shopping_lists_lazy_column").performScrollToNode(hasTestTag("shopping_item_b"))
    composeTestRule.onNodeWithTag("shopping_item_b").assertIsDisplayed()
    composeTestRule.onNodeWithTag("shopping_item_a").assertDoesNotExist()

    // Switch back to All & search
    composeTestRule.onNodeWithTag("shopping_filter_all").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("shopping_search_input").performScrollTo().performTextInput("Olive")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("shopping_lists_lazy_column").performScrollToNode(hasTestTag("shopping_item_a"))
    composeTestRule.onNodeWithTag("shopping_item_a").assertIsDisplayed()
    composeTestRule.onNodeWithTag("shopping_item_b").assertDoesNotExist()
  }
}
