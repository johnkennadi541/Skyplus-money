package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.ShoppingMealIngredientEntity
import com.example.data.local.db.entity.ShoppingMealPlanEntity
import com.example.data.local.db.entity.ShoppingPantryEntity
import com.example.data.local.db.entity.ShoppingReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingExtendedDao {
  @Query("SELECT * FROM shopping_pantry WHERE userId = :userId ORDER BY expiryAt ASC, name COLLATE NOCASE ASC")
  fun observePantry(userId: String): Flow<List<ShoppingPantryEntity>>

  @Query("SELECT * FROM shopping_pantry WHERE userId = :userId AND id = :id LIMIT 1")
  suspend fun getPantryItem(userId: String, id: String): ShoppingPantryEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertPantryItem(item: ShoppingPantryEntity)

  @Query("UPDATE shopping_pantry SET name = :name, category = :category, quantityDecimal = :quantity, unit = :unit, lowStockThresholdDecimal = :threshold, expiryAt = :expiryAt, updatedAt = :updatedAt WHERE userId = :userId AND id = :id")
  suspend fun updatePantryItem(userId: String, id: String, name: String, category: String, quantity: String, unit: String, threshold: String, expiryAt: Long?, updatedAt: Long): Int

  @Query("DELETE FROM shopping_pantry WHERE userId = :userId AND id = :id")
  suspend fun deletePantryItem(userId: String, id: String): Int

  @Query("SELECT * FROM shopping_meal_plans WHERE userId = :userId ORDER BY plannedDate ASC, createdAt ASC")
  fun observeMealPlans(userId: String): Flow<List<ShoppingMealPlanEntity>>

  @Query("SELECT * FROM shopping_meal_ingredients WHERE userId = :userId AND mealPlanId = :mealPlanId ORDER BY name COLLATE NOCASE ASC")
  fun observeMealIngredients(userId: String, mealPlanId: String): Flow<List<ShoppingMealIngredientEntity>>

  @Query("SELECT * FROM shopping_meal_ingredients WHERE userId = :userId AND mealPlanId = :mealPlanId ORDER BY name COLLATE NOCASE ASC")
  suspend fun getMealIngredients(userId: String, mealPlanId: String): List<ShoppingMealIngredientEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertMealPlan(plan: ShoppingMealPlanEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertMealIngredient(ingredient: ShoppingMealIngredientEntity)

  @Query("DELETE FROM shopping_meal_ingredients WHERE userId = :userId AND mealPlanId = :mealPlanId")
  suspend fun deleteMealIngredients(userId: String, mealPlanId: String): Int

  @Query("DELETE FROM shopping_meal_plans WHERE userId = :userId AND id = :id")
  suspend fun deleteMealPlan(userId: String, id: String): Int

  @Query("SELECT * FROM shopping_receipts WHERE userId = :userId AND id = :receiptId LIMIT 1")
  suspend fun getReceipt(userId: String, receiptId: String): ShoppingReceiptEntity?

  @Query("SELECT * FROM shopping_receipts WHERE userId = :userId AND fingerprint = :fingerprint LIMIT 1")
  suspend fun findReceipt(userId: String, fingerprint: String): ShoppingReceiptEntity?

  @Query("SELECT * FROM shopping_receipts WHERE userId = :userId ORDER BY purchaseDate DESC, createdAt DESC")
  fun observeReceipts(userId: String): Flow<List<ShoppingReceiptEntity>>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertReceipt(receipt: ShoppingReceiptEntity)

  @Query("UPDATE shopping_receipts SET accountId = :accountId, financialTransactionId = :transactionId, status = 'POSTED', updatedAt = :updatedAt WHERE userId = :userId AND id = :receiptId AND status = 'REVIEWED'")
  suspend fun markReceiptPosted(userId: String, receiptId: String, accountId: String, transactionId: String, updatedAt: Long): Int
}
