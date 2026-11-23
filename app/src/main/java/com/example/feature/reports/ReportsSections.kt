package com.example.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanNumericStyle
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.BudgetAlertStatus
import com.example.domain.model.BudgetCurrencyReport
import com.example.domain.model.CurrencyFinancialReport
import com.example.domain.model.EmailReportSettings
import com.example.domain.model.IncomeExpenseCurrencyReport
import com.example.domain.model.InvestmentCurrencyReport
import com.example.domain.model.LoanCurrencyReport
import com.example.domain.model.Money
import com.example.domain.model.ReportExportFormat
import com.example.domain.model.ReportFrequency
import com.example.domain.model.ShoppingCurrencyReport
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.feature.investments.AssetAllocationDonutChart
import com.example.feature.investments.PortfolioGrowthChart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OverviewReportSection(
  report: CurrencyFinancialReport,
  windowLabel: String,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val currency = report.currencyCode
  val nw = report.netWorth
  val ie = report.incomeExpense

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
    OceanCard(modifier = Modifier.testTag("reports_net_worth_card")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Net Worth Statement ($currency)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        OceanStatusBadge(
          text = if (nw.netWorthMinor >= 0L) "Positive" else "Net Debt",
          tone = if (nw.netWorthMinor >= 0L) BadgeTone.POSITIVE else BadgeTone.NEGATIVE,
        )
      }
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      Text(
        text = fmtMoney(nw.netWorthMinor, currency),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = if (nw.netWorthMinor >= 0L) extended.gold else extended.negative,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.sm))
      ReportMetricRow(
        label = "Cash & Bank Balances (${nw.activeAccountCount} accounts)",
        value = fmtMoney(nw.cashAndBankAssetsMinor, currency),
        valueColor = extended.positive,
      )
      ReportMetricRow(
        label = "Investment Valuation (${nw.activeInvestmentCount} active)",
        value = fmtMoney(nw.investmentAssetsMinor, currency),
        valueColor = MaterialTheme.colorScheme.secondary,
      )
      ReportMetricRow(
        label = "Total Assets",
        value = fmtMoney(nw.totalAssetsMinor, currency),
        isBold = true,
      )
      HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = extended.cardBorder)
      ReportMetricRow(
        label = "Loan Liabilities (${nw.activeOrOverdueLoanCount} active/overdue)",
        value = fmtMoney(nw.loanLiabilitiesMinor, currency),
        valueColor = extended.negative,
      )
      if (nw.accountOrCardLiabilitiesMinor > 0L) {
        ReportMetricRow(
          label = "Card / Overdraft Liabilities",
          value = fmtMoney(nw.accountOrCardLiabilitiesMinor, currency),
          valueColor = extended.negative,
        )
      }
      ReportMetricRow(
        label = "Total Liabilities",
        value = fmtMoney(nw.totalLiabilitiesMinor, currency),
        valueColor = extended.negative,
        isBold = true,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      Text(
        text = "Cash/bank balances and investment holdings are tracked distinctly and never added to transaction sums.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    OceanCard(modifier = Modifier.testTag("reports_financial_dashboard_card")) {
      Text(
        text = "Financial Summary • $windowLabel ($currency)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        KpiBox(
          title = "Total Income",
          value = fmtMoney(ie.totalIncomeMinor, currency),
          valueColor = extended.positive,
          modifier = Modifier.weight(1f),
        )
        KpiBox(
          title = "Net Expenses",
          value = fmtMoney(ie.netExpensesMinor, currency),
          valueColor = extended.negative,
          modifier = Modifier.weight(1f),
        )
      }
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        KpiBox(
          title = "Net Savings",
          value = fmtMoney(ie.netSavingsMinor, currency),
          valueColor = if (ie.netSavingsMinor >= 0L) extended.gold else extended.negative,
          modifier = Modifier.weight(1f),
        )
        KpiBox(
          title = "Savings Rate",
          value = "${ie.savingsPercentage.toPlainString()}%",
          valueColor = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.weight(1f),
        )
      }
    }

    if (ie.monthlyTrends.isNotEmpty()) {
      IncomeVsExpenseBarChart(trends = ie.monthlyTrends, currencyCode = currency)
    }
  }
}

