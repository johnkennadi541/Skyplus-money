package com.example.domain.usecase

import com.example.domain.model.Money
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/**
 * Formats a [Money] value using its ISO 4217 currency and exact BigDecimal scale.
 */
class FormatMoneyUseCase @Inject constructor() {
  operator fun invoke(money: Money, locale: Locale = Locale.US): String {
    val formatter = NumberFormat.getCurrencyInstance(locale)
    formatter.currency = Currency.getInstance(money.currencyCode)
    formatter.minimumFractionDigits = money.fractionDigits
    formatter.maximumFractionDigits = money.fractionDigits
    return formatter.format(money.toBigDecimal())
  }

  fun format(money: Money, locale: Locale = Locale.US): String = invoke(money, locale)
}
