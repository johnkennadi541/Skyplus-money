package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType

@Entity(
  tableName = "financial_transactions",
  indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "accountId"]),
    Index(value = ["userId", "transactionDate"]),
    Index(value = ["userId", "referenceId"]),
    Index(value = ["userId", "linkedTransferId"]),
    Index(value = ["userId", "normalizedMerchant"]),
  ],
)
data class FinancialTransactionEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val accountId: String,
  val type: String,
  val status: String,
  val amountMinor: Long,
  val currencyCode: String,
  val category: String,
  val merchantOrPayee: String?,
  val notes: String?,
  val transactionDate: Long,
  val linkedTransferId: String?,
  val transferDestinationAccountId: String?,
  val isAutomaticallyDetected: Boolean,
  val sourceApplication: String?,
  val referenceId: String?,
  val confidenceScore: Float?,
  val createdAt: Long,
  val updatedAt: Long,
  val normalizedMerchant: String? = null,
  val isArchived: Boolean = false,
) {
  fun toDomain(): FinancialTransaction =
    FinancialTransaction(
      id = id,
      userId = userId,
      accountId = accountId,
      type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
      status = runCatching { TransactionStatus.valueOf(status) }.getOrDefault(TransactionStatus.COMPLETED),
      amountMinor = amountMinor,
      currencyCode = currencyCode,
      category = category,
      merchantOrPayee = merchantOrPayee,
      notes = notes,
      transactionDate = transactionDate,
      linkedTransferId = linkedTransferId,
      transferDestinationAccountId = transferDestinationAccountId,
      isAutomaticallyDetected = isAutomaticallyDetected,
      sourceApplication = sourceApplication,
      referenceId = referenceId,
      confidenceScore = confidenceScore,
      createdAt = createdAt,
      updatedAt = updatedAt,
      isArchived = isArchived,
    )

  companion object {
    fun fromDomain(transaction: FinancialTransaction): FinancialTransactionEntity =
      FinancialTransactionEntity(
        id = transaction.id,
        userId = transaction.userId,
        accountId = transaction.accountId,
        type = transaction.type.name,
        status = transaction.status.name,
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
        createdAt = transaction.createdAt,
        updatedAt = transaction.updatedAt,
        normalizedMerchant = transaction.merchantOrPayee?.trim()?.lowercase()?.ifBlank { null },
        isArchived = transaction.isArchived,
      )
  }
}
