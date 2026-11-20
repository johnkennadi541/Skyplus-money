package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.dao.AccountMappingRuleDao
import com.example.data.local.db.dao.FinancialAccountDao
import com.example.data.local.db.dao.FinancialTransactionDao
import com.example.data.local.db.entity.FinancialAccountEntity
import com.example.domain.model.FinancialAccount
import com.example.domain.repository.FinancialAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinancialAccountRepositoryImpl @Inject constructor(
  private val database: AppDatabase,
  private val accountDao: FinancialAccountDao,
  private val transactionDao: FinancialTransactionDao,
  private val mappingRuleDao: AccountMappingRuleDao,
  private val dispatchers: DispatcherProvider,
) : FinancialAccountRepository {

  override fun getAccounts(userId: String): Flow<List<FinancialAccount>> =
    accountDao.getAllAccounts(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override suspend fun getAccountById(userId: String, accountId: String): FinancialAccount? =
    withContext(dispatchers.io) {
      accountDao.getAccountById(userId, accountId)?.toDomain()
    }

  override fun getAccountByIdFlow(userId: String, accountId: String): Flow<FinancialAccount?> =
    accountDao.getAccountByIdFlow(userId, accountId)
      .map { it?.toDomain() }
      .flowOn(dispatchers.io)

  override suspend fun createAccount(
    authenticatedUserId: String,
    account: FinancialAccount,
  ): Result<FinancialAccount> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(account.userId == authenticatedUserId) {
          "Account ownership cannot be assigned across users"
        }
        val scoped = account.copy(userId = authenticatedUserId)
        accountDao.insertAccount(FinancialAccountEntity.fromDomain(scoped))
        scoped
      }
    }

  override suspend fun updateAccount(
    authenticatedUserId: String,
    account: FinancialAccount,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(account.userId == authenticatedUserId) {
          "Account ownership cannot be transferred or modified across users"
        }
        database.withTransaction {
          val existing =
            accountDao.getAccountById(authenticatedUserId, account.id)
              ?: throw SecurityException("Account does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) {
            "Account ownership verification failed"
          }
          val immutableEntity =
            FinancialAccountEntity.fromDomain(
              account.copy(
                userId = existing.userId,
                createdAt = existing.createdAt,
                updatedAt = System.currentTimeMillis(),
              )
            )
          val rowsUpdated = accountDao.updateAccount(immutableEntity, authenticatedUserId)
          check(rowsUpdated == 1) { "Account update rejected for authenticated user" }
        }
      }
    }

  override suspend fun setAccountActive(
    userId: String,
    accountId: String,
    isActive: Boolean,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(userId.isNotBlank()) { "Authenticated user ID is required" }
        require(accountId.isNotBlank()) { "Account ID is required" }
        database.withTransaction {
          val existing =
            accountDao.getAccountById(userId, accountId)
              ?: throw SecurityException("Account does not belong to authenticated user")
          require(existing.userId == userId) { "Account ownership verification failed" }
          val rowsUpdated = accountDao.setActive(userId, accountId, isActive, System.currentTimeMillis())
          check(rowsUpdated == 1) { "Account status update rejected for authenticated user" }
        }
      }
    }

  override suspend fun getAccountTransactionCount(userId: String, accountId: String): Int =
    withContext(dispatchers.io) {
      transactionDao.countForAccount(userId, accountId)
    }

  override suspend fun deleteAccount(
    userId: String,
    accountId: String,
    deleteLinkedTransactions: Boolean,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(userId.isNotBlank()) { "Authenticated user ID is required" }
        require(accountId.isNotBlank()) { "Account ID is required" }
        database.withTransaction {
          val existing =
            accountDao.getAccountById(userId, accountId)
              ?: throw SecurityException("Account does not belong to authenticated user")
          require(existing.userId == userId) { "Account ownership verification failed" }
          val txnCount = transactionDao.countForAccount(userId, accountId)
          if (txnCount > 0 && !deleteLinkedTransactions) {
            throw IllegalStateException(
              "Account has $txnCount recorded transactions. Deactivate the account to preserve history or confirm full removal."
            )
          }
          if (txnCount > 0 && deleteLinkedTransactions) {
            transactionDao.deleteByAccount(userId, accountId)
          }
          mappingRuleDao.deleteRulesForAccount(userId, accountId)
          val deletedRows = accountDao.deleteAccount(userId, accountId)
          check(deletedRows == 1) { "Account deletion rejected for authenticated user" }
        }
      }
    }

  override suspend fun adjustAccountBalance(
    userId: String,
    accountId: String,
    deltaMinor: Long,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(userId.isNotBlank()) { "Authenticated user ID is required" }
        require(accountId.isNotBlank()) { "Account ID is required" }
        accountDao.adjustBalance(userId, accountId, deltaMinor, System.currentTimeMillis())
      }
    }
}
