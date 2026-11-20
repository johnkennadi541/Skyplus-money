package com.example.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.OceanSpacing

/**
 * SKYPLUS MONEY — reusable dialog set (Phase 1).
 *
 * Confirmation and information dialogs share one surface, one title style and
 * one action row, so destructive prompts (delete a transaction, delete an
 * account) read the same everywhere. Destructive confirmations use the error
 * colour and never become the default action.
 */

/**
 * Confirmation dialog with a confirm and a dismiss action.
 *
 * @param isDestructive tints the confirm action with the error colour.
 */
@Composable
fun OceanConfirmDialog(
  title: String,
  message: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  dismissLabel: String? = null,
  isDestructive: Boolean = false,
  isConfirmEnabled: Boolean = true,
) {
  AlertDialog(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    containerColor = MaterialTheme.colorScheme.surface,
    title = { OceanDialogTitle(text = title) },
    text = { OceanDialogBody(text = message) },
    confirmButton = {
      OceanTextButton(
        text = confirmLabel,
        onClick = onConfirm,
        enabled = isConfirmEnabled,
        isDestructive = isDestructive,
      )
    },
    dismissButton =
      if (dismissLabel != null) {
        { OceanTextButton(text = dismissLabel, onClick = onDismissRequest) }
      } else {
        null
      },
  )
}

/** Single-action information dialog. */
@Composable
fun OceanInfoDialog(
  title: String,
  message: String,
  actionLabel: String,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AlertDialog(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    containerColor = MaterialTheme.colorScheme.surface,
    title = { OceanDialogTitle(text = title) },
    text = { OceanDialogBody(text = message) },
    confirmButton = { OceanTextButton(text = actionLabel, onClick = onDismissRequest) },
  )
}

@Composable
private fun OceanDialogTitle(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurface,
  )
}

@Composable
private fun OceanDialogBody(text: String) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
