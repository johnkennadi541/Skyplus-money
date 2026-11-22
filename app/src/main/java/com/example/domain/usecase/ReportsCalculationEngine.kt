package com.example.domain.usecase

import com.example.domain.model.Budget
import com.example.domain.model.BudgetCurrencyReport
import com.example.domain.model.BudgetPerformanceHistoryRow
import com.example.domain.model.CategoryBreakdownRow
import com.example.domain.model.CurrencyFinancialReport
import com.example.domain.model.FinancialAccount
import com.example.domain.model.FinancialReportSnapshot
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.IncomeExpenseCurrencyReport
import com.example.domain.model.InvestmentCurrencyReport
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.InvestmentHoldingReportRow
import com.example.domain.model.InvestmentPriceHistory
import com.example.domain.model.InvestmentTransaction
import com.example.domain.model.InvestmentTransactionType
import com.example.domain.model.Loan
import com.example.domain.model.LoanCurrencyReport
import com.example.domain.model.LoanPayment
import com.example.domain.model.LoanPaymentStatus
import com.example.domain.model.LoanStatus
import com.example.domain.model.MonthlyTrendRow
import com.example.domain.model.NetWorthCurrencyBreakdown
import com.example.domain.model.ReportDateWindow
import com.example.domain.model.ReportPeriodType
import com.example.domain.model.ShoppingCategoryReportRow
import com.example.domain.model.ShoppingCurrencyReport
import com.example.domain.model.ShoppingList
import com.example.domain.model.ShoppingListItem
import com.example.domain.model.ShoppingStoreReportRow
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Precision Phase 3F financial analytics engine.
 *
 * Strictly uses [BigDecimal] and exact [Long] minor units — never [Float] or [Double].
 * Preserves per-ISO-4217 currency isolation without inventing exchange rates.
 * Prevents Net Worth double counting across accounts, transactions, investments, and loans.
 */
