package com.example.domain.usecase

import com.example.domain.model.ShoppingListItem
import com.example.domain.model.ShoppingPantryItem
import java.math.BigDecimal
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class ShoppingCurrencySpend(val currencyCode: String, val actualSpentMinor: Long, val estimatedMinor: Long)
data class ShoppingCategorySpend(val currencyCode: String, val category: String, val actualSpentMinor: Long)
data class ShoppingMonthlySpend(val currencyCode: String, val yearMonthUtc: String, val actualSpentMinor: Long)
data class ShoppingAnalyticsSnapshot(
  val totalsByCurrency: List<ShoppingCurrencySpend>,
  val categories: List<ShoppingCategorySpend>,
  val monthly: List<ShoppingMonthlySpend>,
)
data class ShoppingSmartSuggestion(
  val itemName: String,
  val reason: String,
  val suggestedQuantityDecimal: String,
  val unit: String,
)

object ShoppingInsightsEngine {
  fun analyze(items: List<ShoppingListItem>, currencyCodeByListId: Map<String, String>): ShoppingAnalyticsSnapshot {
    val purchased = items.filter { it.isPurchased && it.actualTotalMinor != null && currencyCodeByListId[it.listId] != null }
    fun currency(item: ShoppingListItem) = currencyCodeByListId.getValue(item.listId)
    val currencies = purchased.map(::currency).distinct().sorted()
    val totals = currencies.map { code ->
      ShoppingCurrencySpend(
        currencyCode = code,
        actualSpentMinor = sumMinorExact(purchased.filter { currency(it) == code }.map { it.actualTotalMinor ?: 0L }),
        estimatedMinor = sumMinorExact(purchased.filter { currency(it) == code }.map { it.estimatedTotalMinor }),
      )
    }
    val categories = purchased.groupBy { currency(it) to it.category }
      .map { (key, rows) -> ShoppingCategorySpend(key.first, key.second, sumMinorExact(rows.map { it.actualTotalMinor ?: 0L })) }
      .sortedWith(compareBy({ it.currencyCode }, { it.category }))
    val monthly = purchased.groupBy { currency(it) to monthUtc(it.purchasedAt ?: it.updatedAt) }
      .map { (key, rows) -> ShoppingMonthlySpend(key.first, key.second, rows.sumOf { it.actualTotalMinor ?: 0L }) }
      .sortedWith(compareBy({ it.currencyCode }, { it.yearMonthUtc }))
    return ShoppingAnalyticsSnapshot(totals, categories, monthly)
  }

  fun suggestRestocks(
    items: List<ShoppingListItem>,
    pantry: List<ShoppingPantryItem>,
    now: Long = System.currentTimeMillis(),
    repeatWindowDays: Int = 45,
  ): List<ShoppingSmartSuggestion> {
    val stock = pantry.associateBy { key(it.name, it.unit) }
    return items.filter { it.isPurchased && it.purchasedAt != null && it.purchasedAt <= now && now - it.purchasedAt <= repeatWindowDays.coerceAtLeast(0) * DAY_MS }
      .groupBy { key(it.name, it.unit) }
      .mapNotNull { (itemKey, history) ->
        val sample = history.maxByOrNull { it.purchasedAt ?: 0L } ?: return@mapNotNull null
        val pantryItem = stock[itemKey]
        val onHand = pantryItem?.let { runCatching { BigDecimal(it.quantityDecimal) }.getOrNull() }
        val threshold = pantryItem?.let { runCatching { BigDecimal(it.lowStockThresholdDecimal) }.getOrNull() }
        val suggested = runCatching { BigDecimal(sample.quantityDecimal) }.getOrNull() ?: BigDecimal.ONE
        val shouldSuggest = pantryItem == null || (onHand != null && threshold != null && onHand <= threshold)
        if (!shouldSuggest) return@mapNotNull null
        ShoppingSmartSuggestion(
          itemName = sample.name,
          reason = if (pantryItem == null) "Bought recently; not tracked in pantry" else "Pantry stock is low",
          suggestedQuantityDecimal = suggested.stripTrailingZeros().toPlainString(),
          unit = sample.unit,
        )
      }
      .distinctBy { key(it.itemName, it.unit) }
      .sortedBy { it.itemName.lowercase(Locale.ROOT) }
  }

  private fun sumMinorExact(values: List<Long>): Long = values.fold(0L, Math::addExact)

  private fun monthUtc(timestamp: Long): String {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply { timeInMillis = timestamp }
    return "%04d-%02d".format(Locale.ROOT, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
  }
  private fun key(name: String, unit: String) = name.trim().lowercase(Locale.ROOT) to unit.trim().lowercase(Locale.ROOT)
  private const val DAY_MS = 24L * 60L * 60L * 1000L
}
