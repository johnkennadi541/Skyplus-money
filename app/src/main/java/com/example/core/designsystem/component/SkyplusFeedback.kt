package com.example.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.core.designsystem.theme.SkyplusElevation
import com.example.core.designsystem.theme.SkyplusIconSize
import com.example.core.designsystem.theme.SkyplusMotion
import com.example.core.designsystem.theme.SkyplusRadius

/** Severity of an inline message. Colour alone never carries the meaning: the icon does. */
enum class SkyplusMessageTone {
  INFO,
  SUCCESS,
  WARNING,
  ERROR,
}

/**
 * Empty state for a screen or a list that has no data yet. Always explains what the user can do
 * next instead of showing a blank panel.
 */
@Composable
fun SkyplusEmptyState(
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  action: (@Composable () -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  Column(
    modifier = modifier.fillMaxWidth().padding(OceanSpacing.lg),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (icon != null) {
      Box(
        modifier =
          Modifier.size(SkyplusIconSize.xl * 1.5f)
            .clip(CircleShape)
            .background(colors.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = colors.onSurfaceVariant,
          modifier = Modifier.size(SkyplusIconSize.lg),
        )
      }
      Spacer(modifier = Modifier.height(OceanSpacing.md))
    }
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = colors.onSurface,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = colors.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    if (action != null) {
      Spacer(modifier = Modifier.height(OceanSpacing.md))
      action()
    }
  }
}

/** Full-screen loading state. Announces itself once for TalkBack. */
@Composable
fun SkyplusLoadingState(
  modifier: Modifier = Modifier,
  label: String = "Loading",
) {
  Box(
    modifier = modifier.fillMaxWidth().padding(OceanSpacing.lg),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.semantics { contentDescription = label },
    ) {
      CircularProgressIndicator(
        color = MaterialTheme.colorScheme.tertiary,
        strokeWidth = 3.dp,
        modifier = Modifier.size(SkyplusIconSize.lg),
      )
      Spacer(modifier = Modifier.height(OceanSpacing.sm))
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** One pulsing placeholder block. */
@Composable
fun SkyplusSkeletonBlock(
  modifier: Modifier = Modifier,
  shape: RoundedCornerShape = RoundedCornerShape(SkyplusRadius.sm),
) {
  val transition = rememberInfiniteTransition(label = "skeleton")
  val alpha by
    transition.animateFloat(
      initialValue = 0.28f,
      targetValue = 0.62f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = SkyplusMotion.skeletonPulse, easing = SkyplusMotion.standard),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "skeletonAlpha",
    )
  Box(modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
}

/** Skeleton that mimics the shape of a financial summary card while data loads. */
@Composable
fun SkyplusSkeletonCard(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = OceanTheme.colors.cardSubtle,
    border = BorderStroke(1.dp, OceanTheme.colors.cardBorder.copy(alpha = 0.6f)),
    shadowElevation = SkyplusElevation.low,
  ) {
    Column(modifier = Modifier.padding(OceanSpacing.md)) {
      SkyplusSkeletonBlock(modifier = Modifier.height(12.dp).fillMaxWidth(0.45f))
      Spacer(modifier = Modifier.height(OceanSpacing.sm))
      SkyplusSkeletonBlock(modifier = Modifier.height(24.dp).fillMaxWidth(0.7f))
      Spacer(modifier = Modifier.height(OceanSpacing.sm))
      SkyplusSkeletonBlock(modifier = Modifier.height(10.dp).fillMaxWidth(0.35f))
    }
  }
}

/** A short inline message: form errors, save confirmations, permission warnings. */
@Composable
fun SkyplusInlineMessage(
  text: String,
  tone: SkyplusMessageTone,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  action: (@Composable () -> Unit)? = null,
) {
  val extended = OceanTheme.colors
  val colors = MaterialTheme.colorScheme
  val (container, content) =
    when (tone) {
      SkyplusMessageTone.INFO -> extended.infoContainer to extended.info
      SkyplusMessageTone.SUCCESS -> extended.positiveContainer to extended.positive
      SkyplusMessageTone.WARNING -> extended.warningContainer to extended.warning
      SkyplusMessageTone.ERROR -> extended.negativeContainer to extended.negative
    }

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.small,
    color = container,
    border = BorderStroke(1.dp, content.copy(alpha = 0.35f)),
  ) {
    Row(
      modifier = Modifier.padding(OceanSpacing.sm),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = content,
          modifier = Modifier.size(SkyplusIconSize.sm),
        )
      }
      Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (extended.isDark) Color.White.copy(alpha = 0.92f) else colors.onSurface,
        modifier = Modifier.weight(1f),
      )
      if (action != null) {
        Spacer(modifier = Modifier.width(OceanSpacing.xxs))
        action()
      }
    }
  }
}
