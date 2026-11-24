package com.example

import com.example.domain.model.ShoppingListItem
import com.example.domain.model.ShoppingPantryItem
import com.example.domain.usecase.ShoppingInsightsEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingInsightsEngineTest {
  @Test fun analyticsNeverCombinesDifferentCurrencies() {
    val items = listOf(
      ShoppingListItem(listId = "inr", userId = "u", name = "Rice", category = "Groceries", quantityDecimal = "1", unit = "kg", estimatedUnitPriceMinor = 100, estimatedTotalMinor = 100, isPurchased = true, actualUnitPriceMinor = 90, actualTotalMinor = 90, purchasedAt = 1_800_000_000_000L),
      ShoppingListItem(listId = "kwd", userId = "u", name = "Milk", category = "Dairy", quantityDecimal = "1", unit = "litre", estimatedUnitPriceMinor = 500, estimatedTotalMinor = 500, isPurchased = true, actualUnitPriceMinor = 450, actualTotalMinor = 450, purchasedAt = 1_800_000_000_000L),
    )
    val result = ShoppingInsightsEngine.analyze(items, mapOf("inr" to "INR", "kwd" to "KWD"))
    assertEquals(2, result.totalsByCurrency.size)
    assertEquals(90L, result.totalsByCurrency.single { it.currencyCode == "INR" }.actualSpentMinor)
    assertEquals(450L, result.totalsByCurrency.single { it.currencyCode == "KWD" }.actualSpentMinor)
  }

  @Test fun restockSuggestionsRespectTrackedStockAndRecentPurchaseWindow() {
    val now = 1_800_000_000_000L
    val items = listOf(
      ShoppingListItem(listId = "l", userId = "u", name = "Rice", category = "Groceries", quantityDecimal = "2", unit = "kg", estimatedUnitPriceMinor = 0, estimatedTotalMinor = 0, isPurchased = true, actualUnitPriceMinor = 0, actualTotalMinor = 0, purchasedAt = now - 2 * 86_400_000L),
      ShoppingListItem(listId = "l", userId = "u", name = "Oil", category = "Groceries", quantityDecimal = "1", unit = "litre", estimatedUnitPriceMinor = 0, estimatedTotalMinor = 0, isPurchased = true, actualUnitPriceMinor = 0, actualTotalMinor = 0, purchasedAt = now - 100 * 86_400_000L),
    )
    val pantry = listOf(ShoppingPantryItem(userId = "u", name = "Rice", quantityDecimal = "0.2", unit = "kg", lowStockThresholdDecimal = "0.5"))
    val result = ShoppingInsightsEngine.suggestRestocks(items, pantry, now)
    assertEquals(1, result.size)
    assertEquals("Rice", result.single().itemName)
  }
}
