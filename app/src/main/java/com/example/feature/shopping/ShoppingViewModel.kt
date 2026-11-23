package com.example.feature.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.DispatcherProvider
import com.example.domain.model.AuthSessionState
import com.example.domain.model.FinancialAccount
import com.example.domain.model.ShoppingList
import com.example.domain.model.ShoppingPantryItem
import com.example.domain.model.ShoppingMealPlan
import com.example.domain.model.ShoppingReceipt
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.FinancialAccountRepository
import com.example.domain.repository.ShoppingExtendedRepository
import com.example.domain.usecase.AddShoppingItemUseCase
import com.example.domain.usecase.CreateShoppingListUseCase
import com.example.domain.usecase.DeleteShoppingItemUseCase
import com.example.domain.usecase.DeleteShoppingListUseCase
import com.example.domain.usecase.ManageStorePriceUseCase
import com.example.domain.usecase.ObserveShoppingWorkspaceUseCase
import com.example.domain.usecase.PurchaseShoppingItemUseCase
import com.example.domain.usecase.ShoppingWorkspaceSnapshot
import com.example.domain.usecase.UpdateShoppingItemUseCase
import com.example.domain.usecase.UpdateShoppingListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShoppingViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  private val accountRepository: FinancialAccountRepository,
  private val observeShoppingWorkspaceUseCase: ObserveShoppingWorkspaceUseCase,
  private val createShoppingListUseCase: CreateShoppingListUseCase,
  private val updateShoppingListUseCase: UpdateShoppingListUseCase,
  private val deleteShoppingListUseCase: DeleteShoppingListUseCase,
  private val addShoppingItemUseCase: AddShoppingItemUseCase,
  private val updateShoppingItemUseCase: UpdateShoppingItemUseCase,
  private val purchaseShoppingItemUseCase: PurchaseShoppingItemUseCase,
  private val deleteShoppingItemUseCase: DeleteShoppingItemUseCase,
  private val manageStorePriceUseCase: ManageStorePriceUseCase,
  private val shoppingExtendedRepository: ShoppingExtendedRepository,
  private val dispatchers: DispatcherProvider,
) : ViewModel() {

  private val _selectedListId = MutableStateFlow<String?>(null)
  val selectedListId: StateFlow<String?> = _selectedListId.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _selectedFilter = MutableStateFlow(ShoppingItemFilter.ALL)
  val selectedFilter: StateFlow<ShoppingItemFilter> = _selectedFilter.asStateFlow()

  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  private val _isErrorMessage = MutableStateFlow(false)
  val isErrorMessage: StateFlow<Boolean> = _isErrorMessage.asStateFlow()

  init {
    viewModelScope.launch {
      authRepository.observeAuthSession()
        .distinctUntilChangedBy { (it as? AuthSessionState.Authenticated)?.user?.uid }
        .collect {
          _selectedListId.value = null
          _searchQuery.value = ""
          _selectedFilter.value = ShoppingItemFilter.ALL
          _userMessage.value = null
          _isErrorMessage.value = false
        }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  val accounts: StateFlow<List<FinancialAccount>> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) accountRepository.getAccounts(uid) else flowOf(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val pantry: StateFlow<List<ShoppingPantryItem>> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) shoppingExtendedRepository.observePantry(uid) else flowOf(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  val mealPlans: StateFlow<List<ShoppingMealPlan>> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) shoppingExtendedRepository.observeMealPlans(uid) else flowOf(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  val receipts: StateFlow<List<ShoppingReceipt>> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) shoppingExtendedRepository.observeReceipts(uid) else flowOf(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  val workspace: StateFlow<ShoppingWorkspaceSnapshot> =
    authRepository.observeAuthSession()
      .flatMapLatest { session ->
        val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
        if (uid != null) observeShoppingWorkspaceUseCase(uid) else flowOf(ShoppingWorkspaceSnapshot())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ShoppingWorkspaceSnapshot(),
      )

  fun selectList(listId: String?) {
    _selectedListId.value = listId
  }

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun selectFilter(filter: ShoppingItemFilter) {
    _selectedFilter.value = filter
  }

  fun dismissMessage() {
    _userMessage.value = null
    _isErrorMessage.value = false
  }

  fun createList(
    name: String,
    currencyCode: String,
    budgetDecimal: String?,
    warningThresholdPercent: Int = 80,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result =
        createShoppingListUseCase(
          authenticatedUserId = uid,
          name = name,
          currencyCode = currencyCode,
          budgetDecimal = budgetDecimal,
          warningThresholdPercent = warningThresholdPercent,
        )
      result.fold(
        onSuccess = { created ->
          _selectedListId.value = created.id
          _isErrorMessage.value = false
          _userMessage.value = "Shopping list '${created.name}' created"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to create shopping list"
        },
      )
    }
  }

  fun updateList(
    existingList: ShoppingList,
    newName: String,
    newCurrencyCode: String,
    budgetDecimal: String?,
    warningThresholdPercent: Int = 80,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result =
        updateShoppingListUseCase(
          authenticatedUserId = uid,
          existingList = existingList,
          newName = newName,
          newCurrencyCode = newCurrencyCode,
          budgetDecimal = budgetDecimal,
          warningThresholdPercent = warningThresholdPercent,
        )
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Shopping list updated"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to update shopping list"
        },
      )
    }
  }

  fun deleteList(
    listId: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result = deleteShoppingListUseCase(uid, listId)
      result.fold(
        onSuccess = {
          if (_selectedListId.value == listId) {
            _selectedListId.value = null
          }
          _isErrorMessage.value = false
          _userMessage.value = "Shopping list deleted"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to delete shopping list"
        },
      )
    }
  }

  fun addItem(
    listId: String,
    name: String,
    category: String,
    quantityDecimal: String,
    unit: String,
    estimatedUnitPriceDecimal: String,
    storeName: String?,
    notes: String?,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result =
        addShoppingItemUseCase(
          authenticatedUserId = uid,
          listId = listId,
          name = name,
          category = category,
          quantityDecimal = quantityDecimal,
          unit = unit,
          estimatedUnitPriceDecimal = estimatedUnitPriceDecimal,
          storeName = storeName,
          notes = notes,
        )
      result.fold(
        onSuccess = { created ->
          _isErrorMessage.value = false
          _userMessage.value = "'${created.name}' added to list"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to add shopping item"
        },
      )
    }
  }

  fun updateItem(
    itemId: String,
    name: String,
    category: String,
    quantityDecimal: String,
    unit: String,
    estimatedUnitPriceDecimal: String,
    storeName: String?,
    notes: String?,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result =
        updateShoppingItemUseCase(
          authenticatedUserId = uid,
          itemId = itemId,
          name = name,
          category = category,
          quantityDecimal = quantityDecimal,
          unit = unit,
          estimatedUnitPriceDecimal = estimatedUnitPriceDecimal,
          storeName = storeName,
          notes = notes,
        )
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Shopping item updated"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to update shopping item"
        },
      )
    }
  }

  fun markPurchased(
    itemId: String,
    actualUnitPriceDecimal: String,
    actualTotalOverrideDecimal: String?,
    purchasedAt: Long,
    storeNameOverride: String?,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result =
        purchaseShoppingItemUseCase(
          authenticatedUserId = uid,
          itemId = itemId,
          actualUnitPriceDecimal = actualUnitPriceDecimal,
          actualTotalOverrideDecimal = actualTotalOverrideDecimal,
          purchasedAt = purchasedAt,
          storeNameOverride = storeNameOverride,
        )
      result.fold(
        onSuccess = { updated ->
          _isErrorMessage.value = false
          _userMessage.value = "Recorded purchase for '${updated.name}'"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to record purchase"
        },
      )
    }
  }

  fun markUnpurchased(
    itemId: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result = purchaseShoppingItemUseCase.markUnpurchased(uid, itemId)
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Item moved back to remaining"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to update purchase status"
        },
      )
    }
  }

  fun deleteItem(
    itemId: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result = deleteShoppingItemUseCase(uid, itemId)
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Shopping item deleted"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to delete shopping item"
        },
      )
    }
  }

  fun recordStorePrice(
    itemId: String,
    storeName: String,
    unitPriceDecimal: String,
    recordedAt: Long,
    notes: String?,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result =
        manageStorePriceUseCase.recordPrice(
          authenticatedUserId = uid,
          itemId = itemId,
          storeName = storeName,
          unitPriceDecimal = unitPriceDecimal,
          recordedAt = recordedAt,
          notes = notes,
        )
      result.fold(
        onSuccess = { record ->
          _isErrorMessage.value = false
          _userMessage.value = "Saved price quote from ${record.storeName}"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to save store price"
        },
      )
    }
  }

  fun deleteStorePrice(
    priceId: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      val result = manageStorePriceUseCase.deletePrice(uid, priceId)
      result.fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Store price removed"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Failed to remove store price"
        },
      )
    }
  }


  fun savePantryItem(item: ShoppingPantryItem, onSuccess: () -> Unit = {}) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      shoppingExtendedRepository.savePantryItem(uid, item.copy(userId = uid)).fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Pantry stock saved"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error ->
          _isErrorMessage.value = true
          _userMessage.value = error.message ?: "Could not save pantry stock"
        },
      )
    }
  }

  fun deletePantryItem(itemId: String) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      shoppingExtendedRepository.deletePantryItem(uid, itemId).fold(
        onSuccess = { _userMessage.value = "Pantry item removed"; _isErrorMessage.value = false },
        onFailure = { error -> _userMessage.value = error.message ?: "Could not remove pantry item"; _isErrorMessage.value = true },
      )
    }
  }

  fun createMealPlan(plan: ShoppingMealPlan, onSuccess: () -> Unit = {}) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      shoppingExtendedRepository.createMealPlan(uid, plan.copy(userId = uid, ingredients = plan.ingredients.map { it.copy(userId = uid, mealPlanId = plan.id) })).fold(
        onSuccess = {
          _userMessage.value = "Meal plan saved"
          _isErrorMessage.value = false
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error -> _userMessage.value = error.message ?: "Could not save meal plan"; _isErrorMessage.value = true },
      )
    }
  }

  fun deleteMealPlan(planId: String) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      shoppingExtendedRepository.deleteMealPlan(uid, planId).fold(
        onSuccess = { _userMessage.value = "Meal plan removed"; _isErrorMessage.value = false },
        onFailure = { error -> _userMessage.value = error.message ?: "Could not remove meal plan"; _isErrorMessage.value = true },
      )
    }
  }

  fun saveReceiptReview(receipt: ShoppingReceipt, onSaved: (ShoppingReceipt) -> Unit) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      shoppingExtendedRepository.saveReceiptReview(uid, receipt.copy(userId = uid)).fold(
        onSuccess = { saved ->
          _isErrorMessage.value = false
          _userMessage.value = if (saved.status == "POSTED") "This receipt was already recorded" else "Receipt saved for review"
          launch(dispatchers.main) { onSaved(saved) }
        },
        onFailure = { error -> _isErrorMessage.value = true; _userMessage.value = error.message ?: "Could not save receipt" },
      )
    }
  }

  fun postReceiptExpense(receiptId: String, accountId: String, onSuccess: () -> Unit = {}) {
    viewModelScope.launch(dispatchers.io) {
      val uid = requireAuthenticatedUid() ?: return@launch
      shoppingExtendedRepository.postReceiptExpense(uid, receiptId, accountId).fold(
        onSuccess = {
          _isErrorMessage.value = false
          _userMessage.value = "Receipt added to Expenses"
          launch(dispatchers.main) { onSuccess() }
        },
        onFailure = { error -> _isErrorMessage.value = true; _userMessage.value = error.message ?: "Could not post receipt expense" },
      )
    }
  }

  private suspend fun requireAuthenticatedUid(): String? {
    val session = authRepository.observeAuthSession().first()
    val uid = (session as? AuthSessionState.Authenticated)?.user?.uid
    if (uid == null) {
      _isErrorMessage.value = true
      _userMessage.value = "Authentication session required"
    }
    return uid
  }
}
