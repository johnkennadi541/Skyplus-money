package com.example.feature.cards

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanSectionHeader
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.component.SkyplusConfirmDialog
import com.example.core.designsystem.component.SkyplusEmptyState
import com.example.core.designsystem.component.SkyplusInlineMessage
import com.example.core.designsystem.component.SkyplusMessageTone
import com.example.core.designsystem.component.SkyplusPrimaryButton
import com.example.core.designsystem.component.SkyplusTextField
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardNetwork
import com.example.domain.model.CreditCardStatement
import com.example.domain.model.CreditCardStatementSummary
import com.example.domain.model.CreditCardRewardEvent
import com.example.domain.model.CreditCardRewardEventType
import com.example.domain.model.CreditCardSpendingAnalytics
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.TransactionCategories
import com.example.domain.model.TransactionType
import com.example.core.common.parseAmountToMinor
import com.example.domain.model.CreditCardSummary
import com.example.domain.model.FinancialAccount
import com.example.domain.model.Money
import com.example.domain.usecase.FormatMoneyUseCase
import java.util.Locale

/**
 * Credit cards list. Every figure comes from the linked account's real balance; nothing here is
 * computed with floating point.
 */
@Composable
fun CardsScreen(
  viewModel: CardsViewModel,
  modifier: Modifier = Modifier,
) {
  val cards by viewModel.cards.collectAsStateWithLifecycle()
  val linkableAccounts by viewModel.linkableAccounts.collectAsStateWithLifecycle()
  val accounts by viewModel.accounts.collectAsStateWithLifecycle()
  val selectedCardStatements by viewModel.selectedCardStatements.collectAsStateWithLifecycle()
  val selectedCardRewardEvents by viewModel.selectedCardRewardEvents.collectAsStateWithLifecycle()
  val selectedCardTransactions by viewModel.selectedCardTransactions.collectAsStateWithLifecycle()
  val selectedCardAnalytics by viewModel.selectedCardAnalytics.collectAsStateWithLifecycle()
  val message by viewModel.userMessage.collectAsStateWithLifecycle()
  val isError by viewModel.isErrorMessage.collectAsStateWithLifecycle()

  CardsContent(
    cards = cards,
    linkableAccounts = linkableAccounts,
    accounts = accounts,
    selectedCardStatements = selectedCardStatements,
    selectedCardRewardEvents = selectedCardRewardEvents,
    selectedCardTransactions = selectedCardTransactions,
    selectedCardAnalytics = selectedCardAnalytics,
    message = message,
    isErrorMessage = isError,
    onDismissMessage = viewModel::dismissMessage,
    onCreateCard = viewModel::createCard,
    onUpdateCard = viewModel::updateCard,
    onToggleCardFrozen = viewModel::setCardFrozen,
    onSelectCardDetails = viewModel::selectCardDetails,
    onClearCardDetails = viewModel::clearCardDetails,
    onCreateStatement = viewModel::createStatement,
    onAddRewardEvent = viewModel::addRewardEvent,
    onDeleteCard = viewModel::deleteCard,
    modifier = modifier,
  )
}

