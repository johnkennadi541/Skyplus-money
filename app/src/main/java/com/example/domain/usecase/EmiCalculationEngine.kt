package com.example.domain.usecase

import com.example.domain.model.EmiCalculationResult
import com.example.domain.model.Loan
import com.example.domain.model.LoanCurrencySummary
import com.example.domain.model.LoanStatus
import com.example.domain.model.Money
import com.example.domain.model.PaymentBreakdown
import com.example.domain.model.TenureUnit
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Precision reducing-balance EMI & loan amortization engine.
 *
 * Strictly uses [BigDecimal] and exact [Money] minor units.
 * Floating-point types ([Float], [Double]) are strictly prohibited.
 */
@Singleton
class EmiCalculationEngine @Inject constructor() {

  companion object {
    const val MIN_TENURE_MONTHS = 1
    const val MAX_TENURE_MONTHS = 600
    val MAX_ANNUAL_RATE_PERCENT: BigDecimal = BigDecimal("100.00")
    private val HUNDRED = BigDecimal("100")
    private val MONTHS_IN_YEAR = BigDecimal("12")
    private val ANNUAL_TO_MONTHLY_DIVISOR = MONTHS_IN_YEAR.multiply(HUNDRED) // 1200
  }

  fun calculateEmiFromDecimal(
    principalDecimal: String,
    annualInterestRateText: String,
    tenureValueText: String,
    tenureUnit: TenureUnit,
    currencyCode: String,
  ): Result<EmiCalculationResult> =
    runCatching {
      val trimmedPrincipal = principalDecimal.trim()
      require(trimmedPrincipal.isNotEmpty()) { "Principal amount is required" }
      val principalBd =
        try {
          BigDecimal(trimmedPrincipal)
        } catch (_: NumberFormatException) {
          throw IllegalArgumentException("Enter a valid numeric principal amount")
        }
      require(principalBd > BigDecimal.ZERO) { "Principal amount must be greater than zero" }

      val trimmedRate = annualInterestRateText.trim().ifEmpty { "0" }
      val annualRate =
        try {
          BigDecimal(trimmedRate)
        } catch (_: NumberFormatException) {
          throw IllegalArgumentException("Enter a valid numeric annual interest rate")
        }
      require(annualRate >= BigDecimal.ZERO) { "Interest rate cannot be negative" }
      require(annualRate <= MAX_ANNUAL_RATE_PERCENT) { "Interest rate cannot exceed 100%" }

      val trimmedTenure = tenureValueText.trim()
      require(trimmedTenure.isNotEmpty()) { "Loan tenure is required" }
      val tenureNumber =
        trimmedTenure.toIntOrNull()
          ?: throw IllegalArgumentException("Loan tenure must be a whole number")
      require(tenureNumber > 0) { "Loan tenure must be greater than zero" }

      val tenureMonths =
        when (tenureUnit) {
          TenureUnit.MONTHS -> tenureNumber
          TenureUnit.YEARS -> Math.multiplyExact(tenureNumber, 12)
        }
      require(tenureMonths in MIN_TENURE_MONTHS..MAX_TENURE_MONTHS) {
        "Loan tenure must be between $MIN_TENURE_MONTHS and $MAX_TENURE_MONTHS months"
      }

      val principalMoney = Money.fromBigDecimal(principalBd, currencyCode, RoundingMode.HALF_UP)
      require(principalMoney.minorUnits > 0L) { "Principal amount must be positive in $currencyCode" }

      calculateEmi(
        principalMinor = principalMoney.minorUnits,
        annualInterestRatePercent = annualRate,
        tenureMonths = tenureMonths,
        currencyCode = principalMoney.currencyCode,
      )
    }

