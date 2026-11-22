package com.example.feature.budgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanSectionHeader
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.Budget
import com.example.domain.model.BudgetAlertStatus
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.BudgetStatus
import com.example.domain.model.BudgetSummary
import com.example.domain.model.FinancialAccount
import com.example.domain.model.TransactionCategories
import com.example.domain.usecase.FormatMoneyUseCase
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

@Composable
fun BudgetsScreen(
  viewModel: BudgetsViewModel,
  modifier: Modifier = Modifier,
) {
  val budgets by viewModel.budgets.collectAsStateWithLifecycle()
  val summaries by viewModel.summariesByCurrency.collectAsStateWithLifecycle()
  val accounts by viewModel.accounts.collectAsStateWithLifecycle()
  val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
  val isErrorMessage by viewModel.isErrorMessage.collectAsStateWithLifecycle()
  val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
  val budgetPeriods by viewModel.budgetPeriods.collectAsStateWithLifecycle()

  BudgetsContent(
    budgets = budgets,
    summaries = summaries,
    periods = budgetPeriods,
    accounts = accounts,
    userMessage = userMessage,
    isErrorMessage = isErrorMessage,
    selectedCurrency = selectedCurrency,
    onSelectCurrency = { viewModel.selectCurrency(it) },
    onDismissMessage = { viewModel.dismissMessage() },
    onCreateBudget = { name, category, amountDecimal, currency, periodType, customStart, customEnd, threshold, onSuccess ->
      viewModel.createBudget(name, category, amountDecimal, currency, periodType, customStart, customEnd, threshold, onSuccess)
    },
    onUpdateBudget = { budget, onSuccess ->
      viewModel.updateBudget(budget, onSuccess)
    },
    onDeleteBudget = { budgetId, onSuccess ->
      viewModel.deleteBudget(budgetId, onSuccess)
    },
    modifier = modifier,
  )
}

