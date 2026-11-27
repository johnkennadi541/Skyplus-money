package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ShoppingPurchaseHistory

@Entity(
  tableName = "shopping_purchase_history",
  indices = [
    Index(value = ["userId", "itemId", "purchasedAt"]),
    Index(value = ["userId", "listId", "purchasedAt"]),
  ],
)
data class ShoppingPurchaseHistoryEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val listId: String,
  val itemId: String,
  val itemName: String,
  val quantityDecimal: String,
  val unit: String,
  val actualUnitPriceMinor: Long,
  val actualTotalMinor: Long,
  val currencyCode: String,
  val storeName: String?,
  val purchasedAt: Long,
  val recordedAt: Long,
) {
  fun toDomain() = ShoppingPurchaseHistory(
    id = id,
    userId = userId,
    listId = listId,
    itemId = itemId,
    itemName = itemName,
    quantityDecimal = quantityDecimal,
    unit = unit,
    actualUnitPriceMinor = actualUnitPriceMinor,
    actualTotalMinor = actualTotalMinor,
    currencyCode = currencyCode,
    storeName = storeName,
    purchasedAt = purchasedAt,
    recordedAt = recordedAt,
  )

  companion object {
    fun fromDomain(record: ShoppingPurchaseHistory) = ShoppingPurchaseHistoryEntity(
      id = record.id,
      userId = record.userId,
      listId = record.listId,
      itemId = record.itemId,
      itemName = record.itemName,
      quantityDecimal = record.quantityDecimal,
      unit = record.unit,
      actualUnitPriceMinor = record.actualUnitPriceMinor,
      actualTotalMinor = record.actualTotalMinor,
      currencyCode = record.currencyCode,
      storeName = record.storeName,
      purchasedAt = record.purchasedAt,
      recordedAt = record.recordedAt,
    )
  }
}
