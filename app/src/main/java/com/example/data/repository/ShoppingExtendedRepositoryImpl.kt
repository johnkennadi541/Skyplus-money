package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.dao.FinancialAccountDao
import com.example.data.local.db.dao.FinancialTransactionDao
import com.example.data.local.db.dao.ShoppingExtendedDao
import com.example.data.local.db.entity.FinancialTransactionEntity
import com.example.data.local.db.entity.ShoppingMealIngredientEntity
import com.example.data.local.db.entity.ShoppingMealPlanEntity
import com.example.data.local.db.entity.ShoppingPantryEntity
import com.example.data.local.db.entity.ShoppingReceiptEntity
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.Money
import com.example.domain.model.ShoppingMealPlan
import com.example.domain.model.ShoppingMealIngredient
import com.example.domain.model.ShoppingPantryItem
import com.example.domain.model.ShoppingReceipt
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.repository.ShoppingExtendedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingExtendedRepositoryImpl @Inject constructor(
  private val database: AppDatabase,
  private val shoppingDao: ShoppingExtendedDao,
  private val accountDao: FinancialAccountDao,
  private val transactionDao: FinancialTransactionDao,
  private val dispatchers: DispatcherProvider,
) : ShoppingExtendedRepository {

  override fun observePantry(userId: String): Flow<List<ShoppingPantryItem>> =
    shoppingDao.observePantry(userId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  override fun observeMealPlans(userId: String): Flow<List<ShoppingMealPlan>> =
    shoppingDao.observeMealPlans(userId).mapLatest { rows ->
      rows.map { row ->
        row.toDomain(shoppingDao.getMealIngredients(userId, row.id).map { it.toDomain() })
      }
    }.flowOn(dispatchers.io)

  override fun observeReceipts(userId: String): Flow<List<ShoppingReceipt>> =
    shoppingDao.observeReceipts(userId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

  override suspend fun savePantryItem(authenticatedUserId: String, item: ShoppingPantryItem): Result<Unit> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        require(authenticatedUserId.isNotBlank() && item.userId == authenticatedUserId) {
          "Pantry item ownership verification failed"
        }
        require(item.name.isNotBlank()) { "Pantry item name is required" }
        val quantity = BigDecimal(item.quantityDecimal)
        val threshold = BigDecimal(item.lowStockThresholdDecimal)
        require(quantity >= BigDecimal.ZERO && threshold >= BigDecimal.ZERO) { "Pantry quantities cannot be negative" }
        require(item.unit.isNotBlank()) { "Pantry unit is required" }
        val entity = ShoppingPantryEntity(
          id = item.id, userId = authenticatedUserId, name = item.name.trim(),
          category = item.category.trim().ifBlank { "Groceries" },
          quantityDecimal = quantity.stripTrailingZeros().toPlainString(), unit = item.unit.trim(),
          lowStockThresholdDecimal = threshold.stripTrailingZeros().toPlainString(),
          expiryAt = item.expiryAt, updatedAt = System.currentTimeMillis(),
        )
        database.withTransaction {
          val existing = shoppingDao.getPantryItem(authenticatedUserId, item.id)
            ?: shoppingDao.getPantryItemByNameAndUnit(authenticatedUserId, entity.name, entity.unit)
          if (existing == null) shoppingDao.insertPantryItem(entity)
          else {
            val rows = shoppingDao.updatePantryItem(
              userId = authenticatedUserId, id = existing.id, name = entity.name, category = entity.category,
              quantity = entity.quantityDecimal, unit = entity.unit, threshold = entity.lowStockThresholdDecimal,
              expiryAt = entity.expiryAt, updatedAt = entity.updatedAt,
            )
            check(rows == 1) { "Pantry item update rejected" }
          }
        }
      }
    }

  override suspend fun deletePantryItem(authenticatedUserId: String, itemId: String): Result<Unit> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        require(authenticatedUserId.isNotBlank() && itemId.isNotBlank())
        val rows = shoppingDao.deletePantryItem(authenticatedUserId, itemId)
        check(rows == 1) { "Pantry item not found for authenticated user" }
      }
    }

  override suspend fun createMealPlan(authenticatedUserId: String, plan: ShoppingMealPlan): Result<Unit> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        require(authenticatedUserId.isNotBlank() && plan.userId == authenticatedUserId) {
          "Meal plan ownership verification failed"
        }
        require(plan.title.isNotBlank()) { "Meal title is required" }
        require(plan.mealType in setOf("BREAKFAST", "LUNCH", "DINNER", "SNACK")) { "Unsupported meal type" }
        require(plan.ingredients.all { it.userId == authenticatedUserId && it.name.isNotBlank() }) {
          "Meal ingredients must belong to the authenticated user and have names"
        }
        plan.ingredients.forEach {
          require(BigDecimal(it.quantityDecimal) > BigDecimal.ZERO) { "Ingredient quantity must be positive" }
          require(it.unit.isNotBlank()) { "Ingredient unit is required" }
        }
        database.withTransaction {
          shoppingDao.insertMealPlan(
            ShoppingMealPlanEntity(
              id = plan.id, userId = authenticatedUserId, title = plan.title.trim(),
              plannedDate = plan.plannedDate, mealType = plan.mealType, notes = plan.notes,
              createdAt = plan.createdAt, updatedAt = System.currentTimeMillis(),
            )
          )
          plan.ingredients.forEach { ingredient ->
            shoppingDao.insertMealIngredient(
              ShoppingMealIngredientEntity(
                id = ingredient.id, userId = authenticatedUserId, mealPlanId = plan.id,
                name = ingredient.name.trim(),
                quantityDecimal = BigDecimal(ingredient.quantityDecimal).stripTrailingZeros().toPlainString(),
                unit = ingredient.unit.trim(), shoppingItemId = ingredient.shoppingItemId,
              )
            )
          }
        }
      }
    }

  override suspend fun deleteMealPlan(authenticatedUserId: String, planId: String): Result<Unit> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        require(authenticatedUserId.isNotBlank() && planId.isNotBlank())
        database.withTransaction {
          val rows = shoppingDao.deleteMealPlan(authenticatedUserId, planId)
          check(rows == 1) { "Meal plan not found for authenticated user" }
        }
      }
    }

  override suspend fun saveReceiptReview(authenticatedUserId: String, receipt: ShoppingReceipt): Result<ShoppingReceipt> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        require(authenticatedUserId.isNotBlank() && receipt.userId == authenticatedUserId) {
          "Receipt ownership verification failed"
        }
        require(receipt.fingerprint.matches(Regex("[a-f0-9]{64}"))) { "Receipt fingerprint is invalid" }
        require(receipt.totalMinor > 0L) { "Receipt total must be greater than zero" }
        val currency = receipt.currencyCode.trim().uppercase()
        Money.resolveFractionDigits(currency)
        database.withTransaction {
          val existing = shoppingDao.findReceipt(authenticatedUserId, receipt.fingerprint)
          if (existing != null) existing.toDomain()
          else {
            val normalized = receipt.copy(
              currencyCode = currency, status = "REVIEWED",
              createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
            )
            shoppingDao.insertReceipt(ShoppingReceiptEntity.fromDomain(normalized))
            normalized
          }
        }
      }
    }

  override suspend fun postReceiptExpense(
    authenticatedUserId: String,
    receiptId: String,
    accountId: String,
  ): Result<ShoppingReceipt> = withContext(dispatchers.io) {
    runCatchingPreservingCancellation {
      require(authenticatedUserId.isNotBlank() && receiptId.isNotBlank() && accountId.isNotBlank())
      database.withTransaction {
        val receipt = shoppingDao.getReceipt(authenticatedUserId, receiptId)
          ?: throw SecurityException("Receipt does not belong to authenticated user")
        if (receipt.status == "POSTED" && !receipt.financialTransactionId.isNullOrBlank()) {
          return@withTransaction receipt.toDomain()
        }
        val account = accountDao.getAccountById(authenticatedUserId, accountId)
          ?: throw SecurityException("Selected account does not belong to authenticated user")
        require(account.isActive) { "Selected account is inactive" }
        require(account.currencyCode.equals(receipt.currencyCode, ignoreCase = true)) {
          "Receipt currency " + receipt.currencyCode + " does not match selected account currency " + account.currencyCode
        }
        val reference = "shopping-receipt:" + receipt.fingerprint
        val existing = transactionDao.findByReferenceId(authenticatedUserId, reference)
        val transaction = existing?.toDomain() ?: run {
          val now = System.currentTimeMillis()
          val newTransaction = FinancialTransaction(
            userId = authenticatedUserId, accountId = accountId,
            type = TransactionType.EXPENSE, status = TransactionStatus.COMPLETED,
            amountMinor = receipt.totalMinor, currencyCode = receipt.currencyCode,
            category = "Shopping", merchantOrPayee = receipt.merchant,
            notes = "Expense created from user-confirmed shopping receipt",
            transactionDate = receipt.purchaseDate ?: now, referenceId = reference,
            createdAt = now, updatedAt = now,
          )
          transactionDao.insertTransaction(FinancialTransactionEntity.fromDomain(newTransaction))
          accountDao.adjustBalance(authenticatedUserId, accountId, -receipt.totalMinor, now)
          newTransaction
        }
        require(
          transaction.userId == authenticatedUserId &&
            transaction.type == TransactionType.EXPENSE &&
            transaction.accountId == accountId &&
            transaction.amountMinor == receipt.totalMinor &&
            transaction.currencyCode.equals(receipt.currencyCode, ignoreCase = true)
        ) {
          "This receipt is already linked to a different expense; it was not posted again"
        }
        val rows = shoppingDao.markReceiptPosted(
          userId = authenticatedUserId, receiptId = receiptId, accountId = accountId,
          transactionId = transaction.id, updatedAt = System.currentTimeMillis(),
        )
        check(rows == 1) { "Receipt could not be marked as posted" }
        shoppingDao.getReceipt(authenticatedUserId, receiptId)!!.toDomain()
      }
    }
  }
}

