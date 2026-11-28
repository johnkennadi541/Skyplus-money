package com.example.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme

enum class BadgeTone {
  INFO,
  POSITIVE,
  WARNING,
  NEGATIVE,
  GOLD,
}

@Composable
fun OceanCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  // Neutral, elevated surface: financial figures stay readable without heavy tinted cards.
  // Material theme tokens keep this consistent in both light and dark modes.
  val containerColor = MaterialTheme.colorScheme.surface
  val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))

  if (onClick != null) {
    Card(
      onClick = onClick,
      modifier = modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = containerColor),
      border = border,
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
      Column(modifier = Modifier.padding(OceanSpacing.md), content = content)
    }
  } else {
    Card(
      modifier = modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.medium,
      colors = CardDefaults.cardColors(containerColor = containerColor),
      border = border,
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
      Column(modifier = Modifier.padding(OceanSpacing.md), content = content)
    }
  }
}

@Composable
fun OceanStatusBadge(
  text: String,
  tone: BadgeTone,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val (bg, fg) =
    when (tone) {
      BadgeTone.INFO ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
      BadgeTone.POSITIVE -> extended.positiveContainer to extended.positive
      BadgeTone.WARNING -> extended.warningContainer to extended.warning
      BadgeTone.NEGATIVE -> extended.negativeContainer to extended.negative
      BadgeTone.GOLD ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }

  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.small,
    color = bg,
    border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = fg,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun OceanSectionHeader(
  title: String,
  subtitle: String? = null,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    if (subtitle != null) {
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun OceanFoundationPlaceholder(
  title: String,
  badgeText: String,
  emptyTitle: String,
  emptyBody: String,
  icon: ImageVector,
  highlights: List<Pair<String, String>>,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = OceanSpacing.md, vertical = OceanSpacing.md)
        .widthIn(max = 720.dp)
        .testTag(testTag),
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      OceanStatusBadge(text = badgeText, tone = BadgeTone.GOLD)
    }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      border = BorderStroke(1.dp, extended.cardBorder),
      color = Color.Transparent,
    ) {
      Box(
        modifier =
          Modifier.background(
              Brush.linearGradient(
                listOf(extended.heroGradientStart, extended.heroGradientEnd)
              )
            )
            .padding(OceanSpacing.lg)
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            modifier =
              Modifier.size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = icon,
              contentDescription = title,
              tint = extended.gold,
              modifier = Modifier.size(32.dp),
            )
          }
          Spacer(modifier = Modifier.height(OceanSpacing.md))
          Text(
            text = emptyTitle,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(
            text = emptyBody,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFD3E4F4),
            textAlign = TextAlign.Center,
          )
        }
      }
    }

    OceanCard {
      Text(
        text = "Planned Capabilities",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.sm))
      highlights.forEachIndexed { index, (label, detail) ->
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
          Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (index < highlights.lastIndex) {
          Spacer(modifier = Modifier.height(OceanSpacing.sm))
        }
      }
    }
  }
}
