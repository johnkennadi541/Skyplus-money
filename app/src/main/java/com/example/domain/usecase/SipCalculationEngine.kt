package com.example.domain.usecase

import com.example.domain.model.Money
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class SipCalculationResult(
  val monthlyInvestment: Money,
  val annualReturnRatePercent: BigDecimal,
  val tenureMonths: Int,
  val totalInvested: Money,
  val estimatedReturns: Money,
  val maturityValue: Money,
)

/**
 * Monthly SIP projection using end-of-month contributions and decimal-only math.
 * Returns are estimates, not guaranteed investment outcomes.
 */
class SipCalculationEngine {
  companion object {
    const val MIN_TENURE_MONTHS = 1
    const val MAX_TENURE_MONTHS = 600
    private val MAX_ANNUAL_RATE = BigDecimal("100")
    private val MONTHLY_RATE_DIVISOR = BigDecimal("1200")
    private val CONTEXT = MathContext.DECIMAL128
  }

  fun calculate(
    monthlyInvestmentDecimal: String,
    annualReturnRatePercentText: String,
    tenureMonths: Int,
    currencyCode: String,
  ): Result<SipCalculationResult> = runCatching {
    val investment = try {
      BigDecimal(monthlyInvestmentDecimal.trim())
    } catch (_: NumberFormatException) {
      throw IllegalArgumentException("Enter a valid monthly investment")
    }
    require(investment > BigDecimal.ZERO) { "Monthly investment must be greater than zero" }

    val annualRate = try {
      BigDecimal(annualReturnRatePercentText.trim())
    } catch (_: NumberFormatException) {
      throw IllegalArgumentException("Enter a valid annual return rate")
    }
    require(annualRate >= BigDecimal.ZERO) { "Annual return rate cannot be negative" }
    require(annualRate <= MAX_ANNUAL_RATE) { "Annual return rate cannot exceed 100%" }
    require(tenureMonths in MIN_TENURE_MONTHS..MAX_TENURE_MONTHS) {
      "SIP tenure must be between 1 and 600 months"
    }

    val monthlyMoney = Money.fromBigDecimal(investment, currencyCode, RoundingMode.HALF_UP)
    require(monthlyMoney.minorUnits > 0L) { "Monthly investment is too small for $currencyCode" }
    val totalInvestedMinor = Math.multiplyExact(monthlyMoney.minorUnits, tenureMonths.toLong())
    val totalInvested = Money.fromMinorUnits(totalInvestedMinor, monthlyMoney.currencyCode)

    val monthlyRate = annualRate.divide(MONTHLY_RATE_DIVISOR, 24, RoundingMode.HALF_EVEN)
    val periods = BigDecimal(tenureMonths)
    val futureValue = if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
      totalInvested.toBigDecimal()
    } else {
      val growth = BigDecimal.ONE.add(monthlyRate, CONTEXT).pow(tenureMonths, CONTEXT)
      monthlyMoney.toBigDecimal()
        .multiply(growth.subtract(BigDecimal.ONE, CONTEXT), CONTEXT)
        .divide(monthlyRate, CONTEXT)
    }

    val maturity = Money.fromBigDecimal(futureValue, monthlyMoney.currencyCode, RoundingMode.HALF_UP)
    val returnsMinor = Math.subtractExact(maturity.minorUnits, totalInvested.minorUnits)
    val estimatedReturns = Money.fromMinorUnits(returnsMinor.coerceAtLeast(0L), monthlyMoney.currencyCode)

    SipCalculationResult(
      monthlyInvestment = monthlyMoney,
      annualReturnRatePercent = annualRate.stripTrailingZeros(),
      tenureMonths = tenureMonths,
      totalInvested = totalInvested,
      estimatedReturns = estimatedReturns,
      maturityValue = maturity,
    )
  }
}
