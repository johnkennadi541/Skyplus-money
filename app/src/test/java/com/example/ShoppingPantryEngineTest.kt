package com.example

import com.example.domain.model.ShoppingPantryItem
import com.example.domain.usecase.PantryAlertType
import com.example.domain.usecase.ShoppingPantryEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingPantryEngineTest {
  @Test fun findsLowStockAndExpiryAlerts() {
    val now = 1_800_000_000_000L
    val rows = listOf(
      ShoppingPantryItem(userId = "u", name = "Milk", quantityDecimal = "0.5", unit = "litre", lowStockThresholdDecimal = "1", expiryAt = now + 2 * 86_400_000L),
      ShoppingPantryItem(userId = "u", name = "Rice", quantityDecimal = "5", unit = "kg", lowStockThresholdDecimal = "1", expiryAt = now - 1),
    )
    val alerts = ShoppingPantryEngine.alerts(rows, now)
    assertEquals(3, alerts.size)
    assertEquals(setOf(PantryAlertType.LOW_STOCK, PantryAlertType.EXPIRING_SOON, PantryAlertType.EXPIRED), alerts.map { it.type }.toSet())
  }
}