private fun ShoppingPantryEntity.toDomain() = ShoppingPantryItem(
  id = id, userId = userId, name = name, category = category, quantityDecimal = quantityDecimal,
  unit = unit, lowStockThresholdDecimal = lowStockThresholdDecimal, expiryAt = expiryAt, updatedAt = updatedAt,
)

private fun ShoppingMealPlanEntity.toDomain(ingredients: List<ShoppingMealIngredient> = emptyList()) = ShoppingMealPlan(
  id = id, userId = userId, title = title, plannedDate = plannedDate, mealType = mealType,
  notes = notes, ingredients = ingredients, createdAt = createdAt, updatedAt = updatedAt,
)

private fun ShoppingMealIngredientEntity.toDomain() = ShoppingMealIngredient(
  id = id, userId = userId, mealPlanId = mealPlanId, name = name,
  quantityDecimal = quantityDecimal, unit = unit, shoppingItemId = shoppingItemId,
)

private fun ShoppingReceiptEntity.toDomain() = ShoppingReceipt(
  id = id, userId = userId, listId = listId, accountId = accountId, fingerprint = fingerprint,
  merchant = merchant, purchaseDate = purchaseDate, currencyCode = currencyCode, totalMinor = totalMinor,
  financialTransactionId = financialTransactionId, status = status, createdAt = createdAt, updatedAt = updatedAt,
)