@Composable
fun IncomeExpenseReportSection(
  report: IncomeExpenseCurrencyReport,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val currency = report.currencyCode

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
    OceanCard(modifier = Modifier.testTag("reports_cashflow_summary_card")) {
      Text(
        text = "Income vs Expense Detail ($currency)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      ReportMetricRow("Total Income (${report.completedIncomeCount})", fmtMoney(report.totalIncomeMinor, currency), extended.positive)
      ReportMetricRow("Gross Expenses (${report.completedExpenseCount})", fmtMoney(report.grossExpensesMinor, currency))
      ReportMetricRow("Refunds & Reversals (${report.completedRefundCount})", fmtMoney(report.totalRefundsAndReversalsMinor, currency), extended.positive)
      ReportMetricRow("Net Expenses", fmtMoney(report.netExpensesMinor, currency), extended.negative, isBold = true)
      ReportMetricRow("Net Savings (${report.savingsPercentage.toPlainString()}%)", fmtMoney(report.netSavingsMinor, currency), extended.gold, isBold = true)
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      Row(horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
        OceanStatusBadge(text = "${report.excludedTransferCount} Transfers Excluded", tone = BadgeTone.INFO)
        if (report.excludedNonCompletedCount > 0) {
          OceanStatusBadge(text = "${report.excludedNonCompletedCount} Pending/Failed Excluded", tone = BadgeTone.WARNING)
        }
      }
    }

    IncomeVsExpenseBarChart(trends = report.monthlyTrends, currencyCode = currency)

    CategoryBreakdownChartCard(
      title = "Expense Category Breakdown ($currency)",
      rows = report.expenseByCategory,
      currencyCode = currency,
    )

    if (report.incomeByCategory.isNotEmpty()) {
      CategoryBreakdownChartCard(
        title = "Income Sources Breakdown ($currency)",
        rows = report.incomeByCategory,
        currencyCode = currency,
      )
    }
  }
}

@Composable
fun BudgetReportSection(
  report: BudgetCurrencyReport?,
  currencyCode: String,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors

  if (report == null) {
    EmptyReportCategoryCard(
      title = "No Budgets Configured ($currencyCode)",
      body = "Create monthly or custom budgets in the Budget tab to track allocated, spent, remaining, and overspent metrics.",
      modifier = modifier,
    )
    return
  }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
    OceanCard(modifier = Modifier.testTag("reports_budget_summary_card")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Budget Performance ($currencyCode)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        OceanStatusBadge(
          text = if (report.overspentBudgetCount > 0) "${report.overspentBudgetCount} Overspent" else "${report.activeBudgetCount} Tracked",
          tone = if (report.overspentBudgetCount > 0) BadgeTone.NEGATIVE else BadgeTone.POSITIVE,
        )
      }
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      ReportMetricRow("Budget Allocated", fmtMoney(report.totalAllocatedMinor, currencyCode))
      ReportMetricRow("Actual Spending", fmtMoney(report.totalSpentMinor, currencyCode), extended.negative)
      ReportMetricRow(
        label = "Remaining Budget",
        value = fmtMoney(report.totalRemainingMinor, currencyCode),
        valueColor = if (report.totalRemainingMinor >= 0L) extended.positive else extended.negative,
      )
      ReportMetricRow("Total Overspending", fmtMoney(report.totalOverspendingMinor, currencyCode), extended.warning)
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      val fraction = (report.utilizationPercent.toFloat() / 100f).coerceIn(0f, 1f)
      LinearProgressIndicator(
        progress = { fraction },
        color = if (report.overspentBudgetCount > 0) extended.negative else extended.gold,
        trackColor = extended.cardBorder,
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Utilization: ${report.utilizationPercent.toPlainString()}%",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    OceanCard(modifier = Modifier.testTag("reports_budget_history_card")) {
      Text(
        text = "Historical Budget Performance",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      report.historicalPerformance.forEach { row ->
        val tone =
          when (row.alertStatus) {
            BudgetAlertStatus.EXCEEDED_100 -> BadgeTone.NEGATIVE
            BudgetAlertStatus.WARNING_90, BudgetAlertStatus.APPROACHING_75 -> BadgeTone.WARNING
            BudgetAlertStatus.NORMAL -> BadgeTone.POSITIVE
            BudgetAlertStatus.PERIOD_ENDED -> BadgeTone.INFO
          }
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(text = row.category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OceanStatusBadge(text = "${row.utilizationPercent.toPlainString()}%", tone = tone)
          }
          Text(
            text = "${dateFmt.format(Date(row.startDate))} – ${dateFmt.format(Date(row.endDate))}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          ReportMetricRow("Allocated / Spent", "${fmtMoney(row.allocatedMinor, currencyCode)} / ${fmtMoney(row.spentMinor, currencyCode)}")
          if (row.overspendingMinor > 0L) {
            ReportMetricRow("Overspending", fmtMoney(row.overspendingMinor, currencyCode), extended.negative)
          }
        }
        HorizontalDivider(color = extended.cardBorder)
      }
    }
  }
}

