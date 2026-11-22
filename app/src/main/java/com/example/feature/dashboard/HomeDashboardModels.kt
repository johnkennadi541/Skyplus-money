package com.example.feature.dashboard

/**
 * Home dashboard card identities. The order of this enum is the default display order, and the
 * user can reorder or hide the cards from the dashboard itself.
 */
enum class HomeCardId {
  SUMMARY,
  NET_WORTH,
  CASH_FLOW,
  CATEGORY_BREAKDOWN,
  RECENT_TRANSACTIONS,
}

/** One month of the income versus expense chart. */
data class HomeCashFlowBar(
  val monthLabel: String,
  val incomeMinor: Long,
  val expenseMinor: Long,
)

/** One row of the category-wise expense breakdown. */
data class HomeCategorySlice(
  val category: String,
  val amountMinor: Long,
  val amountText: String,
  val percentage: Float,
)

/** One row of the recent transactions list. */
data class HomeTransactionRow(
  val id: String,
  val title: String,
  val category: String,
  val amountText: String,
  val isIncome: Boolean,
  val dateMillis: Long,
)

/**
 * Everything the Home dashboard renders. All money values are already formatted strings produced
 * by [com.example.domain.usecase.FormatMoneyUseCase] so the UI never does arithmetic.
 */
data class HomeDashboardUiState(
  val isLoading: Boolean = true,
  val hasError: Boolean = false,
  val isEmpty: Boolean = false,
  val currencyCode: String = "INR",
  val totalBalanceText: String = "",
  val monthlyIncomeText: String = "",
  val monthlyExpenseText: String = "",
  val netSavingsText: String = "",
  val netWorthText: String = "",
  val netWorthCaption: String = "",
  val cashFlow: List<HomeCashFlowBar> = emptyList(),
  val categories: List<HomeCategorySlice> = emptyList(),
  val recentTransactions: List<HomeTransactionRow> = emptyList(),
  val cards: List<HomeCardId> = HomeCardId.entries.toList(),
  val hiddenCards: Set<HomeCardId> = emptySet(),
  val lastUpdatedMillis: Long = 0L,
)
