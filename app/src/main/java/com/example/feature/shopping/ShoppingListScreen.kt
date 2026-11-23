package com.example.feature.shopping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanSectionHeader
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.FinancialAccount
import com.example.domain.model.Money
import com.example.domain.model.ShoppingBudgetStatus
import com.example.domain.model.ShoppingItemComparisonSummary
import com.example.domain.model.ShoppingList
import com.example.domain.model.ShoppingListItem
import com.example.domain.model.ShoppingListSummary
import com.example.domain.model.ShoppingReceipt
import com.example.domain.model.ShoppingPantryItem
import com.example.domain.model.ShoppingMealPlan
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.domain.usecase.ShoppingCalculationEngine
import com.example.domain.usecase.ShoppingWorkspaceSnapshot
import com.example.domain.usecase.ShoppingInputParser
import com.example.domain.usecase.ShoppingReceiptDraft
import com.example.domain.usecase.ShoppingInsightsEngine
import com.example.domain.usecase.ShoppingPantryEngine
import com.example.domain.usecase.ShoppingItemDraft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun ShoppingListScreen(
  viewModel: ShoppingViewModel,
  modifier: Modifier = Modifier,
) {
  val workspace by viewModel.workspace.collectAsStateWithLifecycle()
  val accounts by viewModel.accounts.collectAsStateWithLifecycle()
  val selectedListId by viewModel.selectedListId.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
  val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
  val isErrorMessage by viewModel.isErrorMessage.collectAsStateWithLifecycle()
  val pantry by viewModel.pantry.collectAsStateWithLifecycle()
  val mealPlans by viewModel.mealPlans.collectAsStateWithLifecycle()
  val receipts by viewModel.receipts.collectAsStateWithLifecycle()

  ShoppingListContent(
    workspace = workspace,
    accounts = accounts,
    pantry = pantry,
    mealPlans = mealPlans,
    receipts = receipts,
    selectedListId = selectedListId,
    searchQuery = searchQuery,
    selectedFilter = selectedFilter,
    userMessage = userMessage,
    isErrorMessage = isErrorMessage,
    onSelectList = viewModel::selectList,
    onSearchQueryChange = viewModel::updateSearchQuery,
    onSelectFilter = viewModel::selectFilter,
    onDismissMessage = viewModel::dismissMessage,
    onCreateList = { name, currency, budget, threshold, onSuccess ->
      viewModel.createList(name, currency, budget, threshold, onSuccess)
    },
    onUpdateList = { list, newName, newCurrency, budget, threshold, onSuccess ->
      viewModel.updateList(list, newName, newCurrency, budget, threshold, onSuccess)
    },
    onDeleteList = { listId, onSuccess -> viewModel.deleteList(listId, onSuccess) },
    onAddItem = { listId, name, category, qty, unit, estUnitPrice, store, notes, onSuccess ->
      viewModel.addItem(listId, name, category, qty, unit, estUnitPrice, store, notes, onSuccess)
    },
    onUpdateItem = { itemId, name, category, qty, unit, estUnitPrice, store, notes, onSuccess ->
      viewModel.updateItem(itemId, name, category, qty, unit, estUnitPrice, store, notes, onSuccess)
    },
    onMarkPurchased = { itemId, actualUnitPrice, actualTotalOverride, purchasedAt, storeOverride, onSuccess ->
      viewModel.markPurchased(itemId, actualUnitPrice, actualTotalOverride, purchasedAt, storeOverride, onSuccess)
    },
    onMarkUnpurchased = { itemId -> viewModel.markUnpurchased(itemId) },
    onDeleteItem = { itemId, onSuccess -> viewModel.deleteItem(itemId, onSuccess) },
    onRecordStorePrice = { itemId, storeName, unitPrice, recordedAt, notes ->
      viewModel.recordStorePrice(itemId, storeName, unitPrice, recordedAt, notes)
    },
    onDeleteStorePrice = { priceId -> viewModel.deleteStorePrice(priceId) },
    onPostReceiptExpense = { draft, accountId, currencyCode, totalMinor, merchant ->
      val receipt = ShoppingReceipt(
        userId = "pending",
        fingerprint = draft.fingerprint,
        merchant = merchant,
        currencyCode = currencyCode,
        totalMinor = totalMinor,
      )
      viewModel.saveReceiptReview(receipt) { saved ->
        viewModel.postReceiptExpense(saved.id, accountId)
      }
    },
    modifier = modifier,
  )
}

