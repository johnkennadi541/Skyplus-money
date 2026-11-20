package com.example.domain.repository

import com.example.domain.model.FinancialAccount
import kotlinx.coroutines.flow.Flow

interface FinancialAccountRepository {
  fun getAccounts(userId: String): Flow<List<FinancialAccount>>
  suspend fun getAccountById(userId: String, accountId: String): FinancialAccount?
  fun getAccountByIdFlow(userId: String, accountId: String): Flow<FinancialAccount?>
  suspend fun createAccount(authenticatedUserId: String, account: FinancialAccount): Result<FinancialAccount>
  suspend fun updateAccount(authenticatedUserId: String, account: FinancialAccount): Result<Unit>
  suspend fun setAccountActive(userId: String, accountId: String, isActive: Boolean): Result<Unit>
  suspend fun getAccountTransactionCount(userId: String, accountId: String): Int
  suspend fun deleteAccount(userId: String, accountId: String, deleteLinkedTransactions: Boolean = false): Result<Unit>
  suspend fun adjustAccountBalance(userId: String, accountId: String, deltaMinor: Long): Result<Unit>
}
