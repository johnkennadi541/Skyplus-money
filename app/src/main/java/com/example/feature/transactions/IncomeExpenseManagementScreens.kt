package com.example.feature.transactions

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanSectionHeader
import com.example.core.designsystem.theme.OceanNumericStyle
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.Money
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.usecase.FormatMoneyUseCase
import java.util.Calendar

@Composable
fun IncomeManagementScreen(
  viewModel: TransactionsViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  DedicatedLedgerTypeScreen(
    title = "Income Management",
    subtitle = "Salary, business, freelance, interest, cashback & refunds",
    targetType = TransactionType.INCOME,
    addTabIndex = 1,
    viewModel = viewModel,
    onNavigateBack = onNavigateBack,
    testTag = "income_management_screen",
    modifier = modifier,
  )
}

@Composable
fun ExpenseManagementScreen(
  viewModel: TransactionsViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  DedicatedLedgerTypeScreen(
    title = "Expense Management",
    subtitle = "Food, shopping, transport, housing, utilities, bills & EMIs",
    targetType = TransactionType.EXPENSE,
    addTabIndex = 0,
    viewModel = viewModel,
    onNavigateBack = onNavigateBack,
    testTag = "expense_management_screen",
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DedicatedLedgerTypeScreen(
  title: String,
  subtitle: String,
  targetType: TransactionType,
  addTabIndex: Int,
  viewModel: TransactionsViewModel,
  onNavigateBack: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)
  val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
  val accounts by viewModel.accounts.collectAsStateWithLifecycle()

  var selectedPeriod by remember { mutableStateOf(TimePeriodFilter.ALL) }
  var selectedCategory by remember { mutableStateOf<String?>(null) }
  var isAddOpen by remember { mutableStateOf(false) }

  val periodFiltered =
    remember(allTransactions, selectedPeriod, targetType) {
      val now = System.currentTimeMillis()
      val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      val startToday = cal.timeInMillis
      val startWeek = Calendar.getInstance().apply {
        timeInMillis = startToday
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
      }.timeInMillis
      val startMonth = Calendar.getInstance().apply {
        timeInMillis = startToday
        set(Calendar.DAY_OF_MONTH, 1)
      }.timeInMillis

      allTransactions.filter { txn ->
        txn.type == targetType &&
          txn.status == TransactionStatus.COMPLETED &&
          when (selectedPeriod) {
            TimePeriodFilter.TODAY -> txn.transactionDate >= startToday
            TimePeriodFilter.THIS_WEEK -> txn.transactionDate >= startWeek
            TimePeriodFilter.THIS_MONTH -> txn.transactionDate >= startMonth
            else -> true
          }
      }
    }

  val displayed =
    remember(periodFiltered, selectedCategory) {
      if (selectedCategory == null) periodFiltered else periodFiltered.filter { it.category == selectedCategory }
    }

  // Group totals strictly by ISO 4217 currency (never mix currencies)
  val currencyTotals =
    remember(periodFiltered) {
      periodFiltered.groupBy { it.currencyCode }.mapValues { (curr, txns) ->
        Money.fromMinorUnits(txns.sumOf { it.amountMinor }, curr)
      }
    }

  // Group category totals strictly by (Category, Currency)
  val categoryBreakdown =
    remember(periodFiltered) {
      periodFiltered.groupBy { it.category to it.currencyCode }.map { (key, list) ->
        Triple(key.first, Money.fromMinorUnits(list.sumOf { it.amountMinor }, key.second), list.size)
      }.sortedByDescending { it.second.minorUnits }
    }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { isAddOpen = true },
        containerColor = OceanTheme.colors.gold,
        contentColor = Color.Black,
        modifier = Modifier.testTag("${testTag}_add_fab"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "Add $title")
      }
    },
    modifier = modifier.fillMaxSize(),
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(testTag),
        contentPadding = PaddingValues(OceanSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        item {
          OceanSectionHeader(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.widthIn(max = 760.dp),
          )
        }

        // Period Filter
        item {
          Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          ) {
            listOf(
              TimePeriodFilter.ALL to "All",
              TimePeriodFilter.TODAY to "Daily",
              TimePeriodFilter.THIS_WEEK to "Weekly",
              TimePeriodFilter.THIS_MONTH to "Monthly",
            ).forEach { (period, label) ->
              FilterChip(
                selected = selectedPeriod == period,
                onClick = { selectedPeriod = period },
                label = { Text(label) },
              )
            }
          }
        }

        // Totals per currency
        if (currencyTotals.isNotEmpty()) {
          item {
            Column(
              modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
              verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
            ) {
              currencyTotals.forEach { (curr, totalMoney) ->
                OceanCard {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text("Total ($curr)", style = MaterialTheme.typography.titleSmall)
                    Text(
                      text = FormatMoneyUseCase().format(totalMoney),
                      style = OceanNumericStyle,
                      color = if (targetType == TransactionType.INCOME) OceanTheme.colors.positive else OceanTheme.colors.gold,
                    )
                  }
                }
              }
            }
          }
        }

        // Category breakdown
        if (categoryBreakdown.isNotEmpty()) {
          item {
            OceanSectionHeader(
              title = "By Category",
              subtitle = "Tap a category to filter entries",
              modifier = Modifier.widthIn(max = 760.dp),
            )
          }
          items(categoryBreakdown, key = { "${it.first}_${it.second.currencyCode}" }) { (cat, money, count) ->
            OceanCard(
              modifier = Modifier.widthIn(max = 760.dp),
              onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text(cat, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                  Text("$count transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                  text = FormatMoneyUseCase().format(money),
                  style = OceanNumericStyle,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }
          }
        }

        item {
          OceanSectionHeader(
            title = if (selectedCategory != null) "Category: $selectedCategory" else "Entries (${displayed.size})",
            modifier = Modifier.widthIn(max = 760.dp),
          )
        }

        if (displayed.isEmpty()) {
          item {
            OceanCard(modifier = Modifier.widthIn(max = 760.dp)) {
              Text(
                text = "No completed entries for this period yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          items(displayed, key = { it.id }) { txn ->
            val accName = accounts.firstOrNull { it.id == txn.accountId }?.name ?: "Account"
            TransactionListItem(
              transaction = txn,
              accountName = accName,
              onClick = {},
              modifier = Modifier.widthIn(max = 760.dp),
            )
          }
        }
      }
    }
  }

  if (isAddOpen) {
    AddEditTransactionBottomSheet(
      accounts = accounts,
      initialTabIndex = addTabIndex,
      onDismiss = { isAddOpen = false },
      onSaveExpense = { accId, amt, cat, merch, notes, date, status ->
        viewModel.recordExpense(accId, amt, cat, merch, notes, date, status)
        isAddOpen = false
      },
      onSaveIncome = { accId, amt, cat, merch, notes, date, status ->
        viewModel.recordIncome(accId, amt, cat, notes, merch, date, status)
        isAddOpen = false
      },
      onSaveGeneral = { accId, type, status, amt, cat, merch, notes, date ->
        viewModel.recordTransaction(accId, type, status, amt, cat, merch, notes, date)
        isAddOpen = false
      },
      onSaveTransfer = { srcId, dstId, amt, dstAmt, notes, date ->
        viewModel.recordTransfer(srcId, dstId, amt, dstAmt, notes, date)
        isAddOpen = false
      },
    )
  }
}
