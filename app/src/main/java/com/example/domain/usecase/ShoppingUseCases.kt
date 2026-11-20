package com.example.domain.usecase

import com.example.domain.model.ShoppingItemComparisonSummary
import com.example.domain.model.ShoppingList
import com.example.domain.model.ShoppingListItem
import com.example.domain.model.ShoppingListSummary
import com.example.domain.model.ShoppingStorePriceRecord
import com.example.domain.repository.ShoppingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class ShoppingWorkspaceSnapshot(
  val lists: List<ShoppingList> = emptyList(),
  val items: List<ShoppingListItem> = emptyList(),
  val storePrices: List<ShoppingStorePriceRecord> = emptyList(),
  val summariesByListId: Map<String, ShoppingListSummary> = emptyMap(),
  val comparisonsByItemId: Map<String, ShoppingItemComparisonSummary> = emptyMap(),
)

class ObserveShoppingWorkspaceUseCase @Inject constructor(
  private val repository: ShoppingRepository,
  private val calculationEngine: ShoppingCalculationEngine,
) {
  operator fun invoke(userId: String): Flow<ShoppingWorkspaceSnapshot> {
    return combine(
      repository.getLists(userId),
      repository.getItemsForUser(userId),
      repository.getStorePricesForUser(userId),
    ) { lists, items, storePrices ->
      val itemsByList = items.groupBy { it.listId }
      val pricesByItem = storePrices.groupBy { it.itemId }

      val summaries =
        lists.associate { list ->
          list.id to calculationEngine.calculateListSummary(list, itemsByList[list.id].orEmpty())
        }
      val comparisons =
        items.associate { item ->
          item.id to calculationEngine.buildItemComparisonSummary(item, pricesByItem[item.id].orEmpty())
        }

      ShoppingWorkspaceSnapshot(
        lists = lists,
        items = items,
        storePrices = storePrices,
        summariesByListId = summaries,
        comparisonsByItemId = comparisons,
      )
    }
  }
}

class CreateShoppingListUseCase @Inject constructor(
  private val repository: ShoppingRepository,
  private val calculationEngine: ShoppingCalculationEngine,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    name: String,
    currencyCode: String,
    budgetDecimal: String?,
    warningThresholdPercent: Int = 80,
  ): Result<ShoppingList> {
    val budgetMinor =
      if (budgetDecimal.isNullOrBlank()) {
        null
      } else {
        try {
          calculationEngine.parsePositiveMoneyMinor(budgetDecimal, currencyCode, "List budget")
        } catch (e: Throwable) {
          return Result.failure(e)
        }
      }
    return repository.createList(
      authenticatedUserId = authenticatedUserId,
      name = name,
      currencyCode = currencyCode,
      budgetMinor = budgetMinor,
      warningThresholdPercent = warningThresholdPercent,
    )
  }
}

class UpdateShoppingListUseCase @Inject constructor(
  private val repository: ShoppingRepository,
  private val calculationEngine: ShoppingCalculationEngine,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    existingList: ShoppingList,
    newName: String,
    newCurrencyCode: String,
    budgetDecimal: String?,
    warningThresholdPercent: Int = 80,
  ): Result<Unit> {
    val budgetMinor =
      if (budgetDecimal.isNullOrBlank()) {
        null
      } else {
        try {
          calculationEngine.parsePositiveMoneyMinor(budgetDecimal, newCurrencyCode, "List budget")
        } catch (e: Throwable) {
          return Result.failure(e)
        }
      }
    return repository.updateList(
      authenticatedUserId = authenticatedUserId,
      list =
        existingList.copy(
          name = newName,
          currencyCode = newCurrencyCode,
          budgetMinor = budgetMinor,
          warningThresholdPercent = warningThresholdPercent,
        ),
    )
  }
}

class DeleteShoppingListUseCase @Inject constructor(
  private val repository: ShoppingRepository,
) {
  suspend operator fun invoke(authenticatedUserId: String, listId: String): Result<Unit> =
    repository.deleteList(authenticatedUserId, listId)
}

class AddShoppingItemUseCase @Inject constructor(
  private val repository: ShoppingRepository,
) {
  suspend operator fun invoke(
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
    repository.addItem(
      authenticatedUserId = authenticatedUserId,
      listId = listId,
      name = name,
      category = category,
      quantityDecimal = quantityDecimal,
      unit = unit,
      estimatedUnitPriceDecimal = estimatedUnitPriceDecimal,
      storeName = storeName,
      notes = notes,
    )
}

class UpdateShoppingItemUseCase @Inject constructor(
  private val repository: ShoppingRepository,
) {
  suspend operator fun invoke(
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
    repository.updateItem(
      authenticatedUserId = authenticatedUserId,
      itemId = itemId,
      name = name,
      category = category,
      quantityDecimal = quantityDecimal,
      unit = unit,
      estimatedUnitPriceDecimal = estimatedUnitPriceDecimal,
      storeName = storeName,
      notes = notes,
    )
}

class PurchaseShoppingItemUseCase @Inject constructor(
  private val repository: ShoppingRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    itemId: String,
    actualUnitPriceDecimal: String,
    actualTotalOverrideDecimal: String? = null,
    purchasedAt: Long = System.currentTimeMillis(),
    storeNameOverride: String? = null,
  ): Result<ShoppingListItem> =
    repository.markItemPurchased(
      authenticatedUserId = authenticatedUserId,
      itemId = itemId,
      actualUnitPriceDecimal = actualUnitPriceDecimal,
      actualTotalOverrideDecimal = actualTotalOverrideDecimal,
      purchasedAt = purchasedAt,
      storeNameOverride = storeNameOverride,
    )

  suspend fun markUnpurchased(authenticatedUserId: String, itemId: String): Result<Unit> =
    repository.markItemUnpurchased(authenticatedUserId, itemId)
}

class DeleteShoppingItemUseCase @Inject constructor(
  private val repository: ShoppingRepository,
) {
  suspend operator fun invoke(authenticatedUserId: String, itemId: String): Result<Unit> =
    repository.deleteItem(authenticatedUserId, itemId)
}

class ManageStorePriceUseCase @Inject constructor(
  private val repository: ShoppingRepository,
) {
  suspend fun recordPrice(
    authenticatedUserId: String,
    itemId: String,
    storeName: String,
    unitPriceDecimal: String,
    recordedAt: Long = System.currentTimeMillis(),
    notes: String? = null,
  ): Result<ShoppingStorePriceRecord> =
    repository.recordStorePrice(
      authenticatedUserId = authenticatedUserId,
      itemId = itemId,
      storeName = storeName,
      unitPriceDecimal = unitPriceDecimal,
      recordedAt = recordedAt,
      notes = notes,
    )

  suspend fun deletePrice(authenticatedUserId: String, priceId: String): Result<Unit> =
    repository.deleteStorePrice(authenticatedUserId, priceId)
}
