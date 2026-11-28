package com.example.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.core.designsystem.theme.SkyplusChartPalette
import com.example.core.designsystem.theme.SkyplusElevation

/**
 * Shared frame for every chart. Charts in later stages only have to draw inside the box; the
 * title, subtitle, legend slot and empty fallback stay identical across the app.
 */
@Composable
fun SkyplusChartContainer(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  actions: (@Composable () -> Unit)? = null,
  minHeight: Int = 180,
  isEmpty: Boolean = false,
  emptyMessage: String = "No data for this period yet.",
  content: @Composable () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = colors.surface,
    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.72f)),
    shadowElevation = SkyplusElevation.low,
  ) {
    Column(modifier = Modifier.padding(OceanSpacing.md)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (subtitle != null) {
            Text(
              text = subtitle,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (actions != null) {
          actions()
        }
      }
      Spacer(modifier = Modifier.height(OceanSpacing.sm))
      Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = minHeight.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (isEmpty) {
          Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          content()
        }
      }
    }
  }
}

/** Colour for series [index] from the active theme's chart palette, wrapping safely. */
@Composable
fun chartSeriesColor(index: Int): androidx.compose.ui.graphics.Color {
  val palette: SkyplusChartPalette = OceanTheme.chartPalette
  val series = palette.series
  return if (series.isEmpty()) {
    MaterialTheme.colorScheme.tertiary
  } else {
    series[index % series.size]
  }
}
