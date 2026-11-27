package com.example.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ShoppingList

@Entity(
  tableName = "shopping_lists",
  indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "name"]),
    Index(value = ["userId", "currencyCode"]),
  ],
)
data class ShoppingListEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val name: String,
  val currencyCode: String,
  val budgetMinor: Long? = null,
  val warningThresholdPercent: Int,
  val createdAt: Long,
  val updatedAt: Long,
  @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
) {
  fun toDomain(): ShoppingList =
    ShoppingList(
      id = id,
      userId = userId,
      name = name,
      currencyCode = currencyCode,
      budgetMinor = budgetMinor,
      warningThresholdPercent = warningThresholdPercent,
      createdAt = createdAt,
      updatedAt = updatedAt,
      isArchived = isArchived,
    )

  companion object {
    fun fromDomain(list: ShoppingList): ShoppingListEntity =
      ShoppingListEntity(
        id = list.id,
        userId = list.userId,
        name = list.name,
        currencyCode = list.currencyCode,
        budgetMinor = list.budgetMinor,
        warningThresholdPercent = list.warningThresholdPercent,
        createdAt = list.createdAt,
        updatedAt = list.updatedAt,
        isArchived = list.isArchived,
      )
  }
}
