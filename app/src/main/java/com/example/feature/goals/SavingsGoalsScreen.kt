package com.example.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.example.core.designsystem.component.SkyplusSkeletonCard
import com.example.core.designsystem.component.SkyplusPrimaryButton
import com.example.core.designsystem.component.SkyplusTertiaryButton
import com.example.core.designsystem.component.SkyplusTextField
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.Money
import com.example.domain.model.SavingsGoal
import com.example.domain.model.SavingsGoalSummary
import com.example.domain.usecase.FormatMoneyUseCase
import java.util.Locale

@Composable
fun SavingsGoalsScreen(
  viewModel: SavingsGoalsViewModel,
  modifier: Modifier = Modifier,
) {
  val goals by viewModel.goals.collectAsStateWithLifecycle()
  val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
  val message by viewModel.userMessage.collectAsStateWithLifecycle()
  val isError by viewModel.isErrorMessage.collectAsStateWithLifecycle()

  SavingsGoalsContent(
    goals = goals,
    isLoading = isLoading,
    message = message,
    isErrorMessage = isError,
    onDismissMessage = viewModel::dismissMessage,
    onCreateGoal = viewModel::createGoal,
    onAddContribution = viewModel::addContribution,
    onDeleteGoal = viewModel::deleteGoal,
    onEditGoal = viewModel::updateGoal,
    modifier = modifier,
  )
}

@Composable
fun SavingsGoalsContent(
  goals: List<SavingsGoalSummary>,
  isLoading: Boolean = false,
  message: String?,
  isErrorMessage: Boolean,
  onDismissMessage: () -> Unit,
  onCreateGoal: (name: String, currencyCode: String, targetDecimal: String, note: String?, deadlineText: String, onSuccess: () -> Unit) -> Unit,
  onAddContribution: (summary: SavingsGoalSummary, amountDecimal: String, note: String?, onSuccess: () -> Unit) -> Unit,
  onDeleteGoal: (goalId: String, onSuccess: () -> Unit) -> Unit,
  onEditGoal: (goal: SavingsGoal, name: String, currencyCode: String, targetDecimal: String, note: String?, deadlineText: String, onSuccess: () -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
  modifier: Modifier = Modifier,
) {
  var showCreateDialog by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<SavingsGoalSummary?>(null) }
  var contributingTo by remember { mutableStateOf<SavingsGoalSummary?>(null) }
  var deleting by remember { mutableStateOf<SavingsGoalSummary?>(null) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = OceanSpacing.md, vertical = OceanSpacing.md)
        .testTag("savings_goals_screen"),
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
          // OceanSectionHeader fills its width internally, so it needs a weight here or it
          // would push the New goal button off the row entirely.
          OceanSectionHeader(
            title = "Savings goals",
            subtitle = "Saved amounts are always calculated from your contributions",
            modifier = Modifier.weight(1f),
          )
          SkyplusPrimaryButton(
            text = "New goal",
            onClick = { showCreateDialog = true },
            icon = Icons.Rounded.Add,
            modifier = Modifier.testTag("add_goal_button"),
          )
        }
      }

      if (message != null) {
        item {
          SkyplusInlineMessage(
            text = message,
            tone = if (isErrorMessage) SkyplusMessageTone.ERROR else SkyplusMessageTone.SUCCESS,
            modifier = Modifier.testTag("goal_message"),
          )
        }
      }

      if (isLoading && goals.isEmpty()) {
        item {
          SkyplusSkeletonCard(modifier = Modifier.fillMaxWidth().testTag("goals_loading"))
        }
        item {
          SkyplusSkeletonCard(modifier = Modifier.fillMaxWidth().testTag("goals_loading_second"))
        }
      } else if (goals.isEmpty()) {
        item {
          SkyplusEmptyState(
            title = "No savings goals yet",
            message = "Create a goal, then add contributions to watch it fill up.",
            icon = Icons.Rounded.Savings,
            modifier = Modifier.testTag("goals_empty_state"),
          )
        }
      } else {
        items(goals, key = { it.goal.id }) { summary ->
          GoalCard(
            summary = summary,
            onContribute = { contributingTo = summary },
            onEdit = { editing = summary },
            onDelete = { deleting = summary },
          )
        }
      }
    }
  }

  editing?.let { summary ->
    GoalFormDialog(
      existing = summary.goal,
      currencyLocked = summary.contributionCount > 0,
      onDismiss = { editing = null },
      onSave = { name, currency, target, note, deadline, done ->
        onEditGoal(summary.goal, name, currency, target, note, deadline, done)
      },
    )
  }

  if (showCreateDialog) {
    GoalFormDialog(
      onDismiss = { showCreateDialog = false },
      onSave = { name, currency, target, note, deadline, done ->
        onCreateGoal(name, currency, target, note, deadline, done)
      },
    )
  }

  contributingTo?.let { summary ->
    ContributionDialog(
      summary = summary,
      onDismiss = { contributingTo = null },
      onSave = { amount, note, done -> onAddContribution(summary, amount, note, done) },
    )
  }

  deleting?.let { summary ->
    SkyplusConfirmDialog(
      title = "Delete goal?",
      message = "This removes the goal and every contribution recorded against it. Your accounts and transactions are not affected.",
      confirmLabel = "Delete",
      destructive = true,
      onConfirm = {
        onDeleteGoal(summary.goal.id) { deleting = null }
        deleting = null
      },
      onDismiss = { deleting = null },
    )
  }
}

