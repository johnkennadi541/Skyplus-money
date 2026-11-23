package com.example

import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardStatement
import com.example.domain.model.CreditCardStatementStatus
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.TransactionCategories
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.usecase.CreditCardSpendingAnalyticsCalculator
import com.example.domain.usecase.CreditCardStatementCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditCardDetailCalculatorsTest {

  private val card = CreditCard(
    userId = "uid_card_test",
    accountId = "credit_account",
    issuerName = "HDFC",
    currencyCode = "INR",
  )

  @Test
  fun `statement status is derived from completed card payment transfers`() {
    val now = 1_700_000_000_000L

    val partial = CreditCardStatementCalculator.summarize(
      listOf(statement("partial", now - DAY, now + 5 * DAY, 10_000L, 2_000L)),
      card,
      listOf(
        transfer("payment-partial", 4_000L, now, TransactionCategories.TRANSFER_IN_CATEGORY),
        transfer("wrong-direction", 99_000L, now, TransactionCategories.TRANSFER_OUT_CATEGORY),
        expense("purchase", 2_000L, now),
      ),
      now,
    ).single()
    assertEquals(CreditCardStatementStatus.PARTIALLY_PAID, partial.status)
    assertEquals(4_000L, partial.paidAmountMinor)
    assertEquals(6_000L, partial.remainingDueMinor)

    val paid = CreditCardStatementCalculator.summarize(
      listOf(statement("paid", now - DAY, now + 5 * DAY, 5_000L, 500L)),
      card,
      listOf(transfer("payment-paid", 5_000L, now, TransactionCategories.TRANSFER_IN_CATEGORY)),
      now,
    ).single()
    assertEquals(CreditCardStatementStatus.PAID, paid.status)
    assertEquals(0L, paid.remainingDueMinor)

    val overdue = CreditCardStatementCalculator.summarize(
      listOf(statement("overdue", now - 10 * DAY, now - DAY, 7_000L, 700L)),
      card,
      emptyList(),
      now,
    ).single()
    assertEquals(CreditCardStatementStatus.OVERDUE, overdue.status)
  }

  @Test
  fun `monthly card analytics includes only completed expense transactions in period`() {
    val start = 1_700_000_000_000L
    val end = start + 30 * DAY
    val transactions = listOf(
      expense("food-1", 1_500L, start + DAY, "Food & Dining"),
      expense("food-2", 2_500L, start + 2 * DAY, "Food & Dining"),
      expense("shopping", 3_000L, start + 3 * DAY, "Shopping"),
      transfer("payment", 20_000L, start + 4 * DAY, TransactionCategories.TRANSFER_IN_CATEGORY),
      expense("outside", 9_000L, end + DAY, "Shopping"),
      expense("pending", 7_000L, start + DAY, "Food & Dining", TransactionStatus.PENDING),
    )

    val analytics = CreditCardSpendingAnalyticsCalculator.calculate(card, transactions, start, end)

    assertEquals(7_000L, analytics.totalSpentMinor)
    assertEquals(3, analytics.transactionCount)
    assertEquals(4_000L, analytics.categoryTotalsMinor.getValue("Food & Dining"))
    assertEquals(3_000L, analytics.categoryTotalsMinor.getValue("Shopping"))
    assertTrue(analytics.categoryTotalsMinor.keys.contains("Food & Dining"))
  }

  private fun statement(
    id: String,
    statementDate: Long,
    dueDate: Long,
    balance: Long,
    minimumDue: Long,
  ) = CreditCardStatement(
    id = id,
    userId = card.userId,
    cardId = card.id,
    currencyCode = card.currencyCode,
    statementDate = statementDate,
    dueDate = dueDate,
    statementBalanceMinor = balance,
    minimumDueMinor = minimumDue,
  )

  private fun transfer(id: String, amount: Long, date: Long, category: String) =
    FinancialTransaction(
      id = id,
      userId = card.userId,
      accountId = card.accountId,
      type = TransactionType.TRANSFER,
      status = TransactionStatus.COMPLETED,
      amountMinor = amount,
      currencyCode = card.currencyCode,
      category = category,
      transactionDate = date,
    )

  private fun expense(
    id: String,
    amount: Long,
    date: Long,
    category: String = "Other Expenses",
    status: TransactionStatus = TransactionStatus.COMPLETED,
  ) = FinancialTransaction(
    id = id,
    userId = card.userId,
    accountId = card.accountId,
    type = TransactionType.EXPENSE,
    status = status,
    amountMinor = amount,
    currencyCode = card.currencyCode,
    category = category,
    transactionDate = date,
  )

  companion object {
    private const val DAY = 86_400_000L
  }
}
