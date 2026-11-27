package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.ShoppingItemEntity
import com.example.data.local.db.entity.ShoppingListEntity
import com.example.data.local.db.entity.ShoppingStorePriceEntity
import com.example.data.local.db.entity.ShoppingPurchaseHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

  @Query("SELECT * FROM shopping_lists WHERE userId = :userId AND isArchived = 0 ORDER BY updatedAt DESC, createdAt DESC")
  fun getAllLists(userId: String): Flow<List<ShoppingListEntity>>

  @Query("SELECT * FROM shopping_lists WHERE userId = :userId AND isArchived = 1 ORDER BY updatedAt DESC")
  fun observeArchivedLists(userId: String): Flow<List<ShoppingListEntity>>

  @Query("SELECT * FROM shopping_lists WHERE userId = :userId AND id = :listId LIMIT 1")
  suspend fun getListById(userId: String, listId: String): ShoppingListEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertList(list: ShoppingListEntity)

  @Query(
    """
    UPDATE shopping_lists
    SET name = :name,
        currencyCode = :currencyCode,
        budgetMinor = :budgetMinor,
        warningThresholdPercent = :warningThresholdPercent,
        updatedAt = :updatedAt
    WHERE id = :id AND userId = :userId AND isArchived = 0
    """
  )
  suspend fun updateListScoped(
    id: String,
    userId: String,
    name: String,
    currencyCode: String,
    budgetMinor: Long?,
    warningThresholdPercent: Int,
    updatedAt: Long,
  ): Int

  suspend fun updateList(list: ShoppingListEntity, authenticatedUserId: String): Int {
    if (authenticatedUserId.isBlank() || list.userId != authenticatedUserId) return 0
    return updateListScoped(
      id = list.id,
      userId = authenticatedUserId,
      name = list.name,
      currencyCode = list.currencyCode,
      budgetMinor = list.budgetMinor,
      warningThresholdPercent = list.warningThresholdPercent,
      updatedAt = list.updatedAt,
    )
  }

  @Query("UPDATE shopping_lists SET isArchived = 1, updatedAt = :updatedAt WHERE userId = :userId AND id = :listId AND isArchived = 0")
  suspend fun archiveList(userId: String, listId: String, updatedAt: Long): Int

  @Query("UPDATE shopping_lists SET isArchived = 0, updatedAt = :updatedAt WHERE userId = :userId AND id = :listId AND isArchived = 1")
  suspend fun restoreList(userId: String, listId: String, updatedAt: Long): Int

  @Query("SELECT * FROM shopping_items WHERE userId = :userId AND isArchived = 0 AND EXISTS (SELECT 1 FROM shopping_lists WHERE shopping_lists.id = shopping_items.listId AND shopping_lists.userId = :userId AND shopping_lists.isArchived = 0) ORDER BY isPurchased ASC, updatedAt DESC, createdAt DESC")
  fun getAllItemsForUser(userId: String): Flow<List<ShoppingItemEntity>>

  @Query("SELECT * FROM shopping_items WHERE userId = :userId AND listId = :listId AND isArchived = 0 AND EXISTS (SELECT 1 FROM shopping_lists WHERE shopping_lists.id = shopping_items.listId AND shopping_lists.userId = :userId AND shopping_lists.isArchived = 0) ORDER BY isPurchased ASC, updatedAt DESC, createdAt DESC")
  fun getItemsForList(userId: String, listId: String): Flow<List<ShoppingItemEntity>>

  @Query("SELECT * FROM shopping_items WHERE userId = :userId AND isArchived = 1 ORDER BY updatedAt DESC")
  fun observeArchivedItems(userId: String): Flow<List<ShoppingItemEntity>>

  @Query("SELECT COUNT(*) FROM shopping_items WHERE userId = :userId AND listId = :listId")
  suspend fun countItemsForList(userId: String, listId: String): Int

  @Query("SELECT * FROM shopping_items WHERE userId = :userId AND id = :itemId LIMIT 1")
  suspend fun getItemById(userId: String, itemId: String): ShoppingItemEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertItem(item: ShoppingItemEntity)

  @Query(
    """
    UPDATE shopping_items
    SET name = :name,
        category = :category,
        quantityDecimal = :quantityDecimal,
        unit = :unit,
        estimatedUnitPriceMinor = :estimatedUnitPriceMinor,
        estimatedTotalMinor = :estimatedTotalMinor,
        storeName = :storeName,
        notes = :notes,
        isPurchased = :isPurchased,
        actualUnitPriceMinor = :actualUnitPriceMinor,
        actualTotalMinor = :actualTotalMinor,
        purchasedAt = :purchasedAt,
        updatedAt = :updatedAt
    WHERE id = :id AND userId = :userId AND isArchived = 0
    """
  )
  suspend fun updateItemScoped(
    id: String,
    userId: String,
    name: String,
    category: String,
    quantityDecimal: String,
    unit: String,
    estimatedUnitPriceMinor: Long,
    estimatedTotalMinor: Long,
    storeName: String?,
    notes: String?,
    isPurchased: Boolean,
    actualUnitPriceMinor: Long?,
    actualTotalMinor: Long?,
    purchasedAt: Long?,
    updatedAt: Long,
  ): Int

  suspend fun updateItem(item: ShoppingItemEntity, authenticatedUserId: String): Int {
    if (authenticatedUserId.isBlank() || item.userId != authenticatedUserId) return 0
    return updateItemScoped(
      id = item.id,
      userId = authenticatedUserId,
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
      updatedAt = item.updatedAt,
    )
  }

  @Query("UPDATE shopping_items SET isArchived = 1, updatedAt = :updatedAt WHERE userId = :userId AND id = :itemId AND isArchived = 0")
  suspend fun archiveItem(userId: String, itemId: String, updatedAt: Long): Int

  @Query("UPDATE shopping_items SET isArchived = 0, updatedAt = :updatedAt WHERE userId = :userId AND id = :itemId AND isArchived = 1")
  suspend fun restoreItem(userId: String, itemId: String, updatedAt: Long): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertPurchaseHistory(record: ShoppingPurchaseHistoryEntity)

  @Query("SELECT * FROM shopping_purchase_history WHERE userId = :userId AND itemId = :itemId ORDER BY purchasedAt DESC, recordedAt DESC")
  fun getPurchaseHistoryForItem(userId: String, itemId: String): Flow<List<ShoppingPurchaseHistoryEntity>>

  @Query("SELECT * FROM shopping_purchase_history WHERE userId = :userId AND listId = :listId ORDER BY purchasedAt DESC, recordedAt DESC")
  fun getPurchaseHistoryForList(userId: String, listId: String): Flow<List<ShoppingPurchaseHistoryEntity>>

  @Query("SELECT * FROM shopping_store_prices WHERE userId = :userId ORDER BY recordedAt DESC")
  fun getAllStorePricesForUser(userId: String): Flow<List<ShoppingStorePriceEntity>>

  @Query("SELECT * FROM shopping_store_prices WHERE userId = :userId AND listId = :listId ORDER BY recordedAt DESC")
  fun getStorePricesForList(userId: String, listId: String): Flow<List<ShoppingStorePriceEntity>>

  @Query("SELECT * FROM shopping_store_prices WHERE userId = :userId AND id = :priceId LIMIT 1")
  suspend fun getStorePriceById(userId: String, priceId: String): ShoppingStorePriceEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertStorePrice(record: ShoppingStorePriceEntity)

}
