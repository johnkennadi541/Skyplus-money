package com.example.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.core.designsystem.theme.SkyplusElevation
import com.example.core.designsystem.theme.SkyplusIconSize

/** Tone of a delta or trend line: colour only, the wording stays neutral and factual. */
enum class SkyplusDeltaTone {
  POSITIVE,
  NEGATIVE,
  NEUTRAL,
}

/**
 * The one card used for every headline number in the app: balance, monthly spend, portfolio value.
 * The amount is rendered in the tabular-figure style so digits never jump while animating.
 */
@Composable
fun SkyplusFinancialSummaryCard(
  label: String,
  amount: String,
  modifier: Modifier = Modifier,
  caption: String? = null,
  delta: String? = null,
  deltaTone: SkyplusDeltaTone = SkyplusDeltaTone.NEUTRAL,
  icon: ImageVector? = null,
  onClick: (() -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  val extended = OceanTheme.colors
  val deltaColor =
    when (deltaTone) {
      SkyplusDeltaTone.POSITIVE -> extended.positive
      SkyplusDeltaTone.NEGATIVE -> extended.negative
      SkyplusDeltaTone.NEUTRAL -> colors.onSurfaceVariant
    }

  val content: @Composable () -> Unit = {
    Column(modifier = Modifier.padding(OceanSpacing.md)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = colors.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        if (icon != null) {
          Box(
            modifier =
              Modifier.size(SkyplusIconSize.lg)
                .clip(CircleShape)
                .background(extended.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = extended.accent,
              modifier = Modifier.size(SkyplusIconSize.sm),
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      Text(
        text = amount,
        style = MaterialTheme.typography.headlineMedium,
        color = colors.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (delta != null || caption != null) {
        Spacer(modifier = Modifier.height(OceanSpacing.xxs))
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (delta != null) {
            Text(
              text = delta,
              style = MaterialTheme.typography.labelSmall,
              color = deltaColor,
            )
            if (caption != null) {
              Spacer(modifier = Modifier.size(OceanSpacing.xs))
            }
          }
          if (caption != null) {
            Text(
              text = caption,
              style = MaterialTheme.typography.labelSmall,
              color = colors.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }

  if (onClick != null) {
    Card(
      onClick = onClick,
      modifier = modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = colors.surface),
      border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.72f)),
      elevation = CardDefaults.cardElevation(defaultElevation = SkyplusElevation.low),
      content = { content() },
    )
  } else {
    Card(
      modifier = modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = colors.surface),
      border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.72f)),
      elevation = CardDefaults.cardElevation(defaultElevation = SkyplusElevation.low),
      content = { content() },
    )
  }
}

/** Small secondary figure used in rows of two or three (income, expense, savings). */
@Composable
fun SkyplusMetricTile(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  tone: SkyplusDeltaTone = SkyplusDeltaTone.NEUTRAL,
) {
  val colors = MaterialTheme.colorScheme
  val extended = OceanTheme.colors
  val valueColor =
    when (tone) {
      SkyplusDeltaTone.POSITIVE -> extended.positive
      SkyplusDeltaTone.NEGATIVE -> extended.negative
      SkyplusDeltaTone.NEUTRAL -> colors.onSurface
    }
  Column(modifier = modifier) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = colors.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.xxs))
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      color = valueColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
