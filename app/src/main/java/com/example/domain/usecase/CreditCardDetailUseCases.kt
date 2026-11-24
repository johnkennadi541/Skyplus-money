package com.example.domain.usecase

import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardSpendingAnalytics
import com.example.domain.model.CreditCardStatement
import com.example.domain.model.CreditCardStatementStatus
import com.example.domain.model.CreditCardStatementSummary
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.TransactionCategories
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.repository.CreditCardRepository
import com.example.domain.repository.FinancialTransactionRepository
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

object CreditCardStatementCalculator {
  fun summarize(
    statements: List<CreditCardStatement>,
    card: CreditCard,
    transactions: List<FinancialTransaction>,
    nowMillis: Long = System.currentTimeMillis(),
  ): List<CreditCardStatementSummary> =
    statements.map { statement ->
      val payments = transactions.filter { transaction ->
        transaction.type == TransactionType.TRANSFER &&
          transaction.status == TransactionStatus.COMPLETED &&
          transaction.category == TransactionCategories.TRANSFER_IN_CATEGORY &&
          transaction.transactionDate >= statement.statementDate &&
          transaction.transactionDate <= statement.dueDate
      }
      val paidMinor = payments.fold(0L) { total, payment ->
        Math.addExact(total, payment.amountMinor)
      }
      val remainingMinor = (statement.statementBalanceMinor - paidMinor).coerceAtLeast(0L)
      val remainingMinimumMinor = (statement.minimumDueMinor - paidMinor).coerceAtLeast(0L)
      val status = when {
        remainingMinor == 0L -> CreditCardStatementStatus.PAID
        statement.dueDate < nowMillis -> CreditCardStatementStatus.OVERDUE
        paidMinor > 0L -> CreditCardStatementStatus.PARTIALLY_PAID
        else -> CreditCardStatementStatus.UNPAID
      }
      CreditCardStatementSummary(
        statement = statement,
        currencyCode = card.currencyCode,
        paidAmountMinor = paidMinor,
        remainingDueMinor = remainingMinor,
        remainingMinimumDueMinor = remainingMinimumMinor,
        status = status,
      )
    }
}

object CreditCardSpendingAnalyticsCalculator {
  fun calculate(
    card: CreditCard,
    transactions: List<FinancialTransaction>,
    periodStart: Long,
    periodEnd: Long,
  ): CreditCardSpendingAnalytics {
    val expenses = transactions.filter {
      it.type == TransactionType.EXPENSE &&
        it.status == TransactionStatus.COMPLETED &&
        it.transactionDate >= periodStart &&
        it.transactionDate < periodEnd
    }
    val categoryTotals = expenses.groupBy { it.category }.mapValues { (_, entries) ->
      entries.fold(0L) { total, transaction -> Math.addExact(total, transaction.amountMinor) }
    }
    return CreditCardSpendingAnalytics(
      currencyCode = card.currencyCode,
      periodStart = periodStart,
      periodEnd = periodEnd,
      totalSpentMinor = expenses.fold(0L) { total, transaction ->
        Math.addExact(total, transaction.amountMinor)
      },
      transactionCount = expenses.size,
      categoryTotalsMinor = categoryTotals,
    )
  }
}

@Singleton
class ObserveCreditCardStatementSummariesUseCase @Inject constructor(
  private val creditCardRepository: CreditCardRepository,
  private val transactionRepository: FinancialTransactionRepository,
) {
  operator fun invoke(userId: String, card: CreditCard): Flow<List<CreditCardStatementSummary>> =
    combine(
      creditCardRepository.observeStatements(userId, card.id),
      transactionRepository.getTransactionsByAccount(userId, card.accountId),
    ) { statements, transactions ->
      CreditCardStatementCalculator.summarize(statements, card, transactions)
    }
}

@Singleton
class ObserveCreditCardSpendingAnalyticsUseCase @Inject constructor(
  private val transactionRepository: FinancialTransactionRepository,
) {
  operator fun invoke(userId: String, card: CreditCard): Flow<CreditCardSpendingAnalytics> {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val start = calendar.timeInMillis
    calendar.add(Calendar.MONTH, 1)
    val end = calendar.timeInMillis

    return transactionRepository.getTransactionsByAccount(userId, card.accountId).map { transactions ->
      CreditCardSpendingAnalyticsCalculator.calculate(card, transactions, start, end)
    }
  }
}
