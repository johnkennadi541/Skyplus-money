package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ShoppingListItem

@Entity(
  tableName = "shopping_items",
  foreignKeys = [
    ForeignKey(
      entity = ShoppingListEntity::class,
      parentColumns = ["id"],
      childColumns = ["listId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["listId"]),
    Index(value = ["userId", "listId"]),
    Index(value = ["userId", "isPurchased"]),
  ],
)
data class ShoppingItemEntity(
  @PrimaryKey val id: String,
  val listId: String,
  val userId: String,
  val name: String,
  val category: String,
  val quantityDecimal: String,
  val unit: String,
  val estimatedUnitPriceMinor: Long,
  val estimatedTotalMinor: Long,
  val storeName: String? = null,
  val notes: String? = null,
  val isPurchased: Boolean,
  val actualUnitPriceMinor: Long? = null,
  val actualTotalMinor: Long? = null,
  val purchasedAt: Long? = null,
  val createdAt: Long,
  val updatedAt: Long,
) {
  fun toDomain(): ShoppingListItem =
    ShoppingListItem(
      id = id,
      listId = listId,
      userId = userId,
      name = name,
      category = category,
      quantityDecimal = quantityDecimal,
      unit = unit,
      estimatedUnitPriceMinor = estimatedUnitPriceMinor,
      estimatedTotalMinor = estimatedTotalMinor,
      storeName = storeName,
      notes = notes,
      isPurchased = isPurchased,
      actualUnitPriceMinor = actualUnitPriceMinor,
      actualTotalMinor = actualTotalMinor,
      purchasedAt = purchasedAt,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )

  companion object {
    fun fromDomain(item: ShoppingListItem): ShoppingItemEntity =
      ShoppingItemEntity(
        id = item.id,
        listId = item.listId,
        userId = item.userId,
        name = item.name,
        category = item.category,
        quantityDecimal = item.quantityDecimal,
        unit = item.unit,
        estimatedUnitPriceMinor = item.estimatedUnitPriceMinor,
        estimatedTotalMinor = item.estimatedTotalMinor,
        storeName = item.storeName,
        notes = item.notes,
        isPurchased = item.isPurchased,
        actualUnitPriceMinor = item.actualUnitPriceMinor,
        actualTotalMinor = item.actualTotalMinor,
        purchasedAt = item.purchasedAt,
        createdAt = item.createdAt,
        updatedAt = item.updatedAt,
      )
  }
}
