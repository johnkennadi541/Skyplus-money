package com.example.domain.usecase

import com.example.domain.model.FinancialAccount
import com.example.domain.model.FinancialReportSnapshot
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.InvestmentPriceHistory
import com.example.domain.model.InvestmentTransaction
import com.example.domain.model.Loan
import com.example.domain.model.LoanPayment
import com.example.domain.model.ReportDateWindow
import com.example.domain.model.ShoppingList
import com.example.domain.model.ShoppingListItem
import com.example.domain.repository.BudgetRepository
import com.example.domain.repository.FinancialAccountRepository
import com.example.domain.repository.FinancialTransactionRepository
import com.example.domain.repository.InvestmentRepository
import com.example.domain.repository.LoanRepository
import com.example.domain.repository.ShoppingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Observes and aggregates all authenticated user financial modules into a unified
 * [FinancialReportSnapshot] scoped strictly to [userId].
 */
class ObserveFinancialReportsUseCase @Inject constructor(
  private val accountRepository: FinancialAccountRepository,
  private val transactionRepository: FinancialTransactionRepository,
  private val budgetRepository: BudgetRepository,
  private val loanRepository: LoanRepository,
  private val investmentRepository: InvestmentRepository,
  private val shoppingRepository: ShoppingRepository,
  private val calculationEngine: ReportsCalculationEngine,
) {

  private data class CoreLedgerBundle(
    val accounts: List<FinancialAccount>,
    val transactions: List<FinancialTransaction>,
    val budgets: List<com.example.domain.model.Budget>,
  )

  private data class LoanBundle(
    val loans: List<Loan>,
    val payments: List<LoanPayment>,
  )

  private data class InvestmentBundle(
    val holdings: List<InvestmentHolding>,
    val transactions: List<InvestmentTransaction>,
    val priceHistory: List<InvestmentPriceHistory>,
  )

  private data class ShoppingBundle(
    val lists: List<ShoppingList>,
    val items: List<ShoppingListItem>,
  )

  operator fun invoke(
    userId: String,
    windowFlow: Flow<ReportDateWindow>,
  ): Flow<FinancialReportSnapshot> {
    val coreFlow =
      combine(
        accountRepository.getAccounts(userId),
        transactionRepository.getTransactions(userId),
        budgetRepository.getBudgets(userId),
      ) { accounts, transactions, budgets ->
        CoreLedgerBundle(accounts, transactions, budgets)
      }

    val loanFlow =
      combine(
        loanRepository.getLoans(userId),
        loanRepository.getAllPaymentsForUser(userId),
      ) { loans, payments ->
        LoanBundle(loans, payments)
      }

    val investmentFlow =
      combine(
        investmentRepository.getHoldings(userId),
        investmentRepository.getAllTransactionsForUser(userId),
        investmentRepository.getAllPriceHistory(userId),
      ) { holdings, invTx, priceHistory ->
        InvestmentBundle(holdings, invTx, priceHistory)
      }

    val shoppingFlow =
      combine(
        shoppingRepository.getLists(userId),
        shoppingRepository.getItemsForUser(userId),
      ) { lists, items ->
        ShoppingBundle(lists, items)
      }

    return combine(
      windowFlow,
      coreFlow,
      loanFlow,
      investmentFlow,
      shoppingFlow,
    ) { window, core, loanBundle, invBundle, shopBundle ->
      calculationEngine.calculateSnapshot(
        window = window,
        accounts = core.accounts,
        transactions = core.transactions,
        budgets = core.budgets,
        loans = loanBundle.loans,
        loanPayments = loanBundle.payments,
        holdings = invBundle.holdings,
        investmentTransactions = invBundle.transactions,
        investmentPriceHistory = invBundle.priceHistory,
        shoppingLists = shopBundle.lists,
        shoppingItems = shopBundle.items,
      )
    }
  }
}