@Composable
fun CardsContent(
  cards: List<CreditCardSummary>,
  linkableAccounts: List<FinancialAccount>,
  accounts: List<FinancialAccount> = linkableAccounts,
  selectedCardStatements: List<com.example.domain.model.CreditCardStatementSummary> = emptyList(),
  selectedCardRewardEvents: List<com.example.domain.model.CreditCardRewardEvent> = emptyList(),
  selectedCardTransactions: List<com.example.domain.model.FinancialTransaction> = emptyList(),
  selectedCardAnalytics: com.example.domain.model.CreditCardSpendingAnalytics? = null,
  message: String?,
  isErrorMessage: Boolean,
  onDismissMessage: () -> Unit,
  onCreateCard: (
    accountId: String,
    issuerName: String,
    network: CreditCardNetwork,
    last4: String,
    limitDecimal: String,
    billingCycleDay: Int,
    paymentDueDayOffset: Int,
    aprPercent: String,
    annualFeeDecimal: String,
    annualFeeMonth: Int,
    rewardProgramName: String?,
    reminderEnabled: Boolean,
    reminderDaysBefore: Int,
    notes: String?,
    onSuccess: () -> Unit
  ) -> Unit,
  onUpdateCard: (card: com.example.domain.model.CreditCard, limitDecimal: String, annualFeeDecimal: String, onSuccess: () -> Unit) -> Unit,
  onToggleCardFrozen: (cardId: String, frozen: Boolean) -> Unit,
  onSelectCardDetails: (cardId: String) -> Unit,
  onClearCardDetails: () -> Unit,
  onCreateStatement: (com.example.domain.model.CreditCardStatement, onSuccess: () -> Unit) -> Unit,
  onAddRewardEvent: (com.example.domain.model.CreditCardRewardEvent, onSuccess: () -> Unit) -> Unit,
  onDeleteCard: (cardId: String, onSuccess: () -> Unit) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showCreateDialog by remember { mutableStateOf(false) }
  var cardToDelete by remember { mutableStateOf<CreditCardSummary?>(null) }
  var cardToEdit by remember { mutableStateOf<CreditCardSummary?>(null) }
  var cardToDetails by remember { mutableStateOf<CreditCardSummary?>(null) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = OceanSpacing.md, vertical = OceanSpacing.md)
        .testTag("cards_screen"),
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OceanSectionHeader(
            title = "Credit cards",
            subtitle = "Balances are liabilities and are never counted as assets",
          )
          SkyplusPrimaryButton(
            text = "Add card",
            onClick = { showCreateDialog = true },
            icon = Icons.Rounded.Add,
            modifier = Modifier.testTag("add_card_button"),
          )
        }
      }

      if (message != null) {
        item {
          SkyplusInlineMessage(
            text = message,
            tone = if (isErrorMessage) SkyplusMessageTone.ERROR else SkyplusMessageTone.SUCCESS,
            modifier = Modifier.testTag("card_message"),
          )
        }
      }

      if (cards.isEmpty()) {
        item {
          SkyplusEmptyState(
            title = "No credit cards yet",
            message =
              if (linkableAccounts.isEmpty()) {
                "Create a credit card account first, then attach a card to it here."
              } else {
                "Attach a card to one of your credit card accounts to track its limit and utilisation."
              },
            icon = Icons.Rounded.CreditCard,
            modifier = Modifier.testTag("cards_empty_state"),
          )
        }
      } else {
        items(cards, key = { it.card.id }) { summary ->
          CardTile(
            summary = summary,
            onOpenDetails = {
              cardToDetails = summary
              onSelectCardDetails(summary.card.id)
            },
            onEdit = { cardToEdit = summary },
            onToggleFreeze = { frozen -> onToggleCardFrozen(summary.card.id, frozen) },
            onDelete = { cardToDelete = summary },
          )
        }
      }
    }
  }

  if (showCreateDialog) {
    CardFormDialog(
      linkableAccounts = linkableAccounts,
      onDismiss = { showCreateDialog = false },
      onSave = { accountId, issuer, network, last4, limit, cycleDay, dueOffset, apr, annualFee, annualMonth, rewardProgram, reminderEnabled, reminderDays, notes, onDone ->
        onCreateCard(
          accountId, issuer, network, last4, limit, cycleDay, dueOffset, apr,
          annualFee, annualMonth, rewardProgram, reminderEnabled, reminderDays, notes, onDone,
        )
      },
    )
  }

  cardToEdit?.let { summary ->
    CardFormDialog(
      linkableAccounts = emptyList(),
      existingCard = summary.card,
      existingAccountName = summary.account.name,
      onDismiss = { cardToEdit = null },
      onSave = { _, issuer, network, last4, limit, cycleDay, dueOffset, apr, annualFee, annualMonth, rewardProgram, reminderEnabled, reminderDays, notes, onDone ->
        val updated = summary.card.copy(
          issuerName = issuer,
          network = network,
          last4 = last4,
          billingCycleDay = cycleDay,
          paymentDueDayOffset = dueOffset,
          aprPercent = apr,
          annualFeeMonth = annualMonth,
          rewardProgramName = rewardProgram,
          reminderEnabled = reminderEnabled,
          reminderDaysBefore = reminderDays,
          notes = notes,
        )
        onUpdateCard(updated, limit, annualFee, onDone)
      },
    )
  }

  cardToDetails?.let { summary ->
    CardDetailsDialog(
      summary = summary,
      statements = selectedCardStatements,
      rewardEvents = selectedCardRewardEvents,
      transactions = selectedCardTransactions,
      analytics = selectedCardAnalytics,
      onDismiss = {
        cardToDetails = null
        onClearCardDetails()
      },
      onToggleFrozen = { frozen -> onToggleCardFrozen(summary.card.id, frozen) },
      onCreateStatement = onCreateStatement,
      onAddRewardEvent = onAddRewardEvent,
    )
  }

  cardToDelete?.let { summary ->
    SkyplusConfirmDialog(
      title = "Remove card?",
      message =
        "This removes the card record only. The account and its transactions stay exactly as they are.",
      confirmLabel = "Remove",
      destructive = true,
      onConfirm = {
        onDeleteCard(summary.card.id) { cardToDelete = null }
        cardToDelete = null
      },
      onDismiss = { cardToDelete = null },
    )
  }
}

