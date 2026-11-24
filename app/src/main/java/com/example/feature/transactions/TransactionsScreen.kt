package com.example.feature.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanSectionHeader
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanNumericStyle
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.FinancialAccount
import com.example.domain.model.Money
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.TransactionCategories
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.usecase.FormatMoneyUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(
  viewModel: TransactionsViewModel,
  onOpenIncomeManagement: () -> Unit = {},
  onOpenExpenseManagement: () -> Unit = {},
  onOpenReports: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
  val accounts by viewModel.accounts.collectAsStateWithLifecycle()
  val filterState by viewModel.filterState.collectAsStateWithLifecycle()
  val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

  TransactionsContent(
    transactions = transactions,
    accounts = accounts,
    filterState = filterState,
    userMessage = userMessage,
    onDismissMessage = viewModel::dismissMessage,
    onSearchQueryChange = viewModel::setSearchQuery,
    onPeriodChange = viewModel::setPeriodFilter,
    onCustomDateRange = viewModel::setCustomDateRange,
    onTypeFilterChange = viewModel::setTypeFilter,
    onAccountFilterChange = viewModel::setAccountFilter,
    onCurrencyFilterChange = viewModel::setCurrencyFilter,
    onCategoryFilterChange = viewModel::setCategoryFilter,
    onClearAllFilters = viewModel::clearAllFilters,
    onRecordExpense = { accId, amt, cat, merch, notes, date, status ->
      viewModel.recordExpense(accId, amt, cat, merch, notes, date, status)
    },
    onRecordIncome = { accId, amt, cat, merch, notes, date, status ->
      viewModel.recordIncome(accId, amt, cat, notes, merch, date, status)
    },
    onRecordGeneral = { accId, type, status, amt, cat, merch, notes, date ->
      viewModel.recordTransaction(accId, type, status, amt, cat, merch, notes, date)
    },
    onRecordTransfer = { srcId, dstId, amt, dstAmt, notes, date ->
      viewModel.recordTransfer(srcId, dstId, amt, dstAmt, notes, date)
    },
    onUpdateTransaction = { orig, accId, type, status, amt, cat, merch, notes, date ->
      viewModel.updateTransactionFromEdit(orig, accId, type, status, amt, cat, merch, notes, date)
    },
    onDeleteTransaction = viewModel::deleteTransaction,
    onOpenIncomeManagement = onOpenIncomeManagement,
    onOpenExpenseManagement = onOpenExpenseManagement,
    onOpenReports = onOpenReports,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsContent(
  transactions: List<FinancialTransaction>,
  accounts: List<FinancialAccount>,
  filterState: TransactionsFilterState,
  userMessage: String? = null,
  onDismissMessage: () -> Unit = {},
  onSearchQueryChange: (String) -> Unit = {},
  onPeriodChange: (TimePeriodFilter) -> Unit = {},
  onCustomDateRange: (Long?, Long?) -> Unit = { _, _ -> },
  onTypeFilterChange: (TransactionType?) -> Unit = {},
  onAccountFilterChange: (String?) -> Unit = {},
  onCurrencyFilterChange: (String?) -> Unit = {},
  onCategoryFilterChange: (String?) -> Unit = {},
  onClearAllFilters: () -> Unit = {},
  onRecordExpense: (String, String, String, String?, String?, Long, TransactionStatus) -> Unit = { _, _, _, _, _, _, _ -> },
  onRecordIncome: (String, String, String, String?, String?, Long, TransactionStatus) -> Unit = { _, _, _, _, _, _, _ -> },
  onRecordGeneral: (String, TransactionType, TransactionStatus, String, String, String?, String?, Long) -> Unit = { _, _, _, _, _, _, _, _ -> },
  onRecordTransfer: (String, String, String, String?, String?, Long) -> Unit = { _, _, _, _, _, _ -> },
  onUpdateTransaction: (FinancialTransaction, String, TransactionType, TransactionStatus, String, String, String?, String?, Long) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
  onDeleteTransaction: (FinancialTransaction) -> Unit = {},
  onOpenIncomeManagement: () -> Unit = {},
  onOpenExpenseManagement: () -> Unit = {},
  onOpenReports: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val snackbarHostState = remember { SnackbarHostState() }

  var isAddSheetOpen by remember { mutableStateOf(false) }
  var transactionToEdit by remember { mutableStateOf<FinancialTransaction?>(null) }
  var selectedTransactionForDetails by remember { mutableStateOf<FinancialTransaction?>(null) }
  var transactionToDelete by remember { mutableStateOf<FinancialTransaction?>(null) }
  var showDateRangePicker by remember { mutableStateOf(false) }

  LaunchedEffect(userMessage) {
    userMessage?.let {
      snackbarHostState.showSnackbar(it)
      onDismissMessage()
    }
  }

  val availableCurrencies = remember(accounts) { accounts.map { it.currencyCode }.distinct() }
  val availableCategories = remember(transactions) { transactions.map { it.category }.distinct().sorted() }

  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("transactions_screen"),
      contentPadding = PaddingValues(OceanSpacing.md),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        OceanSectionHeader(
          title = "Transactions Ledger",
          subtitle = "Income, expenses, refunds, reversals & atomic transfers",
          modifier = Modifier.widthIn(max = 760.dp),
        )
      }

      // Shortcuts to dedicated Income, Expense & Reports screens
      item {
        Row(
          modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          OutlinedButton(
            onClick = onOpenIncomeManagement,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f).testTag("open_income_management_btn"),
          ) {
            Text("Income", maxLines = 1)
          }
          OutlinedButton(
            onClick = onOpenExpenseManagement,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f).testTag("open_expense_management_btn"),
          ) {
            Text("Expenses", maxLines = 1)
          }
          OutlinedButton(
            onClick = onOpenReports,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f).testTag("open_reports_from_transactions_btn"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Assessment,
              contentDescription = null,
              tint = OceanTheme.colors.gold,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reports", maxLines = 1, fontWeight = FontWeight.SemiBold)
          }
        }
      }

      // Search bar
      item {
        OutlinedTextField(
          value = filterState.searchQuery,
          onValueChange = onSearchQueryChange,
          modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).testTag("transactions_search_input"),
          placeholder = { Text("Search merchant, category, notes, ref...") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (filterState.searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchQueryChange("") }) {
                Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
        )
      }

      // Time period filter chips
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          listOf(
            TimePeriodFilter.ALL to "All Time",
            TimePeriodFilter.TODAY to "Today",
            TimePeriodFilter.THIS_WEEK to "This Week",
            TimePeriodFilter.THIS_MONTH to "This Month",
          ).forEach { (period, label) ->
            FilterChip(
              selected = filterState.selectedPeriod == period,
              onClick = { onPeriodChange(period) },
              label = { Text(label) },
            )
          }
          FilterChip(
            selected = filterState.selectedPeriod == TimePeriodFilter.CUSTOM_RANGE,
            onClick = { showDateRangePicker = true },
            leadingIcon = { Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text("Date Range") },
          )
        }
      }

      // Type filter chips
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          FilterChip(
            selected = filterState.selectedType == null,
            onClick = { onTypeFilterChange(null) },
            label = { Text("All Types") },
          )
          listOf(
            TransactionType.EXPENSE to "Expenses",
            TransactionType.INCOME to "Income",
            TransactionType.TRANSFER to "Transfers",
            TransactionType.REFUND to "Refunds",
            TransactionType.REVERSAL to "Reversals",
            TransactionType.PENDING to "Pending",
            TransactionType.FAILED to "Failed",
          ).forEach { (type, label) ->
            FilterChip(
              selected = filterState.selectedType == type,
              onClick = { onTypeFilterChange(type) },
              label = { Text(label) },
            )
          }
        }
      }

      // Account & Currency filter chips (when accounts exist)
      if (accounts.isNotEmpty() || availableCategories.isNotEmpty()) {
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 760.dp)
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          ) {
            if (availableCurrencies.size > 1) {
              availableCurrencies.forEach { curr ->
                FilterChip(
                  selected = filterState.selectedCurrency == curr,
                  onClick = {
                    onCurrencyFilterChange(if (filterState.selectedCurrency == curr) null else curr)
                  },
                  label = { Text(curr) },
                )
              }
            }
            accounts.forEach { acc ->
              FilterChip(
                selected = filterState.selectedAccountId == acc.id,
                onClick = {
                  onAccountFilterChange(if (filterState.selectedAccountId == acc.id) null else acc.id)
                },
                label = { Text(acc.name) },
              )
            }
            availableCategories.forEach { cat ->
              FilterChip(
                selected = filterState.selectedCategory == cat,
                onClick = {
                  onCategoryFilterChange(if (filterState.selectedCategory == cat) null else cat)
                },
                label = { Text(cat) },
              )
            }
          }
        }
      }

      if (transactions.isEmpty()) {
        item {
          OceanCard(modifier = Modifier.widthIn(max = 760.dp).testTag("transactions_empty_card")) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(OceanSpacing.lg),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                contentDescription = null,
                tint = OceanTheme.colors.gold,
                modifier = Modifier.size(48.dp),
              )
              Spacer(modifier = Modifier.height(OceanSpacing.sm))
              Text(
                text = "No Transactions Found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(OceanSpacing.xs))
              Text(
                text = "Record an expense, income, or transfer using the + button below, or adjust your active filters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (filterState != TransactionsFilterState()) {
                Spacer(modifier = Modifier.height(OceanSpacing.sm))
                TextButton(onClick = onClearAllFilters) { Text("Clear All Filters") }
              }
            }
          }
        }
      } else {
        items(transactions, key = { it.id }) { txn ->
          val account = accounts.firstOrNull { it.id == txn.accountId }
          TransactionListItem(
            transaction = txn,
            accountName = account?.name ?: "Account",
            onClick = { selectedTransactionForDetails = txn },
            modifier = Modifier.widthIn(max = 760.dp),
          )
        }
      }
    }

    FloatingActionButton(
      onClick = { isAddSheetOpen = true },
      modifier =
        Modifier
          .align(Alignment.BottomEnd)
          .padding(OceanSpacing.lg)
          .testTag("add_transaction_fab"),
      containerColor = OceanTheme.colors.gold,
      contentColor = Color.Black,
    ) {
      Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add Transaction")
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter).padding(OceanSpacing.md),
    )
  }

  if (showDateRangePicker) {
    val pickerState = rememberDateRangePickerState()
    DatePickerDialog(
      onDismissRequest = { showDateRangePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            onCustomDateRange(
              pickerState.selectedStartDateMillis,
              pickerState.selectedEndDateMillis?.let { it + 86_399_999L },
            )
            showDateRangePicker = false
          }
        ) { Text("Apply") }
      },
      dismissButton = {
        TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
      },
    ) {
      DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
    }
  }

  if (isAddSheetOpen || transactionToEdit != null) {
    AddEditTransactionBottomSheet(
      accounts = accounts,
      transactionToEdit = transactionToEdit,
      onDismiss = {
        isAddSheetOpen = false
        transactionToEdit = null
      },
      onSaveExpense = { accId, amt, cat, merch, notes, date, status ->
        onRecordExpense(accId, amt, cat, merch, notes, date, status)
        isAddSheetOpen = false
      },
      onSaveIncome = { accId, amt, cat, merch, notes, date, status ->
        onRecordIncome(accId, amt, cat, merch, notes, date, status)
        isAddSheetOpen = false
      },
      onSaveGeneral = { accId, type, status, amt, cat, merch, notes, date ->
        onRecordGeneral(accId, type, status, amt, cat, merch, notes, date)
        isAddSheetOpen = false
      },
      onSaveTransfer = { srcAccId, dstAccId, amt, dstAmt, notes, date ->
        onRecordTransfer(srcAccId, dstAccId, amt, dstAmt, notes, date)
        isAddSheetOpen = false
      },
      onUpdateExisting = { orig, accId, type, status, amt, cat, merch, notes, date ->
        onUpdateTransaction(orig, accId, type, status, amt, cat, merch, notes, date)
        transactionToEdit = null
      },
    )
  }

  selectedTransactionForDetails?.let { txn ->
    val account = accounts.firstOrNull { it.id == txn.accountId }
    TransactionDetailsDialog(
      transaction = txn,
      accountName = account?.name ?: "Unknown",
      onDismiss = { selectedTransactionForDetails = null },
      onEdit = {
        transactionToEdit = txn
        selectedTransactionForDetails = null
      },
      onDelete = {
        transactionToDelete = txn
        selectedTransactionForDetails = null
      },
    )
  }

  transactionToDelete?.let { txn ->
    AlertDialog(
      onDismissRequest = { transactionToDelete = null },
      title = { Text("Delete Transaction") },
      text = { Text("Are you sure you want to delete this transaction? Affected account balances will be updated atomically.") },
      confirmButton = {
        Button(
          onClick = {
            onDeleteTransaction(txn)
            transactionToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = OceanTheme.colors.negative),
          modifier = Modifier.testTag("confirm_delete_transaction_button"),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { transactionToDelete = null }) { Text("Cancel") }
      },
    )
  }
}

