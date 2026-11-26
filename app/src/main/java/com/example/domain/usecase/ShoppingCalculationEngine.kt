package com.example.domain.usecase

import com.example.domain.model.Money
import com.example.domain.model.ShoppingBudgetStatus
import com.example.domain.model.ShoppingItemComparisonSummary
import com.example.domain.model.ShoppingList
import com.example.domain.model.ShoppingListItem
import com.example.domain.model.ShoppingListSummary
import com.example.domain.model.ShoppingStorePriceRecord
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Precision calculation engine for Phase 3E Smart Shopping Lists.
 *
 * Uses [BigDecimal] and exact [Money] minor units ([Long]) exclusively.
 * Floating-point ([Float], [Double]) arithmetic is strictly prohibited.
 */
@Singleton
class ShoppingCalculationEngine @Inject constructor() {

  companion object {
    private val HUNDRED = BigDecimal("100")
  }

  fun parsePositiveQuantity(input: String, fieldName: String = "Quantity"): BigDecimal {
    val trimmed = input.trim()
    require(trimmed.isNotEmpty()) { "$fieldName is required" }
    val parsed =
      try {
        BigDecimal(trimmed)
      } catch (_: NumberFormatException) {
        throw IllegalArgumentException("Enter a valid numeric $fieldName")
      }
    require(parsed > BigDecimal.ZERO) { "$fieldName must be greater than zero" }
    return parsed.stripTrailingZeros()
  }

  fun parseNonNegativeMoneyMinor(
    input: String,
    currencyCode: String,
    fieldName: String = "Price",
  ): Long {
    val trimmed = input.trim().ifEmpty { "0" }
    val parsed =
      try {
        BigDecimal(trimmed)
      } catch (_: NumberFormatException) {
        throw IllegalArgumentException("Enter a valid numeric $fieldName")
      }
    require(parsed >= BigDecimal.ZERO) { "$fieldName cannot be negative" }
    return Money.fromBigDecimal(parsed, currencyCode, RoundingMode.HALF_UP).minorUnits
  }

  fun parsePositiveMoneyMinor(
    input: String,
    currencyCode: String,
    fieldName: String = "Budget",
  ): Long {
    val minor = parseNonNegativeMoneyMinor(input, currencyCode, fieldName)
    require(minor > 0L) { "$fieldName must be greater than zero" }
    return minor
  }

  fun computeLineTotalMinor(
    quantity: BigDecimal,
    unitPriceMinor: Long,
    currencyCode: String,
  ): Long {
    require(quantity > BigDecimal.ZERO) { "Quantity must be greater than zero" }
    require(unitPriceMinor >= 0L) { "Unit price cannot be negative" }
    val scale = Money.resolveFractionDigits(currencyCode)
    val unitPriceBd = BigDecimal.valueOf(unitPriceMinor, scale)
    val rawTotal = quantity.multiply(unitPriceBd, MathContext.DECIMAL128)
    return Money.fromBigDecimal(rawTotal, currencyCode, RoundingMode.HALF_UP).minorUnits
  }

  fun computeLineTotalMinor(
    quantityDecimal: String,
    unitPriceMinor: Long,
    currencyCode: String,
  ): Long {
    val qty = parsePositiveQuantity(quantityDecimal, "Quantity")
    return computeLineTotalMinor(qty, unitPriceMinor, currencyCode)
  }

  fun calculateSavingsOrOverspendMinor(item: ShoppingListItem): Long? {
    if (!item.isPurchased || item.actualTotalMinor == null) return null
    return Math.subtractExact(item.estimatedTotalMinor, item.actualTotalMinor)
  }

  fun calculateListSummary(
    list: ShoppingList,
    items: List<ShoppingListItem>,
  ): ShoppingListSummary {
    val listItems = items.filter { it.listId == list.id }
    var estimatedTotal = 0L
    var remainingEstimated = 0L
    var actualSpent = 0L
    var estimatedOfPurchased = 0L
    var purchasedCount = 0
    var remainingCount = 0

    for (item in listItems) {
      estimatedTotal = Math.addExact(estimatedTotal, item.estimatedTotalMinor)
      if (item.isPurchased) {
        purchasedCount++
        val itemActual = item.actualTotalMinor ?: item.estimatedTotalMinor
        actualSpent = Math.addExact(actualSpent, itemActual)
        estimatedOfPurchased = Math.addExact(estimatedOfPurchased, item.estimatedTotalMinor)
      } else {
        remainingCount++
        remainingEstimated = Math.addExact(remainingEstimated, item.estimatedTotalMinor)
      }
    }

    val savingsOrOverspend = Math.subtractExact(estimatedOfPurchased, actualSpent)
    val projectedSpend = Math.addExact(actualSpent, remainingEstimated)

    val budgetMinor = list.budgetMinor?.takeIf { it > 0L }
    val remainingBudget = budgetMinor?.let { Math.subtractExact(it, actualSpent) }
    val remainingBudgetProjected = budgetMinor?.let { Math.subtractExact(it, projectedSpend) }

    val relevantForAlert = maxOf(actualSpent, projectedSpend)
    val usagePercent =
      if (budgetMinor != null && budgetMinor > 0L) {
        BigDecimal.valueOf(relevantForAlert)
          .multiply(HUNDRED)
          .divide(BigDecimal.valueOf(budgetMinor), 0, RoundingMode.HALF_UP)
          .toInt()
          .coerceAtLeast(0)
      } else {
        0
      }

    val budgetStatus =
      when {
        budgetMinor == null -> ShoppingBudgetStatus.NO_BUDGET
        actualSpent > budgetMinor || projectedSpend > budgetMinor -> ShoppingBudgetStatus.EXCEEDED_BUDGET
        usagePercent >= list.warningThresholdPercent -> ShoppingBudgetStatus.APPROACHING_BUDGET
        else -> ShoppingBudgetStatus.WITHIN_BUDGET
      }

    return ShoppingListSummary(
      list = list,
      totalItemCount = listItems.size,
      purchasedItemCount = purchasedCount,
      remainingItemCount = remainingCount,
      estimatedTotalMinor = estimatedTotal,
      remainingEstimatedMinor = remainingEstimated,
      actualSpentMinor = actualSpent,
      estimatedOfPurchasedMinor = estimatedOfPurchased,
      savingsOrOverspendMinor = savingsOrOverspend,
      projectedSpendMinor = projectedSpend,
      remainingBudgetMinor = remainingBudget,
      remainingBudgetAfterProjectedMinor = remainingBudgetProjected,
      budgetUsagePercent = usagePercent,
      budgetStatus = budgetStatus,
    )
  }

  fun buildItemComparisonSummary(
    item: ShoppingListItem,
    storePricesForItem: List<ShoppingStorePriceRecord>,
  ): ShoppingItemComparisonSummary {
    val sortedDesc = storePricesForItem.sortedByDescending { it.recordedAt }
    val latest = sortedDesc.firstOrNull()
    val lowest = sortedDesc.minWithOrNull(compareBy<ShoppingStorePriceRecord> { it.unitPriceMinor }.thenByDescending { it.recordedAt })
    return ShoppingItemComparisonSummary(
      item = item,
      latestStorePrice = latest,
      lowestStorePrice = lowest,
      storePrices = sortedDesc,
      savingsOrOverspendMinor = calculateSavingsOrOverspendMinor(item),
    )
  }
}
