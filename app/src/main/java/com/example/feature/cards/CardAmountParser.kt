package com.example.feature.cards

import com.example.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parses a typed amount into exact minor units for the given ISO-4217 currency.
 *
 * The number of decimals is **not** hardcoded: it comes from
 * [Money.resolveFractionDigits], so a Kuwaiti dinar (3 decimals) and a Japanese yen (0 decimals)
 * both round-trip correctly. Returns null for anything that is not a non-negative amount.
 */
internal fun parseAmountToMinor(text: String, currencyCode: String): Long? {
  val trimmed = text.trim()
  if (trimmed.isEmpty()) return 0L
  val decimal = trimmed.toBigDecimalOrNull() ?: return null
  if (decimal < BigDecimal.ZERO) return null
  val fractionDigits = Money.resolveFractionDigits(currencyCode)
  return runCatching {
    decimal
      .setScale(fractionDigits, RoundingMode.HALF_EVEN)
      .movePointRight(fractionDigits)
      .longValueExact()
  }.getOrNull()
}
