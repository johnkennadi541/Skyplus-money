package com.example.feature.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.domain.model.FinancialTransaction
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.feature.transactions.TransactionListItem
import com.example.feature.transactions.TransactionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
  accountId: String,
  accountsViewModel: AccountsViewModel,
  transactionsViewModel: TransactionsViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)
  val accounts by accountsViewModel.accounts.collectAsStateWithLifecycle()
  val allTransactions by transactionsViewModel.allTransactions.collectAsStateWithLifecycle()
  val account = remember(accounts, accountId) { accounts.firstOrNull { it.id == accountId } }
  val accountTransactions =
    remember(allTransactions, accountId) { allTransactions.filter { it.accountId == accountId } }

  var isEditOpen by remember { mutableStateOf(false) }
  var isDeleteOpen by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(account?.name ?: "Account Details", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
          IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("account_detail_back")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (account != null) {
            IconButton(onClick = { isEditOpen = true }) {
              Icon(Icons.Rounded.Edit, contentDescription = "Edit Account")
            }
            IconButton(onClick = { isDeleteOpen = true }) {
              Icon(Icons.Rounded.Delete, contentDescription = "Delete Account", tint = OceanTheme.colors.negative)
            }
          }
        },
      )
    },
    modifier = modifier.fillMaxSize(),
  ) { innerPadding ->
    if (account == null) {
      Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(OceanSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text("Account not found or deleted.", style = MaterialTheme.typography.bodyMedium)
      }
    } else {
      val formattedCurrent = FormatMoneyUseCase().format(account.currentBalance)
      val formattedOpening = FormatMoneyUseCase().format(account.initialBalance)

      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("account_detail_screen"),
        contentPadding = PaddingValues(OceanSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        item {
          OceanCard(modifier = Modifier.widthIn(max = 760.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text("Current Balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                  text = formattedCurrent,
                  style = OceanNumericStyle,
                  color = if (account.currentBalanceMinor >= 0) MaterialTheme.colorScheme.primary else OceanTheme.colors.negative,
                )
              }
              OceanStatusBadge(
                text = if (account.isActive) "ACTIVE" else "INACTIVE",
                tone = if (account.isActive) BadgeTone.POSITIVE else BadgeTone.WARNING,
              )
            }
            Spacer(modifier = Modifier.height(OceanSpacing.sm))
            Text("Opening Balance: $formattedOpening", style = MaterialTheme.typography.bodySmall)
            Text("Type: ${account.type.name.replace("_", " ")} • Currency: ${account.currencyCode}", style = MaterialTheme.typography.bodySmall)
            account.institutionName?.let { Text("Institution: $it", style = MaterialTheme.typography.bodySmall) }
            account.accountNumberLast4?.let { Text("Account Hint: ••$it", style = MaterialTheme.typography.bodySmall) }
            Spacer(modifier = Modifier.height(OceanSpacing.sm))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Active for transactions & detection", style = MaterialTheme.typography.bodyMedium)
              Switch(
                checked = account.isActive,
                onCheckedChange = { accountsViewModel.toggleAccountActive(account) },
                modifier = Modifier.testTag("account_detail_active_switch"),
              )
            }
          }
        }

        item {
          OceanSectionHeader(
            title = "Account Ledger (${accountTransactions.size})",
            subtitle = "Recorded transactions for ${account.name}",
            modifier = Modifier.widthIn(max = 760.dp),
          )
        }

        if (accountTransactions.isEmpty()) {
          item {
            OceanCard(modifier = Modifier.widthIn(max = 760.dp)) {
              Text(
                text = "No transactions recorded for this account yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          items(accountTransactions, key = { it.id }) { txn ->
            TransactionListItem(
              transaction = txn,
              accountName = account.name,
              onClick = {},
              modifier = Modifier.widthIn(max = 760.dp),
            )
          }
        }
      }
    }
  }

  if (isEditOpen && account != null) {
    AddEditAccountDialog(
      accountToEdit = account,
      onDismiss = { isEditOpen = false },
      onSave = { name, type, curr, _, inst, last4, color, isActive ->
        accountsViewModel.updateAccount(
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
        isEditOpen = false
      },
    )
  }

  if (isDeleteOpen && account != null) {
    SafeDeleteAccountDialog(
      account = account,
      onDismiss = { isDeleteOpen = false },
      onDeactivate = {
        accountsViewModel.toggleAccountActive(account)
        isDeleteOpen = false
      },
      onSafeDelete = { force ->
        accountsViewModel.deleteAccount(account, force)
        isDeleteOpen = false
        if (accountTransactions.isEmpty() || force) onNavigateBack()
      },
    )
  }
}
