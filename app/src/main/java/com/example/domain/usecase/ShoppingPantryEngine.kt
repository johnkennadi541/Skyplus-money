package com.example.domain.usecase

import com.example.domain.model.ShoppingPantryItem
import java.math.BigDecimal

enum class PantryAlertType { LOW_STOCK, EXPIRING_SOON, EXPIRED }
data class PantryAlert(val item: ShoppingPantryItem, val type: PantryAlertType)

object ShoppingPantryEngine {
  fun alerts(items: List<ShoppingPantryItem>, now: Long, expiryWindowDays: Int = 7): List<PantryAlert> {
    val windowEnd = now + expiryWindowDays.coerceAtLeast(0) * DAY_MS
    return items.flatMap { item ->
      buildList {
        val quantity = runCatching { BigDecimal(item.quantityDecimal) }.getOrNull()
        val threshold = runCatching { BigDecimal(item.lowStockThresholdDecimal) }.getOrNull()
        if (quantity != null && threshold != null && quantity <= threshold) add(PantryAlert(item, PantryAlertType.LOW_STOCK))
        item.expiryAt?.let { expiry ->
          if (expiry < now) add(PantryAlert(item, PantryAlertType.EXPIRED))
          else if (expiry <= windowEnd) add(PantryAlert(item, PantryAlertType.EXPIRING_SOON))
        }
      }
    }
  }
  private const val DAY_MS = 24L * 60L * 60L * 1000L
}
