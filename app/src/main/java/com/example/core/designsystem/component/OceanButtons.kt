package com.example.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.OceanSpacing

/**
 * SKYPLUS MONEY — reusable button set (Phase 1).
 *
 * Every button uses design tokens only, keeps a minimum 48 dp touch target and
 * shares the same shape, typography and loading behaviour, so screens stay
 * visually consistent without repeating `ButtonDefaults` code.
 */
private val MinButtonHeight = 48.dp
private val ButtonProgressSize = 18.dp

/** Primary action. Indigo container, one per screen region. */
@Composable
fun OceanPrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isLoading: Boolean = false,
  leadingIcon: ImageVector? = null,
) {
  Button(
    onClick = onClick,
    enabled = enabled && !isLoading,
    modifier = modifier.heightIn(min = MinButtonHeight),
    shape = MaterialTheme.shapes.medium,
    colors =
      ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.60f),
      ),
  ) {
    OceanButtonLabel(
      text = text,
      isLoading = isLoading,
      leadingIcon = leadingIcon,
      progressColor = MaterialTheme.colorScheme.onPrimary,
    )
  }
}

/** Secondary action. Teal container, used beside a primary button. */
@Composable
fun OceanSecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isLoading: Boolean = false,
  leadingIcon: ImageVector? = null,
) {
  Button(
    onClick = onClick,
    enabled = enabled && !isLoading,
    modifier = modifier.heightIn(min = MinButtonHeight),
    shape = MaterialTheme.shapes.medium,
    colors =
      ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f),
        disabledContentColor = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.60f),
      ),
  ) {
    OceanButtonLabel(
      text = text,
      isLoading = isLoading,
      leadingIcon = leadingIcon,
      progressColor = MaterialTheme.colorScheme.onSecondary,
    )
  }
}

/** Low-emphasis action. Outlined, primary text. */
@Composable
fun OceanOutlinedButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isLoading: Boolean = false,
  leadingIcon: ImageVector? = null,
) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled && !isLoading,
    modifier = modifier.heightIn(min = MinButtonHeight),
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    colors =
      ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
      ),
  ) {
    OceanButtonLabel(
      text = text,
      isLoading = isLoading,
      leadingIcon = leadingIcon,
      progressColor = MaterialTheme.colorScheme.primary,
    )
  }
}

/** Text-only action, for dialog actions and inline links. */
@Composable
fun OceanTextButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isDestructive: Boolean = false,
) {
  TextButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.heightIn(min = MinButtonHeight),
    shape = MaterialTheme.shapes.small,
    colors =
      ButtonDefaults.textButtonColors(
        contentColor =
          if (isDestructive) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.primary
          },
      ),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun OceanButtonLabel(
  text: String,
  isLoading: Boolean,
  leadingIcon: ImageVector?,
  progressColor: androidx.compose.ui.graphics.Color,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    if (isLoading) {
      CircularProgressIndicator(
        modifier = Modifier.size(ButtonProgressSize),
        color = progressColor,
        strokeWidth = 2.dp,
      )
      Spacer(modifier = Modifier.width(OceanSpacing.xs))
    } else if (leadingIcon != null) {
      Icon(
        imageVector = leadingIcon,
        contentDescription = null,
        modifier = Modifier.size(ButtonProgressSize),
      )
      Spacer(modifier = Modifier.width(OceanSpacing.xs))
    }

    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
    )
  }
}
