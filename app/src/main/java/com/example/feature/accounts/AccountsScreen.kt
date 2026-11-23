package com.example.feature.accounts

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.domain.model.AccountType
import com.example.domain.model.FinancialAccount
import com.example.domain.model.Money
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.feature.calculator.SmartCalculatorAmountDialog

@Composable
fun AccountsScreen(
  viewModel: AccountsViewModel,
  onOpenAccountDetail: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val accounts by viewModel.accounts.collectAsStateWithLifecycle()
  val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
  AccountsContent(
    accounts = accounts,
    userMessage = userMessage,
    onDismissMessage = viewModel::dismissMessage,
    onCreateAccount = viewModel::createAccount,
    onUpdateAccount = viewModel::updateAccount,
    onToggleActive = viewModel::toggleAccountActive,
    onDeleteAccount = viewModel::deleteAccount,
    onOpenAccountDetail = onOpenAccountDetail,
    modifier = modifier,
  )
}

@Composable
fun AccountsContent(
  accounts: List<FinancialAccount>,
  userMessage: String? = null,
  onDismissMessage: () -> Unit = {},
  onCreateAccount: (
    name: String,
    type: AccountType,
    currencyCode: String,
    initialBalanceDecimal: String,
    institutionName: String?,
    accountNumberLast4: String?,
    colorHex: String,
  ) -> Unit = { _, _, _, _, _, _, _ -> },
  onUpdateAccount: (FinancialAccount) -> Unit = {},
  onToggleActive: (FinancialAccount) -> Unit = {},
  onDeleteAccount: (FinancialAccount, Boolean) -> Unit = { _, _ -> },
  onOpenAccountDetail: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  var isAddDialogOpen by remember { mutableStateOf(false) }
  var accountToEdit by remember { mutableStateOf<FinancialAccount?>(null) }
  var accountToDelete by remember { mutableStateOf<FinancialAccount?>(null) }

  LaunchedEffect(userMessage) {
    userMessage?.let {
      snackbarHostState.showSnackbar(it)
      onDismissMessage()
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("accounts_screen"),
      contentPadding = PaddingValues(OceanSpacing.md),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        OceanSectionHeader(
          title = "Accounts & Wallets",
          subtitle = "Multi-currency financial accounts with SQLCipher encryption",
          modifier = Modifier.widthIn(max = 760.dp),
        )
      }

      // Active Currency summaries (never mixes currencies)
      item {
        val activeAccounts = accounts.filter { it.isActive }
        val groupedByCurrency = activeAccounts.groupBy { it.currencyCode }
        if (groupedByCurrency.isNotEmpty()) {
          Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          ) {
            groupedByCurrency.forEach { (curr, accList) ->
              val totalMinor = accList.sumOf { it.currentBalanceMinor }
              val formattedTotal = FormatMoneyUseCase().format(Money.fromMinorUnits(totalMinor, curr))
              OceanCard {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column {
                    Text(
                      text = "$curr Total Balance",
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                      text = "${accList.size} active ${if (accList.size == 1) "account" else "accounts"}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Text(
                    text = formattedTotal,
                    style = OceanNumericStyle,
                    color = OceanTheme.colors.gold,
                  )
                }
              }
            }
          }
        }
      }

      if (accounts.isEmpty()) {
        item {
          OceanCard(modifier = Modifier.widthIn(max = 760.dp).testTag("accounts_empty_card")) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(OceanSpacing.lg),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = Icons.Rounded.AccountBalance,
                contentDescription = null,
                tint = OceanTheme.colors.gold,
                modifier = Modifier.size(48.dp),
              )
              Spacer(modifier = Modifier.height(OceanSpacing.sm))
              Text(
                text = "No Accounts Added",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(OceanSpacing.xs))
              Text(
                text = "Tap the + button below to create your bank, cash, savings, wallet, or credit card account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(accounts, key = { it.id }) { account ->
          AccountListItem(
            account = account,
            onEdit = { accountToEdit = account },
            onToggleActive = { onToggleActive(account) },
            onDelete = { accountToDelete = account },
            onClick = { onOpenAccountDetail(account.id) },
            modifier = Modifier.widthIn(max = 760.dp),
          )
        }
      }
    }

    FloatingActionButton(
      onClick = { isAddDialogOpen = true },
      modifier =
        Modifier
          .align(Alignment.BottomEnd)
          .padding(OceanSpacing.lg)
          .testTag("add_account_fab"),
      containerColor = OceanTheme.colors.gold,
      contentColor = Color.Black,
    ) {
      Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add Account")
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter).padding(OceanSpacing.md),
    )
  }

  if (isAddDialogOpen) {
    AddEditAccountDialog(
      accountToEdit = null,
      onDismiss = { isAddDialogOpen = false },
      onSave = { name, type, curr, balance, inst, last4, color, _ ->
        onCreateAccount(name, type, curr, balance, inst, last4, color)
        isAddDialogOpen = false
      },
    )
  }

  accountToEdit?.let { account ->
    AddEditAccountDialog(
      accountToEdit = account,
      onDismiss = { accountToEdit = null },
      onSave = { name, type, curr, _, inst, last4, color, isActive ->
        onUpdateAccount(
          account.copy(
            name = name,
            type = type,
            currencyCode = curr,
            institutionName = inst,
            accountNumberLast4 = last4,
            colorHex = color,
            isActive = isActive,
          )
        )
        accountToEdit = null
      },
    )
  }

  accountToDelete?.let { account ->
    SafeDeleteAccountDialog(
      account = account,
      onDismiss = { accountToDelete = null },
      onDeactivate = {
        onToggleActive(account)
        accountToDelete = null
      },
      onSafeDelete = { forceCascade ->
        onDeleteAccount(account, forceCascade)
        accountToDelete = null
      },
    )
  }
}

