package com.example.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.SkyplusIconSize

/**
 * Standard message dialog. The destructive flag turns the confirm button red and is used for
 * anything that removes data, so the colour always matches the consequence.
 */
@Composable
fun SkyplusAlertDialog(
  title: String,
  message: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  dismissLabel: String? = "Cancel",
  destructive: Boolean = false,
  showWarningIcon: Boolean = false,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    shape = MaterialTheme.shapes.large,
    icon =
      if (showWarningIcon) {
        {
          Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(SkyplusIconSize.md),
          )
        }
      } else {
        null
      },
    title = {
      Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Start)
    },
    text = {
      Text(text = message, style = MaterialTheme.typography.bodyMedium)
    },
    confirmButton = {
      if (destructive) {
        SkyplusDestructiveButton(text = confirmLabel, onClick = onConfirm)
      } else {
        SkyplusPrimaryButton(text = confirmLabel, onClick = onConfirm)
      }
    },
    dismissButton =
      dismissLabel?.let {
        { SkyplusTertiaryButton(text = it, onClick = onDismiss) }
      },
  )
}

/** Thin wrapper that makes the intent explicit at the call site. */
@Composable
fun SkyplusConfirmDialog(
  title: String,
  message: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  destructive: Boolean = false,
) {
  SkyplusAlertDialog(
    title = title,
    message = message,
    confirmLabel = confirmLabel,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    destructive = destructive,
    showWarningIcon = destructive,
  )
}

/**
 * Bottom sheet foundation for forms and pickers. Content is supplied by the caller; the sheet
 * supplies the shape, drag handle behaviour and safe padding so later stages stay consistent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyplusBottomSheet(
  onDismissRequest: () -> Unit,
  title: String? = null,
  content: @Composable () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = sheetState,
    shape = MaterialTheme.shapes.extraLarge,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = OceanSpacing.md),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
    ) {
      if (title != null) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(OceanSpacing.xxs))
      }
      content()
      Spacer(modifier = Modifier.height(OceanSpacing.lg))
    }
  }
}
