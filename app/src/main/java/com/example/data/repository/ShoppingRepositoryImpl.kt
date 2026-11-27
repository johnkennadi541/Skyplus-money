package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.dao.ShoppingDao
import com.example.data.local.db.entity.ShoppingItemEntity
import com.example.data.local.db.entity.ShoppingListEntity
import com.example.data.local.db.entity.ShoppingStorePriceEntity
import com.example.domain.model.Money
import com.example.domain.model.ShoppingList
import com.example.domain.model.ShoppingListItem
import com.example.domain.model.ShoppingStorePriceRecord
import com.example.domain.repository.ShoppingRepository
import com.example.domain.usecase.ShoppingCalculationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingRepositoryImpl @Inject constructor(
  private val database: AppDatabase,
  private val shoppingDao: ShoppingDao,
  private val calculationEngine: ShoppingCalculationEngine,
  private val dispatchers: DispatcherProvider,
) : ShoppingRepository {

  override fun getLists(userId: String): Flow<List<ShoppingList>> =
    shoppingDao.getAllLists(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override suspend fun getListById(userId: String, listId: String): ShoppingList? =
    withContext(dispatchers.io) {
      shoppingDao.getListById(userId, listId)?.toDomain()
    }

  override fun getItemsForUser(userId: String): Flow<List<ShoppingListItem>> =
    shoppingDao.getAllItemsForUser(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun getItemsForList(userId: String, listId: String): Flow<List<ShoppingListItem>> =
    shoppingDao.getItemsForList(userId, listId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun getStorePricesForUser(userId: String): Flow<List<ShoppingStorePriceRecord>> =
    shoppingDao.getAllStorePricesForUser(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun getStorePricesForList(
    userId: String,
    listId: String,
  ): Flow<List<ShoppingStorePriceRecord>> =
    shoppingDao.getStorePricesForList(userId, listId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override suspend fun createList(
    authenticatedUserId: String,
    name: String,
    currencyCode: String,
    budgetMinor: Long?,
    warningThresholdPercent: Int,
  ): Result<ShoppingList> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Shopping list name is required" }
        val normalizedCurrency = currencyCode.trim().uppercase()
        Money.resolveFractionDigits(normalizedCurrency)
        if (budgetMinor != null) {
          require(budgetMinor > 0L) { "List budget must be greater than zero" }
        }
        require(warningThresholdPercent in 10..100) {
          "Warning threshold must be between 10% and 100%"
        }

        val now = System.currentTimeMillis()
        val list =
          ShoppingList(
            id = UUID.randomUUID().toString(),
            userId = authenticatedUserId,
            name = trimmedName,
            currencyCode = normalizedCurrency,
            budgetMinor = budgetMinor,
            warningThresholdPercent = warningThresholdPercent,
            createdAt = now,
            updatedAt = now,
          )

        database.withTransaction {
          shoppingDao.insertList(ShoppingListEntity.fromDomain(list))
        }
        list
      }
    }

  override suspend fun updateList(
    authenticatedUserId: String,
    list: ShoppingList,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(list.userId == authenticatedUserId) {
          "Shopping list ownership cannot be transferred across users"
        }
        val trimmedName = list.name.trim()
        require(trimmedName.isNotEmpty()) { "Shopping list name cannot be blank" }
        val normalizedCurrency = list.currencyCode.trim().uppercase()
        Money.resolveFractionDigits(normalizedCurrency)
        if (list.budgetMinor != null) {
          require(list.budgetMinor > 0L) { "List budget must be greater than zero" }
        }
        require(list.warningThresholdPercent in 10..100) {
          "Warning threshold must be between 10% and 100%"
        }

        database.withTransaction {
          val existing =
            shoppingDao.getListById(authenticatedUserId, list.id)
              ?: throw SecurityException("Shopping list does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) {
            "Shopping list ownership verification failed"
          }
          check(!existing.isArchived) { "Restore the archived shopping list before editing it" }

          if (!existing.currencyCode.equals(normalizedCurrency, ignoreCase = true)) {
            val itemCount = shoppingDao.countItemsForList(authenticatedUserId, list.id)
            if (itemCount > 0) {
              throw IllegalStateException(
                "Cannot change list currency while items exist in this list. Create a new list for $normalizedCurrency."
              )
            }
          }

          val updated =
            existing.toDomain().copy(
              name = trimmedName,
              currencyCode = normalizedCurrency,
              budgetMinor = list.budgetMinor,
              warningThresholdPercent = list.warningThresholdPercent,
              updatedAt = System.currentTimeMillis(),
            )
          val rows = shoppingDao.updateList(ShoppingListEntity.fromDomain(updated), authenticatedUserId)
          check(rows == 1) { "Shopping list update rejected for authenticated user" }
        }
      }
    }

  override suspend fun deleteList(
    authenticatedUserId: String,
    listId: String,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(listId.isNotBlank()) { "List ID is required" }
        database.withTransaction {
          val existing = shoppingDao.getListById(authenticatedUserId, listId)
            ?: throw SecurityException("Shopping list does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) { "Shopping list ownership verification failed" }
          check(shoppingDao.archiveList(authenticatedUserId, listId, System.currentTimeMillis()) == 1) {
            "Shopping list archive rejected for authenticated user"
          }
        }
      }
    }

  override fun observeArchivedLists(userId: String): Flow<List<ShoppingList>> =
    shoppingDao.observeArchivedLists(userId).map { rows -> rows.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun observeArchivedItems(userId: String): Flow<List<ShoppingListItem>> =
    shoppingDao.observeArchivedItems(userId).map { rows -> rows.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override suspend fun restoreList(authenticatedUserId: String, listId: String): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        database.withTransaction {
          val existing = shoppingDao.getListById(authenticatedUserId, listId)
            ?: throw SecurityException("Shopping list does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) { "Shopping list ownership verification failed" }
          check(shoppingDao.restoreList(authenticatedUserId, listId, System.currentTimeMillis()) == 1) {
            "Shopping list restore rejected for authenticated user"
          }
        }
      }
    }

  override suspend fun restoreItem(authenticatedUserId: String, itemId: String): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        database.withTransaction {
          val item = shoppingDao.getItemById(authenticatedUserId, itemId)
            ?: throw SecurityException("Shopping item does not belong to authenticated user")
          val parent = shoppingDao.getListById(authenticatedUserId, item.listId)
            ?: throw SecurityException("Parent shopping list does not belong to authenticated user")
          check(!parent.isArchived) { "Restore the archived shopping list before restoring its item" }
          check(shoppingDao.restoreItem(authenticatedUserId, itemId, System.currentTimeMillis()) == 1) {
            "Shopping item restore rejected for authenticated user"
          }
        }
      }
    }

  override suspend fun addItem(
    authenticatedUserId: String,
    listId: String,
    name: String,
    category: String,
    quantityDecimal: String,
    unit: String,
    estimatedUnitPriceDecimal: String,
    storeName: String?,
    notes: String?,
  ): Result<ShoppingListItem> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(listId.isNotBlank()) { "Shopping list is required" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Item name is required" }
        val trimmedCategory = category.trim().ifEmpty { "Groceries" }
        val trimmedUnit = unit.trim().ifEmpty { "piece" }

        database.withTransaction {
          val parentList =
            shoppingDao.getListById(authenticatedUserId, listId)?.toDomain()
              ?: throw SecurityException("Shopping list does not belong to authenticated user")
          check(!parentList.isArchived) { "Restore the archived shopping list before adding items" }

          val qty = calculationEngine.parsePositiveQuantity(quantityDecimal, "Quantity")
          val unitPriceMinor =
            calculationEngine.parseNonNegativeMoneyMinor(
              estimatedUnitPriceDecimal,
              parentList.currencyCode,
              "Estimated unit price",
            )
          val estTotalMinor =
            calculationEngine.computeLineTotalMinor(qty, unitPriceMinor, parentList.currencyCode)

          val now = System.currentTimeMillis()
          val normalizedStore = storeName?.trim()?.takeIf { it.isNotEmpty() }
          val item =
            ShoppingListItem(
              id = UUID.randomUUID().toString(),
              listId = listId,
              userId = authenticatedUserId,
              name = trimmedName,
              category = trimmedCategory,
              quantityDecimal = qty.toPlainString(),
              unit = trimmedUnit,
              estimatedUnitPriceMinor = unitPriceMinor,
              estimatedTotalMinor = estTotalMinor,
              storeName = normalizedStore,
              notes = notes?.trim()?.takeIf { it.isNotEmpty() },
              isPurchased = false,
              createdAt = now,
              updatedAt = now,
            )

          shoppingDao.insertItem(ShoppingItemEntity.fromDomain(item))

          // If user supplied a store name and positive estimated unit price, seed initial price point?
          // Keep store price comparisons strictly explicit or recorded on purchase/store-price entry.
          item
        }
      }
    }

  override suspend fun updateItem(
    authenticatedUserId: String,
    itemId: String,
    name: String,
    category: String,
    quantityDecimal: String,
    unit: String,
    estimatedUnitPriceDecimal: String,
    storeName: String?,
    notes: String?,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(itemId.isNotBlank()) { "Item ID is required" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Item name cannot be blank" }
        val trimmedCategory = category.trim().ifEmpty { "Groceries" }
        val trimmedUnit = unit.trim().ifEmpty { "piece" }

        database.withTransaction {
          val existing =
            shoppingDao.getItemById(authenticatedUserId, itemId)?.toDomain()
              ?: throw SecurityException("Shopping item does not belong to authenticated user")
          check(!existing.isArchived) { "Restore the archived shopping item before editing it" }
          val parentList =
            shoppingDao.getListById(authenticatedUserId, existing.listId)?.toDomain()
              ?: throw SecurityException("Parent shopping list does not belong to authenticated user")

          val qty = calculationEngine.parsePositiveQuantity(quantityDecimal, "Quantity")
          val estUnitMinor =
            calculationEngine.parseNonNegativeMoneyMinor(
              estimatedUnitPriceDecimal,
              parentList.currencyCode,
              "Estimated unit price",
            )
          val estTotalMinor =
            calculationEngine.computeLineTotalMinor(qty, estUnitMinor, parentList.currencyCode)

          val updated =
            existing.copy(
              name = trimmedName,
              category = trimmedCategory,
              quantityDecimal = qty.toPlainString(),
              unit = trimmedUnit,
              estimatedUnitPriceMinor = estUnitMinor,
              estimatedTotalMinor = estTotalMinor,
              storeName = storeName?.trim()?.takeIf { it.isNotEmpty() },
              notes = notes?.trim()?.takeIf { it.isNotEmpty() },
              updatedAt = System.currentTimeMillis(),
            )
          val rows = shoppingDao.updateItem(ShoppingItemEntity.fromDomain(updated), authenticatedUserId)
          check(rows == 1) { "Shopping item update rejected for authenticated user" }
        }
      }
    }

  override suspend fun markItemPurchased(
    authenticatedUserId: String,
    itemId: String,
    actualUnitPriceDecimal: String,
    actualTotalOverrideDecimal: String?,
    purchasedAt: Long,
    storeNameOverride: String?,
  ): Result<ShoppingListItem> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(itemId.isNotBlank()) { "Item ID is required" }
        require(purchasedAt > 0L) { "Purchase timestamp must be valid" }

        database.withTransaction {
          val existing =
            shoppingDao.getItemById(authenticatedUserId, itemId)?.toDomain()
              ?: throw SecurityException("Shopping item does not belong to authenticated user")
          val parentList =
            shoppingDao.getListById(authenticatedUserId, existing.listId)?.toDomain()
              ?: throw SecurityException("Parent shopping list does not belong to authenticated user")

          val actualUnitMinor =
            calculationEngine.parseNonNegativeMoneyMinor(
              actualUnitPriceDecimal,
              parentList.currencyCode,
              "Actual unit price",
            )
          val actualTotalMinor =
            if (!actualTotalOverrideDecimal.isNullOrBlank()) {
              calculationEngine.parseNonNegativeMoneyMinor(
                actualTotalOverrideDecimal,
                parentList.currencyCode,
                "Actual total",
              )
            } else {
              calculationEngine.computeLineTotalMinor(
                existing.quantityDecimal,
                actualUnitMinor,
                parentList.currencyCode,
              )
            }

          val effectiveStore =
            storeNameOverride?.trim()?.takeIf { it.isNotEmpty() } ?: existing.storeName
          val now = System.currentTimeMillis()
          val updated =
            existing.copy(
              storeName = effectiveStore,
              isPurchased = true,
              actualUnitPriceMinor = actualUnitMinor,
              actualTotalMinor = actualTotalMinor,
              purchasedAt = purchasedAt,
              updatedAt = now,
            )

          val rows = shoppingDao.updateItem(ShoppingItemEntity.fromDomain(updated), authenticatedUserId)
          check(rows == 1) { "Failed to mark shopping item as purchased" }

          if (!effectiveStore.isNullOrBlank() && actualUnitMinor > 0L) {
            val priceRecord =
              ShoppingStorePriceRecord(
                id = UUID.randomUUID().toString(),
                itemId = existing.id,
                listId = existing.listId,
                userId = authenticatedUserId,
                storeName = effectiveStore,
                unitPriceMinor = actualUnitMinor,
                currencyCode = parentList.currencyCode,
                recordedAt = purchasedAt,
                notes = "Purchased",
              )
            shoppingDao.insertStorePrice(ShoppingStorePriceEntity.fromDomain(priceRecord))
          }

          updated
        }
      }
    }

  override suspend fun markItemUnpurchased(
    authenticatedUserId: String,
    itemId: String,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(itemId.isNotBlank()) { "Item ID is required" }

        database.withTransaction {
          val existing =
            shoppingDao.getItemById(authenticatedUserId, itemId)?.toDomain()
              ?: throw SecurityException("Shopping item does not belong to authenticated user")
          val updated =
            existing.copy(
              isPurchased = false,
              actualUnitPriceMinor = null,
              actualTotalMinor = null,
              purchasedAt = null,
              updatedAt = System.currentTimeMillis(),
            )
          val rows = shoppingDao.updateItem(ShoppingItemEntity.fromDomain(updated), authenticatedUserId)
          check(rows == 1) { "Failed to update shopping item status" }
        }
      }
    }

  override suspend fun deleteItem(
    authenticatedUserId: String,
    itemId: String,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(itemId.isNotBlank()) { "Item ID is required" }

        database.withTransaction {
          val existing =
            shoppingDao.getItemById(authenticatedUserId, itemId)
              ?: throw SecurityException("Shopping item does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) {
            "Shopping item ownership verification failed"
          }
          val rows = shoppingDao.archiveItem(authenticatedUserId, itemId, System.currentTimeMillis())
          check(rows == 1) { "Shopping item archive rejected for authenticated user" }
        }
      }
    }

  override suspend fun recordStorePrice(
    authenticatedUserId: String,
    itemId: String,
    storeName: String,
    unitPriceDecimal: String,
    recordedAt: Long,
    notes: String?,
  ): Result<ShoppingStorePriceRecord> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(itemId.isNotBlank()) { "Item ID is required" }
        val trimmedStore = storeName.trim()
        require(trimmedStore.isNotEmpty()) { "Store name is required" }
        require(recordedAt > 0L) { "Recorded timestamp must be valid" }

        database.withTransaction {
          val item =
            shoppingDao.getItemById(authenticatedUserId, itemId)?.toDomain()
              ?: throw SecurityException("Shopping item does not belong to authenticated user")
          val parentList =
            shoppingDao.getListById(authenticatedUserId, item.listId)?.toDomain()
              ?: throw SecurityException("Parent shopping list does not belong to authenticated user")

          val unitPriceMinor =
            calculationEngine.parsePositiveMoneyMinor(
              unitPriceDecimal,
              parentList.currencyCode,
              "Store unit price",
            )

          val record =
            ShoppingStorePriceRecord(
              id = UUID.randomUUID().toString(),
              itemId = item.id,
              listId = item.listId,
              userId = authenticatedUserId,
              storeName = trimmedStore,
              unitPriceMinor = unitPriceMinor,
              currencyCode = parentList.currencyCode,
              recordedAt = recordedAt,
              notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            )
          shoppingDao.insertStorePrice(ShoppingStorePriceEntity.fromDomain(record))
          record
        }
      }
    }

  override suspend fun deleteStorePrice(
    authenticatedUserId: String,
    priceId: String,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(priceId.isNotBlank()) { "Price record ID is required" }

        database.withTransaction {
          val existing =
            shoppingDao.getStorePriceById(authenticatedUserId, priceId)
              ?: throw SecurityException("Store price record does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) {
            "Store price ownership verification failed"
          }
          throw IllegalStateException("Store-price history is retained and cannot be permanently deleted")
        }
      }
    }
}
