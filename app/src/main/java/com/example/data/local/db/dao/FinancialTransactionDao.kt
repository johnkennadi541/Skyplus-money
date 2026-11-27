package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.FinancialTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialTransactionDao {
  @Query("SELECT * FROM financial_transactions WHERE userId = :userId ORDER BY transactionDate DESC, createdAt DESC")
  fun getAllTransactions(userId: String): Flow<List<FinancialTransactionEntity>>

  @Query(
    "SELECT * FROM financial_transactions WHERE userId = :userId AND accountId = :accountId ORDER BY transactionDate DESC, createdAt DESC"
  )
  fun getTransactionsByAccount(userId: String, accountId: String): Flow<List<FinancialTransactionEntity>>

  @Query("SELECT * FROM financial_transactions WHERE userId = :userId AND id = :transactionId LIMIT 1")
  suspend fun getTransactionById(userId: String, transactionId: String): FinancialTransactionEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertTransaction(transaction: FinancialTransactionEntity)

  @Query(
    """
    UPDATE financial_transactions
    SET accountId = :accountId,
        type = :type,
        status = :status,
        amountMinor = :amountMinor,
        currencyCode = :currencyCode,
        category = :category,
        merchantOrPayee = :merchantOrPayee,
        notes = :notes,
        transactionDate = :transactionDate,
        linkedTransferId = :linkedTransferId,
        transferDestinationAccountId = :transferDestinationAccountId,
        isAutomaticallyDetected = :isAutomaticallyDetected,
        sourceApplication = :sourceApplication,
        referenceId = :referenceId,
        confidenceScore = :confidenceScore,
        updatedAt = :updatedAt,
        normalizedMerchant = :normalizedMerchant
    WHERE id = :id AND userId = :userId
    """
  )
  suspend fun updateTransactionScoped(
    id: String,
    userId: String,
    accountId: String,
    type: String,
    status: String,
    amountMinor: Long,
    currencyCode: String,
    category: String,
    merchantOrPayee: String?,
    notes: String?,
    transactionDate: Long,
    linkedTransferId: String?,
    transferDestinationAccountId: String?,
    isAutomaticallyDetected: Boolean,
    sourceApplication: String?,
    referenceId: String?,
    confidenceScore: Float?,
    updatedAt: Long,
    normalizedMerchant: String?,
  ): Int

  suspend fun updateTransaction(
    transaction: FinancialTransactionEntity,
    authenticatedUserId: String,
  ): Int {
    if (authenticatedUserId.isBlank() || transaction.userId != authenticatedUserId) return 0
    return updateTransactionScoped(
      id = transaction.id,
      userId = authenticatedUserId,
      accountId = transaction.accountId,
      type = transaction.type,
      status = transaction.status,
      amountMinor = transaction.amountMinor,
      currencyCode = transaction.currencyCode,
      category = transaction.category,
      merchantOrPayee = transaction.merchantOrPayee,
      notes = transaction.notes,
      transactionDate = transaction.transactionDate,
      linkedTransferId = transaction.linkedTransferId,
      transferDestinationAccountId = transaction.transferDestinationAccountId,
      isAutomaticallyDetected = transaction.isAutomaticallyDetected,
      sourceApplication = transaction.sourceApplication,
      referenceId = transaction.referenceId,
      confidenceScore = transaction.confidenceScore,
      updatedAt = transaction.updatedAt,
      normalizedMerchant = transaction.normalizedMerchant,
    )
  }

  @Query("DELETE FROM financial_transactions WHERE userId = :userId AND id = :transactionId")
  suspend fun deleteTransaction(userId: String, transactionId: String): Int

  @Query(
    """
    SELECT * FROM financial_transactions 
    WHERE userId = :userId 
      AND amountMinor = :amountMinor 
      AND currencyCode = :currencyCode 
      AND transactionDate BETWEEN :startTime AND :endTime 
    ORDER BY transactionDate DESC
    """
  )
  suspend fun findRecentMatching(
    userId: String,
    amountMinor: Long,
    currencyCode: String,
    startTime: Long,
    endTime: Long,
  ): List<FinancialTransactionEntity>

  @Query(
    "SELECT * FROM financial_transactions WHERE userId = :userId AND referenceId = :referenceId LIMIT 1"
  )
  suspend fun findByReferenceId(userId: String, referenceId: String): FinancialTransactionEntity?

  @Query(
    "SELECT * FROM financial_transactions WHERE userId = :userId AND linkedTransferId = :linkedTransferId"
  )
  suspend fun findByLinkedTransferId(userId: String, linkedTransferId: String): List<FinancialTransactionEntity>

  @Query(
    """
    SELECT COUNT(*) FROM financial_transactions
    WHERE userId = :userId
      AND accountId = :accountId
      AND (type = 'TRANSFER' OR linkedTransferId IS NOT NULL)
    """
  )
  suspend fun countTransferEntriesForAccount(userId: String, accountId: String): Int

  @Query("SELECT COUNT(*) FROM financial_transactions WHERE userId = :userId AND accountId = :accountId")
  suspend fun countForAccount(userId: String, accountId: String): Int

  @Query("DELETE FROM financial_transactions WHERE userId = :userId AND accountId = :accountId")
  suspend fun deleteByAccount(userId: String, accountId: String)
}
