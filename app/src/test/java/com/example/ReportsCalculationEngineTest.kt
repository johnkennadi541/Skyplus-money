package com.example

import com.example.domain.model.AccountType
import com.example.domain.model.Budget
import com.example.domain.model.FinancialAccount
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.InvestmentPriceHistory
import com.example.domain.model.InvestmentTransaction
import com.example.domain.model.InvestmentTransactionType
import com.example.domain.model.Loan
import com.example.domain.model.LoanPayment
import com.example.domain.model.LoanPaymentStatus
import com.example.domain.model.LoanStatus
import com.example.domain.model.LoanType
import com.example.domain.model.PriceSourceType
import com.example.domain.model.ReportDateWindow
import com.example.domain.model.ReportPeriodType
import com.example.domain.model.ShoppingList
import com.example.domain.model.ShoppingListItem
import com.example.domain.model.TransactionCategories
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.usecase.BudgetCalculationEngine
import com.example.domain.usecase.EmiCalculationEngine
import com.example.domain.usecase.InvestmentCalculationEngine
import com.example.domain.usecase.ReportsCalculationEngine
import com.example.domain.usecase.ShoppingCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ReportsCalculationEngineTest {

  private val engine =
    ReportsCalculationEngine(
      budgetEngine = BudgetCalculationEngine(),
      emiEngine = EmiCalculationEngine(),
      investmentEngine = InvestmentCalculationEngine(),
      shoppingEngine = ShoppingCalculationEngine(),
    )

  private val uid = "uid_alpha"
  private val startMillis = 1_750_000_000_000L
  private val midMillis = 1_751_000_000_000L
  private val endMillis = 1_752_500_000_000L
  private val outsideMillis = 1_740_000_000_000L

  @Test
  fun `income expense transfers refunds and net worth double counting are prevented`() {
    val window =
      ReportDateWindow(
        periodType = ReportPeriodType.CUSTOM,
        startDateMillis = startMillis,
        endDateMillis = endMillis,
        label = "Test Window",
      )

    val accounts =
      listOf(
        FinancialAccount(
          id = "acc_bank_inr",
          userId = uid,
          name = "HDFC Salary",
          type = AccountType.BANK,
          currencyCode = "INR",
          initialBalanceMinor = 500_000_00L,
          currentBalanceMinor = 500_000_00L, // INR 500,000.00
        ),
        FinancialAccount(
          id = "acc_cc_inr",
          userId = uid,
          name = "Credit Card",
          type = AccountType.CREDIT_CARD,
          currencyCode = "INR",
          initialBalanceMinor = 0L,
          currentBalanceMinor = -25_000_00L, // INR -25,000.00 liability
        ),
        FinancialAccount(
          id = "acc_usd",
          userId = uid,
          name = "USD Travel Wallet",
          type = AccountType.WALLET,
          currencyCode = "USD",
          initialBalanceMinor = 1_000_00L,
          currentBalanceMinor = 1_000_00L,
        ),
      )

    val transactions =
      listOf(
        FinancialTransaction(
          id = "tx_sal",
          userId = uid,
          accountId = "acc_bank_inr",
          type = TransactionType.INCOME,
          amountMinor = 100_000_00L,
          currencyCode = "INR",
          category = "Salary",
          transactionDate = midMillis,
          status = TransactionStatus.COMPLETED,
        ),
        FinancialTransaction(
          id = "tx_groceries",
          userId = uid,
          accountId = "acc_bank_inr",
          type = TransactionType.EXPENSE,
          amountMinor = 25_000_00L,
          currencyCode = "INR",
          category = "Food & Dining",
          transactionDate = midMillis,
          status = TransactionStatus.COMPLETED,
        ),
        FinancialTransaction(
          id = "tx_refund",
          userId = uid,
          accountId = "acc_bank_inr",
          type = TransactionType.REFUND,
          amountMinor = 5_000_00L,
          currencyCode = "INR",
          category = "Food & Dining",
          transactionDate = midMillis,
          status = TransactionStatus.COMPLETED,
        ),
        FinancialTransaction(
          id = "tx_transfer_out",
          userId = uid,
          accountId = "acc_bank_inr",
          type = TransactionType.TRANSFER,
          amountMinor = 40_000_00L,
          currencyCode = "INR",
          category = TransactionCategories.TRANSFER_OUT_CATEGORY,
          transactionDate = midMillis,
          status = TransactionStatus.COMPLETED,
        ),
        FinancialTransaction(
          id = "tx_pending",
          userId = uid,
          accountId = "acc_bank_inr",
          type = TransactionType.EXPENSE,
          amountMinor = 9_999_00L,
          currencyCode = "INR",
          category = "Shopping",
          transactionDate = midMillis,
          status = TransactionStatus.PENDING,
        ),
        FinancialTransaction(
          id = "tx_old",
          userId = uid,
          accountId = "acc_bank_inr",
          type = TransactionType.INCOME,
          amountMinor = 999_999_00L,
          currencyCode = "INR",
          category = "Bonus",
          transactionDate = outsideMillis,
          status = TransactionStatus.COMPLETED,
        ),
        FinancialTransaction(
          id = "tx_usd_exp",
          userId = uid,
          accountId = "acc_usd",
          type = TransactionType.EXPENSE,
          amountMinor = 200_00L,
          currencyCode = "USD",
          category = "Travel",
          transactionDate = midMillis,
          status = TransactionStatus.COMPLETED,
        ),
      )

    val holdings =
      listOf(
        InvestmentHolding(
          id = "inv_mf",
          userId = uid,
          name = "Nifty 50 Index",
          category = InvestmentCategory.MUTUAL_FUNDS,
          currencyCode = "INR",
          purchaseDate = startMillis,
          quantityDecimal = "100",
          totalInvestedMinor = 150_000_00L,
          totalBuyCostMinor = 150_000_00L,
          currentMarketPriceDecimal = "1800",
          currentValuationMinor = 180_000_00L,
          lastPriceUpdateDate = midMillis,
          realizedPnlMinor = 10_000_00L,
        ),
      )

    val loans =
      listOf(
        Loan(
          id = "loan_home",
          userId = uid,
          name = "Home Loan",
          lenderName = "SBI",
          loanType = LoanType.HOME,
          principalMinor = 300_000_00L,
          currencyCode = "INR",
          annualInterestRatePercent = "8.5",
          tenureMonths = 120,
          startDate = outsideMillis,
          nextDueDate = midMillis - 86_400_000L, // overdue relative to endMillis
          monthlyEmiMinor = 3_719_57L,
          totalPayableMinor = 446_348_00L,
          totalInterestMinor = 146_348_00L,
          amountPaidMinor = 50_000_00L,
          outstandingPrincipalMinor = 260_000_00L,
          status = LoanStatus.ACTIVE,
        ),
      )

    val loanPayments =
      listOf(
        LoanPayment(
          id = "lp_1",
          loanId = "loan_home",
          userId = uid,
          paymentDate = midMillis,
          amountMinor = 10_000_00L,
          principalComponentMinor = 7_500_00L,
          interestComponentMinor = 2_500_00L,
          remainingBalanceMinor = 260_000_00L,
          currencyCode = "INR",
          status = LoanPaymentStatus.COMPLETED,
          idempotencyKey = "idem_lp_1",
        ),
      )

    val budgets =
      listOf(
        Budget(
          id = "b_food",
          userId = uid,
          name = "Food Budget",
          category = "Food & Dining",
          amountMinor = 15_000_00L,
          currencyCode = "INR",
          startDate = startMillis,
          endDate = endMillis,
        ),
      )

    val invTx =
      listOf(
        InvestmentTransaction(
          id = "itx_1",
          holdingId = "inv_mf",
          userId = uid,
          type = InvestmentTransactionType.BUY,
          transactionDate = midMillis,
          quantityDecimal = "10",
          unitPriceDecimal = "1500",
          grossAmountMinor = 15_000_00L,
          chargesMinor = 0L,
          netAmountMinor = 15_000_00L,
        ),
      )

    val priceHistory =
      listOf(
        InvestmentPriceHistory(
          id = "ph_1",
          holdingId = "inv_mf",
          userId = uid,
          priceDate = startMillis,
          unitPriceDecimal = "1500",
          valuationMinor = 150_000_00L,
          totalInvestedMinor = 150_000_00L,
          realizedPnlMinor = 0L,
          currencyCode = "INR",
          source = PriceSourceType.MANUAL,
          providerLabel = "Manual",
        ),
        InvestmentPriceHistory(
          id = "ph_2",
          holdingId = "inv_mf",
          userId = uid,
          priceDate = midMillis,
          unitPriceDecimal = "1800",
          valuationMinor = 180_000_00L,
          totalInvestedMinor = 150_000_00L,
          realizedPnlMinor = 10_000_00L,
          currencyCode = "INR",
          source = PriceSourceType.MANUAL,
          providerLabel = "Manual",
        ),
      )

    val shoppingLists =
      listOf(
        ShoppingList(
          id = "sl_1",
          userId = uid,
          name = "Monthly Groceries",
          currencyCode = "INR",
          budgetMinor = 5_000_00L,
        ),
      )

    val shoppingItems =
      listOf(
        ShoppingListItem(
          id = "si_1",
          listId = "sl_1",
          userId = uid,
          name = "Rice 5kg",
          category = "Groceries",
          quantityDecimal = "1",
          unit = "bag",
          estimatedUnitPriceMinor = 500_00L,
          estimatedTotalMinor = 500_00L,
          storeName = "DMart",
          isPurchased = true,
          actualUnitPriceMinor = 450_00L,
          actualTotalMinor = 450_00L,
          purchasedAt = midMillis,
        ),
      )

    val snapshot =
      engine.calculateSnapshot(
        window = window,
        accounts = accounts,
        transactions = transactions,
        budgets = budgets,
        loans = loans,
        loanPayments = loanPayments,
        holdings = holdings,
        investmentTransactions = invTx,
        investmentPriceHistory = priceHistory,
        shoppingLists = shoppingLists,
        shoppingItems = shoppingItems,
        nowMillis = endMillis,
      )

    // Multi-currency isolation: INR and USD kept strictly separate
    assertEquals(listOf("INR", "USD"), snapshot.currencies)

    val inr = snapshot.reportsByCurrency["INR"]
    assertNotNull(inr)
    inr!!

    // Income & Expense checks (transfer & pending excluded, refund nets out expense)
    assertEquals(100_000_00L, inr.incomeExpense.totalIncomeMinor)
    assertEquals(25_000_00L, inr.incomeExpense.grossExpensesMinor)
    assertEquals(5_000_00L, inr.incomeExpense.totalRefundsAndReversalsMinor)
    assertEquals(20_000_00L, inr.incomeExpense.netExpensesMinor)
    assertEquals(80_000_00L, inr.incomeExpense.netSavingsMinor)
    assertEquals(BigDecimal("80.00"), inr.incomeExpense.savingsPercentage)
    assertEquals(1, inr.incomeExpense.excludedTransferCount)
    assertEquals(1, inr.incomeExpense.excludedNonCompletedCount)

    // Net Worth checks (no double-counting transaction sums on top of account balance!)
    assertEquals(500_000_00L, inr.netWorth.cashAndBankAssetsMinor)
    assertEquals(180_000_00L, inr.netWorth.investmentAssetsMinor)
    assertEquals(680_000_00L, inr.netWorth.totalAssetsMinor)
    assertEquals(260_000_00L, inr.netWorth.loanLiabilitiesMinor)
    assertEquals(25_000_00L, inr.netWorth.accountOrCardLiabilitiesMinor)
    assertEquals(285_000_00L, inr.netWorth.totalLiabilitiesMinor)
    assertEquals(395_000_00L, inr.netWorth.netWorthMinor)

    // Budget checks (net spent 20k vs 15k allocated -> 5k overspending)
    val budgetReport = inr.budgets
    assertNotNull(budgetReport)
    budgetReport!!
    assertEquals(15_000_00L, budgetReport.totalAllocatedMinor)
    assertEquals(20_000_00L, budgetReport.totalSpentMinor)
    assertEquals(5_000_00L, budgetReport.totalOverspendingMinor)
    assertEquals(1, budgetReport.overspentBudgetCount)

    // Loan checks
    val loanReport = inr.loans
    assertNotNull(loanReport)
    loanReport!!
    assertEquals(260_000_00L, loanReport.totalOutstandingMinor)
    assertEquals(2_500_00L, loanReport.periodInterestPaidMinor)
    assertEquals(10_000_00L, loanReport.periodTotalEmiPaidMinor)
    assertEquals(1, loanReport.overdueCount)

    // Investment checks
    val invReport = inr.investments
    assertNotNull(invReport)
    invReport!!
    assertEquals(180_000_00L, invReport.portfolioSummary.totalValuationMinor)
    assertEquals(30_000_00L, invReport.portfolioSummary.unrealizedPnlMinor)
    assertEquals(10_000_00L, invReport.portfolioSummary.realizedPnlMinor)
    assertEquals(15_000_00L, invReport.periodBuyTotalMinor)
    assertEquals(2, invReport.portfolioSummary.growthPoints.size)

    // Shopping checks
    val shopReport = inr.shopping
    assertNotNull(shopReport)
    shopReport!!
    assertEquals(450_00L, shopReport.actualSpentInPeriodMinor)
    assertEquals(50_00L, shopReport.savingsOrOverspendMinor)
    assertEquals("DMart", shopReport.storeBreakdown.first().storeName)

    // USD report remains strictly isolated
    val usd = snapshot.reportsByCurrency["USD"]!!
    assertEquals(1_000_00L, usd.netWorth.netWorthMinor)
    assertEquals(200_00L, usd.incomeExpense.netExpensesMinor)
    assertEquals(0L, usd.incomeExpense.totalIncomeMinor)
  }

  @Test
  fun `buildDateWindow generates accurate boundaries for all report periods`() {
    val now = 1_751_500_000_000L
    ReportPeriodType.entries.forEach { period ->
      val w = engine.buildDateWindow(period, customStartMillis = now - 86_400_000L, customEndMillis = now, nowMillis = now)
      assertTrue("Start must be <= end for $period", w.startDateMillis <= w.endDateMillis)
      assertTrue("Label must be non-blank for $period", w.label.isNotBlank())
    }
  }
}
