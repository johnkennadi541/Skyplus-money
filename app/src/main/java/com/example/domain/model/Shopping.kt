package com.example.domain.model

import java.util.UUID

enum class ShoppingUnit(val label: String) {
  PIECE("piece"),
  KG("kg"),
  G("g"),
  LITRE("litre"),
  ML("ml"),
  PACKET("packet"),
  BOX("box"),
  DOZEN("dozen"),
  BOTTLE("bottle"),
  PACK("pack"),
}

enum class ShoppingBudgetStatus {
  NO_BUDGET,
  WITHIN_BUDGET,
  APPROACHING_BUDGET,
  EXCEEDED_BUDGET,
}

object ShoppingCategories {
  val DEFAULT_CATEGORIES =
    listOf(
      "Groceries",
      "Fresh Produce",
      "Dairy & Bakery",
      "Kitchen",
      "Household",
      "Personal Care",
      "Electronics",
      "Pharmacy",
      "Other",
    )
}

data class ShoppingList(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val name: String,
  val currencyCode: String,
  val budgetMinor: Long? = null,
  val warningThresholdPercent: Int = 80,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val isArchived: Boolean = false,
)

data class ShoppingListItem(
  val id: String = UUID.randomUUID().toString(),
  val listId: String,
  val userId: String,
  val name: String,
  val category: String,
  val quantityDecimal: String,
  val unit: String,
  val estimatedUnitPriceMinor: Long,
  val estimatedTotalMinor: Long,
  val storeName: String? = null,
  val notes: String? = null,
  val isPurchased: Boolean = false,
  val actualUnitPriceMinor: Long? = null,
  val actualTotalMinor: Long? = null,
  val purchasedAt: Long? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val isArchived: Boolean = false,
)

data class ShoppingStorePriceRecord(
  val id: String = UUID.randomUUID().toString(),
  val itemId: String,
  val listId: String,
  val userId: String,
  val storeName: String,
  val unitPriceMinor: Long,
  val currencyCode: String,
  val recordedAt: Long = System.currentTimeMillis(),
  val notes: String? = null,
)

data class ShoppingListSummary(
  val list: ShoppingList,
  val totalItemCount: Int,
  val purchasedItemCount: Int,
  val remainingItemCount: Int,
  val estimatedTotalMinor: Long,
  val remainingEstimatedMinor: Long,
  val actualSpentMinor: Long,
  val estimatedOfPurchasedMinor: Long,
  val savingsOrOverspendMinor: Long,
  val projectedSpendMinor: Long,
  val remainingBudgetMinor: Long?,
  val remainingBudgetAfterProjectedMinor: Long?,
  val budgetUsagePercent: Int,
  val budgetStatus: ShoppingBudgetStatus,
)

data class ShoppingItemComparisonSummary(
  val item: ShoppingListItem,
  val latestStorePrice: ShoppingStorePriceRecord?,
  val lowestStorePrice: ShoppingStorePriceRecord?,
  val storePrices: List<ShoppingStorePriceRecord>,
  val savingsOrOverspendMinor: Long?,
)
