package com.example.feature.smartdetection.engine

import com.example.domain.model.TransactionType
import java.text.Normalizer
import java.util.Locale

/** Offline, deterministic merchant-to-category rules. Unknown merchants return null. */
object MerchantCategoryResolver {
  private val categoryByAlias = mapOf(
    "SWIGGY" to "Food & Dining", "ZOMATO" to "Food & Dining", "STARBUCKS" to "Food & Dining",
    "CAFE COFFEE DAY" to "Food & Dining", "MCDONALDS" to "Food & Dining", "DOMINOS" to "Food & Dining",
    "PIZZA HUT" to "Food & Dining", "KFC" to "Food & Dining",
    "D MART" to "Groceries", "DMART" to "Groceries", "RELIANCE SMART" to "Groceries",
    "MORE" to "Groceries", "BIGBASKET" to "Groceries", "JIOMART" to "Groceries",
    "BLINKIT" to "Groceries", "ZEPTO" to "Groceries", "INSTAMART" to "Groceries",
    "TANGEDCO" to "Bills & Utilities", "KSEB" to "Bills & Utilities", "BESCOM" to "Bills & Utilities",
    "AIRTEL" to "Bills & Utilities", "JIO" to "Bills & Utilities", "BSNL" to "Bills & Utilities",
    "ACT FIBERNET" to "Bills & Utilities", "HATHWAY" to "Bills & Utilities", "TATA PLAY" to "Bills & Utilities",
    "UBER" to "Transport", "OLA" to "Transport", "RAPIDO" to "Transport",
    "INDIAN OIL" to "Fuel", "IOCL" to "Fuel", "HPCL" to "Fuel",
    "BPCL" to "Fuel", "BHARAT PETROLEUM" to "Fuel",
    "APOLLO PHARMACY" to "Medical & Healthcare", "MEDPLUS" to "Medical & Healthcare",
    "NETFLIX" to "Entertainment", "SPOTIFY" to "Entertainment",
    "AMAZON" to "Shopping", "FLIPKART" to "Shopping", "MYNTRA" to "Shopping",
  )

  /** Only expense merchants are resolved; other transaction types keep their own categories. */
  fun resolve(merchant: String?, type: TransactionType): String? {
    if (type != TransactionType.EXPENSE) return null
    val normalized = normalize(merchant ?: return null)
    if (normalized.isBlank()) return null
    return categoryByAlias.entries.sortedByDescending { it.key.length }
      .firstOrNull { (alias, _) -> normalized == alias || normalized.startsWith("$alias ") }
      ?.value
  }

  /** Stable Unicode-aware normalization shared by resolver and persisted merchant keys. */
  fun normalizeMerchantName(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC)
      .uppercase(Locale.ROOT)
      .replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ")
      .trim()
      .replace(Regex("\\s+"), " ")

  private fun normalize(value: String): String = normalizeMerchantName(value)
}