@Composable
fun BudgetsContent(
  budgets: List<BudgetStatus>,
  summaries: Map<String, BudgetSummary>,
  periods: List<com.example.domain.model.BudgetPeriod> = emptyList(),
  accounts: List<FinancialAccount>,
  userMessage: String? = null,
  isErrorMessage: Boolean = false,
  selectedCurrency: String? = null,
  onSelectCurrency: (String?) -> Unit = {},
  onDismissMessage: () -> Unit = {},
  onCreateBudget: (String, String, String, String, BudgetPeriodType, Long?, Long?, Int, () -> Unit) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
  onUpdateBudget: (Budget, () -> Unit) -> Unit = { _, _ -> },
  onDeleteBudget: (String, () -> Unit) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier,
) {

  var showCreateDialog by remember { mutableStateOf(false) }
  var budgetToEdit by remember { mutableStateOf<Budget?>(null) }
  var budgetToDelete by remember { mutableStateOf<Budget?>(null) }

  val defaultCurrency =
    accounts.firstOrNull()?.currencyCode ?: "USD"
  val availableCurrencies =
    remember(budgets, accounts) {
      val fromBudgets = budgets.map { it.budget.currencyCode }
      val fromAccounts = accounts.map { it.currencyCode }
      (fromBudgets + fromAccounts).distinct().ifEmpty { listOf("USD") }
    }

  val activeCurrency = selectedCurrency ?: availableCurrencies.firstOrNull() ?: defaultCurrency

  val filteredBudgets =
    remember(budgets, activeCurrency) {
      budgets.filter { it.budget.currencyCode.equals(activeCurrency, ignoreCase = true) }
    }

  val activeSummary = summaries[activeCurrency]

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = OceanSpacing.md, vertical = OceanSpacing.md)
        .testTag("budgets_screen"),
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Header Section
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OceanSectionHeader(
            title = stringResource(R.string.budgets_title),
            subtitle = "Active spending envelopes & threshold monitoring",
          )
          Button(
            onClick = { showCreateDialog = true },
            modifier = Modifier.testTag("create_budget_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Budget")
          }
        }
      }

      // User Message Banner
      if (userMessage != null) {
        item {
          Surface(
            modifier = Modifier.fillMaxWidth().testTag("budget_user_message"),
            shape = MaterialTheme.shapes.small,
            color = if (isErrorMessage) OceanTheme.colors.negativeContainer else MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, if (isErrorMessage) OceanTheme.colors.negative else MaterialTheme.colorScheme.primary),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(OceanSpacing.sm),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                  imageVector = if (isErrorMessage) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = if (isErrorMessage) OceanTheme.colors.negative else MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(OceanSpacing.xs))
                Text(
                  text = userMessage ?: "",
                  style = MaterialTheme.typography.bodyMedium,
                  color = if (isErrorMessage) OceanTheme.colors.negative else MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
              TextButton(onClick = onDismissMessage) {
                Text("Dismiss")
              }
            }
          }
        }
      }

      // Currency Selection Tabs (Multi-currency separation)
      if (availableCurrencies.size > 1) {
        item {
          LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          ) {
            items(availableCurrencies) { curr ->
              FilterChip(
                selected = curr == activeCurrency,
                onClick = { onSelectCurrency(curr) },
                label = { Text(curr) },
                modifier = Modifier.testTag("currency_chip_$curr"),
              )
            }
          }
        }
      }

      // Overall Summary Card for Active Currency
      if (activeSummary != null && activeSummary.activeBudgetCount > 0) {
        item {
          OceanCard(modifier = Modifier.testTag("budget_summary_card")) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "Allocated (${activeSummary.currencyCode})",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = FormatMoneyUseCase().format(
                    com.example.domain.model.Money.fromMinorUnits(
                      activeSummary.totalAllocatedMinor,
                      activeSummary.currencyCode,
                    )
                  ),
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "Spent / Remaining",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = "${FormatMoneyUseCase().format(com.example.domain.model.Money.fromMinorUnits(activeSummary.totalSpentMinor, activeSummary.currencyCode))} / ${FormatMoneyUseCase().format(com.example.domain.model.Money.fromMinorUnits(activeSummary.totalRemainingMinor, activeSummary.currencyCode))}",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = if (activeSummary.totalRemainingMinor < 0L) OceanTheme.colors.negative else OceanTheme.colors.positive,
                )
              }
            }

            Spacer(modifier = Modifier.height(OceanSpacing.sm))
            val summaryProgress = (activeSummary.averageUtilizationPercent / 100.0).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
              progress = { summaryProgress },
              modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
              color = if (activeSummary.averageUtilizationPercent >= 100.0) OceanTheme.colors.negative
              else if (activeSummary.averageUtilizationPercent >= 75.0) OceanTheme.colors.warning
              else OceanTheme.colors.positive,
              trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.height(OceanSpacing.xs))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "${"%.1f".format(activeSummary.averageUtilizationPercent)}% utilized across ${activeSummary.activeBudgetCount} budgets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (activeSummary.overspentBudgetCount > 0) {
                OceanStatusBadge(
                  text = "${activeSummary.overspentBudgetCount} Overspent",
                  tone = BadgeTone.NEGATIVE,
                )
              }
            }
          }
        }
      }

      // Empty State
      if (filteredBudgets.isEmpty()) {
        item {
          OceanCard(modifier = Modifier.testTag("budgets_empty_state")) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(OceanSpacing.lg),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = Icons.Rounded.PieChart,
                contentDescription = null,
                tint = OceanTheme.colors.gold,
                modifier = Modifier.size(48.dp),
              )
              Spacer(modifier = Modifier.height(OceanSpacing.sm))
              Text(
                text = "No Budgets for $activeCurrency",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Spacer(modifier = Modifier.height(OceanSpacing.xs))
              Text(
                text = "Create category-wise budget envelopes to track live spending against the financial ledger.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(OceanSpacing.md))
              Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("create_first_budget_button"),
              ) {
                Text("Create First Budget")
              }
            }
          }
        }
      } else {
        items(filteredBudgets, key = { it.budget.id }) { status ->
          BudgetCard(
            status = status,
            onEdit = { budgetToEdit = status.budget },
            onDelete = { budgetToDelete = status.budget },
          )
        }
      }

      val currencyPeriods =
        periods.filter { it.currencyCode.equals(activeCurrency, ignoreCase = true) }
      if (currencyPeriods.isNotEmpty()) {
        item {
          OceanSectionHeader(
            title = stringResource(R.string.budgets_history_title),
            subtitle = stringResource(R.string.budgets_history_subtitle),
            modifier = Modifier.testTag("budget_history_section"),
          )
        }
        items(currencyPeriods, key = { it.id }) { period ->
          BudgetPeriodRow(period = period)
        }
      }
    }
  }

  // Create Budget Dialog
  if (showCreateDialog) {
    BudgetFormDialog(
      title = "Create New Budget",
      initialCurrency = activeCurrency,
      availableCurrencies = availableCurrencies,
      onDismiss = { showCreateDialog = false },
      onSave = { name, category, amountDecimal, currency, periodType, customStart, customEnd, threshold ->
        onCreateBudget(
          name,
          category,
          amountDecimal,
          currency,
          periodType,
          customStart,
          customEnd,
          threshold,
        ) { showCreateDialog = false }
      },
    )
  }

  // Edit Budget Dialog
  budgetToEdit?.let { budget ->
    val initialAmount =
      try {
        com.example.domain.model.Money.fromMinorUnits(budget.amountMinor, budget.currencyCode).toBigDecimal().toPlainString()
      } catch (e: Exception) {
        "0.00"
      }

    BudgetFormDialog(
      title = "Edit Budget",
      initialName = budget.name,
      initialCategory = budget.category,
      initialAmount = initialAmount,
      initialCurrency = budget.currencyCode,
      initialPeriodType = budget.periodType,
      initialThreshold = budget.warningThresholdPercent,
      availableCurrencies = availableCurrencies,
      onDismiss = { budgetToEdit = null },
      onSave = { name, category, amountDecimal, currency, periodType, customStart, customEnd, threshold ->
        val minorUnits =
          try {
            val bd = java.math.BigDecimal(amountDecimal.trim())
            com.example.domain.model.Money.fromBigDecimal(bd, currency).minorUnits
          } catch (e: Exception) {
            budget.amountMinor
          }
        onUpdateBudget(
          budget.copy(
            name = name,
            category = category,
            amountMinor = minorUnits,
            currencyCode = currency,
            periodType = periodType,
            warningThresholdPercent = threshold,
            startDate = customStart ?: budget.startDate,
            endDate = customEnd ?: budget.endDate,
          ),
        ) { budgetToEdit = null }
      },
    )
  }

  // Delete Confirmation Dialog
  budgetToDelete?.let { budget ->
    AlertDialog(
      onDismissRequest = { budgetToDelete = null },
      title = { Text("Delete Budget") },
      text = {
        Text("Are you sure you want to delete '${budget.name}'? Recorded financial transactions will remain safe in your ledger.")
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteBudget(budget.id) {
              budgetToDelete = null
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("confirm_delete_budget_button"),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { budgetToDelete = null }) {
          Text("Cancel")
        }
      },
      modifier = Modifier.testTag("delete_budget_dialog"),
    )
  }
}

