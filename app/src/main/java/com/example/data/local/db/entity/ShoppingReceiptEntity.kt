package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "shopping_receipts",
  indices = [
    Index(value = ["userId", "fingerprint"], unique = true),
    Index(value = ["userId", "purchaseDate"]),
    Index(value = ["userId", "financialTransactionId"]),
  ],
)
data class ShoppingReceiptEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val listId: String?,
  val accountId: String?,
  val fingerprint: String,
  val merchant: String?,
  val purchaseDate: Long?,
  val currencyCode: String,
  val totalMinor: Long,
  val financialTransactionId: String?,
  val status: String,
  val createdAt: Long,
  val updatedAt: Long,
) {
  companion object {
    fun fromDomain(receipt: com.example.domain.model.ShoppingReceipt): ShoppingReceiptEntity =
      ShoppingReceiptEntity(
        id = receipt.id,
        userId = receipt.userId,
        listId = receipt.listId,
        accountId = receipt.accountId,
        fingerprint = receipt.fingerprint,
        merchant = receipt.merchant,
        purchaseDate = receipt.purchaseDate,
        currencyCode = receipt.currencyCode,
        totalMinor = receipt.totalMinor,
        financialTransactionId = receipt.financialTransactionId,
        status = receipt.status,
        createdAt = receipt.createdAt,
        updatedAt = receipt.updatedAt,
      )
  }
}
