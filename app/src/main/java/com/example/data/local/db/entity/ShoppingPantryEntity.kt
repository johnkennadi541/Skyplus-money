package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "shopping_pantry",
  indices = [Index(value = ["userId", "name", "unit"], unique = true), Index(value = ["userId", "expiryAt"])],
)
data class ShoppingPantryEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val name: String,
  val category: String,
  val quantityDecimal: String,
  val unit: String,
  val lowStockThresholdDecimal: String,
  val expiryAt: Long?,
  val updatedAt: Long,
)
