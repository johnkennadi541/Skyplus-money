package com.example.domain.model

import java.util.UUID

data class ShoppingPantryItem(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val name: String,
  val category: String = "Groceries",
  val quantityDecimal: String,
  val unit: String,
  val lowStockThresholdDecimal: String = "1",
  val expiryAt: Long? = null,
  val updatedAt: Long = System.currentTimeMillis(),
)

data class ShoppingMealPlan(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val title: String,
  val plannedDate: Long,
  val mealType: String,
  val notes: String? = null,
  val ingredients: List<ShoppingMealIngredient> = emptyList(),
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

data class ShoppingMealIngredient(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val mealPlanId: String,
  val name: String,
  val quantityDecimal: String,
  val unit: String,
  val shoppingItemId: String? = null,
)

data class ShoppingReceipt(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val listId: String? = null,
  val accountId: String? = null,
  val fingerprint: String,
  val merchant: String? = null,
  val purchaseDate: Long? = null,
  val currencyCode: String,
  val totalMinor: Long,
  val financialTransactionId: String? = null,
  val status: String = "REVIEWED",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

data class ShoppingReceiptExpenseRequest(
  val receiptId: String,
  val accountId: String,
)