@Composable
fun ShoppingListContent(
  workspace: ShoppingWorkspaceSnapshot,
  accounts: List<FinancialAccount> = emptyList(),
  pantry: List<ShoppingPantryItem> = emptyList(),
  mealPlans: List<ShoppingMealPlan> = emptyList(),
  receipts: List<ShoppingReceipt> = emptyList(),
  selectedListId: String? = null,
  searchQuery: String = "",
  selectedFilter: ShoppingItemFilter = ShoppingItemFilter.ALL,
  userMessage: String? = null,
  isErrorMessage: Boolean = false,
  onSelectList: (String?) -> Unit = {},
  onSearchQueryChange: (String) -> Unit = {},
  onSelectFilter: (ShoppingItemFilter) -> Unit = {},
  onDismissMessage: () -> Unit = {},
  onCreateList: (String, String, String?, Int, () -> Unit) -> Unit = { _, _, _, _, _ -> },
  onUpdateList: (ShoppingList, String, String, String?, Int, () -> Unit) -> Unit = { _, _, _, _, _, _ -> },
  onDeleteList: (String, () -> Unit) -> Unit = { _, _ -> },
  onAddItem: (String, String, String, String, String, String, String?, String?, () -> Unit) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
  onUpdateItem: (String, String, String, String, String, String, String?, String?, () -> Unit) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
  onMarkPurchased: (String, String, String?, Long, String?, () -> Unit) -> Unit = { _, _, _, _, _, _ -> },
  onMarkUnpurchased: (String) -> Unit = {},
  onDeleteItem: (String, () -> Unit) -> Unit = { _, _ -> },
  onRecordStorePrice: (String, String, String, Long, String?) -> Unit = { _, _, _, _, _ -> },
  onDeleteStorePrice: (String) -> Unit = {},
  onPostReceiptExpense: (ShoppingReceiptDraft, String, String, Long, String?) -> Unit = { _, _, _, _, _ -> },
  modifier: Modifier = Modifier,
) {
  val formatMoneyUseCase = remember { FormatMoneyUseCase() }
  val calculationEngine = remember { ShoppingCalculationEngine() }

  var showCreateListDialog by remember { mutableStateOf(false) }
  var listToEdit by remember { mutableStateOf<ShoppingList?>(null) }
  var listToDelete by remember { mutableStateOf<ShoppingList?>(null) }

  var showAddItemDialog by remember { mutableStateOf(false) }
  var itemToEdit by remember { mutableStateOf<ShoppingListItem?>(null) }
  var itemToPurchase by remember { mutableStateOf<ShoppingListItem?>(null) }
  var itemToDelete by remember { mutableStateOf<ShoppingListItem?>(null) }
  var compareItemId by remember { mutableStateOf<String?>(null) }
  var importedDrafts by remember { mutableStateOf<List<ShoppingItemDraft>>(emptyList()) }
  var receiptDraft by remember { mutableStateOf<ShoppingReceiptDraft?>(null) }

  val lists = workspace.lists
  val activeList = remember(lists, selectedListId) {
    lists.firstOrNull { it.id == selectedListId } ?: lists.firstOrNull()
  }
  val activeSummary = activeList?.let { workspace.summariesByListId[it.id] }
  val listItems = remember(workspace.items, activeList?.id) {
    if (activeList == null) emptyList()
    else workspace.items.filter { it.listId == activeList.id }
  }

  val filteredItems = remember(listItems, searchQuery, selectedFilter, workspace.comparisonsByItemId) {
    val q = searchQuery.trim().lowercase()
    listItems.filter { item ->
      val matchesFilter =
        when (selectedFilter) {
          ShoppingItemFilter.ALL -> true
          ShoppingItemFilter.REMAINING -> !item.isPurchased
          ShoppingItemFilter.PURCHASED -> item.isPurchased
          ShoppingItemFilter.PRICE_COMPARE ->
            (workspace.comparisonsByItemId[item.id]?.storePrices?.isNotEmpty() == true)
        }
      val matchesSearch =
        q.isEmpty() ||
          item.name.lowercase().contains(q) ||
          item.category.lowercase().contains(q) ||
          (item.storeName?.lowercase()?.contains(q) == true) ||
          (item.notes?.lowercase()?.contains(q) == true)
      matchesFilter && matchesSearch
    }
  }

  val analytics = remember(workspace.items, lists) {
    ShoppingInsightsEngine.analyze(workspace.items, lists.associate { it.id to it.currencyCode })
  }
  val smartSuggestions = remember(workspace.items, pantry) {
    ShoppingInsightsEngine.suggestRestocks(workspace.items, pantry)
  }
  val pantryAlerts = remember(pantry) {
    ShoppingPantryEngine.alerts(pantry, System.currentTimeMillis())
  }

  val defaultCurrency = accounts.firstOrNull()?.currencyCode ?: activeList?.currencyCode ?: "USD"
  val availableCurrencies = remember(accounts, lists) {
    (accounts.map { it.currencyCode } + lists.map { it.currencyCode }).distinct().ifEmpty { listOf("USD") }
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = OceanSpacing.md, vertical = OceanSpacing.md)
        .widthIn(max = 840.dp)
        .testTag("shopping_screen"),
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OceanSectionHeader(
        title = stringResource(R.string.shopping_title),
        subtitle = stringResource(R.string.shopping_subtitle),
        modifier = Modifier.weight(1f),
      )
      Spacer(modifier = Modifier.width(OceanSpacing.xs))
      Row(horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
        OutlinedButton(
          onClick = { showCreateListDialog = true },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("create_shopping_list_button"),
        ) {
          Icon(imageVector = Icons.Rounded.PlaylistAdd, contentDescription = null)
          Spacer(modifier = Modifier.width(4.dp))
          Text("New List")
        }
        if (activeList != null) {
          Button(
            onClick = { showAddItemDialog = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("add_shopping_item_button"),
          ) {
            Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Keyboard")
          }
        }
      }
    }

    if (activeList != null) {
      ShoppingReceiptCaptureAction(
        onReceiptReady = { receiptDraft = it },
        modifier = Modifier.fillMaxWidth(),
      )
      ShoppingCaptureActions(
        onDraftsReady = { drafts -> importedDrafts = drafts },
        modifier = Modifier.fillMaxWidth(),
      )
    }

    if (analytics.totalsByCurrency.isNotEmpty()) {
      OceanCard(modifier = Modifier.fillMaxWidth().testTag("shopping_analytics_card")) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(OceanSpacing.md),
          verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          Text("Shopping Analytics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          analytics.totalsByCurrency.forEach { total ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Actual spend · ${total.currencyCode}")
              Text(Money.fromMinorUnits(total.actualSpentMinor, total.currencyCode).toString(), fontWeight = FontWeight.SemiBold)
            }
          }
          analytics.categories.take(4).forEach { category ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(category.category, style = MaterialTheme.typography.bodySmall)
              Text(Money.fromMinorUnits(category.actualSpentMinor, category.currencyCode).toString(), style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      }
    }

    OceanCard(modifier = Modifier.fillMaxWidth().testTag("shopping_pantry_card")) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(OceanSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
      ) {
        Text("Pantry & Expiry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (pantry.isEmpty()) Text("No pantry stock tracked yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        pantry.take(5).forEach { item ->
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.name)
            Text(item.quantityDecimal + " " + item.unit, style = MaterialTheme.typography.bodySmall)
          }
        }
        pantryAlerts.take(5).forEach { alert ->
          Text(
            alert.item.name + " · " + when (alert.type) {
              com.example.domain.usecase.PantryAlertType.LOW_STOCK -> "Low stock"
              com.example.domain.usecase.PantryAlertType.EXPIRING_SOON -> "Expiring soon"
              com.example.domain.usecase.PantryAlertType.EXPIRED -> "Expired"
            },
            color = if (alert.type == com.example.domain.usecase.PantryAlertType.EXPIRED) OceanTheme.colors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }

    OceanCard(modifier = Modifier.fillMaxWidth().testTag("shopping_meal_planner_card")) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(OceanSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
      ) {
        Text("Meal Planner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (mealPlans.isEmpty()) Text("No meals planned yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        mealPlans.take(4).forEach { plan ->
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(plan.title)
            Text(plan.mealType.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }

    if (smartSuggestions.isNotEmpty()) {
      OceanCard(modifier = Modifier.fillMaxWidth().testTag("shopping_smart_suggestions_card")) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(OceanSpacing.md),
          verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          Text("Smart Suggestions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          smartSuggestions.take(5).forEach { suggestion ->
            Text(
              suggestion.itemName + " · " + suggestion.suggestedQuantityDecimal + " " + suggestion.unit,
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(suggestion.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }

    // Feedback Banner
    AnimatedVisibility(visible = userMessage != null) {
      userMessage?.let { msg ->
        Surface(
          shape = MaterialTheme.shapes.small,
          color = if (isErrorMessage) OceanTheme.colors.negativeContainer else OceanTheme.colors.positiveContainer,
          border = BorderStroke(
            1.dp,
            if (isErrorMessage) OceanTheme.colors.negative else OceanTheme.colors.positive,
          ),
          modifier = Modifier.fillMaxWidth().testTag("shopping_feedback_banner"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = OceanSpacing.sm, vertical = OceanSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = msg,
              style = MaterialTheme.typography.bodySmall,
              color = if (isErrorMessage) OceanTheme.colors.negative else OceanTheme.colors.positive,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismissMessage) { Text("Dismiss") }
          }
        }
      }
    }

    if (lists.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize().padding(vertical = OceanSpacing.lg),
        contentAlignment = Alignment.TopCenter,
      ) {
        OceanCard(modifier = Modifier.testTag("shopping_empty_state")) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(OceanSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
          ) {
            Icon(
              imageVector = Icons.Rounded.ShoppingCart,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(44.dp),
            )
            Text(
              text = stringResource(R.string.shopping_empty_title),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = stringResource(R.string.shopping_empty_body),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = { showCreateListDialog = true },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("empty_create_list_button"),
            ) {
              Icon(imageVector = Icons.Rounded.PlaylistAdd, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Create First Shopping List")
            }
          }
        }
      }
    } else if (activeList != null && activeSummary != null) {
      // List Selector Row
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        modifier = Modifier.fillMaxWidth().testTag("shopping_lists_row"),
      ) {
        items(lists, key = { it.id }) { list ->
          val summary = workspace.summariesByListId[list.id]
          val estText =
            summary?.let {
              formatMoneyUseCase.format(Money.fromMinorUnits(it.estimatedTotalMinor, list.currencyCode))
            } ?: ""
          val itemCount = summary?.totalItemCount ?: 0
          FilterChip(
            selected = list.id == activeList.id,
            onClick = { onSelectList(list.id) },
            label = {
              Text(
                text = "${list.name} ($itemCount • $estText)",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            modifier = Modifier.testTag("shopping_list_chip_${list.id}"),
          )
        }
      }

      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("shopping_lists_lazy_column"),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        // Active List Summary & Budget Card
        item(key = "summary_${activeList.id}") {
          ActiveShoppingListBudgetCard(
            summary = activeSummary,
            formatMoneyUseCase = formatMoneyUseCase,
            onEditList = { listToEdit = activeList },
            onDeleteList = { listToDelete = activeList },
          )
        }

        // Search & Filter Controls
        item(key = "search_filters") {
          Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = onSearchQueryChange,
              label = { Text("Search items, category, or store") },
              leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
              trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                  IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear search")
                  }
                }
              },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("shopping_search_input"),
            )

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
              verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
              modifier = Modifier.fillMaxWidth(),
            ) {
              ShoppingItemFilter.entries.forEach { filter ->
                val tag =
                  when (filter) {
                    ShoppingItemFilter.ALL -> "shopping_filter_all"
                    ShoppingItemFilter.REMAINING -> "shopping_filter_remaining"
                    ShoppingItemFilter.PURCHASED -> "shopping_filter_purchased"
                    ShoppingItemFilter.PRICE_COMPARE -> "shopping_filter_compare"
                  }
                FilterChip(
                  selected = selectedFilter == filter,
                  onClick = { onSelectFilter(filter) },
                  label = { Text(filter.label) },
                  modifier = Modifier.testTag(tag),
                )
              }
            }
          }
        }

        if (filteredItems.isEmpty()) {
          item(key = "items_empty") {
            OceanCard(modifier = Modifier.testTag("shopping_items_empty_state")) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(OceanSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
              ) {
                Text(
                  text =
                    if (listItems.isEmpty()) "No items in '${activeList.name}' yet"
                    else "No items match the current search or filter",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text =
                    if (listItems.isEmpty()) "Add groceries or essentials with quantities and estimated prices."
                    else "Try switching the status filter or clearing your search query.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(filteredItems, key = { it.id }) { item ->
            ShoppingListItemCard(
              item = item,
              currencyCode = activeList.currencyCode,
              comparison = workspace.comparisonsByItemId[item.id],
              formatMoneyUseCase = formatMoneyUseCase,
              onTogglePurchase = {
                if (item.isPurchased) {
                  onMarkUnpurchased(item.id)
                } else {
                  itemToPurchase = item
                }
              },
              onOpenPriceCompare = { compareItemId = item.id },
              onEditItem = { itemToEdit = item },
              onDeleteItem = { itemToDelete = item },
            )
          }
        }

        item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(OceanSpacing.xl)) }
      }
    }
  }

  // Dialogs
  if (showCreateListDialog || listToEdit != null) {
    val targetEdit = listToEdit
    val hasItems =
      targetEdit != null && workspace.items.any { it.listId == targetEdit.id }
    ShoppingListFormDialog(
      existingList = targetEdit,
      hasItemsInList = hasItems,
      defaultCurrency = defaultCurrency,
      availableCurrencies = availableCurrencies,
      onDismiss = {
        showCreateListDialog = false
        listToEdit = null
      },
      onSave = { name, currency, budget, threshold ->
        if (targetEdit == null) {
          onCreateList(name, currency, budget, threshold) {
            showCreateListDialog = false
          }
        } else {
          onUpdateList(targetEdit, name, currency, budget, threshold) {
            listToEdit = null
          }
        }
      },
    )
  }

  listToDelete?.let { target ->
    ConfirmDeleteShoppingEntityDialog(
      title = "Delete '${target.name}'?",
      message = "All items and store price records inside '${target.name}' will be permanently removed.",
      confirmTag = "confirm_delete_shopping_list_button",
      onDismiss = { listToDelete = null },
      onConfirm = {
        onDeleteList(target.id) { listToDelete = null }
      },
    )
  }

  if ((showAddItemDialog && activeList != null) || itemToEdit != null) {
    val targetEdit = itemToEdit
    val currency =
      targetEdit?.let { item ->
        lists.firstOrNull { it.id == item.listId }?.currencyCode
      } ?: activeList?.currencyCode ?: "USD"
    val targetListId = targetEdit?.listId ?: activeList?.id.orEmpty()

    ShoppingItemFormDialog(
      existingItem = targetEdit,
      currencyCode = currency,
      calculationEngine = calculationEngine,
      formatMoneyUseCase = formatMoneyUseCase,
      onDismiss = {
        showAddItemDialog = false
        itemToEdit = null
      },
      onSave = { name, category, qty, unit, estUnitPrice, store, notes ->
        if (targetEdit == null) {
          onAddItem(targetListId, name, category, qty, unit, estUnitPrice, store, notes) {
            showAddItemDialog = false
          }
        } else {
          onUpdateItem(targetEdit.id, name, category, qty, unit, estUnitPrice, store, notes) {
            itemToEdit = null
          }
        }
      },
    )
  }

  receiptDraft?.let { draft ->
    ShoppingReceiptReviewDialog(
      draft = draft,
      accounts = accounts,
      onDismiss = { receiptDraft = null },
      onConfirm = { accountId, currencyCode, totalMinor, merchant ->
        onPostReceiptExpense(draft, accountId, currencyCode, totalMinor, merchant)
        receiptDraft = null
      },
    )
  }

  if (importedDrafts.isNotEmpty() && activeList != null) {
    val targetListId = activeList.id
    ShoppingImportReviewDialog(
      drafts = importedDrafts,
      onDismiss = { importedDrafts = emptyList() },
      onConfirm = {
        importedDrafts.forEach { draft ->
          onAddItem(
            targetListId,
            draft.name,
            ShoppingInputParser.suggestCategory(draft.name),
            draft.quantityDecimal,
            draft.unit,
            draft.estimatedUnitPriceDecimal ?: "0",
            null,
            "Imported from voice/photo; verify item details and price",
          ) {}
        }
        importedDrafts = emptyList()
      },
    )
  }

  itemToPurchase?.let { target ->
    val currency = lists.firstOrNull { it.id == target.listId }?.currencyCode ?: "USD"
    RecordPurchaseDialog(
      item = target,
      currencyCode = currency,
      calculationEngine = calculationEngine,
      formatMoneyUseCase = formatMoneyUseCase,
      onDismiss = { itemToPurchase = null },
      onConfirmPurchase = { actualUnitPrice, actualTotalOverride, purchasedAt, storeOverride ->
        onMarkPurchased(target.id, actualUnitPrice, actualTotalOverride, purchasedAt, storeOverride) {
          itemToPurchase = null
        }
      },
    )
  }

  itemToDelete?.let { target ->
    ConfirmDeleteShoppingEntityDialog(
      title = "Delete '${target.name}'?",
      message = "This removes '${target.name}' and its store price quotes from your list.",
      confirmTag = "confirm_delete_shopping_item_button",
      onDismiss = { itemToDelete = null },
      onConfirm = {
        onDeleteItem(target.id) { itemToDelete = null }
      },
    )
  }

  compareItemId?.let { id ->
    val comparison = workspace.comparisonsByItemId[id]
    if (comparison != null) {
      val currency = lists.firstOrNull { it.id == comparison.item.listId }?.currencyCode ?: "USD"
      StorePriceComparisonDialog(
        comparison = comparison,
        currencyCode = currency,
        calculationEngine = calculationEngine,
        formatMoneyUseCase = formatMoneyUseCase,
        onDismiss = { compareItemId = null },
        onRecordPrice = { store, unitPrice, recordedAt, notes ->
          onRecordStorePrice(id, store, unitPrice, recordedAt, notes)
        },
        onDeletePrice = { priceId -> onDeleteStorePrice(priceId) },
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveShoppingListBudgetCard(
  summary: ShoppingListSummary,
  formatMoneyUseCase: FormatMoneyUseCase,
  onEditList: () -> Unit,
  onDeleteList: () -> Unit,
) {
  val list = summary.list
  val currency = list.currencyCode
  val extended = OceanTheme.colors

  val estTotalStr = formatMoneyUseCase.format(Money.fromMinorUnits(summary.estimatedTotalMinor, currency))
  val actualSpentStr = formatMoneyUseCase.format(Money.fromMinorUnits(summary.actualSpentMinor, currency))
  val projectedStr = formatMoneyUseCase.format(Money.fromMinorUnits(summary.projectedSpendMinor, currency))

  OceanCard(modifier = Modifier.testTag("shopping_budget_card")) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          Text(
            text = list.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          OceanStatusBadge(text = currency, tone = BadgeTone.INFO)
        }
        Text(
          text = "${summary.purchasedItemCount} purchased • ${summary.remainingItemCount} remaining",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Row {
        IconButton(
          onClick = onEditList,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_shopping_list_button"),
        ) {
          Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Edit shopping list")
        }
        IconButton(
          onClick = onDeleteList,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_shopping_list_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = "Delete shopping list",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.sm))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
    ) {
      MetricColumn(
        label = "Estimated Total",
        value = estTotalStr,
        modifier = Modifier.weight(1f),
      )
      MetricColumn(
        label = "Actual Spent",
        value = actualSpentStr,
        modifier = Modifier.weight(1f),
      )
      MetricColumn(
        label = "Projected",
        value = projectedStr,
        modifier = Modifier.weight(1f),
      )
    }

    if (summary.purchasedItemCount > 0) {
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      val varianceMinor = summary.savingsOrOverspendMinor
      val varianceFormatted =
        formatMoneyUseCase.format(Money.fromMinorUnits(abs(varianceMinor), currency))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
      ) {
        OceanStatusBadge(
          text =
            when {
              varianceMinor > 0L -> "Saved $varianceFormatted vs Est."
              varianceMinor < 0L -> "Overspent $varianceFormatted vs Est."
              else -> "On Estimate ($actualSpentStr)"
            },
          tone =
            when {
              varianceMinor > 0L -> BadgeTone.POSITIVE
              varianceMinor < 0L -> BadgeTone.NEGATIVE
              else -> BadgeTone.INFO
            },
        )
      }
    }

    list.budgetMinor?.let { budgetMinor ->
      Spacer(modifier = Modifier.height(OceanSpacing.sm))
      val budgetFormatted = formatMoneyUseCase.format(Money.fromMinorUnits(budgetMinor, currency))
      val remainingAfterActual = summary.remainingBudgetMinor ?: 0L
      val remainingFormatted =
        formatMoneyUseCase.format(Money.fromMinorUnits(abs(remainingAfterActual), currency))

      val barColor =
        when (summary.budgetStatus) {
          ShoppingBudgetStatus.EXCEEDED_BUDGET -> extended.negative
          ShoppingBudgetStatus.APPROACHING_BUDGET -> extended.warning
          else -> extended.positive
        }
      val clampedFraction = (summary.budgetUsagePercent.coerceIn(0, 100)) / 100f

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Budget: $budgetFormatted (${summary.budgetUsagePercent}% used/projected)",
          style = MaterialTheme.typography.labelLarge,
        )
        Text(
          text =
            if (remainingAfterActual >= 0L) "Left: $remainingFormatted"
            else "Over by: $remainingFormatted",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = if (remainingAfterActual >= 0L) extended.positive else extended.negative,
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      LinearProgressIndicator(
        progress = { clampedFraction },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = barColor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
      )

      if (
        summary.budgetStatus == ShoppingBudgetStatus.APPROACHING_BUDGET ||
          summary.budgetStatus == ShoppingBudgetStatus.EXCEEDED_BUDGET
      ) {
        Spacer(modifier = Modifier.height(OceanSpacing.xs))
        val isExceeded = summary.budgetStatus == ShoppingBudgetStatus.EXCEEDED_BUDGET
        val bannerBg = if (isExceeded) extended.negativeContainer else extended.warningContainer
        val bannerFg = if (isExceeded) extended.negative else extended.warning
        Surface(
          color = bannerBg,
          border = BorderStroke(1.dp, bannerFg.copy(alpha = 0.5f)),
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth().testTag("shopping_budget_warning_banner"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(OceanSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          ) {
            Icon(imageVector = Icons.Rounded.Warning, contentDescription = null, tint = bannerFg)
            Text(
              text =
                if (isExceeded)
                  "Budget exceeded or projected to exceed $budgetFormatted!"
                else
                  "Approaching budget (${summary.budgetUsagePercent}% of $budgetFormatted, alert at ${list.warningThresholdPercent}%).",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Medium,
              color = bannerFg,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MetricColumn(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShoppingListItemCard(
  item: ShoppingListItem,
  currencyCode: String,
  comparison: ShoppingItemComparisonSummary?,
  formatMoneyUseCase: FormatMoneyUseCase,
  onTogglePurchase: () -> Unit,
  onOpenPriceCompare: () -> Unit,
  onEditItem: () -> Unit,
  onDeleteItem: () -> Unit,
) {
  val estUnitFormatted = formatMoneyUseCase.format(Money.fromMinorUnits(item.estimatedUnitPriceMinor, currencyCode))
  val estTotalFormatted = formatMoneyUseCase.format(Money.fromMinorUnits(item.estimatedTotalMinor, currencyCode))
  val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US) }

  OceanCard(modifier = Modifier.testTag("shopping_item_${item.id}")) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      IconButton(
        onClick = onTogglePurchase,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("toggle_purchase_${item.id}"),
      ) {
        Icon(
          imageVector =
            if (item.isPurchased) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
          contentDescription = if (item.isPurchased) "Mark unpurchased" else "Mark purchased",
          tint =
            if (item.isPurchased) OceanTheme.colors.positive
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
        Text(
          text = item.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          textDecoration = if (item.isPurchased) TextDecoration.LineThrough else TextDecoration.None,
          color =
            if (item.isPurchased) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          OceanStatusBadge(text = item.category, tone = BadgeTone.INFO)
          item.storeName?.let { store ->
            OceanStatusBadge(text = store, tone = BadgeTone.GOLD)
          }
          if (item.isPurchased && comparison?.savingsOrOverspendMinor != null) {
            val diff = comparison.savingsOrOverspendMinor
            val diffFormatted = formatMoneyUseCase.format(Money.fromMinorUnits(abs(diff), currencyCode))
            OceanStatusBadge(
              text =
                when {
                  diff > 0L -> "Saved $diffFormatted"
                  diff < 0L -> "Over $diffFormatted"
                  else -> "On Estimate"
                },
              tone =
                when {
                  diff > 0L -> BadgeTone.POSITIVE
                  diff < 0L -> BadgeTone.NEGATIVE
                  else -> BadgeTone.INFO
                },
            )
          }
        }

        Spacer(modifier = Modifier.height(OceanSpacing.xs))
        Text(
          text = "Qty: ${item.quantityDecimal} ${item.unit} • Est: $estTotalFormatted ($estUnitFormatted/${item.unit})",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (item.isPurchased && item.actualTotalMinor != null && item.actualUnitPriceMinor != null) {
          val actTotalFormatted = formatMoneyUseCase.format(Money.fromMinorUnits(item.actualTotalMinor, currencyCode))
          val actUnitFormatted = formatMoneyUseCase.format(Money.fromMinorUnits(item.actualUnitPriceMinor, currencyCode))
          val purchasedDateStr = item.purchasedAt?.let { dateFormat.format(Date(it)) } ?: ""
          Text(
            text = "Actual Paid: $actTotalFormatted ($actUnitFormatted/${item.unit}) • $purchasedDateStr",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        val latestQuote = comparison?.latestStorePrice
        if (latestQuote != null) {
          val latestFormatted = formatMoneyUseCase.format(
            Money.fromMinorUnits(latestQuote.unitPriceMinor, latestQuote.currencyCode)
          )
          Text(
            text = "Latest Quote: ${latestQuote.storeName} $latestFormatted/${item.unit} (${comparison.storePrices.size} stores)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }

        item.notes?.let { noteText ->
          Text(
            text = noteText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(
        onClick = onOpenPriceCompare,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("compare_prices_${item.id}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.CompareArrows,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Compare (${comparison?.storePrices?.size ?: 0})")
      }
      IconButton(
        onClick = onEditItem,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_item_${item.id}"),
      ) {
        Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Edit item")
      }
      IconButton(
        onClick = onDeleteItem,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_item_${item.id}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Delete,
          contentDescription = "Delete item",
          tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}
