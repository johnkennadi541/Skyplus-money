package com.example.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme

/**
 * SKYPLUS MONEY — reusable loading, error and empty states (Phase 1).
 *
 * Every screen needs the same three states. They are defined once here so the
 * whole app reports them consistently, and so a screen can never show an empty
 * white area where data failed to load.
 */

/** Centred progress with an optional message. */
@Composable
fun OceanLoadingState(
  modifier: Modifier = Modifier,
  message: String? = null,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(OceanSpacing.lg),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(32.dp),
      color = MaterialTheme.colorScheme.primary,
      strokeWidth = 3.dp,
    )
    if (message != null) {
      Spacer(modifier = Modifier.height(OceanSpacing.sm))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/** Error state with an optional retry action. */
@Composable
fun OceanErrorState(
  message: String,
  modifier: Modifier = Modifier,
  title: String? = null,
  icon: ImageVector = Icons.Rounded.ErrorOutline,
  retryLabel: String? = null,
  onRetry: (() -> Unit)? = null,
  testTag: String? = null,
) {
  val extended = OceanTheme.colors

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    shape = MaterialTheme.shapes.medium,
    color = extended.negativeContainer,
    border = BorderStroke(1.dp, extended.negative.copy(alpha = 0.40f)),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(OceanSpacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = extended.negative,
        modifier = Modifier.size(28.dp),
      )
      if (title != null) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
        )
      }
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      if (onRetry != null && retryLabel != null) {
        OceanOutlinedButton(text = retryLabel, onClick = onRetry)
      }
    }
  }
}

/** Empty state with an optional call to action. */
@Composable
fun OceanEmptyState(
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  icon: ImageVector = Icons.Rounded.Inbox,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  testTag: String? = null,
) {
  val extended = OceanTheme.colors

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .widthIn(max = 520.dp)
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .padding(OceanSpacing.lg),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
  ) {
    Surface(
      modifier = Modifier.size(56.dp),
      shape = CircleShape,
      color = extended.cardSubtle,
      border = BorderStroke(1.dp, extended.cardBorder),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(26.dp),
        )
      }
    }
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onBackground,
      textAlign = TextAlign.Center,
    )
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    if (onAction != null && actionLabel != null) {
      OceanPrimaryButton(text = actionLabel, onClick = onAction)
    }
  }
}

/** Offline / connectivity state, so a network failure reads differently from an empty list. */
@Composable
fun OceanOfflineState(
  message: String,
  modifier: Modifier = Modifier,
  retryLabel: String? = null,
  onRetry: (() -> Unit)? = null,
) {
  OceanErrorState(
    message = message,
    modifier = modifier,
    icon = Icons.Rounded.CloudOff,
    retryLabel = retryLabel,
    onRetry = onRetry,
  )
}
