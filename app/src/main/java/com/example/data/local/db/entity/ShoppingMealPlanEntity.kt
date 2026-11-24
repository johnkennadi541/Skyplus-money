package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "shopping_meal_plans",
  indices = [Index(value = ["userId", "plannedDate"]), Index(value = ["userId", "mealType"])],
)
data class ShoppingMealPlanEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val title: String,
  val plannedDate: Long,
  val mealType: String,
  val notes: String?,
  val createdAt: Long,
  val updatedAt: Long,
)
