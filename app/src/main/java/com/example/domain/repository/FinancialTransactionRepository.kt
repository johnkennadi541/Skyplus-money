package com.example.domain.repository

import com.example.domain.model.FinancialTransaction
import kotlinx.coroutines.flow.Flow

interface FinancialTransactionRepository {
  fun getTransactions(userId: String): Flow<List<FinancialTransaction>>
  fun getTransactionsByAccount(userId: String, accountId: String): Flow<List<FinancialTransaction>>
  suspend fun getTransactionById(userId: String, transactionId: String): FinancialTransaction?
  suspend fun recordIncome(
    authenticatedUserId: String,
    transaction: FinancialTransaction,
  ): Result<FinancialTransaction>
  suspend fun recordExpense(
    authenticatedUserId: String,
    transaction: FinancialTransaction,
  ): Result<FinancialTransaction>
  suspend fun recordTransfer(
    authenticatedUserId: String,
    sourceTransaction: FinancialTransaction,
    destinationAccountId: String,
    destinationAmountMinor: Long? = null,
  ): Result<Pair<FinancialTransaction, FinancialTransaction>>
  suspend fun recordGeneralTransaction(
    authenticatedUserId: String,
    transaction: FinancialTransaction,
  ): Result<FinancialTransaction>
  suspend fun updateTransaction(
    authenticatedUserId: String,
    updatedTransaction: FinancialTransaction,
    oldTransaction: FinancialTransaction,
  ): Result<Unit>
  suspend fun deleteTransaction(userId: String, transactionId: String): Result<Unit>
  suspend fun findRecentDuplicate(
    userId: String,
    amountMinor: Long,
    currencyCode: String,
    referenceId: String?,
    timeWindowMs: Long,
  ): FinancialTransaction?
}