@Composable
fun LoansEmiReportSection(
  report: LoanCurrencyReport?,
  currencyCode: String,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors

  if (report == null) {
    EmptyReportCategoryCard(
      title = "No Loans or EMIs Tracked ($currencyCode)",
      body = "Add home, vehicle, education, or personal loans from the Home dashboard to view principal, EMI payments, and interest reports.",
      modifier = modifier,
    )
    return
  }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
    OceanCard(modifier = Modifier.testTag("reports_loans_summary_card")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Loans & EMI Summary ($currencyCode)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        OceanStatusBadge(
          text = if (report.overdueCount > 0) "${report.overdueCount} Overdue" else "${report.activeCount} Active",
          tone = if (report.overdueCount > 0) BadgeTone.NEGATIVE else BadgeTone.INFO,
        )
      }
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      ReportMetricRow("Original Principal", fmtMoney(report.totalPrincipalMinor, currencyCode))
      ReportMetricRow("Outstanding Principal", fmtMoney(report.totalOutstandingMinor, currencyCode), extended.negative, isBold = true)
      ReportMetricRow("Scheduled Monthly EMI", fmtMoney(report.totalMonthlyEmiMinor, currencyCode), extended.gold)
      ReportMetricRow("Period EMI Paid (${report.periodPaymentsCount} payments)", fmtMoney(report.periodTotalEmiPaidMinor, currencyCode), extended.positive)
      ReportMetricRow("Period Interest Paid", fmtMoney(report.periodInterestPaidMinor, currencyCode), extended.warning)
      ReportMetricRow("All-Time Interest Paid", fmtMoney(report.allTimeInterestPaidMinor, currencyCode))
    }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    if (report.overdueLoans.isNotEmpty() || report.upcomingLoans.isNotEmpty()) {
      OceanCard(modifier = Modifier.testTag("reports_loans_schedule_card")) {
        Text(
          text = "Overdue & Upcoming EMI Schedule",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(OceanSpacing.xs))
        report.overdueLoans.forEach { loan ->
          ReportMetricRow(
            label = "OVERDUE • ${loan.name} (Due ${dateFmt.format(Date(loan.nextDueDate))})",
            value = fmtMoney(loan.monthlyEmiMinor, currencyCode),
            valueColor = extended.negative,
          )
        }
        report.upcomingLoans.forEach { loan ->
          ReportMetricRow(
            label = "${loan.name} (Due ${dateFmt.format(Date(loan.nextDueDate))})",
            value = fmtMoney(loan.monthlyEmiMinor, currencyCode),
            valueColor = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

@Composable
fun InvestmentsReportSection(
  report: InvestmentCurrencyReport?,
  currencyCode: String,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors

  if (report == null) {
    EmptyReportCategoryCard(
      title = "No Investments Tracked ($currencyCode)",
      body = "Record Indian Stocks, Mutual Funds, ETFs, Gold, Fixed Deposits, or custom holdings to analyze valuations, P&L, and authentic historical growth.",
      modifier = modifier,
    )
    return
  }

  val summary = report.portfolioSummary
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
    OceanCard(modifier = Modifier.testTag("reports_investments_summary_card")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Investment Report ($currencyCode)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        OceanStatusBadge(text = "India Only", tone = BadgeTone.INFO)
      }
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      ReportMetricRow("Invested Cost Basis", fmtMoney(summary.totalInvestedMinor, currencyCode))
      ReportMetricRow("Latest Recorded Valuation", fmtMoney(summary.totalValuationMinor, currencyCode), MaterialTheme.colorScheme.secondary, isBold = true)
      ReportMetricRow(
        label = "Unrealized Profit / Loss",
        value = fmtMoney(summary.unrealizedPnlMinor, currencyCode),
        valueColor = if (summary.unrealizedPnlMinor >= 0L) extended.positive else extended.negative,
      )
      ReportMetricRow(
        label = "Realized Profit / Loss",
        value = fmtMoney(summary.realizedPnlMinor, currencyCode),
        valueColor = if (summary.realizedPnlMinor >= 0L) extended.positive else extended.negative,
      )
      ReportMetricRow("Period Purchases / Sales", "${fmtMoney(report.periodBuyTotalMinor, currencyCode)} / ${fmtMoney(report.periodSellTotalMinor, currencyCode)}")
    }

    AssetAllocationDonutChart(
      allocations = summary.categoryAllocations,
      totalValuationMinor = summary.totalValuationMinor,
      currencyCode = currencyCode,
    )

    PortfolioGrowthChart(
      growthPoints = summary.growthPoints,
      unrealizedPnlMinor = summary.unrealizedPnlMinor,
      realizedPnlMinor = summary.realizedPnlMinor,
      currencyCode = currencyCode,
    )

    OceanCard(modifier = Modifier.testTag("reports_investments_holdings_card")) {
      Text(
        text = "Holdings Valuation & Return (${report.holdingRows.size})",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      report.holdingRows.forEach { row ->
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "${row.name} (${row.category.displayName})",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = fmtMoney(row.valuationMinor, currencyCode),
              style = OceanNumericStyle,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Invested: ${fmtMoney(row.investedMinor, currencyCode)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "Unrealized ${fmtMoney(row.unrealizedPnlMinor, currencyCode)}",
              style = MaterialTheme.typography.labelSmall,
              color = if (row.unrealizedPnlMinor >= 0L) extended.positive else extended.negative,
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Realized: ${fmtMoney(row.realizedPnlMinor, currencyCode)}",
              style = MaterialTheme.typography.labelSmall,
              color = if (row.realizedPnlMinor >= 0L) extended.positive else extended.negative,
            )
            Text(
              text = "Total return: ${row.returnPercent.toPlainString()}%",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (row.lastPriceUpdateDate <= 0L) {
            OceanStatusBadge(text = "Awaiting first price", tone = BadgeTone.WARNING)
          } else if (row.isStalePrice) {
            OceanStatusBadge(text = "Price older than 7 days", tone = BadgeTone.WARNING)
          }
        }
        HorizontalDivider(color = extended.cardBorder)
      }
    }
  }
}

@Composable
fun ShoppingReportSection(
  report: ShoppingCurrencyReport?,
  currencyCode: String,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors

  if (report == null) {
    EmptyReportCategoryCard(
      title = "No Shopping Lists Recorded ($currencyCode)",
      body = "Add shopping lists and mark purchased items in the Shopping List tab to view estimated vs actual spending, category and store breakdowns.",
      modifier = modifier,
    )
    return
  }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
    OceanCard(modifier = Modifier.testTag("reports_shopping_summary_card")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Shopping Spend Analysis ($currencyCode)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        OceanStatusBadge(
          text = if (report.savingsOrOverspendMinor >= 0L) "On/Under Est." else "Overspent",
          tone = if (report.savingsOrOverspendMinor >= 0L) BadgeTone.POSITIVE else BadgeTone.NEGATIVE,
        )
      }
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      ReportMetricRow("Lists / Total Items", "${report.totalLists} lists • ${report.totalItems} items")
      ReportMetricRow("Total Planned Estimated", fmtMoney(report.totalEstimatedMinor, currencyCode))
      ReportMetricRow("Actual Spent in Period (${report.purchasedItemsInPeriod} bought)", fmtMoney(report.actualSpentInPeriodMinor, currencyCode), extended.negative, isBold = true)
      ReportMetricRow(
        label = if (report.savingsOrOverspendMinor >= 0L) "Savings vs Estimate" else "Overspending vs Estimate",
        value = fmtMoney(kotlin.math.abs(report.savingsOrOverspendMinor), currencyCode),
        valueColor = if (report.savingsOrOverspendMinor >= 0L) extended.positive else extended.negative,
      )
    }

    if (report.categoryBreakdown.isNotEmpty()) {
      OceanCard(modifier = Modifier.testTag("reports_shopping_categories_card")) {
        Text(text = "Shopping Category Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(OceanSpacing.xs))
        report.categoryBreakdown.forEach { cat ->
          ReportMetricRow(
            label = "${cat.category} (${cat.purchasedItemCount}/${cat.totalItemCount} bought)",
            value = "Est ${fmtMoney(cat.estimatedTotalMinor, currencyCode)} • Act ${fmtMoney(cat.actualSpentMinor, currencyCode)}",
          )
        }
      }
    }

    if (report.storeBreakdown.isNotEmpty()) {
      OceanCard(modifier = Modifier.testTag("reports_shopping_stores_card")) {
        Text(text = "Store Spend Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(OceanSpacing.xs))
        report.storeBreakdown.forEach { st ->
          ReportMetricRow(
            label = "${st.storeName} (${st.purchasedItemCount} items)",
            value = fmtMoney(st.actualSpentMinor, currencyCode),
            valueColor = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }
  }
}

@Composable
fun ExportsAndEmailSection(
  emailSettings: EmailReportSettings,
  defaultRecipientFallback: String,
  onSaveExport: (ReportExportFormat) -> Unit,
  onShareExport: (ReportExportFormat) -> Unit,
  onToggleEmailEnabled: (Boolean) -> Unit,
  onSelectFrequency: (ReportFrequency) -> Unit,
  onSaveRecipient: (String) -> Unit,
  onOpenEmailPreview: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  var recipientDraft by remember { mutableStateOf("") }

  LaunchedEffect(emailSettings.recipientEmail, defaultRecipientFallback) {
    recipientDraft = emailSettings.recipientEmail.ifBlank { defaultRecipientFallback }
  }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
    OceanCard(modifier = Modifier.testTag("reports_export_card")) {
      Text(
        text = "Export Sanitized Financial Statements",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.xxs))
      Text(
        text = "Includes selected date window, multi-currency summaries, generation timestamp & transactions. Sensitive IDs, keys, and account numbers are excluded.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.sm))

      listOf(
        Triple(ReportExportFormat.PDF, "PDF Statement", Icons.Rounded.PictureAsPdf),
        Triple(ReportExportFormat.CSV, "CSV Ledger Sheet", Icons.Rounded.TableChart),
        Triple(ReportExportFormat.DOCX, "Word Document (.docx)", Icons.Rounded.Assessment),
      ).forEach { (format, label, icon) ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(icon, contentDescription = null, tint = extended.gold, modifier = Modifier.size(20.dp))
          Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
          )
          OutlinedButton(
            onClick = { onSaveExport(format) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("export_save_${format.name.lowercase()}_button"),
          ) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Save")
          }
          Button(
            onClick = { onShareExport(format) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("export_share_${format.name.lowercase()}_button"),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Share")
          }
        }
      }
    }

    OceanCard(modifier = Modifier.testTag("reports_email_settings_card")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.Email, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
          Spacer(modifier = Modifier.width(OceanSpacing.xs))
          Text(
            text = "Email Summary Preferences",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
        Switch(
          checked = emailSettings.enabled,
          onCheckedChange = onToggleEmailEnabled,
          modifier = Modifier.testTag("email_reports_enable_switch"),
        )
      }
      Spacer(modifier = Modifier.height(OceanSpacing.xxs))
      Text(
        text = "User-controlled reporting only: reports are never sent silently or automatically without your explicit preview and send confirmation.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.sm))

      Text(text = "Preferred Frequency", style = MaterialTheme.typography.labelMedium)
      Row(horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
        ReportFrequency.entries.forEach { freq ->
          FilterChip(
            selected = emailSettings.frequency == freq,
            onClick = { onSelectFrequency(freq) },
            label = { Text(freq.label) },
            modifier = Modifier.testTag("email_frequency_${freq.name.lowercase()}"),
          )
        }
      }

      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      OutlinedTextField(
        value = recipientDraft,
        onValueChange = { recipientDraft = it },
        label = { Text("Recipient Email Address") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("email_recipient_input"),
      )

      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
      ) {
        OutlinedButton(
          onClick = { onSaveRecipient(recipientDraft) },
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("email_save_recipient_button"),
        ) {
          Text("Save Recipient")
        }
        Button(
          onClick = { onOpenEmailPreview(recipientDraft) },
          colors = ButtonDefaults.buttonColors(containerColor = extended.gold, contentColor = Color.Black),
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("email_preview_send_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Preview & Send", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun EmptyReportCategoryCard(
  title: String,
  body: String,
  modifier: Modifier = Modifier,
) {
  OceanCard(modifier = modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Rounded.AccountBalance, contentDescription = null, tint = OceanTheme.colors.gold)
      Spacer(modifier = Modifier.width(OceanSpacing.xs))
      Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    Text(
      text = body,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun KpiBox(
  title: String,
  value: String,
  valueColor: Color,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = OceanNumericStyle,
      color = valueColor,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
private fun ReportMetricRow(
  label: String,
  value: String,
  valueColor: Color = MaterialTheme.colorScheme.onSurface,
  isBold: Boolean = false,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = if (isBold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
      modifier = Modifier.weight(1f),
    )
    Spacer(modifier = Modifier.width(OceanSpacing.xs))
    Text(
      text = value,
      style = OceanNumericStyle,
      color = valueColor,
      fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
    )
  }
}

private fun fmtMoney(minorUnits: Long, currencyCode: String): String =
  FormatMoneyUseCase().format(Money.fromMinorUnits(minorUnits, currencyCode))