@Composable
fun SafeDeleteAccountDialog(
  account: FinancialAccount,
  onDismiss: () -> Unit,
  onDeactivate: () -> Unit,
  onSafeDelete: (forceCascade: Boolean) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Delete Account") },
    text = {
      Text(
        "Remove '${account.name}'? If this account has recorded transactions, we recommend deactivating it instead to preserve ledger history, or you can permanently remove the account and its transactions."
      )
    },
    confirmButton = {
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
          onClick = { onSafeDelete(false) },
          colors = ButtonDefaults.buttonColors(containerColor = OceanTheme.colors.negative),
          modifier = Modifier.testTag("confirm_delete_account_button"),
        ) {
          Text("Delete Safely")
        }
        if (account.isActive) {
          OutlinedButton(onClick = onDeactivate) {
            Text("Deactivate Instead")
          }
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun AccountListItem(
  account: FinancialAccount,
  onEdit: () -> Unit,
  onToggleActive: () -> Unit,
  onDelete: () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var menuExpanded by remember { mutableStateOf(false) }
  val formattedBalance = FormatMoneyUseCase().format(account.currentBalance)
  val typeIcon = getAccountTypeIcon(account.type)

  OceanCard(
    modifier = modifier.testTag("account_item_${account.id}"),
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
              .size(44.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(parseHexColor(account.colorHex)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = typeIcon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
          )
        }
        Spacer(modifier = Modifier.width(OceanSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = account.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OceanStatusBadge(
              text = account.type.name.replace("_", " "),
              tone = if (account.isActive) BadgeTone.INFO else BadgeTone.WARNING,
            )
            if (!account.isActive) {
              OceanStatusBadge(text = "INACTIVE", tone = BadgeTone.WARNING)
            }
            account.accountNumberLast4?.let {
              Text(
                text = "••$it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = formattedBalance,
            style = OceanNumericStyle,
            color = if (account.currentBalanceMinor >= 0) OceanTheme.colors.positive else OceanTheme.colors.negative,
          )
          Text(
            text = account.currencyCode,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }

        Box {
          IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.testTag("account_menu_${account.id}"),
          ) {
            Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "Options")
          }
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
          ) {
            DropdownMenuItem(
              text = { Text("Edit") },
              leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onEdit()
              },
            )
            DropdownMenuItem(
              text = { Text(if (account.isActive) "Deactivate" else "Activate") },
              onClick = {
                menuExpanded = false
                onToggleActive()
              },
            )
            DropdownMenuItem(
              text = { Text("Delete", color = OceanTheme.colors.negative) },
              leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = OceanTheme.colors.negative) },
              onClick = {
                menuExpanded = false
                onDelete()
              },
            )
          }
        }
      }
    }
  }
}

