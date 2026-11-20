package com.example

import com.example.domain.model.Loan
import com.example.domain.model.LoanStatus
import com.example.domain.model.LoanType
import com.example.domain.model.TenureUnit
import com.example.domain.usecase.EmiCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class EmiCalculationEngineTest {

  private lateinit var engine: EmiCalculationEngine

  @Before
  fun setUp() {
    engine = EmiCalculationEngine()
  }

  @Test
  fun `calculates standard reducing balance EMI accurately with BigDecimal`() {
    // $10,000.00 at 12% p.a. for 12 months => EMI = $888.49
    val result =
      engine.calculateEmi(
        principalMinor = 10_000_00L,
        annualInterestRatePercent = BigDecimal("12"),
        tenureMonths = 12,
        currencyCode = "USD",
      )

    assertEquals(888_49L, result.monthlyEmiMoney.minorUnits)
    assertEquals(10_661_88L, result.totalRepaymentMoney.minorUnits)
    assertEquals(661_88L, result.totalInterestMoney.minorUnits)
  }

  @Test
  fun `calculates zero interest loan accurately without interest charge`() {
    val result =
      engine.calculateEmi(
        principalMinor = 12_000_00L,
        annualInterestRatePercent = BigDecimal.ZERO,
        tenureMonths = 12,
        currencyCode = "USD",
      )

    assertEquals(1_000_00L, result.monthlyEmiMoney.minorUnits)
    assertEquals(12_000_00L, result.totalRepaymentMoney.minorUnits)
    assertEquals(0L, result.totalInterestMoney.minorUnits)
    assertEquals(BigDecimal("100.00"), result.principalSharePercent)
    assertEquals(BigDecimal("0.00"), result.interestSharePercent)
  }

  @Test
  fun `handles different tenures in months and years`() {
    val monthsResult =
      engine.calculateEmiFromDecimal(
        principalDecimal = "500000",
        annualInterestRateText = "8.5",
        tenureValueText = "240",
        tenureUnit = TenureUnit.MONTHS,
        currencyCode = "INR",
      ).getOrThrow()

    val yearsResult =
      engine.calculateEmiFromDecimal(
        principalDecimal = "500000",
        annualInterestRateText = "8.5",
        tenureValueText = "20",
        tenureUnit = TenureUnit.YEARS,
        currencyCode = "INR",
      ).getOrThrow()

    assertEquals(240, yearsResult.tenureMonths)
    assertEquals(monthsResult.monthlyEmiMoney.minorUnits, yearsResult.monthlyEmiMoney.minorUnits)
    assertEquals(4339_12L, yearsResult.monthlyEmiMoney.minorUnits) // ₹4,339.12
  }

  @Test
  fun `handles rounding boundaries and multi-currency fraction digits safely`() {
    // 0-decimal currency (JPY)
    val jpyResult =
      engine.calculateEmi(
        principalMinor = 1_000_000L,
        annualInterestRatePercent = BigDecimal("3.6"),
        tenureMonths = 12,
        currencyCode = "JPY",
      )
    assertEquals(0, jpyResult.monthlyEmiMoney.fractionDigits)
    assertEquals(84967L, jpyResult.monthlyEmiMoney.minorUnits)

    // 3-decimal currency (KWD)
    val kwdResult =
      engine.calculateEmi(
        principalMinor = 10_000_000L, // 10,000.000 KWD
        annualInterestRatePercent = BigDecimal("6.0"),
        tenureMonths = 24,
        currencyCode = "KWD",
      )
    assertEquals(3, kwdResult.monthlyEmiMoney.fractionDigits)
    assertEquals(443_206L, kwdResult.monthlyEmiMoney.minorUnits) // 443.206 KWD

    // Large principal without overflow
    val largeResult =
      engine.calculateEmiFromDecimal(
        principalDecimal = "50000000", // 50 million USD
        annualInterestRateText = "7.25",
        tenureValueText = "30",
        tenureUnit = TenureUnit.YEARS,
        currencyCode = "USD",
      )
    assertTrue(largeResult.isSuccess)
    assertTrue(largeResult.getOrThrow().monthlyEmiMoney.minorUnits > 0L)
  }

  @Test
  fun `rejects invalid loan calculator inputs safely`() {
    assertTrue(
      engine.calculateEmiFromDecimal("0", "8.5", "12", TenureUnit.MONTHS, "USD").isFailure
    )
    assertTrue(
      engine.calculateEmiFromDecimal("-500", "8.5", "12", TenureUnit.MONTHS, "USD").isFailure
    )
    assertTrue(
      engine.calculateEmiFromDecimal("1000", "-1", "12", TenureUnit.MONTHS, "USD").isFailure
    )
    assertTrue(
      engine.calculateEmiFromDecimal("1000", "8.5", "0", TenureUnit.MONTHS, "USD").isFailure
    )
    assertTrue(
      engine.calculateEmiFromDecimal("1000", "8.5", "55", TenureUnit.YEARS, "USD").isFailure
    )
    assertTrue(
      engine.calculateEmiFromDecimal("abc", "8.5", "12", TenureUnit.MONTHS, "USD").isFailure
    )
  }

  @Test
  fun `multi-currency loan summaries remain strictly isolated`() {
    val now = 2_000_000L
    val usdLoan =
      Loan(
        id = "l_usd",
        userId = "u1",
        name = "Auto Loan",
        lenderName = "Chase",
        loanType = LoanType.VEHICLE,
        principalMinor = 20_000_00L,
        currencyCode = "USD",
        annualInterestRatePercent = "6.0",
        tenureMonths = 36,
        startDate = 1_000_000L,
        nextDueDate = 3_000_000L, // active
        monthlyEmiMinor = 608_44L,
        totalPayableMinor = 21_903_84L,
        totalInterestMinor = 1_903_84L,
        amountPaidMinor = 5_000_00L,
        outstandingPrincipalMinor = 15_000_00L,
        status = LoanStatus.ACTIVE,
      )

    val inrLoanOverdue =
      Loan(
        id = "l_inr",
        userId = "u1",
        name = "Home Loan",
        lenderName = "HDFC",
        loanType = LoanType.HOME,
        principalMinor = 5_000_000_00L,
        currencyCode = "INR",
        annualInterestRatePercent = "8.5",
        tenureMonths = 240,
        startDate = 1_000_000L,
        nextDueDate = 1_500_000L, // < now => overdue
        monthlyEmiMinor = 43_391_16L,
        totalPayableMinor = 10_413_878_40L,
        totalInterestMinor = 5_413_878_40L,
        amountPaidMinor = 100_000_00L,
        outstandingPrincipalMinor = 4_900_000_00L,
        status = LoanStatus.ACTIVE,
      )

    val summaries = engine.calculateCurrencySummaries(listOf(usdLoan, inrLoanOverdue), nowMillis = now)
    assertEquals(2, summaries.size)

    val usdSummary = summaries["USD"]!!
    assertEquals(15_000_00L, usdSummary.totalOutstandingMinor)
    assertEquals(1, usdSummary.activeLoanCount)
    assertEquals(0, usdSummary.overdueLoanCount)

    val inrSummary = summaries["INR"]!!
    assertEquals(4_900_000_00L, inrSummary.totalOutstandingMinor)
    assertEquals(0, inrSummary.activeLoanCount)
    assertEquals(1, inrSummary.overdueLoanCount)
  }

  @Test
  fun `calculates payment principal and interest breakdown accurately`() {
    // $10,000.00 outstanding at 12% p.a. => monthly interest = $100.00
    val breakdown =
      engine.calculatePaymentBreakdown(
        outstandingPrincipalMinor = 10_000_00L,
        annualInterestRatePercent = "12",
        paymentAmountMinor = 888_49L,
        currencyCode = "USD",
      )

    assertEquals(100_00L, breakdown.interestComponentMoney.minorUnits)
    assertEquals(788_49L, breakdown.principalComponentMoney.minorUnits)
    assertEquals(9_211_51L, breakdown.remainingBalanceMoney.minorUnits)
  }
}
