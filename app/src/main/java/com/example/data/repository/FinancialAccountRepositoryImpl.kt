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
          // A card-linked account anchors a credit card record: changing its type or currency
          // would break the card's limit, statement cycle and liability maths.
          val linkedCard = database.creditCardDao().getCardByAccountId(authenticatedUserId, account.id)
          if (linkedCard != null) {
            require(account.type.name == existing.accountType) {
              "Cannot change the type of an account linked to a credit card"
            }
            require(account.currencyCode.equals(existing.currencyCode, ignoreCase = true)) {
              "Cannot change the currency of an account linked to a credit card"
            }
          }
          require(account.currencyCode.equals(existing.currencyCode, ignoreCase = true)) {
            "Account currency is immutable. Create a new account in the desired currency and record any conversion explicitly."
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
          // Remove the card record in the same transaction so an orphan card can never survive
          // its account. The card row carries no ledger data of its own.
          database.creditCardDao().getCardByAccountId(userId, accountId)?.let { linkedCard ->
            database.creditCardDao().deleteCard(userId, linkedCard.id)
          }
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
        // Preserve the existing UID-isolation contract: a caller cannot learn whether
        // another user's account exists, and cross-UID adjustments remain a no-op.
        if (accountDao.getAccountById(userId, accountId) == null) return@runCatching
        val rowsUpdated = accountDao.adjustBalance(userId, accountId, deltaMinor, System.currentTimeMillis())
        check(rowsUpdated == 1) { "Account balance adjustment rejected for authenticated user or overflow" }
      }
    }
}