@Composable
fun AddEditAccountDialog(
  accountToEdit: FinancialAccount?,
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    type: AccountType,
    currencyCode: String,
    initialBalance: String,
    institution: String?,
    last4: String?,
    colorHex: String,
    isActive: Boolean,
  ) -> Unit,
) {
  var name by remember { mutableStateOf(accountToEdit?.name.orEmpty()) }
  var selectedType by remember { mutableStateOf(accountToEdit?.type ?: AccountType.BANK) }
  var currencyCode by remember { mutableStateOf(accountToEdit?.currencyCode ?: "INR") }
  var balanceStr by remember {
    mutableStateOf(accountToEdit?.initialBalance?.toBigDecimal()?.toPlainString().orEmpty())
  }
  var showBalanceCalculator by remember { mutableStateOf(false) }
  var institutionName by remember { mutableStateOf(accountToEdit?.institutionName.orEmpty()) }
  var last4 by remember { mutableStateOf(accountToEdit?.accountNumberLast4.orEmpty()) }
  var selectedColor by remember { mutableStateOf(accountToEdit?.colorHex ?: "#0A2540") }
  var isActive by remember { mutableStateOf(accountToEdit?.isActive ?: true) }

  val currencies = listOf("KWD", "INR", "USD", "EUR", "AED", "SAR", "GBP")
  val colorPalette = listOf("#0A2540", "#00B4D8", "#D4AF37", "#2E7D32", "#6A1B9A", "#D32F2F")

  val isValidIsoCurrency =
    remember(currencyCode) {
      val trimmed = currencyCode.trim().uppercase()
      trimmed.length == 3 && runCatching { java.util.Currency.getInstance(trimmed) }.isSuccess
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (accountToEdit == null) "Add Account" else "Edit Account") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Account Name *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("account_name_input"),
        )

        // All 5 Account Types
        Text("Account Type", style = MaterialTheme.typography.labelMedium)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          AccountType.entries.forEach { type ->
            val isSelected = selectedType == type
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)),
              modifier = Modifier.clickable { selectedType = type }.testTag("account_type_${type.name.lowercase()}"),
            ) {
              Text(
                text = type.name.replace("_", " "),
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
              )
            }
          }
        }

        // ISO 4217 Currency selection (Quick picks + custom ISO code input)
        Text("Currency (ISO 4217)", style = MaterialTheme.typography.labelMedium)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          currencies.forEach { curr ->
            val isSelected = currencyCode.equals(curr, ignoreCase = true)
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (isSelected) OceanTheme.colors.gold.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, if (isSelected) OceanTheme.colors.gold else Color.Gray.copy(alpha = 0.4f)),
              modifier = Modifier.clickable { currencyCode = curr }.testTag("currency_chip_$curr"),
            ) {
              Text(
                text = curr,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) OceanTheme.colors.gold else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
              )
            }
          }
        }
        if (accountToEdit == null) {
          OutlinedTextField(
            value = currencyCode,
            onValueChange = { if (it.length <= 3) currencyCode = it.uppercase() },
            label = { Text("ISO 4217 Code (e.g. KWD, INR, USD)") },
            isError = !isValidIsoCurrency,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("account_currency_input"),
          )
        }

        if (accountToEdit == null) {
          OutlinedTextField(
            value = balanceStr,
            onValueChange = { balanceStr = it },
            label = { Text("Opening Balance") },
            placeholder = { Text("0.00") },
            singleLine = true,
            trailingIcon = {
              IconButton(
                onClick = { showBalanceCalculator = true },
                modifier = Modifier.testTag("account_balance_calculator_button"),
              ) {
                Icon(Icons.Rounded.Calculate, contentDescription = "Calculate opening balance")
              }
            },
            modifier = Modifier.fillMaxWidth().testTag("account_balance_input"),
          )
          if (showBalanceCalculator) {
            SmartCalculatorAmountDialog(
              initialExpression = balanceStr,
              decimalScale = Money.resolveFractionDigits(currencyCode),
              onApply = { calculatedAmount ->
                balanceStr = calculatedAmount
                showBalanceCalculator = false
              },
              onDismiss = { showBalanceCalculator = false },
            )
          }
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Account Active", style = MaterialTheme.typography.bodyMedium)
            Switch(
              checked = isActive,
              onCheckedChange = { isActive = it },
              modifier = Modifier.testTag("account_active_switch"),
            )
          }
        }

        OutlinedTextField(
          value = institutionName,
          onValueChange = { institutionName = it },
          label = { Text("Bank / Institution (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("account_institution_input"),
        )

        OutlinedTextField(
          value = last4,
          onValueChange = { if (it.length <= 4) last4 = it },
          label = { Text("Last 4 Digits (For smart detection)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("account_last4_input"),
        )

        Text("Badge Color", style = MaterialTheme.typography.labelMedium)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          colorPalette.forEach { hex ->
            Box(
              modifier =
                Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(parseHexColor(hex))
                  .clickable { selectedColor = hex },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isNotBlank() && isValidIsoCurrency) {
            onSave(name, selectedType, currencyCode.trim().uppercase(), balanceStr, institutionName, last4, selectedColor, isActive)
          }
        },
        enabled = name.isNotBlank() && isValidIsoCurrency,
        modifier = Modifier.testTag("save_account_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

fun getAccountTypeIcon(type: AccountType): ImageVector =
  when (type) {
    AccountType.BANK -> Icons.Rounded.AccountBalance
    AccountType.CASH -> Icons.Rounded.Payments
    AccountType.SAVINGS -> Icons.Rounded.Savings
    AccountType.WALLET -> Icons.Rounded.Wallet
    AccountType.CREDIT_CARD -> Icons.Rounded.CreditCard
  }

fun parseHexColor(hex: String): Color =
  runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF0A2540))
