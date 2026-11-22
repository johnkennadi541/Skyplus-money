package com.example.core.common

import com.example.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parses a typed amount into exact minor units for the given ISO-4217 currency.
 *
 * The number of decimals is **not** hardcoded: it comes from [Money.fractionDigitsOrNull], so a
 * Kuwaiti dinar (3 decimals) and a Japanese yen (0 decimals) both round-trip correctly, and an
 * unsupported code such as `ABC` or `XXX` returns null instead of throwing.
 *
 * Shared by every screen that accepts a typed amount, so the decimal count is decided in exactly
 * one place.
 */
fun parseAmountToMinor(text: String, currencyCode: String): Long? {
  val trimmed = text.trim()
  if (trimmed.isEmpty()) return 0L
  val decimal = trimmed.toBigDecimalOrNull() ?: return null
  if (decimal < BigDecimal.ZERO) return null
  val fractionDigits = Money.fractionDigitsOrNull(currencyCode) ?: return null
  return runCatching {
    decimal
      .setScale(fractionDigits, RoundingMode.HALF_EVEN)
      .movePointRight(fractionDigits)
      .longValueExact()
  }.getOrNull()
}
