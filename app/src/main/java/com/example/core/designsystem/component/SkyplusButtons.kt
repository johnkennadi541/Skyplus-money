package com.example.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.SkyplusIconSize
import com.example.core.designsystem.theme.SkyplusSizes

/**
 * Stage 1 button set. One place decides height, radius, padding and colours so screens never
 * hand-roll a button again.
 *
 * The primary brand colour is used for the main action; semantic warning yellow is reserved
 * for warning states rather than being used as a general action colour.
 */
@Composable
fun SkyplusPrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  loading: Boolean = false,
  icon: ImageVector? = null,
) {
  val colors = MaterialTheme.colorScheme
  Button(
    onClick = onClick,
    modifier = modifier.defaultMinSize(minHeight = SkyplusSizes.buttonHeight),
    enabled = enabled && !loading,
    shape = MaterialTheme.shapes.small,
    colors =
      ButtonDefaults.buttonColors(
        containerColor = colors.primary,
        contentColor = colors.onPrimary,
        disabledContainerColor = colors.primary.copy(alpha = 0.38f),
        disabledContentColor = colors.onPrimary.copy(alpha = 0.6f),
      ),
  ) {
    ButtonContent(text = text, loading = loading, icon = icon, tint = colors.onPrimary)
  }
}

@Composable
fun SkyplusSecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  loading: Boolean = false,
  icon: ImageVector? = null,
) {
  val colors = MaterialTheme.colorScheme
  OutlinedButton(
    onClick = onClick,
    modifier = modifier.defaultMinSize(minHeight = SkyplusSizes.buttonHeight),
    enabled = enabled && !loading,
    shape = MaterialTheme.shapes.small,
    border = BorderStroke(1.dp, colors.outline),
    colors =
      ButtonDefaults.outlinedButtonColors(
        contentColor = colors.onSurface,
        disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
      ),
  ) {
    ButtonContent(text = text, loading = loading, icon = icon, tint = colors.onSurface)
  }
}

@Composable
fun SkyplusTertiaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
    Text(text = text, style = MaterialTheme.typography.labelLarge)
  }
}

/** Destructive action (delete a record, sign out of everything). Never the primary path. */
@Composable
fun SkyplusDestructiveButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
) {
  val colors = MaterialTheme.colorScheme
  Button(
    onClick = onClick,
    modifier = modifier.defaultMinSize(minHeight = SkyplusSizes.buttonHeight),
    enabled = enabled,
    shape = MaterialTheme.shapes.small,
    colors =
      ButtonDefaults.buttonColors(
        containerColor = colors.error,
        contentColor = colors.onError,
        disabledContainerColor = colors.error.copy(alpha = 0.38f),
        disabledContentColor = colors.onError.copy(alpha = 0.6f),
      ),
  ) {
    ButtonContent(text = text, loading = false, icon = icon, tint = colors.onError)
  }
}

/**
 * Icon-only action. The box is always at least 48dp so it satisfies the accessibility minimum,
 * and [contentDescription] is required: an icon with no label is unusable with TalkBack.
 */
@Composable
fun SkyplusIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  tint: androidx.compose.ui.graphics.Color? = null,
) {
  IconButton(
    onClick = onClick,
    modifier = modifier.defaultMinSize(
      minWidth = SkyplusIconSize.minTouchTarget,
      minHeight = SkyplusIconSize.minTouchTarget,
    ),
    enabled = enabled,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(SkyplusIconSize.md),
    )
  }
}

@Composable
private fun ButtonContent(
  text: String,
  loading: Boolean,
  icon: ImageVector?,
  tint: androidx.compose.ui.graphics.Color,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = tint,
      )
      Spacer(modifier = Modifier.width(OceanSpacing.xs))
    } else if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(SkyplusIconSize.sm),
      )
      Spacer(modifier = Modifier.width(OceanSpacing.xs))
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.padding(vertical = 2.dp),
    )
  }
}