  fun calculateEmi(
    principalMinor: Long,
    annualInterestRatePercent: BigDecimal,
    tenureMonths: Int,
    currencyCode: String,
  ): EmiCalculationResult {
    require(principalMinor > 0L) { "Principal amount must be strictly positive" }
    require(tenureMonths in MIN_TENURE_MONTHS..MAX_TENURE_MONTHS) {
      "Tenure must be between $MIN_TENURE_MONTHS and $MAX_TENURE_MONTHS months"
    }
    require(annualInterestRatePercent >= BigDecimal.ZERO) { "Annual interest rate cannot be negative" }
    require(annualInterestRatePercent <= MAX_ANNUAL_RATE_PERCENT) { "Annual interest rate cannot exceed 100%" }

    val normalizedCurrency = currencyCode.trim().uppercase()
    val scale = Money.resolveFractionDigits(normalizedCurrency)
    val principalMoney = Money.fromMinorUnits(principalMinor, normalizedCurrency)
    val principalDec = principalMoney.toBigDecimal()
    val tenureBd = BigDecimal(tenureMonths)

    val monthlyEmiMoney: Money
    val totalRepaymentMoney: Money
    val totalInterestMoney: Money

    if (annualInterestRatePercent.compareTo(BigDecimal.ZERO) == 0) {
      val exactMonthlyEmi = principalDec.divide(tenureBd, scale, RoundingMode.HALF_UP)
      monthlyEmiMoney = Money.fromBigDecimal(exactMonthlyEmi, normalizedCurrency, RoundingMode.HALF_UP)
      totalRepaymentMoney = principalMoney
      totalInterestMoney = Money.zero(normalizedCurrency)
    } else {
      val monthlyRate =
        annualInterestRatePercent.divide(ANNUAL_TO_MONTHLY_DIVISOR, 24, RoundingMode.HALF_EVEN)
      val onePlusRate = BigDecimal.ONE.add(monthlyRate, MathContext.DECIMAL128)
      val factor = onePlusRate.pow(tenureMonths, MathContext.DECIMAL128)
      val numerator =
        principalDec
          .multiply(monthlyRate, MathContext.DECIMAL128)
          .multiply(factor, MathContext.DECIMAL128)
      val denominator = factor.subtract(BigDecimal.ONE, MathContext.DECIMAL128)
      require(denominator > BigDecimal.ZERO) { "Invalid rate/tenure combination" }

      val exactEmi = numerator.divide(denominator, MathContext.DECIMAL128)
      val roundedEmiDec = exactEmi.setScale(scale, RoundingMode.HALF_UP)
      monthlyEmiMoney = Money.fromBigDecimal(roundedEmiDec, normalizedCurrency, RoundingMode.HALF_UP)

      val totalRepaymentMinor =
        BigDecimal.valueOf(monthlyEmiMoney.minorUnits)
          .multiply(tenureBd)
          .longValueExact()
      totalRepaymentMoney = Money.fromMinorUnits(totalRepaymentMinor, normalizedCurrency)

      val interestMinor = maxOf(0L, totalRepaymentMinor - principalMinor)
      totalInterestMoney = Money.fromMinorUnits(interestMinor, normalizedCurrency)
    }

    val totalRepaymentDec = totalRepaymentMoney.toBigDecimal()
    val (principalShare, interestShare) =
      if (totalRepaymentDec > BigDecimal.ZERO) {
        val pShare =
          principalDec
            .multiply(HUNDRED)
            .divide(totalRepaymentDec, 2, RoundingMode.HALF_UP)
            .coerceInRange(BigDecimal.ZERO, HUNDRED)
        val iShare = HUNDRED.subtract(pShare).max(BigDecimal.ZERO)
        pShare to iShare
      } else {
        HUNDRED to BigDecimal.ZERO.setScale(2)
      }

    return EmiCalculationResult(
      principalMoney = principalMoney,
      annualInterestRatePercent = annualInterestRatePercent.stripTrailingZeros(),
      tenureMonths = tenureMonths,
      monthlyEmiMoney = monthlyEmiMoney,
      totalRepaymentMoney = totalRepaymentMoney,
      totalInterestMoney = totalInterestMoney,
      principalSharePercent = principalShare,
      interestSharePercent = interestShare,
    )
  }

