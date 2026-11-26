package com.example.domain.usecase

import com.example.domain.model.FinancialTransaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.FinancialTransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTransactionsUseCase @Inject constructor(
  private val repository: FinancialTransactionRepository,
) {
  operator fun invoke(userId: String): Flow<List<FinancialTransaction>> =
    repository.getTransactions(userId)

  fun forAccount(userId: String, accountId: String): Flow<List<FinancialTransaction>> =
    repository.getTransactionsByAccount(userId, accountId)
}

class RecordIncomeUseCase @Inject constructor(
  private val repository: FinancialTransactionRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    transaction: FinancialTransaction,
  ): Result<FinancialTransaction> {
    require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
    require(transaction.amountMinor > 0L) { "Income amount must be greater than zero" }
    require(transaction.accountId.isNotBlank()) { "Account must be selected" }
    return repository.recordIncome(authenticatedUserId, transaction.copy(type = TransactionType.INCOME))
  }
}

class RecordExpenseUseCase @Inject constructor(
  private val repository: FinancialTransactionRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    transaction: FinancialTransaction,
  ): Result<FinancialTransaction> {
    require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
    require(transaction.amountMinor > 0L) { "Expense amount must be greater than zero" }
    require(transaction.accountId.isNotBlank()) { "Account must be selected" }
    return repository.recordExpense(authenticatedUserId, transaction.copy(type = TransactionType.EXPENSE))
  }
}

class RecordTransferUseCase @Inject constructor(
  private val repository: FinancialTransactionRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    sourceTransaction: FinancialTransaction,
    destinationAccountId: String,
    destinationAmountMinor: Long? = null,
  ): Result<Pair<FinancialTransaction, FinancialTransaction>> {
    require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
    require(sourceTransaction.amountMinor > 0L) { "Transfer amount must be greater than zero" }
    require(sourceTransaction.accountId != destinationAccountId) {
      "Source and destination accounts must be different"
    }
    return repository.recordTransfer(
      authenticatedUserId,
      sourceTransaction,
      destinationAccountId,
      destinationAmountMinor,
    )
  }
}

class RecordGeneralTransactionUseCase @Inject constructor(
  private val repository: FinancialTransactionRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    transaction: FinancialTransaction,
  ): Result<FinancialTransaction> {
    require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
    require(transaction.amountMinor > 0L) { "Amount must be greater than zero" }
    require(transaction.accountId.isNotBlank()) { "Account must be selected" }
    return repository.recordGeneralTransaction(authenticatedUserId, transaction)
  }
}

class DeleteTransactionUseCase @Inject constructor(
  private val repository: FinancialTransactionRepository,
) {
  suspend operator fun invoke(userId: String, transactionId: String): Result<Unit> =
    repository.deleteTransaction(userId, transactionId)
}

class UpdateTransactionUseCase @Inject constructor(
  private val repository: FinancialTransactionRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    updatedTransaction: FinancialTransaction,
    oldTransaction: FinancialTransaction,
  ): Result<Unit> {
    require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
    require(updatedTransaction.amountMinor > 0L) { "Amount must be greater than zero" }
    return repository.updateTransaction(authenticatedUserId, updatedTransaction, oldTransaction)
  }
}