@Composable
fun TransactionListItem(
  transaction: FinancialTransaction,
  accountName: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val formattedAmount = FormatMoneyUseCase().format(transaction.money)
  val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
  val dateStr = remember(transaction.transactionDate) { dateFormat.format(Date(transaction.transactionDate)) }

  val (icon, tint, amountPrefix, amountColor) =
    when (transaction.type) {
      TransactionType.INCOME ->
        Quad(Icons.Rounded.ArrowDownward, OceanTheme.colors.positive, "+", OceanTheme.colors.positive)
      TransactionType.EXPENSE ->
        Quad(Icons.Rounded.ArrowUpward, OceanTheme.colors.negative, "-", MaterialTheme.colorScheme.onSurface)
      TransactionType.TRANSFER ->
        Quad(Icons.Rounded.SyncAlt, MaterialTheme.colorScheme.secondary, "", MaterialTheme.colorScheme.secondary)
      TransactionType.REFUND ->
        Quad(Icons.Rounded.ArrowDownward, OceanTheme.colors.positive, "+", OceanTheme.colors.positive)
      TransactionType.REVERSAL ->
        Quad(Icons.Rounded.SyncAlt, OceanTheme.colors.warning, "+", OceanTheme.colors.warning)
      TransactionType.PENDING ->
        Quad(Icons.Rounded.SyncAlt, OceanTheme.colors.warning, "", OceanTheme.colors.warning)
      TransactionType.FAILED ->
        Quad(Icons.Rounded.Clear, OceanTheme.colors.negative, "", OceanTheme.colors.negative)
    }

  OceanCard(
    modifier = modifier.testTag("transaction_item_${transaction.id}"),
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Box(
          modifier =
            Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(tint.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
          )
        }
        Spacer(modifier = Modifier.width(OceanSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = transaction.merchantOrPayee ?: transaction.category,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = accountName,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary,
            )
            Text(
              text = "• $dateStr",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "$amountPrefix$formattedAmount",
          style = OceanNumericStyle,
          color = amountColor,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          if (transaction.status != TransactionStatus.COMPLETED) {
            OceanStatusBadge(text = transaction.status.name, tone = BadgeTone.WARNING)
          }
          if (transaction.isAutomaticallyDetected) {
            OceanStatusBadge(text = "Auto-Saved", tone = BadgeTone.POSITIVE)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionBottomSheet(
  accounts: List<FinancialAccount>,
  transactionToEdit: FinancialTransaction? = null,
  initialTabIndex: Int = 0,
  onDismiss: () -> Unit,
  onSaveExpense: (String, String, String, String?, String?, Long, TransactionStatus) -> Unit,
  onSaveIncome: (String, String, String, String?, String?, Long, TransactionStatus) -> Unit,
  onSaveGeneral: (String, TransactionType, TransactionStatus, String, String, String?, String?, Long) -> Unit = { _, _, _, _, _, _, _, _ -> },
  onSaveTransfer: (String, String, String, String?, String?, Long) -> Unit,
  onUpdateExisting: (FinancialTransaction, String, TransactionType, TransactionStatus, String, String, String?, String?, Long) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val isEditing = transactionToEdit != null

  var selectedTab by remember {
    mutableIntStateOf(
      when (transactionToEdit?.type) {
        TransactionType.INCOME -> 1
        TransactionType.TRANSFER -> 2
        TransactionType.REFUND, TransactionType.REVERSAL, TransactionType.PENDING, TransactionType.FAILED -> 3
        TransactionType.EXPENSE -> 0
        null -> initialTabIndex
      }
    )
  }

  var specialType by remember {
    mutableStateOf(
      when (transactionToEdit?.type) {
        TransactionType.REFUND, TransactionType.REVERSAL, TransactionType.PENDING, TransactionType.FAILED -> transactionToEdit.type
        else -> TransactionType.REFUND
      }
    )
  }

  var selectedStatus by remember {
    mutableStateOf(transactionToEdit?.status ?: TransactionStatus.COMPLETED)
  }
  var selectedAccountId by remember {
    mutableStateOf(transactionToEdit?.accountId ?: accounts.firstOrNull { it.isActive }?.id ?: accounts.firstOrNull()?.id.orEmpty())
  }
  var destAccountId by remember {
    mutableStateOf(accounts.firstOrNull { it.id != selectedAccountId }?.id.orEmpty())
  }
  var amountText by remember {
    mutableStateOf(transactionToEdit?.money?.toBigDecimal()?.toPlainString().orEmpty())
  }
  var showAmountCalculator by remember { mutableStateOf(false) }
  var calculatorExpression by remember { mutableStateOf("") }
  var destAmountText by remember { mutableStateOf("") }
  var selectedCategory by remember {
    mutableStateOf(
      transactionToEdit?.category ?: if (selectedTab == 1) TransactionCategories.INCOME_CATEGORIES.first() else TransactionCategories.EXPENSE_CATEGORIES.first()
    )
  }
  var merchantText by remember { mutableStateOf(transactionToEdit?.merchantOrPayee.orEmpty()) }
  var notesText by remember { mutableStateOf(transactionToEdit?.notes.orEmpty()) }
  var selectedDateMillis by remember { mutableLongStateOf(transactionToEdit?.transactionDate ?: System.currentTimeMillis()) }

  val sourceAcc = accounts.firstOrNull { it.id == selectedAccountId }
  val destAcc = accounts.firstOrNull { it.id == destAccountId }
  val isCrossCurrencyTransfer =
    selectedTab == 2 && sourceAcc != null && destAcc != null && !sourceAcc.currencyCode.equals(destAcc.currencyCode, ignoreCase = true)

  val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = OceanSpacing.md, vertical = OceanSpacing.sm)
          .testTag("add_transaction_bottom_sheet"),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
    ) {
      Text(
        text = if (isEditing) "Edit Transaction" else "New Transaction",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )

      if (!isEditing) {
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = Color.Transparent,
          contentColor = OceanTheme.colors.gold,
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = {
              selectedTab = 0
              selectedCategory = TransactionCategories.EXPENSE_CATEGORIES.first()
            },
            text = { Text("Expense") },
            modifier = Modifier.testTag("tab_expense"),
          )
          Tab(
            selected = selectedTab == 1,
            onClick = {
              selectedTab = 1
              selectedCategory = TransactionCategories.INCOME_CATEGORIES.first()
            },
            text = { Text("Income") },
            modifier = Modifier.testTag("tab_income"),
          )
          Tab(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            text = { Text("Transfer") },
            modifier = Modifier.testTag("tab_transfer"),
          )
          Tab(
            selected = selectedTab == 3,
            onClick = {
              selectedTab = 3
              selectedCategory = "Refunds"
            },
            text = { Text("Other") },
            modifier = Modifier.testTag("tab_other"),
          )
        }
      }

      if (accounts.isEmpty()) {
        Text(
          text = "Please create at least one account before recording transactions.",
          style = MaterialTheme.typography.bodyMedium,
          color = OceanTheme.colors.warning,
          modifier = Modifier.padding(vertical = OceanSpacing.md),
        )
        return@Column
      }

      if (selectedTab == 3) {
        Text("Special Transaction Type", style = MaterialTheme.typography.labelMedium)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf(
            TransactionType.REFUND,
            TransactionType.REVERSAL,
            TransactionType.PENDING,
            TransactionType.FAILED,
          ).forEach { t ->
            FilterChip(
              selected = specialType == t,
              onClick = { specialType = t },
              label = { Text(t.name) },
            )
          }
        }
      }

      OutlinedTextField(
        value = amountText,
        onValueChange = { amountText = it },
        label = { Text("Amount (${sourceAcc?.currencyCode ?: ""}) *") },
        placeholder = { Text("0.00") },
        singleLine = true,
        trailingIcon = {
          IconButton(
            onClick = {
              calculatorExpression = amountText
              showAmountCalculator = true
            },
            modifier = Modifier.testTag("transaction_amount_calculator_button"),
          ) {
            Icon(Icons.Rounded.Calculate, contentDescription = "Open amount calculator")
          }
        },
        modifier = Modifier.fillMaxWidth().testTag("transaction_amount_input"),
      )

      if (showAmountCalculator) {
        com.example.feature.calculator.SmartCalculatorAmountDialog(
          initialExpression = calculatorExpression,
          decimalScale = sourceAcc?.let { Money.resolveFractionDigits(it.currencyCode) } ?: 2,
          onApply = { calculatedAmount ->
            amountText = calculatedAmount
            showAmountCalculator = false
          },
          onDismiss = { showAmountCalculator = false },
        )
      }

      Text("Account", style = MaterialTheme.typography.labelMedium)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        accounts.forEach { acc ->
          val isSelected = selectedAccountId == acc.id
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)),
            modifier = Modifier.clickable { selectedAccountId = acc.id },
          ) {
            Text(
              text = "${acc.name} (${acc.currencyCode})",
              style = MaterialTheme.typography.labelSmall,
              color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }

      if (selectedTab == 2 && !isEditing) {
        Text("Destination Account", style = MaterialTheme.typography.labelMedium)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          accounts.filter { it.id != selectedAccountId }.forEach { acc ->
            val isSelected = destAccountId == acc.id
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.secondary else Color.Gray.copy(alpha = 0.4f)),
              modifier = Modifier.clickable { destAccountId = acc.id },
            ) {
              Text(
                text = "${acc.name} (${acc.currencyCode})",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        }

        if (isCrossCurrencyTransfer && destAcc != null) {
          OutlinedTextField(
            value = destAmountText,
            onValueChange = { destAmountText = it },
            label = { Text("Converted Amount in ${destAcc.currencyCode} *") },
            placeholder = { Text("Exact credit amount in ${destAcc.currencyCode}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("transfer_converted_amount_input"),
          )
        }
      }

      if (selectedTab != 2) {
        val categories =
          when (selectedTab) {
            1 -> TransactionCategories.INCOME_CATEGORIES
            3 -> TransactionCategories.INCOME_CATEGORIES + TransactionCategories.EXPENSE_CATEGORIES
            else -> TransactionCategories.EXPENSE_CATEGORIES
          }

        Text("Category", style = MaterialTheme.typography.labelMedium)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          categories.forEach { cat ->
            val isSelected = selectedCategory == cat
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (isSelected) OceanTheme.colors.gold.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, if (isSelected) OceanTheme.colors.gold else Color.Gray.copy(alpha = 0.4f)),
              modifier = Modifier.clickable { selectedCategory = cat },
            ) {
              Text(
                text = cat,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) OceanTheme.colors.gold else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        }

        Text("Status", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(TransactionStatus.COMPLETED, TransactionStatus.PENDING, TransactionStatus.FAILED).forEach { st ->
            FilterChip(
              selected = selectedStatus == st,
              onClick = { selectedStatus = st },
              label = { Text(st.name) },
            )
          }
        }
      }

      if (selectedTab != 2) {
        OutlinedTextField(
          value = merchantText,
          onValueChange = { merchantText = it },
          label = { Text("Merchant / Payee (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("transaction_merchant_input"),
        )
      }

      // Date quick selector
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Date: ${dateFormat.format(Date(selectedDateMillis))}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(onClick = { selectedDateMillis = System.currentTimeMillis() }) { Text("Now") }
          TextButton(onClick = { selectedDateMillis = System.currentTimeMillis() - 86_400_000L }) { Text("Yesterday") }
        }
      }

      OutlinedTextField(
        value = notesText,
        onValueChange = { notesText = it },
        label = { Text("Notes (Optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("transaction_notes_input"),
      )

      Spacer(modifier = Modifier.height(OceanSpacing.xs))

      Button(
        onClick = {
          if (isEditing && transactionToEdit != null) {
            val editType =
              when (selectedTab) {
                1 -> TransactionType.INCOME
                3 -> specialType
                else -> TransactionType.EXPENSE
              }
            onUpdateExisting(
              transactionToEdit,
              selectedAccountId,
              editType,
              selectedStatus,
              amountText,
              selectedCategory,
              merchantText,
              notesText,
              selectedDateMillis,
            )
          } else {
            when (selectedTab) {
              0 -> onSaveExpense(selectedAccountId, amountText, selectedCategory, merchantText, notesText, selectedDateMillis, selectedStatus)
              1 -> onSaveIncome(selectedAccountId, amountText, selectedCategory, merchantText, notesText, selectedDateMillis, selectedStatus)
              2 -> onSaveTransfer(selectedAccountId, destAccountId, amountText, destAmountText.ifBlank { null }, notesText, selectedDateMillis)
              3 -> onSaveGeneral(selectedAccountId, specialType, selectedStatus, amountText, selectedCategory, merchantText, notesText, selectedDateMillis)
            }
          }
        },
        enabled =
          amountText.isNotBlank() &&
            selectedAccountId.isNotBlank() &&
            (!isCrossCurrencyTransfer || destAmountText.isNotBlank()),
        modifier = Modifier.fillMaxWidth().testTag("save_transaction_button"),
        colors = ButtonDefaults.buttonColors(containerColor = OceanTheme.colors.gold, contentColor = Color.Black),
      ) {
        Text(if (isEditing) "Update Transaction" else "Save Transaction", fontWeight = FontWeight.Bold)
      }

      Spacer(modifier = Modifier.height(OceanSpacing.lg))
    }
  }
}

@Composable
fun TransactionDetailsDialog(
  transaction: FinancialTransaction,
  accountName: String,
  onDismiss: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val formattedAmount = FormatMoneyUseCase().format(transaction.money)
  val dateFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a", Locale.getDefault()) }
  val dateStr = remember(transaction.transactionDate) { dateFormat.format(Date(transaction.transactionDate)) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Transaction Details")
        OceanStatusBadge(text = transaction.type.name, tone = BadgeTone.INFO)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
        DetailRow("Amount", formattedAmount, isHighlighted = true)
        DetailRow("Account", "$accountName (${transaction.currencyCode})")
        DetailRow("Category", transaction.category)
        transaction.merchantOrPayee?.let { DetailRow("Merchant/Payee", it) }
        DetailRow("Date", dateStr)
        DetailRow("Status", transaction.status.name)
        DetailRow("Transaction ID", transaction.id.take(12) + "…")
        transaction.linkedTransferId?.let { DetailRow("Transfer Link ID", it.take(12) + "…") }
        transaction.referenceId?.let { DetailRow("Reference ID", it) }
        transaction.sourceApplication?.let { DetailRow("Detected Source", it) }
        transaction.confidenceScore?.let { DetailRow("Confidence", "${(it * 100).toInt()}%") }
        transaction.notes?.let { DetailRow("Notes", it) }
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (transaction.type != TransactionType.TRANSFER) {
          OutlinedButton(onClick = onEdit, modifier = Modifier.testTag("edit_transaction_button")) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Edit")
          }
        }
        TextButton(onClick = onDismiss) { Text("Close") }
      }
    },
    dismissButton = {
      TextButton(onClick = onDelete, modifier = Modifier.testTag("delete_transaction_dialog_button")) {
        Text("Delete", color = OceanTheme.colors.negative)
      }
    },
  )
}

@Composable
private fun DetailRow(label: String, value: String, isHighlighted: Boolean = false) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = if (isHighlighted) OceanNumericStyle else MaterialTheme.typography.bodyMedium,
      color = if (isHighlighted) OceanTheme.colors.gold else MaterialTheme.colorScheme.onSurface,
      fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
    )
  }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
