package com.example.domain.repository

import com.example.domain.model.ShoppingList
import com.example.domain.model.ShoppingListItem
import com.example.domain.model.ShoppingStorePriceRecord
import com.example.domain.model.ShoppingPurchaseHistory
import kotlinx.coroutines.flow.Flow

interface ShoppingRepository {

  fun getLists(userId: String): Flow<List<ShoppingList>>

  suspend fun getListById(userId: String, listId: String): ShoppingList?

  fun observeArchivedLists(userId: String): Flow<List<ShoppingList>>

  fun observeArchivedItems(userId: String): Flow<List<ShoppingListItem>>

  suspend fun restoreList(authenticatedUserId: String, listId: String): Result<Unit>

  suspend fun restoreItem(authenticatedUserId: String, itemId: String): Result<Unit>

  fun getItemsForUser(userId: String): Flow<List<ShoppingListItem>>

  fun getItemsForList(userId: String, listId: String): Flow<List<ShoppingListItem>>

  fun getPurchaseHistoryForItem(userId: String, itemId: String): Flow<List<ShoppingPurchaseHistory>>

  fun getPurchaseHistoryForList(userId: String, listId: String): Flow<List<ShoppingPurchaseHistory>>

  fun getStorePricesForUser(userId: String): Flow<List<ShoppingStorePriceRecord>>

  fun getStorePricesForList(userId: String, listId: String): Flow<List<ShoppingStorePriceRecord>>

  suspend fun createList(
    authenticatedUserId: String,
    name: String,
    currencyCode: String,
    budgetMinor: Long?,
    warningThresholdPercent: Int = 80,
  ): Result<ShoppingList>

  suspend fun updateList(
    authenticatedUserId: String,
    list: ShoppingList,
  ): Result<Unit>

  suspend fun deleteList(
    authenticatedUserId: String,
    listId: String,
  ): Result<Unit>

  suspend fun addItem(
    authenticatedUserId: String,
    listId: String,
    name: String,
    category: String,
    quantityDecimal: String,
    unit: String,
    estimatedUnitPriceDecimal: String,
    storeName: String?,
    notes: String?,
  ): Result<ShoppingListItem>

  suspend fun updateItem(
    authenticatedUserId: String,
    itemId: String,
    name: String,
    category: String,
    quantityDecimal: String,
    unit: String,
    estimatedUnitPriceDecimal: String,
    storeName: String?,
    notes: String?,
  ): Result<Unit>

  suspend fun markItemPurchased(
    authenticatedUserId: String,
    itemId: String,
    actualUnitPriceDecimal: String,
    actualTotalOverrideDecimal: String? = null,
    purchasedAt: Long = System.currentTimeMillis(),
    storeNameOverride: String? = null,
  ): Result<ShoppingListItem>

  suspend fun markItemUnpurchased(
    authenticatedUserId: String,
    itemId: String,
  ): Result<Unit>

  suspend fun deleteItem(
    authenticatedUserId: String,
    itemId: String,
  ): Result<Unit>

  suspend fun recordStorePrice(
    authenticatedUserId: String,
    itemId: String,
    storeName: String,
    unitPriceDecimal: String,
    recordedAt: Long = System.currentTimeMillis(),
    notes: String? = null,
  ): Result<ShoppingStorePriceRecord>

  suspend fun deleteStorePrice(
    authenticatedUserId: String,
    priceId: String,
  ): Result<Unit>
}
