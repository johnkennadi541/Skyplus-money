package com.example.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/**
 * Immutable monetary value object enforcing exact minor-unit and BigDecimal arithmetic.
 *
 * Floating-point types ([Double], [Float]) are strictly prohibited for financial calculations
 * in SKYPLUS MONEY. All values preserve their ISO 4217 [currencyCode] and currency fraction scale.
 */
data class Money private constructor(
  val minorUnits: Long,
  val currencyCode: String,
  val fractionDigits: Int,
) {
  init {
    require(currencyCode.length == 3 && currencyCode == currencyCode.uppercase()) {
      "Currency code must be a 3-letter uppercase ISO 4217 code, got '$currencyCode'"
    }
    require(fractionDigits in 0..4) {
      "Unsupported currency fraction digits: $fractionDigits for $currencyCode"
    }
  }

  val isZero: Boolean
    get() = minorUnits == 0L

  val isPositive: Boolean
    get() = minorUnits > 0L

  val isNegative: Boolean
    get() = minorUnits < 0L

  fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(minorUnits, fractionDigits)

  operator fun plus(other: Money): Money {
    requireSameCurrency(other)
    return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
  }

  operator fun minus(other: Money): Money {
    requireSameCurrency(other)
    return copy(minorUnits = Math.subtractExact(minorUnits, other.minorUnits))
  }

  operator fun unaryMinus(): Money = copy(minorUnits = Math.negateExact(minorUnits))

  fun abs(): Money = if (minorUnits < 0L) -this else this

  /**
   * Allocates this monetary amount proportionally by integer [weight] out of [totalWeight],
   * rounding with [RoundingMode.HALF_EVEN] in exact minor units.
   */
  fun allocateRatio(weight: Long, totalWeight: Long): Money {
    require(weight >= 0L) { "Weight must be non-negative" }
    require(totalWeight > 0L) { "Total weight must be positive" }
    val scaled =
      BigDecimal.valueOf(minorUnits)
        .multiply(BigDecimal.valueOf(weight))
        .divide(BigDecimal.valueOf(totalWeight), 0, RoundingMode.HALF_EVEN)
        .longValueExact()
    return copy(minorUnits = scaled)
  }

  private fun requireSameCurrency(other: Money) {
    require(currencyCode == other.currencyCode && fractionDigits == other.fractionDigits) {
      "Currency mismatch: cannot combine $currencyCode with ${other.currencyCode}"
    }
  }

  companion object {
    fun resolveFractionDigits(iso4217Code: String): Int {
      val normalized = iso4217Code.trim().uppercase()
      require(normalized.length == 3) { "Invalid ISO 4217 currency code: '$iso4217Code'" }
      val currency = Currency.getInstance(normalized)
      val digits = currency.defaultFractionDigits
      return if (digits < 0) 2 else digits
    }

    fun fromMinorUnits(minorUnits: Long, currencyCode: String): Money {
      val normalized = currencyCode.trim().uppercase()
      val scale = resolveFractionDigits(normalized)
      return Money(minorUnits = minorUnits, currencyCode = normalized, fractionDigits = scale)
    }

    fun fromBigDecimal(
      amount: BigDecimal,
      currencyCode: String,
      roundingMode: RoundingMode = RoundingMode.HALF_EVEN,
    ): Money {
      val normalized = currencyCode.trim().uppercase()
      val scale = resolveFractionDigits(normalized)
      val scaledMinorUnits =
        amount.setScale(scale, roundingMode).movePointRight(scale).longValueExact()
      return Money(
        minorUnits = scaledMinorUnits,
        currencyCode = normalized,
        fractionDigits = scale,
      )
    }

    fun fromDecimalString(amountText: String, currencyCode: String): Money {
      val trimmed = amountText.trim()
      require(trimmed.isNotEmpty()) { "Amount text must not be blank" }
      return fromBigDecimal(BigDecimal(trimmed), currencyCode)
    }

    fun zero(currencyCode: String): Money = fromMinorUnits(0L, currencyCode)
  }
}
