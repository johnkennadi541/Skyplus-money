package com.example.domain.model

import java.util.UUID

enum class TransactionType {
  INCOME,
  EXPENSE,
  TRANSFER,
  REFUND,
  REVERSAL,
  PENDING,
  FAILED,
}

enum class TransactionStatus {
  COMPLETED,
  PENDING,
  FAILED,
  CANCELLED,
}

object TransactionCategories {
  val INCOME_CATEGORIES =
    listOf(
      "Salary",
      "Business",
      "Freelance",
      "Interest",
      "Cashback",
      "Refunds",
      "Other Income",
    )

  val EXPENSE_CATEGORIES =
    listOf(
      "Food & Dining",
      "Groceries",
      "Shopping",
      "Transport",
      "Housing",
      "Utilities",
      "Bills & Utilities",
      "Medical & Healthcare",
      "Entertainment",
      "Education",
      "Bills",
      "EMI Payments",
      "Other Expenses",
    )

  const val TRANSFER_CATEGORY = "Transfer"
  const val TRANSFER_OUT_CATEGORY = "Transfer Out"
  const val TRANSFER_IN_CATEGORY = "Transfer In"
}

data class FinancialTransaction(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val accountId: String,
  val type: TransactionType,
  val status: TransactionStatus = TransactionStatus.COMPLETED,
  val amountMinor: Long,
  val currencyCode: String,
  val category: String,
  val merchantOrPayee: String? = null,
  val notes: String? = null,
  val transactionDate: Long = System.currentTimeMillis(),
  val linkedTransferId: String? = null,
  val transferDestinationAccountId: String? = null,
  val isAutomaticallyDetected: Boolean = false,
  val sourceApplication: String? = null,
  val referenceId: String? = null,
  val confidenceScore: Float? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
) {
  val money: Money
    get() = Money.fromMinorUnits(amountMinor, currencyCode)
}
