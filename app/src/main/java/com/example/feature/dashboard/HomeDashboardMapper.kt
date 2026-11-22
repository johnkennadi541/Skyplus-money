package com.example.feature.dashboard

import com.example.domain.model.CurrencyFinancialReport
import com.example.domain.model.FinancialReportSnapshot
import com.example.domain.model.Money
import com.example.domain.model.TransactionType

/**
 * Pure mapping from the already-tested [FinancialReportSnapshot] to the Home dashboard state.
 *
 * No arithmetic on money happens here beyond reading minor units straight off the report engine,
 * and no Android type is touched, so this is fully unit testable on the JVM.
 */
object HomeDashboardMapper {

  private const val RECENT_TRANSACTION_LIMIT = 5
  private const val CATEGORY_LIMIT = 5
  private const val CASH_FLOW_MONTHS = 6

  fun map(
    snapshot: FinancialReportSnapshot,
    preferredCurrencyCode: String? = null,
    formatMoney: (Money) -> String,
  ): HomeDashboardUiState {
    val currencyCode =
      preferredCurrencyCode?.takeIf { snapshot.reportsByCurrency.containsKey(it) }
        ?: snapshot.currencies.firstOrNull()

    val report: CurrencyFinancialReport? = currencyCode?.let { snapshot.reportsByCurrency[it] }
    if (currencyCode == null || report == null) {
      return HomeDashboardUiState(isLoading = false, isEmpty = true)
    }

    fun money(minor: Long): String = formatMoney(Money.fromMinorUnits(minor, currencyCode))

    val incomeExpense = report.incomeExpense
    val netWorth = report.netWorth

    val cashFlow =
      incomeExpense.monthlyTrends
        .takeLast(CASH_FLOW_MONTHS)
        .map { trend ->
          HomeCashFlowBar(
            monthLabel = trend.monthLabel,
            incomeMinor = trend.incomeMinor,
            expenseMinor = trend.netExpenseMinor,
          )
        }

    val categories =
      incomeExpense.expenseByCategory
        .sortedByDescending { it.totalMinor }
        .take(CATEGORY_LIMIT)
        .map { row ->
          HomeCategorySlice(
            category = row.category,
            amountMinor = row.totalMinor,
            amountText = money(row.totalMinor),
            percentage = row.percentage.toFloat().coerceIn(0f, 100f),
          )
        }

    val recentTransactions =
      incomeExpense.transactionsInRange
        .sortedByDescending { it.transactionDate }
        .take(RECENT_TRANSACTION_LIMIT)
        .map { transaction ->
          HomeTransactionRow(
            id = transaction.id,
            title =
              transaction.merchantOrPayee?.takeIf { it.isNotBlank() } ?: transaction.category,
            category = transaction.category,
            amountText = formatMoney(Money.fromMinorUnits(transaction.amountMinor, transaction.currencyCode)),
            isIncome = transaction.type == TransactionType.INCOME,
            dateMillis = transaction.transactionDate,
          )
        }

    return HomeDashboardUiState(
      isLoading = false,
      hasError = false,
      isEmpty = false,
      currencyCode = currencyCode,
      totalBalanceText = money(netWorth.cashAndBankAssetsMinor),
      monthlyIncomeText = money(incomeExpense.totalIncomeMinor),
      monthlyExpenseText = money(incomeExpense.netExpensesMinor),
      netSavingsText = money(incomeExpense.netSavingsMinor),
      netWorthText = money(netWorth.netWorthMinor),
      netWorthCaption =
        "Assets ${money(netWorth.totalAssetsMinor)} · Liabilities ${money(netWorth.totalLiabilitiesMinor)}",
      cashFlow = cashFlow,
      categories = categories,
      recentTransactions = recentTransactions,
      lastUpdatedMillis = snapshot.generatedAtMillis,
    )
  }
}
