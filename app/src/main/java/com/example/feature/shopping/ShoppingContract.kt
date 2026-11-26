package com.example.feature.shopping

/**
 * Shopping Lists module contract (Phase 3E — Smart Shopping List).
 */
object ShoppingContract {
  const val TARGET_PHASE: Int = 3
  const val SUB_PHASE: String = "3E"
}

enum class ShoppingItemFilter(val label: String) {
  ALL("All"),
  REMAINING("Remaining"),
  PURCHASED("Purchased"),
  PRICE_COMPARE("Compared"),
}