@Composable
private fun BudgetCard(
  status: BudgetStatus,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val budget = status.budget
  val progress = (status.percentageConsumed / 100.0).toFloat().coerceIn(0f, 1f)

  val (tone, statusLabel) =
    when (status.alertStatus) {
      BudgetAlertStatus.NORMAL -> BadgeTone.POSITIVE to "On Track"
      BudgetAlertStatus.APPROACHING_75 -> BadgeTone.WARNING to "75% Reached"
      BudgetAlertStatus.WARNING_90 -> BadgeTone.WARNING to "90% Warning"
      BudgetAlertStatus.EXCEEDED_100 -> BadgeTone.NEGATIVE to "Overspent"
      BudgetAlertStatus.PERIOD_ENDED -> BadgeTone.INFO to "Period ended"
    }

  val progressColor =
    when (status.alertStatus) {
      BudgetAlertStatus.NORMAL -> OceanTheme.colors.positive
      BudgetAlertStatus.APPROACHING_75, BudgetAlertStatus.WARNING_90 -> OceanTheme.colors.warning
      BudgetAlertStatus.EXCEEDED_100 -> OceanTheme.colors.negative
      BudgetAlertStatus.PERIOD_ENDED -> MaterialTheme.colorScheme.outline
    }

  val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
  val dateRangeStr =
    "${dateFormat.format(Date(budget.startDate))} – ${dateFormat.format(Date(budget.endDate))}"

  OceanCard(
    modifier = modifier.fillMaxWidth().testTag("budget_card_${budget.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = budget.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.width(OceanSpacing.xs))
          OceanStatusBadge(text = statusLabel, tone = tone)
        }
        Text(
          text = "${budget.category} • $dateRangeStr",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status.isStale) {
          Text(
            text = stringResource(R.string.budgets_period_ended_note),
            style = MaterialTheme.typography.labelSmall,
            color = OceanTheme.colors.warning,
            modifier = Modifier.testTag("budget_stale_note_${budget.id}"),
          )
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onEdit,
          modifier = Modifier.testTag("edit_budget_${budget.id}"),
        ) {
          Icon(Icons.Rounded.Edit, contentDescription = "Edit budget", tint = MaterialTheme.colorScheme.secondary)
        }
        IconButton(
          onClick = onDelete,
          modifier = Modifier.testTag("delete_budget_${budget.id}"),
        ) {
          Icon(Icons.Rounded.Delete, contentDescription = "Delete budget", tint = MaterialTheme.colorScheme.error)
        }
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.sm))

    // Progress Bar
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
      color = progressColor,
      trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )

    Spacer(modifier = Modifier.height(OceanSpacing.xs))

    // Amounts breakdown
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(
          text = "Spent: ${FormatMoneyUseCase().format(status.spentMoney)} (${"%.1f".format(status.percentageConsumed)}%)",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = "${status.transactionCount} transactions",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        // A closed period's remaining amount is history, not this month's available budget, so it
        // is relabelled and muted instead of being shown as if it were live.
        Text(
          text =
            if (status.isStale) {
              stringResource(
                R.string.budgets_remaining_at_close,
                FormatMoneyUseCase().format(status.remainingMoney),
              )
            } else {
              "Remaining: ${FormatMoneyUseCase().format(status.remainingMoney)}"
            },
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Bold,
          color =
            when {
              status.isStale -> MaterialTheme.colorScheme.onSurfaceVariant
              status.remainingMoney.minorUnits < 0L -> OceanTheme.colors.negative
              else -> MaterialTheme.colorScheme.onSurface
            },
          modifier = Modifier.testTag("budget_remaining_${budget.id}"),
        )
        Text(
          text = "Limit: ${FormatMoneyUseCase().format(status.allocatedMoney)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetFormDialog(
  title: String,
  initialName: String = "",
  initialCategory: String = TransactionCategories.EXPENSE_CATEGORIES.first(),
  initialAmount: String = "",
  initialCurrency: String = "USD",
  initialPeriodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
  initialThreshold: Int = 75,
  availableCurrencies: List<String> = listOf("USD"),
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    category: String,
    amountDecimal: String,
    currency: String,
    periodType: BudgetPeriodType,
    customStart: Long?,
    customEnd: Long?,
    threshold: Int,
  ) -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  var category by remember { mutableStateOf(initialCategory) }
  var amountDecimal by remember { mutableStateOf(initialAmount) }
  var currency by remember { mutableStateOf(initialCurrency) }
  var periodType by remember { mutableStateOf(initialPeriodType) }
  var threshold by remember { mutableIntStateOf(initialThreshold) }
  var categoryExpanded by remember { mutableStateOf(false) }
  var currencyExpanded by remember { mutableStateOf(false) }
  var formError by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            formError = null
          },
          label = { Text("Budget Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("budget_name_input"),
        )

        // Category Dropdown
        ExposedDropdownMenuBox(
          expanded = categoryExpanded,
          onExpandedChange = { categoryExpanded = it },
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedTextField(
            value = category,
            onValueChange = {},
            readOnly = true,
            label = { Text("Expense Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag("budget_category_select"),
          )
          ExposedDropdownMenu(
            expanded = categoryExpanded,
            onDismissRequest = { categoryExpanded = false },
          ) {
            TransactionCategories.EXPENSE_CATEGORIES.forEach { cat ->
              DropdownMenuItem(
                text = { Text(cat) },
                onClick = {
                  category = cat
                  categoryExpanded = false
                },
              )
            }
          }
        }

        // Amount and Currency Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          OutlinedTextField(
            value = amountDecimal,
            onValueChange = {
              amountDecimal = it
              formError = null
            },
            label = { Text("Amount") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("budget_amount_input"),
          )

          ExposedDropdownMenuBox(
            expanded = currencyExpanded,
            onExpandedChange = { currencyExpanded = it },
            modifier = Modifier.width(110.dp),
          ) {
            OutlinedTextField(
              value = currency,
              onValueChange = {},
              readOnly = true,
              label = { Text("Currency") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag("budget_currency_select"),
            )
            ExposedDropdownMenu(
              expanded = currencyExpanded,
              onDismissRequest = { currencyExpanded = false },
            ) {
              val currencies = (availableCurrencies + listOf("USD", "INR", "EUR", "GBP", "KWD")).distinct()
              currencies.forEach { curr ->
                DropdownMenuItem(
                  text = { Text(curr) },
                  onClick = {
                    currency = curr
                    currencyExpanded = false
                  },
                )
              }
            }
          }
        }

        // Warning Threshold Selector
        Column {
          Text("Alert Threshold", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Row(horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
            listOf(75, 90, 100).forEach { t ->
              FilterChip(
                selected = threshold == t,
                onClick = { threshold = t },
                label = { Text("$t%") },
                modifier = Modifier.testTag("threshold_chip_$t"),
              )
            }
          }
        }

        if (formError != null) {
          Text(
            text = formError ?: "",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isBlank()) {
            formError = "Please enter a budget name"
            return@Button
          }
          val amountParsed = amountDecimal.toDoubleOrNull()
          if (amountParsed == null || amountParsed <= 0.0) {
            formError = "Please enter a valid positive amount"
            return@Button
          }
          onSave(name, category, amountDecimal, currency, periodType, null, null, threshold)
        },
        modifier = Modifier.testTag("save_budget_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
    modifier = Modifier.testTag("budget_form_dialog"),
  )
}

@Composable
private fun BudgetPeriodRow(period: com.example.domain.model.BudgetPeriod) {
  val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
  val range =
    "${dateFormat.format(Date(period.periodStart))} – ${dateFormat.format(Date(period.periodEnd))}"
  val currency = remember(period.currencyCode) { Currency.getInstance(period.currencyCode) }
  val numberFormat =
    remember(currency) {
      NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        this.currency = currency
        minimumFractionDigits = 2
        maximumFractionDigits = 2
      }
    }
  val spentText = numberFormat.format(BigDecimal.valueOf(period.spentMinor, 2))
  val allocatedText = numberFormat.format(BigDecimal.valueOf(period.allocatedMinor, 2))

  Surface(
    modifier = Modifier.fillMaxWidth().testTag("budget_period_${period.id}"),
    shape = MaterialTheme.shapes.small,
    color = OceanTheme.colors.cardSubtle,
    border = BorderStroke(1.dp, OceanTheme.colors.cardBorder.copy(alpha = 0.6f)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(OceanSpacing.sm),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "${period.category} • $range",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(R.string.budgets_history_spent, spentText, allocatedText),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
