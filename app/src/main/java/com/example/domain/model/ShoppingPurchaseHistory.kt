package com.example.domain.model

data class ShoppingPurchaseHistory(
  val id: String,
  val userId: String,
  val listId: String,
  val itemId: String,
  val itemName: String,
  val quantityDecimal: String,
  val unit: String,
  val actualUnitPriceMinor: Long,
  val actualTotalMinor: Long,
  val currencyCode: String,
  val storeName: String?,
  val purchasedAt: Long,
  val recordedAt: Long,
)
