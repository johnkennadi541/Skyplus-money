package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "shopping_meal_ingredients",
  foreignKeys = [
    ForeignKey(
      entity = ShoppingMealPlanEntity::class,
      parentColumns = ["id"],
      childColumns = ["mealPlanId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["mealPlanId"]), Index(value = ["userId", "name"])],
)
data class ShoppingMealIngredientEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val mealPlanId: String,
  val name: String,
  val quantityDecimal: String,
  val unit: String,
  val shoppingItemId: String?,
)
