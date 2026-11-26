package com.example

import com.example.domain.model.ShoppingBudgetStatus
import com.example.domain.model.ShoppingList
import com.example.domain.model.ShoppingListItem
import com.example.domain.model.ShoppingStorePriceRecord
import com.example.domain.usecase.ShoppingCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ShoppingCalculationEngineTest {

  private val engine = ShoppingCalculationEngine()

  @Test
  fun `computeLineTotalMinor calculates fractional decimal quantities accurately with HALF_UP`() {
    // 1.75 kg * $4.33/kg = $7.5775 -> $7.58 (758 minor units)
    val totalUsd = engine.computeLineTotalMinor(
      quantity = BigDecimal("1.75"),
      unitPriceMinor = 433L,
      currencyCode = "USD",
    )
    assertEquals(758L, totalUsd)

    // 2.5 kg * 500 JPY (0 decimals) = 1250 JPY
    val totalJpy = engine.computeLineTotalMinor(
      quantityDecimal = "2.5",
      unitPriceMinor = 500L,
      currencyCode = "JPY",
    )
    assertEquals(1250L, totalJpy)
  }

  @Test
  fun `parsePositiveQuantity and parseNonNegativeMoneyMinor reject invalid and negative inputs`() {
    val zeroQty = runCatching { engine.parsePositiveQuantity("0") }
    assertTrue(zeroQty.isFailure)

    val negQty = runCatching { engine.parsePositiveQuantity("-2") }
    assertTrue(negQty.isFailure)

    val badQty = runCatching { engine.parsePositiveQuantity("abc") }
    assertTrue(badQty.isFailure)

    val negPrice = runCatching { engine.parseNonNegativeMoneyMinor("-10.00", "USD") }
    assertTrue(negPrice.isFailure)

    val zeroBudget = runCatching { engine.parsePositiveMoneyMinor("0", "INR", "Budget") }
    assertTrue(zeroBudget.isFailure)
  }

  @Test
  fun `calculateListSummary tracks estimated actual savings overspend and budget thresholds`() {
    val list = ShoppingList(
      id = "list_1",
      userId = "u1",
      name = "Monthly Groceries",
      currencyCode = "INR",
      budgetMinor = 1000_00L,
      warningThresholdPercent = 80,
    )

    val items = listOf(
      // Purchased for less than estimate: Est 300.00, Actual 250.00 => Savings +50.00
      ShoppingListItem(
        id = "i1",
        listId = "list_1",
        userId = "u1",
        name = "Basmati Rice",
        category = "Groceries",
        quantityDecimal = "5",
        unit = "kg",
        estimatedUnitPriceMinor = 60_00L,
        estimatedTotalMinor = 300_00L,
        isPurchased = true,
        actualUnitPriceMinor = 50_00L,
        actualTotalMinor = 250_00L,
        purchasedAt = 1000L,
      ),
      // Remaining estimated: 600.00 => Projected = 250 + 600 = 850.00 (85% >= 80% warning threshold)
      ShoppingListItem(
        id = "i2",
        listId = "list_1",
        userId = "u1",
        name = "Cooking Oil",
        category = "Kitchen",
        quantityDecimal = "3",
        unit = "litre",
        estimatedUnitPriceMinor = 200_00L,
        estimatedTotalMinor = 600_00L,
        isPurchased = false,
      ),
    )

    val summary = engine.calculateListSummary(list, items)
    assertEquals(2, summary.totalItemCount)
    assertEquals(1, summary.purchasedItemCount)
    assertEquals(1, summary.remainingItemCount)
    assertEquals(900_00L, summary.estimatedTotalMinor)
    assertEquals(600_00L, summary.remainingEstimatedMinor)
    assertEquals(250_00L, summary.actualSpentMinor)
    assertEquals(300_00L, summary.estimatedOfPurchasedMinor)
    assertEquals(50_00L, summary.savingsOrOverspendMinor)
    assertEquals(850_00L, summary.projectedSpendMinor)
    assertEquals(750_00L, summary.remainingBudgetMinor)
    assertEquals(150_00L, summary.remainingBudgetAfterProjectedMinor)
    assertEquals(85, summary.budgetUsagePercent)
    assertEquals(ShoppingBudgetStatus.APPROACHING_BUDGET, summary.budgetStatus)
  }

  @Test
  fun `calculateListSummary flags EXCEEDED_BUDGET when actual or projected spend exceeds cap`() {
    val list = ShoppingList(
      id = "list_2",
      userId = "u1",
      name = "Weekend",
      currencyCode = "USD",
      budgetMinor = 50_00L,
      warningThresholdPercent = 80,
    )
    val item = ShoppingListItem(
      id = "i3",
      listId = "list_2",
      userId = "u1",
      name = "Coffee Beans",
      category = "Groceries",
      quantityDecimal = "2",
      unit = "packet",
      estimatedUnitPriceMinor = 20_00L,
      estimatedTotalMinor = 40_00L,
      isPurchased = true,
      actualUnitPriceMinor = 27_50L,
      actualTotalMinor = 55_00L,
      purchasedAt = 2000L,
    )

    val summary = engine.calculateListSummary(list, listOf(item))
    assertEquals(-15_00L, summary.savingsOrOverspendMinor)
    assertEquals(-5_00L, summary.remainingBudgetMinor)
    assertEquals(ShoppingBudgetStatus.EXCEEDED_BUDGET, summary.budgetStatus)
    assertEquals(-15_00L, engine.calculateSavingsOrOverspendMinor(item))
  }

  @Test
  fun `buildItemComparisonSummary identifies latest by recordedAt and lowest by unitPrice`() {
    val item = ShoppingListItem(
      id = "i_comp",
      listId = "l1",
      userId = "u1",
      name = "Olive Oil",
      category = "Kitchen",
      quantityDecimal = "1",
      unit = "litre",
      estimatedUnitPriceMinor = 900_00L,
      estimatedTotalMinor = 900_00L,
      isPurchased = false,
    )
    assertNull(engine.calculateSavingsOrOverspendMinor(item))

    val quotes = listOf(
      ShoppingStorePriceRecord("q1", "i_comp", "l1", "u1", "Store A", 850_00L, "INR", recordedAt = 1000L),
      ShoppingStorePriceRecord("q2", "i_comp", "l1", "u1", "Store B", 790_00L, "INR", recordedAt = 2000L),
      ShoppingStorePriceRecord("q3", "i_comp", "l1", "u1", "Store C", 820_00L, "INR", recordedAt = 3000L),
    )

    val comparison = engine.buildItemComparisonSummary(item, quotes)
    assertEquals("q3", comparison.latestStorePrice?.id)
    assertEquals("Store C", comparison.latestStorePrice?.storeName)
    assertEquals("q2", comparison.lowestStorePrice?.id)
    assertEquals("Store B", comparison.lowestStorePrice?.storeName)
    assertEquals(listOf("q3", "q2", "q1"), comparison.storePrices.map { it.id })
  }
}
