package com.example.domain.model

import java.math.BigDecimal

enum class ReportPeriodType(val label: String) {
  DAILY("Daily"),
  WEEKLY("Weekly"),
  MONTHLY("Monthly"),
  YEARLY("Yearly"),
  CUSTOM("Custom"),
}

enum class ReportCategoryTab(val label: String) {
  OVERVIEW("Overview"),
  INCOME_EXPENSE("Cash Flow"),
  BUDGETS("Budgets"),
  LOANS_EMI("Loans & EMI"),
  INVESTMENTS("Investments"),
  SHOPPING("Shopping"),
  EXPORTS_EMAIL("Exports & Email"),
}

enum class ReportExportFormat(
  val label: String,
  val extension: String,
  val mimeType: String,
) {
  PDF(
    label = "PDF",
    extension = "pdf",
    mimeType = "application/pdf",
  ),
  CSV(
    label = "CSV",
    extension = "csv",
    mimeType = "text/csv",
  ),
  DOCX(
    label = "DOCX",
    extension = "docx",
    mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  ),
}

enum class ReportFrequency(val label: String) {
  WEEKLY("Weekly"),
  MONTHLY("Monthly"),
}

data class EmailReportSettings(
  val enabled: Boolean = false,
  val frequency: ReportFrequency = ReportFrequency.MONTHLY,
  val recipientEmail: String = "",
  val lastPreparedAtMillis: Long? = null,
)

data class ReportDateWindow(
  val periodType: ReportPeriodType,
  val startDateMillis: Long,
  val endDateMillis: Long,
  val label: String,
)

data class CategoryBreakdownRow(
  val category: String,
  val currencyCode: String,
  val totalMinor: Long,
  val grossMinor: Long,
  val refundOffsetMinor: Long,
  val percentage: BigDecimal,
  val transactionCount: Int,
)

data class MonthlyTrendRow(
  val monthKey: String,
  val monthLabel: String,
  val incomeMinor: Long,
  val grossExpenseMinor: Long,
  val refundMinor: Long,
  val netExpenseMinor: Long,
  val netSavingsMinor: Long,
)

data class NetWorthCurrencyBreakdown(
  val currencyCode: String,
  val cashAndBankAssetsMinor: Long,
  val investmentAssetsMinor: Long,
  val totalAssetsMinor: Long,
  val loanLiabilitiesMinor: Long,
  val accountOrCardLiabilitiesMinor: Long,
  val totalLiabilitiesMinor: Long,
  val netWorthMinor: Long,
  val activeAccountCount: Int,
  val activeInvestmentCount: Int,
  val activeOrOverdueLoanCount: Int,
)

data class IncomeExpenseCurrencyReport(
  val currencyCode: String,
  val totalIncomeMinor: Long,
  val grossExpensesMinor: Long,
  val totalRefundsAndReversalsMinor: Long,
  val netExpensesMinor: Long,
  val netSavingsMinor: Long,
  val savingsPercentage: BigDecimal,
  val completedIncomeCount: Int,
  val completedExpenseCount: Int,
  val completedRefundCount: Int,
  val excludedTransferCount: Int,
  val excludedNonCompletedCount: Int,
  val expenseByCategory: List<CategoryBreakdownRow>,
  val incomeByCategory: List<CategoryBreakdownRow>,
  val monthlyTrends: List<MonthlyTrendRow>,
  val transactionsInRange: List<FinancialTransaction>,
)

data class BudgetPerformanceHistoryRow(
  val budgetId: String,
  val category: String,
  val currencyCode: String,
  val startDate: Long,
  val endDate: Long,
  val allocatedMinor: Long,
  val spentMinor: Long,
  val remainingMinor: Long,
  val overspendingMinor: Long,
  val utilizationPercent: BigDecimal,
  val alertStatus: BudgetAlertStatus,
  val overlapsSelectedWindow: Boolean,
)