@Composable
private fun GoalCard(
  summary: SavingsGoalSummary,
  onContribute: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val extended = OceanTheme.colors
  val formatter = remember { FormatMoneyUseCase() }
  val currency = summary.goal.currencyCode
  // A stored code that is not a real ISO currency must not crash the list. Show the raw figures
  // with the code and flag it, so the data problem stays visible instead of being hidden.
  val supported = Money.isSupportedCurrency(currency)
  val savedText =
    if (supported) formatter.format(Money.fromMinorUnits(summary.savedMinor, currency))
    else "$currency ${summary.savedMinor}"
  val targetText =
    if (supported) formatter.format(Money.fromMinorUnits(summary.goal.targetMinor, currency))
    else "$currency ${summary.goal.targetMinor}"
  val remainingText =
    if (supported) formatter.format(Money.fromMinorUnits(summary.remainingMinor, currency))
    else "$currency ${summary.remainingMinor}"
  val progress = (summary.progressPercent.toFloat() / 100f).coerceIn(0f, 1f)

  OceanCard(modifier = Modifier.fillMaxWidth().testTag("goal_card_${summary.goal.id}")) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = summary.goal.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(OceanSpacing.xxs))
          if (summary.isComplete) {
            OceanStatusBadge(
              text = if (summary.isOverTarget) "Over target" else "Complete",
              tone = BadgeTone.POSITIVE,
            )
          }
        }
        Text(
          text = "${summary.contributionCount} contributions",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        summary.goal.deadlineMillis?.let { deadline ->
          Text(
            text = "Due ${GoalDeadlineParser.format(deadline)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("goal_deadline_${summary.goal.id}"),
          )
        }
        if (!supported) {
          OceanStatusBadge(text = "Unsupported currency", tone = BadgeTone.WARNING)
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onEdit,
          modifier = Modifier.testTag("edit_goal_${summary.goal.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = "Edit goal",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = onDelete,
          modifier = Modifier.testTag("delete_goal_${summary.goal.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = "Delete goal",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.sm))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text(
          text = "Saved",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = savedText,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = extended.positive,
          modifier = Modifier.testTag("goal_saved_${summary.goal.id}"),
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "Target",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = targetText,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
      color = if (summary.isComplete) extended.positive else MaterialTheme.colorScheme.tertiary,
      trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.xxs))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text =
          if (summary.isComplete) {
            String.format(Locale.getDefault(), "%.1f%% of target", summary.progressPercent.toDouble())
          } else {
            "$remainingText to go"
          },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("goal_remaining_${summary.goal.id}"),
      )
      SkyplusTertiaryButton(
        text = "Add money",
        onClick = onContribute,
        modifier = Modifier.testTag("contribute_goal_${summary.goal.id}"),
      )
    }
  }
}

