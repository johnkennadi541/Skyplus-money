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
        require(sourceTransaction.accountId.isNotBlank()) { "Source account ID is required" }
        require(destinationAccountId.isNotBlank()) { "Destination account ID is required" }
        require(sourceTransaction.accountId != destinationAccountId) {
          "Source and destination accounts must be different"
        }
        require(sourceTransaction.amountMinor > 0L) { "Transfer amount must be greater than zero" }
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
        require(transaction.amountMinor > 0L) { "Transaction amount must be greater than zero" }
        require(transaction.type != TransactionType.TRANSFER) {
          "Transfers must be recorded through the paired transfer workflow."
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
            updatedTransaction.userId == authenticatedUserId &&
            oldTransaction.id == updatedTransaction.id
        ) {
          "Transaction ownership cannot be transferred or modified across users"
        }
        require(updatedTransaction.amountMinor > 0L) {
          "Transaction amount must be greater than zero"
        }
        database.withTransaction {
          val existing =
            transactionDao.getTransactionById(authenticatedUserId, updatedTransaction.id)
              ?: throw SecurityException("Transaction does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) {
            "Transaction ownership verification failed"
          }
          require(existing.amountMinor > 0L) {
            "Persisted transaction amount is invalid; refusing to alter account balances."
          }
          require(
            existing.type != TransactionType.TRANSFER.name &&
              updatedTransaction.type != TransactionType.TRANSFER
          ) {
            "Transfer transactions cannot be edited. Delete and recreate the transfer to preserve both linked entries and account balances."
          }
          // The persisted transaction is authoritative; never trust the caller's stale
          // snapshot for the original account, type, status, or amount.
          val originalAccount =
            accountDao.getAccountById(authenticatedUserId, existing.accountId)
              ?: throw IllegalArgumentException("Original account not found for authenticated user")
          val destinationAccount =
            accountDao.getAccountById(authenticatedUserId, updatedTransaction.accountId)
              ?: throw IllegalArgumentException("Destination account not found for authenticated user")
          require(existing.currencyCode.equals(originalAccount.currencyCode, ignoreCase = true)) {
            "Persisted transaction currency does not match its original account currency; refusing to alter balances."
          }
          require(updatedTransaction.currencyCode.equals(destinationAccount.currencyCode, ignoreCase = true)) {
            "Currency mismatch: transaction currency ${updatedTransaction.currencyCode} does not match destination account currency ${destinationAccount.currencyCode}"
          }

          val now = System.currentTimeMillis()

          val persistedOldType = TransactionType.valueOf(existing.type)
          val persistedOldStatus = TransactionStatus.valueOf(existing.status)
          val oldDelta = balanceDeltaFor(persistedOldType, persistedOldStatus, existing.amountMinor)
          if (oldDelta != 0L) {
            accountDao.adjustBalance(authenticatedUserId, existing.accountId, -oldDelta, now)
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
          // Persisted enum values and amounts are untrusted database state. Never guess a
          // fallback type/status: an unknown value must abort the transaction without writes.
          val type = TransactionType.valueOf(txn.type)
          val status = TransactionStatus.valueOf(txn.status)
          require(txn.amountMinor > 0L) {
            "Persisted transaction amount is invalid; refusing to alter account balances."
          }

          if (type == TransactionType.TRANSFER) {
            require(status == TransactionStatus.COMPLETED) {
              "Only completed paired transfers can be deleted."
            }
            val transferId = txn.linkedTransferId?.takeIf { it.isNotBlank() }
              ?: throw IllegalStateException("Transfer is missing its linked transfer ID.")
            val linkedEntries = transactionDao.findByLinkedTransferId(userId, transferId)
            require(linkedEntries.size == 2) {
              "Transfer ledger is incomplete or duplicated; refusing to delete either entry."
            }
            val outgoing = linkedEntries.singleOrNull {
              it.category == TransactionCategories.TRANSFER_OUT_CATEGORY
            } ?: throw IllegalStateException("Transfer is missing its unique outgoing entry.")
            val incoming = linkedEntries.singleOrNull {
              it.category == TransactionCategories.TRANSFER_IN_CATEGORY
            } ?: throw IllegalStateException("Transfer is missing its unique incoming entry.")

            require(
              outgoing.id != incoming.id &&
                outgoing.id == txn.id || incoming.id == txn.id
            ) { "Selected transaction is not part of a valid paired transfer." }
            require(
              outgoing.type == TransactionType.TRANSFER.name &&
                incoming.type == TransactionType.TRANSFER.name &&
                outgoing.status == TransactionStatus.COMPLETED.name &&
                incoming.status == TransactionStatus.COMPLETED.name &&
                outgoing.amountMinor > 0L &&
                incoming.amountMinor > 0L &&
                outgoing.accountId.isNotBlank() &&
                incoming.accountId.isNotBlank() &&
                outgoing.accountId != incoming.accountId &&
                outgoing.transferDestinationAccountId == incoming.accountId &&
                incoming.transferDestinationAccountId == outgoing.accountId &&
                outgoing.linkedTransferId == transferId &&
                incoming.linkedTransferId == transferId
            ) { "Transfer pair failed integrity checks; refusing to change balances." }

            val sourceAccount = accountDao.getAccountById(userId, outgoing.accountId)
              ?: throw IllegalStateException("Transfer source account is missing.")
            val destinationAccount = accountDao.getAccountById(userId, incoming.accountId)
              ?: throw IllegalStateException("Transfer destination account is missing.")
            require(
              sourceAccount.userId == userId &&
                destinationAccount.userId == userId &&
                sourceAccount.currencyCode.equals(outgoing.currencyCode, ignoreCase = true) &&
                destinationAccount.currencyCode.equals(incoming.currencyCode, ignoreCase = true)
            ) { "Transfer account ownership or currency validation failed." }

            // Reverse both legs atomically, then verify that both rows were deleted.
            accountDao.adjustBalance(userId, outgoing.accountId, outgoing.amountMinor, now)
            accountDao.adjustBalance(userId, incoming.accountId, -incoming.amountMinor, now)
            check(transactionDao.deleteTransaction(userId, outgoing.id) == 1) {
              "Transfer outgoing entry deletion failed."
            }
            check(transactionDao.deleteTransaction(userId, incoming.id) == 1) {
              "Transfer incoming entry deletion failed."
            }
          } else {
            require(txn.linkedTransferId.isNullOrBlank()) {
              "Non-transfer transaction contains a transfer link; refusing to delete it."
            }
            val delta = balanceDeltaFor(type, status, txn.amountMinor)
            if (delta != 0L) {
              accountDao.adjustBalance(userId, txn.accountId, Math.negateExact(delta), now)
            }
            check(transactionDao.deleteTransaction(userId, transactionId) == 1) {
              "Transaction deletion rejected for authenticated user."
            }
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