data class BudgetCurrencyReport(
  val currencyCode: String,
  val totalAllocatedMinor: Long,
  val totalSpentMinor: Long,
  val totalRemainingMinor: Long,
  val totalOverspendingMinor: Long,
  val utilizationPercent: BigDecimal,
  val activeBudgetCount: Int,
  val overspentBudgetCount: Int,
  val historicalPerformance: List<BudgetPerformanceHistoryRow>,
)

data class LoanCurrencyReport(
  val currencyCode: String,
  val totalPrincipalMinor: Long,
  val totalOutstandingMinor: Long,
  val totalPaidAllTimeMinor: Long,
  val totalMonthlyEmiMinor: Long,
  val periodPaymentsCount: Int,
  val periodTotalEmiPaidMinor: Long,
  val periodPrincipalPaidMinor: Long,
  val periodInterestPaidMinor: Long,
  val allTimeInterestPaidMinor: Long,
  val activeCount: Int,
  val overdueCount: Int,
  val completedCount: Int,
  val upcomingLoans: List<Loan>,
  val overdueLoans: List<Loan>,
)

data class InvestmentHoldingReportRow(
  val holdingId: String,
  val name: String,
  val category: InvestmentCategory,
  val currencyCode: String,
  val investedMinor: Long,
  val valuationMinor: Long,
  val unrealizedPnlMinor: Long,
  val realizedPnlMinor: Long,
  val returnPercent: BigDecimal,
  val lastPriceUpdateDate: Long,
  val isStalePrice: Boolean,
)

data class InvestmentCurrencyReport(
  val currencyCode: String,
  val portfolioSummary: PortfolioCurrencySummary,
  val periodBuyTotalMinor: Long,
  val periodSellTotalMinor: Long,
  val periodRealizedPnlMinor: Long,
  val holdingRows: List<InvestmentHoldingReportRow>,
)

data class ShoppingCategoryReportRow(
  val category: String,
  val totalItemCount: Int,
  val purchasedItemCount: Int,
  val estimatedTotalMinor: Long,
  val actualSpentMinor: Long,
  val savingsOrOverspendMinor: Long,
)

data class ShoppingStoreReportRow(
  val storeName: String,
  val purchasedItemCount: Int,
  val estimatedOfPurchasedMinor: Long,
  val actualSpentMinor: Long,
  val savingsOrOverspendMinor: Long,
)

data class ShoppingCurrencyReport(
  val currencyCode: String,
  val totalLists: Int,
  val totalItems: Int,
  val purchasedItemsInPeriod: Int,
  val totalEstimatedMinor: Long,
  val estimatedOfPurchasedInPeriodMinor: Long,
  val actualSpentInPeriodMinor: Long,
  val savingsOrOverspendMinor: Long,
  val categoryBreakdown: List<ShoppingCategoryReportRow>,
  val storeBreakdown: List<ShoppingStoreReportRow>,
  val recentPurchases: List<ShoppingListItem>,
)

data class CurrencyFinancialReport(
  val currencyCode: String,
  val netWorth: NetWorthCurrencyBreakdown,
  val incomeExpense: IncomeExpenseCurrencyReport,
  val budgets: BudgetCurrencyReport?,
  val loans: LoanCurrencyReport?,
  val investments: InvestmentCurrencyReport?,
  val shopping: ShoppingCurrencyReport?,
)

data class FinancialReportSnapshot(
  val window: ReportDateWindow,
  val generatedAtMillis: Long,
  val currencies: List<String>,
  val reportsByCurrency: Map<String, CurrencyFinancialReport>,
)

data class ExportedReportFile(
  val format: ReportExportFormat,
  val fileName: String,
  val mimeType: String,
  val bytes: ByteArray,
  val generatedAtMillis: Long,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ExportedReportFile) return false
    return format == other.format &&
      fileName == other.fileName &&
      mimeType == other.mimeType &&
      generatedAtMillis == other.generatedAtMillis &&
      bytes.contentEquals(other.bytes)
  }

  override fun hashCode(): Int {
    var result = format.hashCode()
    result = 31 * result + fileName.hashCode()
    result = 31 * result + mimeType.hashCode()
    result = 31 * result + bytes.contentHashCode()
    result = 31 * result + generatedAtMillis.hashCode()
    return result
  }
}
