package com.example

import com.example.domain.model.CategoryBreakdownRow
import com.example.domain.model.CurrencyFinancialReport
import com.example.domain.model.FinancialReportSnapshot
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.IncomeExpenseCurrencyReport
import com.example.domain.model.MonthlyTrendRow
import com.example.domain.model.Money
import com.example.domain.model.NetWorthCurrencyBreakdown
import com.example.domain.model.ReportDateWindow
import com.example.domain.model.ReportPeriodType
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.feature.dashboard.HomeDashboardMapper
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Home dashboard must never invent a figure. This test pins the mapping from the existing
 * report engine to the dashboard state, using exact minor units only.
 */
class HomeDashboardMapperTest {

  private val formatter: (Money) -> String = { money ->
    "${money.currencyCode} ${money.minorUnits}"
  }

  private fun snapshot(): FinancialReportSnapshot {
    val incomeExpense =
      IncomeExpenseCurrencyReport(
        currencyCode = "INR",
        totalIncomeMinor = 500_000L,
        grossExpensesMinor = 320_000L,
        totalRefundsAndReversalsMinor = 20_000L,
        netExpensesMinor = 300_000L,
        netSavingsMinor = 200_000L,
        savingsPercentage = BigDecimal("40.00"),
        completedIncomeCount = 2,
        completedExpenseCount = 3,
        completedRefundCount = 1,
        excludedTransferCount = 0,
        excludedNonCompletedCount = 0,
        expenseByCategory =
          listOf(
            CategoryBreakdownRow(
              category = "Food & Dining",
              currencyCode = "INR",
              totalMinor = 120_000L,
              grossMinor = 120_000L,
              refundOffsetMinor = 0L,
              percentage = BigDecimal("40.00"),
              transactionCount = 1,
            ),
            CategoryBreakdownRow(
              category = "Transport",
              currencyCode = "INR",
              totalMinor = 180_000L,
              grossMinor = 180_000L,
              refundOffsetMinor = 0L,
              percentage = BigDecimal("60.00"),
              transactionCount = 1,
            ),
          ),
        incomeByCategory = emptyList(),
        monthlyTrends =
          listOf(
            MonthlyTrendRow(
              monthKey = "2026-10",
              monthLabel = "Oct",
              incomeMinor = 250_000L,
              grossExpenseMinor = 150_000L,
              refundMinor = 0L,
              netExpenseMinor = 150_000L,
              netSavingsMinor = 100_000L,
            ),
            MonthlyTrendRow(
              monthKey = "2026-11",
              monthLabel = "Nov",
              incomeMinor = 250_000L,
              grossExpenseMinor = 170_000L,
              refundMinor = 20_000L,
              netExpenseMinor = 150_000L,
              netSavingsMinor = 100_000L,
            ),
          ),
        transactionsInRange =
          listOf(
            FinancialTransaction(
              userId = "uid-1",
              accountId = "acc-1",
              type = TransactionType.EXPENSE,
              status = TransactionStatus.COMPLETED,
              amountMinor = 45_000L,
              currencyCode = "INR",
              category = "Food & Dining",
              merchantOrPayee = "Cafe",
              transactionDate = 1_700_000_000_000L,
            ),
            FinancialTransaction(
              userId = "uid-1",
              accountId = "acc-1",
              type = TransactionType.INCOME,
              status = TransactionStatus.COMPLETED,
              amountMinor = 500_000L,
              currencyCode = "INR",
              category = "Salary",
              transactionDate = 1_700_100_000_000L,
            ),
          ),
      )

    val netWorth =
      NetWorthCurrencyBreakdown(
        currencyCode = "INR",
        cashAndBankAssetsMinor = 150_000L,
        investmentAssetsMinor = 900_000L,
        totalAssetsMinor = 1_050_000L,
        loanLiabilitiesMinor = 250_000L,
        accountOrCardLiabilitiesMinor = 0L,
        totalLiabilitiesMinor = 250_000L,
        netWorthMinor = 800_000L,
        activeAccountCount = 1,
        activeInvestmentCount = 1,
        activeOrOverdueLoanCount = 1,
      )

    return FinancialReportSnapshot(
      window =
        ReportDateWindow(
          periodType = ReportPeriodType.MONTHLY,
          startDateMillis = 0L,
          endDateMillis = 1L,
          label = "November 2026",
        ),
      generatedAtMillis = 1_700_200_000_000L,
      currencies = listOf("INR"),
      reportsByCurrency =
        mapOf(
          "INR" to
            CurrencyFinancialReport(
              currencyCode = "INR",
              netWorth = netWorth,
              incomeExpense = incomeExpense,
              budgets = null,
              loans = null,
              investments = null,
              shopping = null,
            )
        ),
    )
  }

  @Test
  fun `maps balances and monthly figures from the report snapshot`() {
    val state = HomeDashboardMapper.map(snapshot(), null, formatter)

    assertFalse(state.isLoading)
    assertFalse(state.isEmpty)
    assertEquals("INR", state.currencyCode)
    assertEquals("INR 150000", state.totalBalanceText)
    assertEquals("INR 500000", state.monthlyIncomeText)
    assertEquals("INR 300000", state.monthlyExpenseText)
    assertEquals("INR 200000", state.netSavingsText)
    assertEquals("INR 800000", state.netWorthText)
    assertTrue(state.netWorthCaption.contains("INR 1050000"))
    assertTrue(state.netWorthCaption.contains("INR 250000"))
  }

  @Test
  fun `sorts categories by amount and keeps the largest five`() {
    val state = HomeDashboardMapper.map(snapshot(), null, formatter)

    assertEquals(2, state.categories.size)
    assertEquals("Transport", state.categories.first().category)
    assertEquals("INR 180000", state.categories.first().amountText)
    assertEquals(60f, state.categories.first().percentage, 0.01f)
  }

  @Test
  fun `recent transactions are newest first and carry the income flag`() {
    val state = HomeDashboardMapper.map(snapshot(), null, formatter)

    assertEquals(2, state.recentTransactions.size)
    val newest = state.recentTransactions.first()
    assertEquals("Salary", newest.title)
    assertTrue(newest.isIncome)
    val older = state.recentTransactions.last()
    assertEquals("Cafe", older.title)
    assertFalse(older.isIncome)
  }

  @Test
  fun `cash flow keeps the last six months in order`() {
    val state = HomeDashboardMapper.map(snapshot(), null, formatter)

    assertEquals(listOf("Oct", "Nov"), state.cashFlow.map { it.monthLabel })
    assertEquals(250_000L, state.cashFlow.first().incomeMinor)
    assertEquals(150_000L, state.cashFlow.first().expenseMinor)
  }

  @Test
  fun `an empty snapshot produces the empty state instead of fake numbers`() {
    val empty =
      FinancialReportSnapshot(
        window =
          ReportDateWindow(
            periodType = ReportPeriodType.MONTHLY,
            startDateMillis = 0L,
            endDateMillis = 1L,
            label = "November 2026",
          ),
        generatedAtMillis = 0L,
        currencies = emptyList(),
        reportsByCurrency = emptyMap(),
      )

    val state = HomeDashboardMapper.map(empty, null, formatter)

    assertTrue(state.isEmpty)
    assertFalse(state.isLoading)
    assertTrue(state.recentTransactions.isEmpty())
    assertTrue(state.categories.isEmpty())
  }
}
