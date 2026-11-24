package com.example.core.common

import com.example.domain.model.Money
import java.util.Currency
import java.util.Locale

/**
 * Runtime ISO 4217 catalogue for account and reporting currency pickers.
 *
 * This deliberately derives its entries from the platform currency registry instead of a
 * hand-maintained short list. Money remains stored in its original currency; this catalogue
 * supplies metadata only and does not perform FX conversion.
 */
object CurrencyCatalog {

  data class CurrencyOption(
    val code: String,
    val displayName: String,
    val symbol: String,
    val fractionDigits: Int,
  )

  /** All real ISO currencies exposed by the current Android runtime, excluding pseudo-currencies. */
  fun all(locale: Locale = Locale.getDefault()): List<CurrencyOption> =
    Currency.getAvailableCurrencies()
      .asSequence()
      .filter { Money.isSupportedCurrency(it.currencyCode) }
      .map { currency ->
        CurrencyOption(
          code = currency.currencyCode,
          displayName = currency.getDisplayName(locale),
          symbol = currency.getSymbol(locale),
          fractionDigits = currency.defaultFractionDigits,
        )
      }
      .distinctBy { it.code }
      .sortedWith(compareBy<CurrencyOption>({ it.displayName.lowercase(locale) }, { it.code }))
      .toList()

  fun find(code: String, locale: Locale = Locale.getDefault()): CurrencyOption? {
    val normalized = code.trim().uppercase(Locale.ROOT)
    if (!Money.isSupportedCurrency(normalized)) return null
    val currency = runCatching { Currency.getInstance(normalized) }.getOrNull() ?: return null
    return CurrencyOption(
      code = currency.currencyCode,
      displayName = currency.getDisplayName(locale),
      symbol = currency.getSymbol(locale),
      fractionDigits = currency.defaultFractionDigits,
    )
  }
}