@Composable
private fun CardTile(
  summary: CreditCardSummary,
  onOpenDetails: () -> Unit,
  onEdit: () -> Unit,
  onToggleFreeze: (Boolean) -> Unit,
  onDelete: () -> Unit,
) {
  val extended = OceanTheme.colors
  val formatter = remember { FormatMoneyUseCase() }
  val outstandingText = formatter.format(Money.fromMinorUnits(summary.outstandingMinor, summary.card.currencyCode))
  val limitText = formatter.format(Money.fromMinorUnits(summary.card.creditLimitMinor, summary.card.currencyCode))
  val utilisation = (summary.utilisationPercent.toFloat() / 100f).coerceIn(0f, 1f)

  OceanCard(modifier = Modifier.fillMaxWidth().testTag("card_tile_${summary.card.id}")) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = summary.card.issuerName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.width(OceanSpacing.xs))
          OceanStatusBadge(
            text = if (summary.card.last4.isBlank()) summary.card.network.name
            else "${summary.card.network.name} ••${summary.card.last4}",
            tone = BadgeTone.INFO,
          )
          if (summary.isOverLimit) {
            Spacer(modifier = Modifier.width(OceanSpacing.xs))
            OceanStatusBadge(text = "Over limit", tone = BadgeTone.NEGATIVE)
          }
          if (summary.card.isFrozen) {
            Spacer(modifier = Modifier.width(OceanSpacing.xs))
            OceanStatusBadge(text = "Marked frozen", tone = BadgeTone.WARNING)
          }
        }
        Text(
          text = summary.account.name,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(
        onClick = onDelete,
        modifier = Modifier.testTag("delete_card_${summary.card.id}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Delete,
          contentDescription = "Remove card",
          tint = MaterialTheme.colorScheme.error,
        )
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.sm))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text("Outstanding", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          text = outstandingText,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = if (summary.outstandingMinor > 0L) extended.negative else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.testTag("card_outstanding_${summary.card.id}"),
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        Text("Limit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = limitText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    LinearProgressIndicator(
      progress = { utilisation },
      modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
      color = when {
        summary.isOverLimit -> extended.negative
        utilisation >= 0.75f -> extended.warning
        else -> extended.positive
      },
      trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.xxs))
    Text(
      text = if (summary.card.creditLimitMinor > 0L) {
        String.format(Locale.getDefault(), "%.2f%% of limit used", summary.utilisationPercent.toDouble())
      } else {
        "No limit set"
      },
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = onOpenDetails, modifier = Modifier.weight(1f).testTag("card_details_${summary.card.id}")) {
        Text("Details")
      }
      TextButton(onClick = onEdit, modifier = Modifier.weight(1f).testTag("edit_card_${summary.card.id}")) {
        Text("Edit")
      }
      TextButton(
        onClick = { onToggleFreeze(!summary.card.isFrozen) },
        modifier = Modifier.weight(1f).testTag("freeze_card_${summary.card.id}"),
      ) {
        Text(if (summary.card.isFrozen) "Unfreeze" else "Freeze")
      }
    }
    Text(
      "Freeze is a local tracker status only; contact your card issuer to block the physical card.",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun CardFormDialog(
  linkableAccounts: List<FinancialAccount>,
  existingCard: CreditCard? = null,
  existingAccountName: String? = null,
  onDismiss: () -> Unit,
  onSave: (
    accountId: String,
    issuerName: String,
    network: CreditCardNetwork,
    last4: String,
    limitDecimal: String,
    billingCycleDay: Int,
    paymentDueDayOffset: Int,
    aprPercent: String,
    annualFeeDecimal: String,
    annualFeeMonth: Int,
    rewardProgramName: String?,
    reminderEnabled: Boolean,
    reminderDaysBefore: Int,
    notes: String?,
    onSuccess: () -> Unit,
  ) -> Unit,
) {
  var selectedAccountId by remember { mutableStateOf(existingCard?.accountId ?: linkableAccounts.firstOrNull()?.id.orEmpty()) }
  var issuer by remember { mutableStateOf(existingCard?.issuerName.orEmpty()) }
  var network by remember { mutableStateOf(existingCard?.network ?: CreditCardNetwork.VISA) }
  var last4 by remember { mutableStateOf(existingCard?.last4.orEmpty()) }
  var limitText by remember {
    mutableStateOf(existingCard?.let { minorToDecimal(it.creditLimitMinor, it.currencyCode) }.orEmpty())
  }
  var aprText by remember { mutableStateOf(existingCard?.aprPercent ?: "0") }
  var billingCycleDay by remember { mutableStateOf((existingCard?.billingCycleDay ?: 1).toString()) }
  var dueOffset by remember { mutableStateOf((existingCard?.paymentDueDayOffset ?: 20).toString()) }
  var annualFeeText by remember {
    mutableStateOf(existingCard?.let { minorToDecimal(it.annualFeeMinor, it.currencyCode) } ?: "0")
  }
  var annualFeeMonth by remember { mutableStateOf((existingCard?.annualFeeMonth ?: 1).toString()) }
  var rewardProgram by remember { mutableStateOf(existingCard?.rewardProgramName.orEmpty()) }
  var reminderEnabled by remember { mutableStateOf(existingCard?.reminderEnabled ?: true) }
  var reminderDays by remember { mutableStateOf((existingCard?.reminderDaysBefore ?: 3).toString()) }
  var notes by remember { mutableStateOf(existingCard?.notes.orEmpty()) }
  var formError by remember { mutableStateOf<String?>(null) }
  val context = LocalContext.current
  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      reminderEnabled = granted
    }

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("card_form_dialog"),
    title = { Text(if (existingCard == null) "Add credit card" else "Edit credit card") },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
      ) {
        if (existingCard == null) {
          Text(
            text = "Choose the credit card account this card belongs to",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (linkableAccounts.isEmpty()) {
            Text(
              text = "No credit card account is available. Create one in Accounts first.",
              style = MaterialTheme.typography.bodySmall,
              color = OceanTheme.colors.warning,
            )
          }
          linkableAccounts.forEach { account ->
            val selected = account.id == selectedAccountId
            Surface(
              modifier = Modifier.fillMaxWidth().clickable { selectedAccountId = account.id }
                .testTag("card_account_option_${account.id}"),
              shape = MaterialTheme.shapes.small,
              color = if (selected) OceanTheme.colors.accentContainer else MaterialTheme.colorScheme.surfaceVariant,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            ) {
              Text(
                text = "${account.name} (${account.currencyCode})",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(OceanSpacing.xs),
              )
            }
          }
        } else {
          Text(
            text = "Linked account: ${existingAccountName ?: existingCard.accountId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        SkyplusTextField(value = issuer, onValueChange = { issuer = it }, label = "Issuer", placeholder = "HDFC Bank")
        SkyplusTextField(
          value = last4,
          onValueChange = { last4 = it.filter(Char::isDigit).take(4) },
          label = "Last 4 digits",
          placeholder = "1234",
        )
        SkyplusTextField(value = limitText, onValueChange = { limitText = it }, label = "Credit limit", placeholder = "100000")
        SkyplusTextField(value = aprText, onValueChange = { aprText = it }, label = "APR %", placeholder = "36.0")
        SkyplusTextField(
          value = billingCycleDay,
          onValueChange = { billingCycleDay = it.filter(Char::isDigit).take(2) },
          label = "Statement closing day (1–28)",
          placeholder = "5",
        )
        SkyplusTextField(
          value = dueOffset,
          onValueChange = { dueOffset = it.filter(Char::isDigit).take(2) },
          label = "Payment due days after closing (0–45)",
          placeholder = "20",
        )
        SkyplusTextField(value = annualFeeText, onValueChange = { annualFeeText = it }, label = "Annual fee", placeholder = "0")
        SkyplusTextField(
          value = annualFeeMonth,
          onValueChange = { annualFeeMonth = it.filter(Char::isDigit).take(2) },
          label = "Annual fee month (1–12)",
          placeholder = "1",
        )
        SkyplusTextField(
          value = rewardProgram,
          onValueChange = { rewardProgram = it.take(80) },
          label = "Rewards programme",
          placeholder = "Cashback / Reward points",
        )
        Text("Card network", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        CreditCardNetwork.entries.chunked(3).forEach { rowNetworks ->
          Row(horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
            rowNetworks.forEach { candidate ->
              val selected = candidate == network
              Surface(
                modifier = Modifier.clickable { network = candidate }.testTag("card_network_${candidate.name}"),
                shape = MaterialTheme.shapes.small,
                color = if (selected) OceanTheme.colors.accentContainer else MaterialTheme.colorScheme.surfaceVariant,
              ) {
                Text(
                  text = candidate.name,
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = OceanSpacing.xs, vertical = 4.dp),
                )
              }
            }
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Payment reminders", style = MaterialTheme.typography.labelLarge)
            Text("Notify before the statement due date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Switch(
            checked = reminderEnabled,
            onCheckedChange = { enabled ->
              if (
                enabled &&
                  Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                  ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
              ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              } else {
                reminderEnabled = enabled
              }
            },
          )
        }
        if (reminderEnabled) {
          SkyplusTextField(
            value = reminderDays,
            onValueChange = { reminderDays = it.filter(Char::isDigit).take(2) },
            label = "Reminder days before due date (1–30)",
            placeholder = "3",
          )
        }
        SkyplusTextField(value = notes, onValueChange = { notes = it.take(500) }, label = "Notes", placeholder = "Optional card notes")
        if (formError != null) {
          Text(text = formError!!, style = MaterialTheme.typography.labelSmall, color = OceanTheme.colors.negative)
        }
      }
    },
    confirmButton = {
      SkyplusPrimaryButton(
        text = if (existingCard == null) "Save card" else "Save changes",
        onClick = {
          val cycle = billingCycleDay.toIntOrNull()
          val due = dueOffset.toIntOrNull()
          val feeMonth = annualFeeMonth.toIntOrNull()
          val reminderLead = reminderDays.toIntOrNull()
          if (selectedAccountId.isBlank()) {
            formError = "Choose an account first"
          } else if (issuer.isBlank()) {
            formError = "Enter the card issuer"
          } else if (cycle == null || cycle !in 1..28) {
            formError = "Statement closing day must be between 1 and 28"
          } else if (due == null || due !in 0..45) {
            formError = "Payment due offset must be between 0 and 45"
          } else if (feeMonth == null || feeMonth !in 1..12) {
            formError = "Annual fee month must be between 1 and 12"
          } else if (reminderEnabled && (reminderLead == null || reminderLead !in 1..30)) {
            formError = "Reminder lead time must be between 1 and 30 days"
          } else if (
            reminderEnabled &&
              Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
              ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
          ) {
            formError = "Allow notifications to receive card payment reminders."
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            formError = null
            onSave(
              selectedAccountId,
              issuer,
              network,
              last4,
              limitText,
              cycle!!,
              due!!,
              aprText.ifBlank { "0" },
              annualFeeText.ifBlank { "0" },
              feeMonth!!,
              rewardProgram.ifBlank { null },
              reminderEnabled,
              reminderLead ?: 3,
              notes.ifBlank { null },
            ) { onDismiss() }
          }
        },
        modifier = Modifier.testTag("save_card_button"),
      )
    },
    dismissButton = {
      com.example.core.designsystem.component.SkyplusTertiaryButton(text = "Cancel", onClick = onDismiss)
    },
  )
}

@Composable
private fun CardDetailsDialog(
  summary: CreditCardSummary,
  statements: List<CreditCardStatementSummary>,
  rewardEvents: List<CreditCardRewardEvent>,
  transactions: List<FinancialTransaction>,
  analytics: CreditCardSpendingAnalytics?,
  onDismiss: () -> Unit,
  onToggleFrozen: (Boolean) -> Unit,
  onCreateStatement: (CreditCardStatement, () -> Unit) -> Unit,
  onAddRewardEvent: (CreditCardRewardEvent, () -> Unit) -> Unit,
) {
  var showStatementDialog by remember { mutableStateOf(false) }
  var showRewardDialog by remember { mutableStateOf(false) }
  val formatter = remember { FormatMoneyUseCase() }
  val pointsBalance = rewardEvents.fold(0L) { total, event -> Math.addExact(total, event.pointsDelta) }
  val paymentHistory = transactions
    .filter { it.type == TransactionType.TRANSFER && it.category == TransactionCategories.TRANSFER_IN_CATEGORY }
    .sortedByDescending { it.transactionDate }
    .take(8)

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("card_details_dialog"),
    title = {
      Column {
        Text(summary.card.issuerName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
          "${summary.card.network.name} ••${summary.card.last4}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        OceanCard {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Freeze status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
              Text(
                "This is a local tracker flag only. It does not lock or block the physical card with your issuer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(checked = summary.card.isFrozen, onCheckedChange = onToggleFrozen)
          }
        }

        OceanCard {
          Text("Statement history & payment status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          Text(
            "Payments are calculated from completed transfers into this card account. No paid status is manually assumed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(OceanSpacing.xs))
          SkyplusPrimaryButton(
            text = "Add statement",
            onClick = { showStatementDialog = true },
            modifier = Modifier.testTag("add_card_statement_button"),
          )
          if (statements.isEmpty()) {
            Text("No statements recorded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          } else {
            statements.forEach { item ->
              val statusTone = when (item.status) {
                com.example.domain.model.CreditCardStatementStatus.PAID -> BadgeTone.POSITIVE
                com.example.domain.model.CreditCardStatementStatus.OVERDUE -> BadgeTone.NEGATIVE
                com.example.domain.model.CreditCardStatementStatus.PARTIALLY_PAID -> BadgeTone.WARNING
                com.example.domain.model.CreditCardStatementStatus.UNPAID -> BadgeTone.INFO
              }
              Column(modifier = Modifier.fillMaxWidth().padding(vertical = OceanSpacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Statement ${formatCardDate(item.statement.statementDate)}", fontWeight = FontWeight.SemiBold)
                  OceanStatusBadge(text = item.status.name.replace('_', ' '), tone = statusTone)
                }
                Text(
                  "Due ${formatCardDate(item.statement.dueDate)} • Balance ${formatter.format(item.statementBalance)}",
                  style = MaterialTheme.typography.bodySmall,
                )
                Text(
                  "Minimum ${formatter.format(item.minimumDue)} • Paid ${formatter.format(Money.fromMinorUnits(item.paidAmountMinor, item.currencyCode))} • Remaining ${formatter.format(item.remainingDue)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  "Fees ${formatter.format(Money.fromMinorUnits(item.statement.feesMinor, item.currencyCode))} • Interest ${formatter.format(Money.fromMinorUnits(item.statement.interestMinor, item.currencyCode))}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        CardDueDateCalendar(statements)

        OceanCard {
          Text("Rewards", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          Text("$pointsBalance points", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
          Text(
            summary.card.rewardProgramName?.takeIf { it.isNotBlank() } ?: "No rewards programme name set",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          SkyplusPrimaryButton(
            text = "Record reward activity",
            onClick = { showRewardDialog = true },
            modifier = Modifier.testTag("add_card_reward_button"),
          )
          rewardEvents.take(5).forEach { event ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Column(modifier = Modifier.weight(1f)) {
                Text(event.description, style = MaterialTheme.typography.bodySmall)
                Text("${event.eventType.name} • ${formatCardDate(event.eventDate)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Text(
                if (event.pointsDelta > 0L) "+${event.pointsDelta}" else event.pointsDelta.toString(),
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }

        OceanCard {
          Text("This month's spending", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          Text(
            analytics?.let { formatter.format(Money.fromMinorUnits(it.totalSpentMinor, it.currencyCode)) } ?: formatter.format(Money.fromMinorUnits(0L, summary.card.currencyCode)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            "${analytics?.transactionCount ?: 0} completed purchases",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          analytics?.categoryTotalsMinor?.toList()?.sortedByDescending { it.second }?.take(5)?.forEach { (category, amount) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(category, style = MaterialTheme.typography.bodySmall)
              Text(formatter.format(Money.fromMinorUnits(amount, summary.card.currencyCode)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
          }
        }

        OceanCard {
          Text("Payment history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          if (paymentHistory.isEmpty()) {
            Text("No completed payments into this card account found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          } else {
            paymentHistory.forEach { payment ->
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(payment.merchantOrPayee ?: payment.notes ?: "Card payment", style = MaterialTheme.typography.bodySmall)
                  Text(formatCardDate(payment.transactionDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatter.format(Money.fromMinorUnits(payment.amountMinor, payment.currencyCode)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }

        OceanCard {
          Text("Card charges & settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          Text("APR: ${summary.card.aprPercent}% • Annual fee month: ${summary.card.annualFeeMonth}", style = MaterialTheme.typography.bodySmall)
          Text("Annual fee: ${formatter.format(summary.card.annualFee)}", style = MaterialTheme.typography.bodySmall)
          Text(
            if (summary.card.reminderEnabled) "Payment reminder: ${summary.card.reminderDaysBefore} days before due date"
            else "Payment reminders disabled",
            style = MaterialTheme.typography.bodySmall,
          )
          summary.card.notes?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    },
    confirmButton = {
      com.example.core.designsystem.component.SkyplusTertiaryButton(text = "Close", onClick = onDismiss)
    },
  )

  if (showStatementDialog) {
    CardStatementFormDialog(
      card = summary.card,
      onDismiss = { showStatementDialog = false },
      onSave = { statement, onDone ->
        onCreateStatement(statement) {
          showStatementDialog = false
          onDone()
        }
      },
    )
  }

  if (showRewardDialog) {
    CardRewardFormDialog(
      card = summary.card,
      onDismiss = { showRewardDialog = false },
      onSave = { event, onDone ->
        onAddRewardEvent(event) {
          showRewardDialog = false
          onDone()
        }
      },
    )
  }
}

@Composable
private fun CardDueDateCalendar(statements: List<CreditCardStatementSummary>) {
  val calendar = remember {
    java.util.Calendar.getInstance().apply {
      set(java.util.Calendar.DAY_OF_MONTH, 1)
      set(java.util.Calendar.HOUR_OF_DAY, 0)
      set(java.util.Calendar.MINUTE, 0)
      set(java.util.Calendar.SECOND, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
  }
  val monthLabel = java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
  val offset = (calendar.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.MONDAY + 7) % 7
  val daysInMonth = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
  val dueDays = statements.mapNotNull { statement ->
    java.util.Calendar.getInstance().apply { timeInMillis = statement.statement.dueDate }
      .takeIf { it.get(java.util.Calendar.MONTH) == calendar.get(java.util.Calendar.MONTH) &&
        it.get(java.util.Calendar.YEAR) == calendar.get(java.util.Calendar.YEAR) }
      ?.get(java.util.Calendar.DAY_OF_MONTH)
  }.toSet()
  val cells: List<Int?> = (List(offset) { null } + (1..daysInMonth).map { it }).let { values ->
    values + List((7 - values.size % 7) % 7) { null }
  }

  OceanCard {
    Text("Payment due-date calendar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(monthLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(modifier = Modifier.fillMaxWidth()) {
      listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
        Text(day, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall)
      }
    }
    cells.chunked(7).forEach { week ->
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        week.forEach { day ->
          Surface(
            modifier = Modifier.weight(1f).height(34.dp),
            shape = RoundedCornerShape(50),
            color = when {
              day == null -> MaterialTheme.colorScheme.surface
              day in dueDays -> OceanTheme.colors.accentContainer
              else -> MaterialTheme.colorScheme.surfaceVariant
            },
          ) {
            Box(contentAlignment = Alignment.Center) {
              if (day != null) {
                Text(
                  text = day.toString(),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (day in dueDays) FontWeight.Bold else FontWeight.Normal,
                )
              }
            }
          }
        }
      }
    }
    if (dueDays.isNotEmpty()) {
      Text(
        "Due dates highlighted: ${dueDays.sorted().joinToString(", ")}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Text("No statement due dates in this month.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun CardStatementFormDialog(
  card: CreditCard,
  onDismiss: () -> Unit,
  onSave: (CreditCardStatement, () -> Unit) -> Unit,
) {
  val today = java.time.LocalDate.now()
  var statementDate by remember { mutableStateOf(today.toString()) }
  var dueDate by remember { mutableStateOf(today.plusDays(20).toString()) }
  var balance by remember { mutableStateOf("") }
  var minimumDue by remember { mutableStateOf("") }
  var fees by remember { mutableStateOf("0") }
  var interest by remember { mutableStateOf("0") }
  var notes by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add statement") },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
      ) {
        SkyplusTextField(value = statementDate, onValueChange = { statementDate = it }, label = "Statement date (YYYY-MM-DD)")
        SkyplusTextField(value = dueDate, onValueChange = { dueDate = it }, label = "Payment due date (YYYY-MM-DD)")
        SkyplusTextField(value = balance, onValueChange = { balance = it }, label = "Statement balance")
        SkyplusTextField(value = minimumDue, onValueChange = { minimumDue = it }, label = "Minimum due")
        SkyplusTextField(value = fees, onValueChange = { fees = it }, label = "Fees / charges")
        SkyplusTextField(value = interest, onValueChange = { interest = it }, label = "Interest charged")
        SkyplusTextField(value = notes, onValueChange = { notes = it.take(500) }, label = "Notes")
        error?.let { Text(it, color = OceanTheme.colors.negative, style = MaterialTheme.typography.labelSmall) }
      }
    },
    confirmButton = {
      SkyplusPrimaryButton(
        text = "Save statement",
        onClick = {
          val statementDay = runCatching { java.time.LocalDate.parse(statementDate) }.getOrNull()
          val dueDay = runCatching { java.time.LocalDate.parse(dueDate) }.getOrNull()
          val balanceMinor = parseAmountToMinor(balance, card.currencyCode)
          val minimumMinor = parseAmountToMinor(minimumDue, card.currencyCode)
          val feesMinor = parseAmountToMinor(fees.ifBlank { "0" }, card.currencyCode)
          val interestMinor = parseAmountToMinor(interest.ifBlank { "0" }, card.currencyCode)
          if (statementDay == null || dueDay == null || dueDay.isBefore(statementDay)) {
            error = "Enter valid statement and due dates"
          } else if (balanceMinor == null || minimumMinor == null || feesMinor == null || interestMinor == null) {
            error = "Enter valid amounts in ${card.currencyCode}"
          } else {
            error = null
            val zone = java.time.ZoneId.systemDefault()
            val statement = CreditCardStatement(
              userId = card.userId,
              cardId = card.id,
              currencyCode = card.currencyCode,
              statementDate = statementDay.atStartOfDay(zone).toInstant().toEpochMilli(),
              dueDate = dueDay.atStartOfDay(zone).toInstant().toEpochMilli(),
              statementBalanceMinor = balanceMinor,
              minimumDueMinor = minimumMinor,
              feesMinor = feesMinor,
              interestMinor = interestMinor,
              notes = notes.ifBlank { null },
            )
            onSave(statement) {}
          }
        },
      )
    },
    dismissButton = { com.example.core.designsystem.component.SkyplusTertiaryButton(text = "Cancel", onClick = onDismiss) },
  )
}

@Composable
private fun CardRewardFormDialog(
  card: CreditCard,
  onDismiss: () -> Unit,
  onSave: (CreditCardRewardEvent, () -> Unit) -> Unit,
) {
  var eventType by remember { mutableStateOf(CreditCardRewardEventType.EARN) }
  var points by remember { mutableStateOf("") }
  var cashValue by remember { mutableStateOf("0") }
  var description by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Record rewards") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
          CreditCardRewardEventType.entries.forEach { type ->
            TextButton(onClick = { eventType = type }) {
              Text(if (eventType == type) "• ${type.name}" else type.name)
            }
          }
        }
        SkyplusTextField(value = points, onValueChange = { points = it.filter { ch -> ch == '-' || ch.isDigit() } }, label = "Points change")
        SkyplusTextField(value = cashValue, onValueChange = { cashValue = it }, label = "Cash value")
        SkyplusTextField(value = description, onValueChange = { description = it.take(160) }, label = "Description")
        error?.let { Text(it, color = OceanTheme.colors.negative, style = MaterialTheme.typography.labelSmall) }
      }
    },
    confirmButton = {
      SkyplusPrimaryButton(
        text = "Save reward",
        onClick = {
          val rawPoints = points.toLongOrNull()
          val cashMinor = parseAmountToMinor(cashValue.ifBlank { "0" }, card.currencyCode)
          val delta = when (eventType) {
            CreditCardRewardEventType.EARN -> rawPoints?.let { kotlin.math.abs(it) }
            CreditCardRewardEventType.REDEEM -> rawPoints?.let { -kotlin.math.abs(it) }
            CreditCardRewardEventType.ADJUSTMENT -> rawPoints
          }
          if (delta == null || delta == 0L || cashMinor == null || description.isBlank()) {
            error = "Enter a valid points change, cash value and description"
          } else {
            error = null
            val event = CreditCardRewardEvent(
              userId = card.userId,
              cardId = card.id,
              eventDate = System.currentTimeMillis(),
              eventType = eventType,
              pointsDelta = delta,
              cashValueMinor = cashMinor,
              currencyCode = card.currencyCode,
              description = description.trim(),
            )
            onSave(event) {}
          }
        },
      )
    },
    dismissButton = { com.example.core.designsystem.component.SkyplusTertiaryButton(text = "Cancel", onClick = onDismiss) },
  )
}

private fun formatCardDate(timestamp: Long): String =
  java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(java.util.Date(timestamp))

private fun minorToDecimal(amountMinor: Long, currencyCode: String): String {
  val digits = runCatching { java.util.Currency.getInstance(currencyCode).defaultFractionDigits }
    .getOrDefault(2).coerceAtLeast(0)
  return java.math.BigDecimal.valueOf(amountMinor, digits).stripTrailingZeros().toPlainString()
}