@Singleton
class ReportsCalculationEngine @Inject constructor(
  private val budgetEngine: BudgetCalculationEngine,
  private val emiEngine: EmiCalculationEngine,
  private val investmentEngine: InvestmentCalculationEngine,
  private val shoppingEngine: ShoppingCalculationEngine,
) {

  companion object {
    private val HUNDRED = BigDecimal("100")
    private val ZERO_PCT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
  }

  fun buildDateWindow(
    periodType: ReportPeriodType,
    customStartMillis: Long? = null,
    customEndMillis: Long? = null,
    nowMillis: Long = System.currentTimeMillis(),
  ): ReportDateWindow {
    val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    if (periodType == ReportPeriodType.CUSTOM && customStartMillis != null && customEndMillis != null) {
      val start = minOf(customStartMillis, customEndMillis)
      val end = maxOf(customStartMillis, customEndMillis)
      val label = "${dateFmt.format(Date(start))} – ${dateFmt.format(Date(end))}"
      return ReportDateWindow(
        periodType = ReportPeriodType.CUSTOM,
        startDateMillis = start,
        endDateMillis = end,
        label = label,
      )
    }

    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMillis

    val endCal = Calendar.getInstance()
    endCal.timeInMillis = nowMillis
    endCal.set(Calendar.HOUR_OF_DAY, 23)
    endCal.set(Calendar.MINUTE, 59)
    endCal.set(Calendar.SECOND, 59)
    endCal.set(Calendar.MILLISECOND, 999)
    val endMillis = endCal.timeInMillis

    val startCal = Calendar.getInstance()
    startCal.timeInMillis = nowMillis
    startCal.set(Calendar.HOUR_OF_DAY, 0)
    startCal.set(Calendar.MINUTE, 0)
    startCal.set(Calendar.SECOND, 0)
    startCal.set(Calendar.MILLISECOND, 0)

    val label =
      when (periodType) {
        ReportPeriodType.DAILY -> {
          dateFmt.format(Date(startCal.timeInMillis))
        }
        ReportPeriodType.WEEKLY -> {
          startCal.add(Calendar.DAY_OF_YEAR, -6)
          "${dateFmt.format(Date(startCal.timeInMillis))} – ${dateFmt.format(Date(endMillis))}"
        }
        ReportPeriodType.MONTHLY -> {
          startCal.set(Calendar.DAY_OF_MONTH, 1)
          monthFmt.format(Date(startCal.timeInMillis))
        }
        ReportPeriodType.YEARLY -> {
          startCal.set(Calendar.DAY_OF_YEAR, 1)
          "Year ${startCal.get(Calendar.YEAR)}"
        }
        ReportPeriodType.CUSTOM -> {
          startCal.set(Calendar.DAY_OF_MONTH, 1)
          "${dateFmt.format(Date(startCal.timeInMillis))} – ${dateFmt.format(Date(endMillis))}"
        }
      }

    return ReportDateWindow(
      periodType = periodType,
      startDateMillis = startCal.timeInMillis,
      endDateMillis = endMillis,
      label = label,
    )
  }

  fun calculateSnapshot(
    window: ReportDateWindow,
    accounts: List<FinancialAccount>,
    transactions: List<FinancialTransaction>,
    budgets: List<Budget>,
    loans: List<Loan>,
    loanPayments: List<LoanPayment>,
    holdings: List<InvestmentHolding>,
    investmentTransactions: List<InvestmentTransaction>,
    investmentPriceHistory: List<InvestmentPriceHistory>,
    shoppingLists: List<ShoppingList>,
    shoppingItems: List<ShoppingListItem>,
    nowMillis: Long = System.currentTimeMillis(),
  ): FinancialReportSnapshot {
    val activeCurrencies = linkedSetOf<String>()
    accounts.forEach { activeCurrencies += it.currencyCode.uppercase() }
    transactions.forEach { activeCurrencies += it.currencyCode.uppercase() }
    budgets.forEach { activeCurrencies += it.currencyCode.uppercase() }
    loans.forEach { activeCurrencies += it.currencyCode.uppercase() }
    holdings.forEach { activeCurrencies += it.currencyCode.uppercase() }
    shoppingLists.forEach { activeCurrencies += it.currencyCode.uppercase() }

    val sortedCurrencies =
      activeCurrencies.sortedWith(
        compareBy<String> { if (it == "INR") 0 else 1 }.thenBy { it }
      )

    val listCurrencyById = shoppingLists.associate { it.id to it.currencyCode.uppercase() }

    val reportsByCurrency =
      sortedCurrencies.associateWith { currency ->
        val currAccounts = accounts.filter { it.currencyCode.equals(currency, ignoreCase = true) }
        val currTransactions = transactions.filter { it.currencyCode.equals(currency, ignoreCase = true) }
        val currBudgets = budgets.filter { it.currencyCode.equals(currency, ignoreCase = true) }
        val currLoans = loans.filter { it.currencyCode.equals(currency, ignoreCase = true) }
        val currLoanPayments = loanPayments.filter { it.currencyCode.equals(currency, ignoreCase = true) }
        val currHoldings = holdings.filter { it.currencyCode.equals(currency, ignoreCase = true) }
        val currHoldingIds = currHoldings.mapTo(hashSetOf()) { it.id }
        val currInvTx = investmentTransactions.filter { it.holdingId in currHoldingIds }
        val currPriceHistory = investmentPriceHistory.filter { it.currencyCode.equals(currency, ignoreCase = true) }
        val currShoppingLists = shoppingLists.filter { it.currencyCode.equals(currency, ignoreCase = true) }
        val currShoppingItems = shoppingItems.filter { listCurrencyById[it.listId] == currency }

        CurrencyFinancialReport(
          currencyCode = currency,
          netWorth = computeNetWorth(currency, currAccounts, currHoldings, currLoans),
          incomeExpense = computeIncomeExpenseReport(currency, window, currTransactions),
          budgets =
            if (currBudgets.isEmpty()) null
            else computeBudgetReport(currency, window, currBudgets, currTransactions),
          loans =
            if (currLoans.isEmpty() && currLoanPayments.isEmpty()) null
            else computeLoanReport(currency, window, currLoans, currLoanPayments, nowMillis),
          investments =
            if (currHoldings.isEmpty() && currInvTx.isEmpty()) null
            else computeInvestmentReport(currency, window, currHoldings, currInvTx, currPriceHistory, nowMillis),
          shopping =
            if (currShoppingLists.isEmpty() && currShoppingItems.isEmpty()) null
            else computeShoppingReport(currency, window, currShoppingLists, currShoppingItems),
        )
      }

    return FinancialReportSnapshot(
      window = window,
      generatedAtMillis = nowMillis,
      currencies = sortedCurrencies,
      reportsByCurrency = reportsByCurrency,
    )
  }

  /**
   * Computes Net Worth cleanly without double counting:
   * - Cash/Bank assets come ONLY from current account balances (>= 0), never added to transactions.
   * - Investment assets come strictly from holding valuations, separate from bank accounts.
   * - Liabilities combine active loan remaining principal and negative account/credit card debt.
   */
  fun computeNetWorth(
    currencyCode: String,
    accounts: List<FinancialAccount>,
    holdings: List<InvestmentHolding>,
    loans: List<Loan>,
  ): NetWorthCurrencyBreakdown {
    var cashAndBankAssets = 0L
    var accountOrCardLiabilities = 0L
    var activeAccountCount = 0

    for (acc in accounts) {
      if (!acc.isActive) continue
      activeAccountCount++
      if (acc.currentBalanceMinor >= 0L) {
        cashAndBankAssets = Math.addExact(cashAndBankAssets, acc.currentBalanceMinor)
      } else {
        accountOrCardLiabilities = Math.addExact(accountOrCardLiabilities, abs(acc.currentBalanceMinor))
      }
    }

    var investmentAssets = 0L
    var activeInvestmentCount = 0
    for (holding in holdings) {
      val qty = runCatching { BigDecimal(holding.quantityDecimal.trim()) }.getOrDefault(BigDecimal.ZERO)
      if (qty > BigDecimal.ZERO && holding.currentValuationMinor > 0L) {
        activeInvestmentCount++
        investmentAssets = Math.addExact(investmentAssets, holding.currentValuationMinor)
      }
    }

    var loanLiabilities = 0L
    var activeOrOverdueLoans = 0
    for (loan in loans) {
      if (loan.status != LoanStatus.COMPLETED && loan.outstandingPrincipalMinor > 0L) {
        activeOrOverdueLoans++
        loanLiabilities = Math.addExact(loanLiabilities, loan.outstandingPrincipalMinor)
      }
    }

    val totalAssets = Math.addExact(cashAndBankAssets, investmentAssets)
    val totalLiabilities = Math.addExact(loanLiabilities, accountOrCardLiabilities)
    val netWorth = Math.subtractExact(totalAssets, totalLiabilities)

    return NetWorthCurrencyBreakdown(
      currencyCode = currencyCode,
      cashAndBankAssetsMinor = cashAndBankAssets,
      investmentAssetsMinor = investmentAssets,
      totalAssetsMinor = totalAssets,
      loanLiabilitiesMinor = loanLiabilities,
      accountOrCardLiabilitiesMinor = accountOrCardLiabilities,
      totalLiabilitiesMinor = totalLiabilities,
      netWorthMinor = netWorth,
      activeAccountCount = activeAccountCount,
      activeInvestmentCount = activeInvestmentCount,
      activeOrOverdueLoanCount = activeOrOverdueLoans,
    )
  }

  fun computeIncomeExpenseReport(
    currencyCode: String,
    window: ReportDateWindow,
    transactions: List<FinancialTransaction>,
  ): IncomeExpenseCurrencyReport {
    val inRange =
      transactions.filter {
        it.transactionDate in window.startDateMillis..window.endDateMillis
      }

    var totalIncome = 0L
    var grossExpenses = 0L
    var totalRefundsAndReversals = 0L
    var incomeCount = 0
    var expenseCount = 0
    var refundCount = 0
    var excludedTransfers = 0
    var excludedNonCompleted = 0

    val expenseCatGross = linkedMapOf<String, Long>()
    val expenseCatRefund = linkedMapOf<String, Long>()
    val expenseCatCount = linkedMapOf<String, Int>()
    val incomeCatTotal = linkedMapOf<String, Long>()
    val incomeCatCount = linkedMapOf<String, Int>()

    val monthKeyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
    val monthLabelFmt = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    val monthlyAcc = linkedMapOf<String, MonthlyMutable>()

    val includedTransactions = mutableListOf<FinancialTransaction>()

    for (txn in inRange.sortedBy { it.transactionDate }) {
      if (txn.type == TransactionType.TRANSFER) {
        excludedTransfers++
        continue
      }
      if (txn.status != TransactionStatus.COMPLETED ||
        txn.type == TransactionType.PENDING ||
        txn.type == TransactionType.FAILED
      ) {
        excludedNonCompleted++
        continue
      }

      includedTransactions += txn
      val cat = txn.category.trim().ifEmpty { "General" }
      val mKey = monthKeyFmt.format(Date(txn.transactionDate))
      val mLabel = monthLabelFmt.format(Date(txn.transactionDate))
      val monthBucket = monthlyAcc.getOrPut(mKey) { MonthlyMutable(monthKey = mKey, monthLabel = mLabel) }

      when (txn.type) {
        TransactionType.INCOME -> {
          totalIncome = Math.addExact(totalIncome, txn.amountMinor)
          incomeCount++
          incomeCatTotal[cat] = Math.addExact(incomeCatTotal[cat] ?: 0L, txn.amountMinor)
          incomeCatCount[cat] = (incomeCatCount[cat] ?: 0) + 1
          monthBucket.incomeMinor = Math.addExact(monthBucket.incomeMinor, txn.amountMinor)
        }
        TransactionType.EXPENSE -> {
          grossExpenses = Math.addExact(grossExpenses, txn.amountMinor)
          expenseCount++
          expenseCatGross[cat] = Math.addExact(expenseCatGross[cat] ?: 0L, txn.amountMinor)
          expenseCatCount[cat] = (expenseCatCount[cat] ?: 0) + 1
          monthBucket.grossExpenseMinor = Math.addExact(monthBucket.grossExpenseMinor, txn.amountMinor)
        }
        TransactionType.REFUND,
        TransactionType.REVERSAL -> {
          totalRefundsAndReversals = Math.addExact(totalRefundsAndReversals, txn.amountMinor)
          refundCount++
          expenseCatRefund[cat] = Math.addExact(expenseCatRefund[cat] ?: 0L, txn.amountMinor)
          expenseCatCount[cat] = (expenseCatCount[cat] ?: 0) + 1
          monthBucket.refundMinor = Math.addExact(monthBucket.refundMinor, txn.amountMinor)
        }
        TransactionType.TRANSFER,
        TransactionType.PENDING,
        TransactionType.FAILED -> Unit
      }
    }

    val netExpenses = maxOf(0L, Math.subtractExact(grossExpenses, totalRefundsAndReversals))
    val netSavings = Math.subtractExact(totalIncome, netExpenses)
    val savingsPct =
      if (totalIncome > 0L) {
        BigDecimal.valueOf(netSavings)
          .multiply(HUNDRED)
          .divide(BigDecimal.valueOf(totalIncome), 2, RoundingMode.HALF_UP)
      } else {
        ZERO_PCT
      }

    val allExpenseCats = (expenseCatGross.keys + expenseCatRefund.keys).distinct()
    val rawNetByExpenseCat =
      allExpenseCats.associateWith { cat ->
        val gross = expenseCatGross[cat] ?: 0L
        val refund = expenseCatRefund[cat] ?: 0L
        maxOf(0L, gross - refund)
      }
    val categoryNetSum = rawNetByExpenseCat.values.sum()
    val expenseByCategory =
      allExpenseCats
        .map { cat ->
          val gross = expenseCatGross[cat] ?: 0L
          val refund = expenseCatRefund[cat] ?: 0L
          val net = rawNetByExpenseCat[cat] ?: 0L
          CategoryBreakdownRow(
            category = cat,
            currencyCode = currencyCode,
            totalMinor = net,
            grossMinor = gross,
            refundOffsetMinor = refund,
            percentage = percentageOf(net, categoryNetSum),
            transactionCount = expenseCatCount[cat] ?: 0,
          )
        }
        .filter { it.grossMinor > 0L || it.refundOffsetMinor > 0L }
        .sortedByDescending { it.totalMinor }

    val incomeByCategory =
      incomeCatTotal.entries
        .map { (cat, sumMinor) ->
          CategoryBreakdownRow(
            category = cat,
            currencyCode = currencyCode,
            totalMinor = sumMinor,
            grossMinor = sumMinor,
            refundOffsetMinor = 0L,
            percentage = percentageOf(sumMinor, totalIncome),
            transactionCount = incomeCatCount[cat] ?: 0,
          )
        }
        .sortedByDescending { it.totalMinor }

    val monthlyTrends =
      monthlyAcc.values
        .sortedBy { it.monthKey }
        .map { m ->
          val mNetExp = maxOf(0L, Math.subtractExact(m.grossExpenseMinor, m.refundMinor))
          val mNetSav = Math.subtractExact(m.incomeMinor, mNetExp)
          MonthlyTrendRow(
            monthKey = m.monthKey,
            monthLabel = m.monthLabel,
            incomeMinor = m.incomeMinor,
            grossExpenseMinor = m.grossExpenseMinor,
            refundMinor = m.refundMinor,
            netExpenseMinor = mNetExp,
            netSavingsMinor = mNetSav,
          )
        }

    return IncomeExpenseCurrencyReport(
      currencyCode = currencyCode,
      totalIncomeMinor = totalIncome,
      grossExpensesMinor = grossExpenses,
      totalRefundsAndReversalsMinor = totalRefundsAndReversals,
      netExpensesMinor = netExpenses,
      netSavingsMinor = netSavings,
      savingsPercentage = savingsPct,
      completedIncomeCount = incomeCount,
      completedExpenseCount = expenseCount,
      completedRefundCount = refundCount,
      excludedTransferCount = excludedTransfers,
      excludedNonCompletedCount = excludedNonCompleted,
      expenseByCategory = expenseByCategory,
      incomeByCategory = incomeByCategory,
      monthlyTrends = monthlyTrends,
      transactionsInRange = includedTransactions.sortedByDescending { it.transactionDate },
    )
  }

  fun computeBudgetReport(
    currencyCode: String,
    window: ReportDateWindow,
    budgets: List<Budget>,
    transactions: List<FinancialTransaction>,
  ): BudgetCurrencyReport {
    val rows =
      budgets
        .sortedByDescending { it.endDate }
        .map { budget ->
          // Evaluate the historical band at the budget's own window end, so a report row keeps
          // the status the budget had while it was running instead of being marked period-ended.
          val status =
            budgetEngine.calculateBudgetStatus(
              budget = budget,
              transactions = transactions,
              nowMillis = budget.endDate,
            )
          val overlaps =
            budget.endDate >= window.startDateMillis && budget.startDate <= window.endDateMillis
          val utilizationPct = percentageOf(status.spentMoney.minorUnits, budget.amountMinor)
          BudgetPerformanceHistoryRow(
            budgetId = budget.id,
            category = budget.category,
            currencyCode = currencyCode,
            startDate = budget.startDate,
            endDate = budget.endDate,
            allocatedMinor = budget.amountMinor,
            spentMinor = status.spentMoney.minorUnits,
            remainingMinor = status.remainingMoney.minorUnits,
            overspendingMinor = status.overspendingMoney.minorUnits,
            utilizationPercent = utilizationPct,
            alertStatus = status.alertStatus,
            overlapsSelectedWindow = overlaps,
          )
        }

    val focusRows = rows.filter { it.overlapsSelectedWindow }.ifEmpty { rows }
    var totalAllocated = 0L
    var totalSpent = 0L
    var totalOverspending = 0L
    var overspentCount = 0

    for (row in focusRows) {
      totalAllocated = Math.addExact(totalAllocated, row.allocatedMinor)
      totalSpent = Math.addExact(totalSpent, row.spentMinor)
      totalOverspending = Math.addExact(totalOverspending, row.overspendingMinor)
      if (row.overspendingMinor > 0L) overspentCount++
    }
    val totalRemaining = Math.subtractExact(totalAllocated, totalSpent)
    val utilizationPercent = percentageOf(totalSpent, totalAllocated)

    return BudgetCurrencyReport(
      currencyCode = currencyCode,
      totalAllocatedMinor = totalAllocated,
      totalSpentMinor = totalSpent,
      totalRemainingMinor = totalRemaining,
      totalOverspendingMinor = totalOverspending,
      utilizationPercent = utilizationPercent,
      activeBudgetCount = focusRows.size,
      overspentBudgetCount = overspentCount,
      historicalPerformance = rows,
    )
  }

  fun computeLoanReport(
    currencyCode: String,
    window: ReportDateWindow,
    loans: List<Loan>,
    loanPayments: List<LoanPayment>,
    nowMillis: Long,
  ): LoanCurrencyReport {
    val baseSummary = emiEngine.calculateCurrencySummaries(loans, nowMillis)[currencyCode.uppercase()]
    val completedPayments = loanPayments.filter { it.status == LoanPaymentStatus.COMPLETED }
    val allTimeInterest = completedPayments.fold(0L) { acc, p -> Math.addExact(acc, p.interestComponentMinor) }

    val periodPayments =
      completedPayments.filter { it.paymentDate in window.startDateMillis..window.endDateMillis }
    var periodEmiPaid = 0L
    var periodPrincipalPaid = 0L
    var periodInterestPaid = 0L
    for (p in periodPayments) {
      periodEmiPaid = Math.addExact(periodEmiPaid, p.amountMinor)
      periodPrincipalPaid = Math.addExact(periodPrincipalPaid, p.principalComponentMinor)
      periodInterestPaid = Math.addExact(periodInterestPaid, p.interestComponentMinor)
    }

    val evaluated = loans.map { loan -> loan to emiEngine.evaluateEffectiveStatus(loan, nowMillis) }
    val overdueLoans =
      evaluated
        .filter { it.second == LoanStatus.OVERDUE }
        .map { it.first.copy(status = LoanStatus.OVERDUE) }
        .sortedBy { it.nextDueDate }
    val upcomingLoans =
      evaluated
        .filter { it.second == LoanStatus.ACTIVE }
        .map { it.first.copy(status = LoanStatus.ACTIVE) }
        .sortedBy { it.nextDueDate }

    return LoanCurrencyReport(
      currencyCode = currencyCode,
      totalPrincipalMinor = baseSummary?.totalPrincipalMinor ?: 0L,
      totalOutstandingMinor = baseSummary?.totalOutstandingMinor ?: 0L,
      totalPaidAllTimeMinor = baseSummary?.totalAmountPaidMinor ?: 0L,
      totalMonthlyEmiMinor = baseSummary?.totalMonthlyEmiMinor ?: 0L,
      periodPaymentsCount = periodPayments.size,
      periodTotalEmiPaidMinor = periodEmiPaid,
      periodPrincipalPaidMinor = periodPrincipalPaid,
      periodInterestPaidMinor = periodInterestPaid,
      allTimeInterestPaidMinor = allTimeInterest,
      activeCount = baseSummary?.activeLoanCount ?: 0,
      overdueCount = baseSummary?.overdueLoanCount ?: 0,
      completedCount = baseSummary?.completedLoanCount ?: 0,
      upcomingLoans = upcomingLoans,
      overdueLoans = overdueLoans,
    )
  }

  fun computeInvestmentReport(
    currencyCode: String,
    window: ReportDateWindow,
    holdings: List<InvestmentHolding>,
    investmentTransactions: List<InvestmentTransaction>,
    priceHistory: List<InvestmentPriceHistory>,
    nowMillis: Long,
  ): InvestmentCurrencyReport {
    val windowHistory = priceHistory.filter { it.priceDate <= window.endDateMillis }
    val summary =
      investmentEngine.calculateCurrencySummaries(
        holdings = holdings,
        priceHistory = windowHistory,
        nowMillis = nowMillis,
      )[currencyCode.uppercase()]
        ?: investmentEngine.calculateCurrencySummaries(holdings, emptyList(), nowMillis).values.first()

    val holdingRows =
      holdings.map { h ->
        val perf = investmentEngine.evaluateHoldingPerformance(h, nowMillis)
        InvestmentHoldingReportRow(
          holdingId = h.id,
          name = h.name,
          category = h.category,
          currencyCode = h.currencyCode,
          investedMinor = h.totalInvestedMinor,
          valuationMinor = h.currentValuationMinor,
          unrealizedPnlMinor = perf.unrealizedPnlMoney.minorUnits,
          realizedPnlMinor = perf.realizedPnlMoney.minorUnits,
          returnPercent = perf.totalReturnPercent,
          lastPriceUpdateDate = h.lastPriceUpdateDate,
          isStalePrice = perf.isPriceStale,
        )
      }.sortedByDescending { it.valuationMinor }

    var periodBuy = 0L
    var periodSell = 0L
    var periodRealized = 0L
    for (tx in investmentTransactions) {
      if (tx.transactionDate !in window.startDateMillis..window.endDateMillis) continue
      when (tx.type) {
        InvestmentTransactionType.BUY -> {
          periodBuy = Math.addExact(periodBuy, tx.netAmountMinor)
        }
        InvestmentTransactionType.SELL -> {
          periodSell = Math.addExact(periodSell, tx.netAmountMinor)
          periodRealized = Math.addExact(periodRealized, tx.realizedPnlMinor)
        }
      }
    }

    return InvestmentCurrencyReport(
      currencyCode = currencyCode,
      portfolioSummary = summary,
      periodBuyTotalMinor = periodBuy,
      periodSellTotalMinor = periodSell,
      periodRealizedPnlMinor = periodRealized,
      holdingRows = holdingRows,
    )
  }

  fun computeShoppingReport(
    currencyCode: String,
    window: ReportDateWindow,
    lists: List<ShoppingList>,
    items: List<ShoppingListItem>,
  ): ShoppingCurrencyReport {
    val totalEstimatedAll = items.fold(0L) { acc, item -> Math.addExact(acc, item.estimatedTotalMinor) }
    val purchasedInPeriod =
      items.filter { item ->
        item.isPurchased &&
          (item.purchasedAt ?: item.updatedAt) in window.startDateMillis..window.endDateMillis
      }

    var estimatedOfPurchased = 0L
    var actualSpent = 0L
    for (item in purchasedInPeriod) {
      estimatedOfPurchased = Math.addExact(estimatedOfPurchased, item.estimatedTotalMinor)
      val actual = item.actualTotalMinor ?: item.estimatedTotalMinor
      actualSpent = Math.addExact(actualSpent, actual)
    }
    val savingsOrOverspend = Math.subtractExact(estimatedOfPurchased, actualSpent)

    val categoryBreakdown =
      items
        .groupBy { it.category.trim().ifEmpty { "General" } }
        .map { (category, catItems) ->
          val estAll = catItems.fold(0L) { acc, i -> Math.addExact(acc, i.estimatedTotalMinor) }
          val catPurchased = catItems.filter { it.isPurchased && (it.purchasedAt ?: it.updatedAt) in window.startDateMillis..window.endDateMillis }
          val estPurch = catPurchased.fold(0L) { acc, i -> Math.addExact(acc, i.estimatedTotalMinor) }
          val actPurch = catPurchased.fold(0L) { acc, i -> Math.addExact(acc, i.actualTotalMinor ?: i.estimatedTotalMinor) }
          ShoppingCategoryReportRow(
            category = category,
            totalItemCount = catItems.size,
            purchasedItemCount = catPurchased.size,
            estimatedTotalMinor = estAll,
            actualSpentMinor = actPurch,
            savingsOrOverspendMinor = Math.subtractExact(estPurch, actPurch),
          )
        }
        .sortedByDescending { maxOf(it.actualSpentMinor, it.estimatedTotalMinor) }

    val storeBreakdown =
      purchasedInPeriod
        .groupBy { it.storeName?.trim()?.takeIf { s -> s.isNotEmpty() } ?: "Unassigned Store" }
        .map { (store, storeItems) ->
          val estPurch = storeItems.fold(0L) { acc, i -> Math.addExact(acc, i.estimatedTotalMinor) }
          val actPurch = storeItems.fold(0L) { acc, i -> Math.addExact(acc, i.actualTotalMinor ?: i.estimatedTotalMinor) }
          ShoppingStoreReportRow(
            storeName = store,
            purchasedItemCount = storeItems.size,
            estimatedOfPurchasedMinor = estPurch,
            actualSpentMinor = actPurch,
            savingsOrOverspendMinor = Math.subtractExact(estPurch, actPurch),
          )
        }
        .sortedByDescending { it.actualSpentMinor }

    // Reference shoppingEngine so list-level summary consistency is preserved across screens
    lists.forEach { list ->
      val listItems = items.filter { it.listId == list.id }
      shoppingEngine.calculateListSummary(list, listItems)
    }

    return ShoppingCurrencyReport(
      currencyCode = currencyCode,
      totalLists = lists.size,
      totalItems = items.size,
      purchasedItemsInPeriod = purchasedInPeriod.size,
      totalEstimatedMinor = totalEstimatedAll,
      estimatedOfPurchasedInPeriodMinor = estimatedOfPurchased,
      actualSpentInPeriodMinor = actualSpent,
      savingsOrOverspendMinor = savingsOrOverspend,
      categoryBreakdown = categoryBreakdown,
      storeBreakdown = storeBreakdown,
      recentPurchases = purchasedInPeriod.sortedByDescending { it.purchasedAt ?: it.updatedAt }.take(10),
    )
  }

  private fun percentageOf(numeratorMinor: Long, denominatorMinor: Long): BigDecimal {
    if (denominatorMinor <= 0L || numeratorMinor <= 0L) return ZERO_PCT
    return BigDecimal.valueOf(numeratorMinor)
      .multiply(HUNDRED)
      .divide(BigDecimal.valueOf(denominatorMinor), 2, RoundingMode.HALF_UP)
  }

  private data class MonthlyMutable(
    val monthKey: String,
    val monthLabel: String,
    var incomeMinor: Long = 0L,
    var grossExpenseMinor: Long = 0L,
    var refundMinor: Long = 0L,
  )
}