  /**
   * Computes exact principal and interest components for an incoming loan payment.
   */
  fun calculatePaymentBreakdown(
    outstandingPrincipalMinor: Long,
    annualInterestRatePercent: String,
    paymentAmountMinor: Long,
    currencyCode: String,
  ): PaymentBreakdown {
    require(outstandingPrincipalMinor > 0L) { "Loan has no remaining principal balance" }
    require(paymentAmountMinor > 0L) { "Payment amount must be strictly positive" }

    val normalizedCurrency = currencyCode.trim().uppercase()
    val scale = Money.resolveFractionDigits(normalizedCurrency)
    val rate =
      try {
        BigDecimal(annualInterestRatePercent.trim().ifEmpty { "0" })
      } catch (_: NumberFormatException) {
        throw IllegalArgumentException("Invalid stored interest rate")
      }
    require(rate >= BigDecimal.ZERO) { "Interest rate cannot be negative" }

    val outstandingMoney = Money.fromMinorUnits(outstandingPrincipalMinor, normalizedCurrency)
    val monthlyInterestDec =
      if (rate.compareTo(BigDecimal.ZERO) == 0) {
        BigDecimal.ZERO
      } else {
        outstandingMoney
          .toBigDecimal()
          .multiply(rate, MathContext.DECIMAL128)
          .divide(ANNUAL_TO_MONTHLY_DIVISOR, scale, RoundingMode.HALF_UP)
      }

    val monthlyInterestMinor =
      Money.fromBigDecimal(monthlyInterestDec, normalizedCurrency, RoundingMode.HALF_UP).minorUnits

    val maxPayoffMinor = Math.addExact(outstandingPrincipalMinor, monthlyInterestMinor)
    require(paymentAmountMinor <= maxPayoffMinor) {
      "Payment exceeds total payoff amount (${Money.fromMinorUnits(maxPayoffMinor, normalizedCurrency).toBigDecimal().toPlainString()} $normalizedCurrency)"
    }

    val interestComponentMinor = minOf(monthlyInterestMinor, paymentAmountMinor)
    val principalComponentMinor =
      minOf(outstandingPrincipalMinor, paymentAmountMinor - interestComponentMinor)
    val remainingBalanceMinor = maxOf(0L, outstandingPrincipalMinor - principalComponentMinor)

    return PaymentBreakdown(
      paymentMoney = Money.fromMinorUnits(paymentAmountMinor, normalizedCurrency),
      principalComponentMoney = Money.fromMinorUnits(principalComponentMinor, normalizedCurrency),
      interestComponentMoney = Money.fromMinorUnits(interestComponentMinor, normalizedCurrency),
      remainingBalanceMoney = Money.fromMinorUnits(remainingBalanceMinor, normalizedCurrency),
    )
  }

  fun evaluateEffectiveStatus(
    loan: Loan,
    nowMillis: Long = System.currentTimeMillis(),
  ): LoanStatus {
    return when {
      loan.outstandingPrincipalMinor <= 0L -> LoanStatus.COMPLETED
      loan.status == LoanStatus.COMPLETED -> LoanStatus.COMPLETED
      nowMillis > loan.nextDueDate -> LoanStatus.OVERDUE
      else -> LoanStatus.ACTIVE
    }
  }

  fun addMonths(timestampMillis: Long, months: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestampMillis
    calendar.add(Calendar.MONTH, months)
    return calendar.timeInMillis
  }

  /**
   * Generates per-currency loan summaries without ever combining different currencies.
   */
  fun calculateCurrencySummaries(
    loans: List<Loan>,
    nowMillis: Long = System.currentTimeMillis(),
  ): Map<String, LoanCurrencySummary> {
    return loans
      .groupBy { it.currencyCode.uppercase() }
      .mapValues { (currency, currencyLoans) ->
        var totalPrincipal = 0L
        var totalOutstanding = 0L
        var totalPaid = 0L
        var totalMonthlyEmi = 0L
        var activeCount = 0
        var overdueCount = 0
        var completedCount = 0

        for (loan in currencyLoans) {
          totalPrincipal = Math.addExact(totalPrincipal, loan.principalMinor)
          totalOutstanding = Math.addExact(totalOutstanding, loan.outstandingPrincipalMinor)
          totalPaid = Math.addExact(totalPaid, loan.amountPaidMinor)

          when (evaluateEffectiveStatus(loan, nowMillis)) {
            LoanStatus.ACTIVE -> {
              activeCount++
              totalMonthlyEmi = Math.addExact(totalMonthlyEmi, loan.monthlyEmiMinor)
            }
            LoanStatus.OVERDUE -> {
              overdueCount++
              totalMonthlyEmi = Math.addExact(totalMonthlyEmi, loan.monthlyEmiMinor)
            }
            LoanStatus.COMPLETED -> {
              completedCount++
            }
          }
        }

        LoanCurrencySummary(
          currencyCode = currency,
          totalPrincipalMinor = totalPrincipal,
          totalOutstandingMinor = totalOutstanding,
          totalAmountPaidMinor = totalPaid,
          totalMonthlyEmiMinor = totalMonthlyEmi,
          activeLoanCount = activeCount,
          overdueLoanCount = overdueCount,
          completedLoanCount = completedCount,
        )
      }
  }

  private fun BigDecimal.coerceInRange(min: BigDecimal, max: BigDecimal): BigDecimal {
    return when {
      this < min -> min
      this > max -> max
      else -> this
    }
  }
}
