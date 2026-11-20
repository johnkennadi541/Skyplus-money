package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ShoppingStorePriceRecord

@Entity(
  tableName = "shopping_store_prices",
  foreignKeys = [
    ForeignKey(
      entity = ShoppingItemEntity::class,
      parentColumns = ["id"],
      childColumns = ["itemId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["itemId"]),
    Index(value = ["userId", "itemId", "recordedAt"]),
    Index(value = ["userId", "listId"]),
  ],
)
data class ShoppingStorePriceEntity(
  @PrimaryKey val id: String,
  val itemId: String,
  val listId: String,
  val userId: String,
  val storeName: String,
  val unitPriceMinor: Long,
  val currencyCode: String,
  val recordedAt: Long,
  val notes: String? = null,
) {
  fun toDomain(): ShoppingStorePriceRecord =
    ShoppingStorePriceRecord(
      id = id,
      itemId = itemId,
      listId = listId,
      userId = userId,
      storeName = storeName,
      unitPriceMinor = unitPriceMinor,
      currencyCode = currencyCode,
      recordedAt = recordedAt,
      notes = notes,
    )

  companion object {
    fun fromDomain(record: ShoppingStorePriceRecord): ShoppingStorePriceEntity =
      ShoppingStorePriceEntity(
        id = record.id,
        itemId = record.itemId,
        listId = record.listId,
        userId = record.userId,
        storeName = record.storeName,
        unitPriceMinor = record.unitPriceMinor,
        currencyCode = record.currencyCode,
        recordedAt = record.recordedAt,
        notes = record.notes,
      )
  }
}
