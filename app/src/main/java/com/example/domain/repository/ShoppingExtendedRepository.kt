package com.example.domain.repository

import com.example.domain.model.ShoppingMealPlan
import com.example.domain.model.ShoppingPantryItem
import com.example.domain.model.ShoppingReceipt
import kotlinx.coroutines.flow.Flow

interface ShoppingExtendedRepository {
  fun observePantry(userId: String): Flow<List<ShoppingPantryItem>>
  fun observeMealPlans(userId: String): Flow<List<ShoppingMealPlan>>
  fun observeReceipts(userId: String): Flow<List<ShoppingReceipt>>
  suspend fun savePantryItem(authenticatedUserId: String, item: ShoppingPantryItem): Result<Unit>
  suspend fun deletePantryItem(authenticatedUserId: String, itemId: String): Result<Unit>
  suspend fun createMealPlan(authenticatedUserId: String, plan: ShoppingMealPlan): Result<Unit>
  suspend fun deleteMealPlan(authenticatedUserId: String, planId: String): Result<Unit>
  suspend fun saveReceiptReview(authenticatedUserId: String, receipt: ShoppingReceipt): Result<ShoppingReceipt>
  suspend fun postReceiptExpense(authenticatedUserId: String, receiptId: String, accountId: String): Result<ShoppingReceipt>
}