@Composable
private fun GoalFormDialog(
  onDismiss: () -> Unit,
  onSave: (name: String, currencyCode: String, targetDecimal: String, note: String?, deadlineText: String, onSuccess: () -> Unit) -> Unit,
  existing: SavingsGoal? = null,
  currencyLocked: Boolean = false,
) {
  var name by remember { mutableStateOf(existing?.name.orEmpty()) }
  var currency by remember { mutableStateOf(existing?.currencyCode ?: "INR") }
  var target by remember {
    mutableStateOf(
      existing
        ?.let { goal ->
          // A stored code that is not a real ISO currency must never throw while composing. Show
          // the raw minor units instead, which the user can correct by picking a real currency.
          if (Money.isSupportedCurrency(goal.currencyCode)) {
            Money.fromMinorUnits(goal.targetMinor, goal.currencyCode).toBigDecimal().toPlainString()
          } else {
            goal.targetMinor.toString()
          }
        }
        .orEmpty()
    )
  }
  var note by remember { mutableStateOf(existing?.note.orEmpty()) }
  var deadline by remember { mutableStateOf(GoalDeadlineParser.format(existing?.deadlineMillis)) }
  var formError by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("goal_form_dialog"),
    title = { Text(if (existing == null) "New savings goal" else "Edit goal") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
        SkyplusTextField(
          value = name,
          onValueChange = { name = it },
          label = "Goal name",
          placeholder = "Emergency fund",
          modifier = Modifier.testTag("goal_name_input"),
        )
        if (currencyLocked) {
          Text(
            text = "Currency: $currency — cannot change once the goal has contributions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("goal_currency_locked"),
          )
        } else {
          SkyplusTextField(
            value = currency,
            onValueChange = { input -> currency = input.uppercase().filter { it.isLetter() }.take(3) },
            label = "Currency",
            placeholder = "INR",
            modifier = Modifier.testTag("goal_currency_input"),
          )
        }
        SkyplusTextField(
          value = target,
          onValueChange = { target = it },
          label = "Target amount",
          placeholder = "100000",
          modifier = Modifier.testTag("goal_target_input"),
        )
        SkyplusTextField(
          value = deadline,
          onValueChange = { deadline = it },
          label = "Deadline (optional, dd/MM/yyyy)",
          placeholder = "31/12/2026",
          modifier = Modifier.testTag("goal_deadline_input"),
        )
        SkyplusTextField(
          value = note,
          onValueChange = { note = it },
          label = "Note (optional)",
          placeholder = "",
          modifier = Modifier.testTag("goal_note_input"),
        )
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
          if (name.isBlank()) {
            formError = "Enter a goal name"
            return@SkyplusPrimaryButton
          }
          if (!Money.isSupportedCurrency(currency)) {
            formError = "'$currency' is not a supported currency"
            return@SkyplusPrimaryButton
          }
          if (currencyLocked && existing != null &&
            !currency.equals(existing.currencyCode, ignoreCase = true)
          ) {
            formError = "The currency cannot change once the goal has contributions"
            return@SkyplusPrimaryButton
          }
          formError = null
          onSave(name, currency, target, note.ifBlank { null }, deadline) { onDismiss() }
        },
        modifier = Modifier.testTag("save_goal_button"),
      )
    },
    dismissButton = {
      SkyplusTertiaryButton(
        text = "Cancel",
        onClick = onDismiss,
        modifier = Modifier.testTag("goal_form_cancel"),
      )
    },
  )
}

@Composable
private fun ContributionDialog(
  summary: SavingsGoalSummary,
  onDismiss: () -> Unit,
  onSave: (amountDecimal: String, note: String?, onSuccess: () -> Unit) -> Unit,
) {
  var amount by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var formError by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("contribution_dialog"),
    title = { Text("Add to ${summary.goal.name}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
        Text(
          text = "Currency is fixed to ${summary.goal.currencyCode}. Contributions in another currency are not converted.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SkyplusTextField(
          value = amount,
          onValueChange = { amount = it },
          label = "Amount",
          placeholder = "5000",
          modifier = Modifier.testTag("contribution_amount_input"),
        )
        SkyplusTextField(
          value = note,
          onValueChange = { note = it },
          label = "Note (optional)",
          placeholder = "",
          modifier = Modifier.testTag("contribution_note_input"),
        )
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
        text = "Add",
        onClick = {
          if (amount.isBlank()) {
            formError = "Enter an amount"
            return@SkyplusPrimaryButton
          }
          formError = null
          onSave(amount, note.ifBlank { null }) { onDismiss() }
        },
        modifier = Modifier.testTag("save_contribution_button"),
      )
    },
    dismissButton = { SkyplusTertiaryButton(text = "Cancel", onClick = onDismiss) },
  )
}
