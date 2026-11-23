package com.example.feature.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.domain.model.CreditCardNetwork
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
    onSuccess: () -> Unit
  ) -> Unit,
) {
  var selectedAccountId by remember { mutableStateOf(linkableAccounts.firstOrNull()?.id.orEmpty()) }
  var issuer by remember { mutableStateOf("") }
  var network by remember { mutableStateOf(CreditCardNetwork.VISA) }
  var last4 by remember { mutableStateOf("") }
  var limitText by remember { mutableStateOf("") }
  var aprText by remember { mutableStateOf("0") }
  var formError by remember { mutableStateOf<String?>(null) }

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("card_form_dialog"),
    title = { Text("Add credit card") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
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
            modifier =
              Modifier.fillMaxWidth()
                .clickable { selectedAccountId = account.id }
                .testTag("card_account_option_${account.id}"),
            shape = MaterialTheme.shapes.small,
            color =
              if (selected) OceanTheme.colors.accentContainer
              else MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
          ) {
            Text(
              text = "${account.name} (${account.currencyCode})",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(OceanSpacing.xs),
            )
          }
        }

        SkyplusTextField(
          value = issuer,
          onValueChange = { issuer = it },
          label = "Issuer",
          placeholder = "HDFC Bank",
          modifier = Modifier.testTag("card_issuer_input"),
        )
        SkyplusTextField(
          value = last4,
          onValueChange = { if (it.length <= 4) last4 = it.filter { c -> c.isDigit() } },
          label = "Last 4 digits",
          placeholder = "1234",
          modifier = Modifier.testTag("card_last4_input"),
        )
        SkyplusTextField(
          value = limitText,
          onValueChange = { limitText = it },
          label = "Credit limit",
          placeholder = "100000",
          modifier = Modifier.testTag("card_limit_input"),
        )
        SkyplusTextField(
          value = aprText,
          onValueChange = { aprText = it },
          label = "APR %",
          placeholder = "42",
          modifier = Modifier.testTag("card_apr_input"),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          CreditCardNetwork.entries.take(4).forEach { candidate ->
            val selected = candidate == network
            Surface(
              modifier =
                Modifier.clickable { network = candidate }.testTag("card_network_${candidate.name}"),
              shape = MaterialTheme.shapes.small,
              color =
                if (selected) OceanTheme.colors.accentContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ) {
              Text(
                text = candidate.name,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = OceanSpacing.xs, vertical = 4.dp),
              )
            }
          }
        }
        if (formError != null) {
          Text(
            text = formError!!,
            style = MaterialTheme.typography.labelSmall,
            color = OceanTheme.colors.negative,
          )
        }
      }
    },
    confirmButton = {
      SkyplusPrimaryButton(
        text = "Save",
        onClick = {
          if (selectedAccountId.isBlank()) {
            formError = "Choose an account first"
            return@SkyplusPrimaryButton
          }
          if (issuer.isBlank()) {
            formError = "Enter the card issuer"
            return@SkyplusPrimaryButton
          }
          formError = null
          onSave(
            selectedAccountId,
            issuer,
            network,
            last4,
            limitText,
            1,
            20,
            aprText.ifBlank { "0" },
          ) { onDismiss() }
        },
        modifier = Modifier.testTag("save_card_button"),
      )
    },
    dismissButton = {
      com.example.core.designsystem.component.SkyplusTertiaryButton(
        text = "Cancel",
        onClick = onDismiss,
      )
    },
  )
}
