package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.dao.FinancialAccountDao
import com.example.data.local.db.dao.FinancialTransactionDao
import com.example.data.local.db.entity.FinancialTransactionEntity
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.TransactionCategories
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.repository.FinancialTransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinancialTransactionRepositoryImpl @Inject constructor(
  private val database: AppDatabase,
  private val transactionDao: FinancialTransactionDao,
  private val accountDao: FinancialAccountDao,
  private val dispatchers: DispatcherProvider,
) : FinancialTransactionRepository {

  override fun getTransactions(userId: String): Flow<List<FinancialTransaction>> =
    transactionDao.getAllTransactions(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun getTransactionsByAccount(userId: String, accountId: String): Flow<List<FinancialTransaction>> =
    transactionDao.getTransactionsByAccount(userId, accountId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override suspend fun getTransactionById(userId: String, transactionId: String): FinancialTransaction? =
    withContext(dispatchers.io) {
      transactionDao.getTransactionById(userId, transactionId)?.toDomain()
    }

  override suspend fun recordExpense(
    authenticatedUserId: String,
    transaction: FinancialTransaction,
  ): Result<FinancialTransaction> =
    recordGeneralTransaction(authenticatedUserId, transaction.copy(type = TransactionType.EXPENSE))

  override suspend fun recordIncome(
    authenticatedUserId: String,
    transaction: FinancialTransaction,
  ): Result<FinancialTransaction> =
    recordGeneralTransaction(authenticatedUserId, transaction.copy(type = TransactionType.INCOME))

  override suspend fun recordTransfer(
    authenticatedUserId: String,
    sourceTransaction: FinancialTransaction,
    destinationAccountId: String,
    destinationAmountMinor: Long?,
  ): Result<Pair<FinancialTransaction, FinancialTransaction>> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(sourceTransaction.userId == authenticatedUserId) {
          "Transaction ownership cannot be assigned across users"
        }
        database.withTransaction {
          val sourceAccount =
            accountDao.getAccountById(authenticatedUserId, sourceTransaction.accountId)
              ?: throw IllegalArgumentException("Source account not found")
          val destAccount =
            accountDao.getAccountById(authenticatedUserId, destinationAccountId)
              ?: throw IllegalArgumentException("Destination account not found")
          require(sourceAccount.userId == authenticatedUserId && destAccount.userId == authenticatedUserId) {
            "Transfer account ownership verification failed"
          }

          val creditMinor: Long =
            if (sourceAccount.currencyCode.equals(destAccount.currencyCode, ignoreCase = true)) {
              sourceTransaction.amountMinor
            } else {
              require(destinationAmountMinor != null && destinationAmountMinor > 0L) {
                "Cross-currency transfer (${sourceAccount.currencyCode} -> ${destAccount.currencyCode}) requires explicit converted destination amount"
              }
              destinationAmountMinor
            }

          val transferId = UUID.randomUUID().toString()
          val now = System.currentTimeMillis()

          // Source transaction (debit from source account)
          val sourceEntity =
            FinancialTransactionEntity.fromDomain(
              sourceTransaction.copy(
                userId = authenticatedUserId,
                type = TransactionType.TRANSFER,
                category = TransactionCategories.TRANSFER_OUT_CATEGORY,
                currencyCode = sourceAccount.currencyCode,
                linkedTransferId = transferId,
                transferDestinationAccountId = destinationAccountId,
                status = TransactionStatus.COMPLETED,
                notes =
                  sourceTransaction.notes?.takeIf { it.isNotBlank() }
                    ?: "Transfer to ${destAccount.name}",
                createdAt = now,
                updatedAt = now,
              )
            )

          // Destination transaction (credit into destination account)
          val destEntity =
            FinancialTransactionEntity.fromDomain(
              sourceTransaction.copy(
                id = UUID.randomUUID().toString(),
                userId = authenticatedUserId,
                accountId = destinationAccountId,
                type = TransactionType.TRANSFER,
                category = TransactionCategories.TRANSFER_IN_CATEGORY,
                amountMinor = creditMinor,
                currencyCode = destAccount.currencyCode,
                linkedTransferId = transferId,
                transferDestinationAccountId = sourceTransaction.accountId,
                status = TransactionStatus.COMPLETED,
                notes =
                  sourceTransaction.notes?.takeIf { it.isNotBlank() }
                    ?: "Transfer from ${sourceAccount.name}",
                createdAt = now,
                updatedAt = now,
              )
            )

          transactionDao.insertTransaction(sourceEntity)
          transactionDao.insertTransaction(destEntity)

          // Atomic balance updates: debit source, credit destination
          accountDao.adjustBalance(authenticatedUserId, sourceTransaction.accountId, -sourceTransaction.amountMinor, now)
          accountDao.adjustBalance(authenticatedUserId, destinationAccountId, creditMinor, now)

          Pair(sourceEntity.toDomain(), destEntity.toDomain())
        }
      }
    }

  override suspend fun recordGeneralTransaction(
    authenticatedUserId: String,
    transaction: FinancialTransaction,
  ): Result<FinancialTransaction> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(transaction.userId == authenticatedUserId) {
          "Transaction ownership cannot be assigned across users"
        }
        database.withTransaction {
          val account =
            accountDao.getAccountById(authenticatedUserId, transaction.accountId)
              ?: throw IllegalArgumentException("Selected account not found")
          require(account.userId == authenticatedUserId) { "Account ownership verification failed" }
          require(account.currencyCode.equals(transaction.currencyCode, ignoreCase = true)) {
            "Currency mismatch: transaction currency ${transaction.currencyCode} does not match account currency ${account.currencyCode}"
          }

          val effectiveStatus =
            when (transaction.type) {
              TransactionType.PENDING -> TransactionStatus.PENDING
              TransactionType.FAILED -> TransactionStatus.FAILED
              else -> transaction.status
            }
          val normalizedTxn = transaction.copy(userId = authenticatedUserId, status = effectiveStatus)
          val entity = FinancialTransactionEntity.fromDomain(normalizedTxn)
          transactionDao.insertTransaction(entity)

          val delta = balanceDeltaFor(normalizedTxn.type, normalizedTxn.status, normalizedTxn.amountMinor)
          if (delta != 0L) {
            accountDao.adjustBalance(authenticatedUserId, normalizedTxn.accountId, delta, normalizedTxn.updatedAt)
          }
          entity.toDomain()
        }
      }
    }

  override suspend fun updateTransaction(
    authenticatedUserId: String,
    updatedTransaction: FinancialTransaction,
    oldTransaction: FinancialTransaction,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(
          oldTransaction.userId == authenticatedUserId &&
            updatedTransaction.userId == authenticatedUserId
        ) {
          "Transaction ownership cannot be transferred or modified across users"
        }
        database.withTransaction {
          val existing =
            transactionDao.getTransactionById(authenticatedUserId, updatedTransaction.id)
              ?: throw SecurityException("Transaction does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) {
            "Transaction ownership verification failed"
          }
          require(existing.type != TransactionType.TRANSFER.name &&
            oldTransaction.type != TransactionType.TRANSFER &&
            updatedTransaction.type != TransactionType.TRANSFER
          ) {
            "Transfer transactions cannot be edited. Delete and recreate the transfer to preserve both linked entries and account balances."
          }
          requireNotNull(accountDao.getAccountById(authenticatedUserId, oldTransaction.accountId)) {
            "Original account not found for authenticated user"
          }
          requireNotNull(accountDao.getAccountById(authenticatedUserId, updatedTransaction.accountId)) {
            "Destination account not found for authenticated user"
          }

          val now = System.currentTimeMillis()

          val oldDelta = balanceDeltaFor(oldTransaction.type, oldTransaction.status, oldTransaction.amountMinor)
          if (oldDelta != 0L) {
            accountDao.adjustBalance(authenticatedUserId, oldTransaction.accountId, -oldDelta, now)
          }

          val effectiveStatus =
            when (updatedTransaction.type) {
              TransactionType.PENDING -> TransactionStatus.PENDING
              TransactionType.FAILED -> TransactionStatus.FAILED
              else -> updatedTransaction.status
            }
          val normalizedUpdated =
            updatedTransaction.copy(
              userId = existing.userId,
              status = effectiveStatus,
              createdAt = existing.createdAt,
              updatedAt = now,
            )
          val newDelta = balanceDeltaFor(normalizedUpdated.type, normalizedUpdated.status, normalizedUpdated.amountMinor)
          if (newDelta != 0L) {
            accountDao.adjustBalance(authenticatedUserId, normalizedUpdated.accountId, newDelta, now)
          }

          val rowsUpdated =
            transactionDao.updateTransaction(
              transaction = FinancialTransactionEntity.fromDomain(normalizedUpdated),
              authenticatedUserId = authenticatedUserId,
            )
          check(rowsUpdated == 1) { "Transaction update rejected for authenticated user" }
        }
      }
    }

  override suspend fun deleteTransaction(userId: String, transactionId: String): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(userId.isNotBlank()) { "Authenticated user ID is required" }
        require(transactionId.isNotBlank()) { "Transaction ID is required" }
        database.withTransaction {
          val txn =
            transactionDao.getTransactionById(userId, transactionId)
              ?: throw SecurityException("Transaction does not belong to authenticated user")
          require(txn.userId == userId) { "Transaction ownership verification failed" }
          val now = System.currentTimeMillis()
          val type = runCatching { TransactionType.valueOf(txn.type) }.getOrDefault(TransactionType.EXPENSE)
          val status = runCatching { TransactionStatus.valueOf(txn.status) }.getOrDefault(TransactionStatus.COMPLETED)

          if (status == TransactionStatus.COMPLETED && type == TransactionType.TRANSFER && !txn.linkedTransferId.isNullOrBlank()) {
            val linkedEntries = transactionDao.findByLinkedTransferId(userId, txn.linkedTransferId)
            for (entry in linkedEntries) {
              val reverseDelta =
                if (entry.category == TransactionCategories.TRANSFER_IN_CATEGORY) {
                  -entry.amountMinor
                } else {
                  entry.amountMinor
                }
              accountDao.adjustBalance(userId, entry.accountId, reverseDelta, now)
              transactionDao.deleteTransaction(userId, entry.id)
            }
          } else {
            val delta = balanceDeltaFor(type, status, txn.amountMinor)
            if (delta != 0L) {
              accountDao.adjustBalance(userId, txn.accountId, -delta, now)
            }
            transactionDao.deleteTransaction(userId, transactionId)
          }
        }
      }
    }

  private fun balanceDeltaFor(
    type: TransactionType,
    status: TransactionStatus,
    amountMinor: Long,
  ): Long {
    if (status != TransactionStatus.COMPLETED) return 0L
    return when (type) {
      TransactionType.EXPENSE -> -amountMinor
      TransactionType.INCOME, TransactionType.REFUND, TransactionType.REVERSAL -> amountMinor
      TransactionType.TRANSFER, TransactionType.PENDING, TransactionType.FAILED -> 0L
    }
  }

  override suspend fun findRecentDuplicate(
    userId: String,
    amountMinor: Long,
    currencyCode: String,
    referenceId: String?,
    timeWindowMs: Long,
  ): FinancialTransaction? =
    withContext(dispatchers.io) {
      if (!referenceId.isNullOrBlank()) {
        transactionDao.findByReferenceId(userId, referenceId)?.toDomain()
      } else {
        null
      }
    }
}
