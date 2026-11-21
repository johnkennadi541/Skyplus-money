package com.example.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.core.designsystem.theme.PortfolioPalette

/**
 * Grouped income-versus-expense bars, one group per month.
 *
 * Income is always green and expenses are always red, in both themes. Values come
 * straight from the existing monthly trend rows; nothing is interpolated or invented.
 * Every month's exact figures are also available as text in the caller, so the chart
 * is never the only way to read a number.
 */
@Composable
fun IncomeExpenseTrendChart(
  trends: List<HomeTrendPoint>,
  modifier: Modifier = Modifier,
  chartHeight: Dp = 112.dp,
) {
  if (trends.isEmpty()) return

  val incomeColor = OceanTheme.colors.positive
  val expenseColor = OceanTheme.colors.negative
  val maxMinor =
    remember(trends) {
      trends.maxOfOrNull { point -> maxOf(point.income.minorUnits, point.expenses.minorUnits) } ?: 0L
    }

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().height(chartHeight),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      verticalAlignment = Alignment.Bottom,
    ) {
      trends.forEach { point ->
        val incomeTarget = fractionOf(point.income.minorUnits, maxMinor)
        val expenseTarget = fractionOf(point.expenses.minorUnits, maxMinor)
        val incomeBar by
          animateFloatAsState(targetValue = incomeTarget, animationSpec = tween(400), label = "incomeBar")
        val expenseBar by
          animateFloatAsState(
            targetValue = expenseTarget,
            animationSpec = tween(400),
            label = "expenseBar",
          )

        Row(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          horizontalArrangement = Arrangement.spacedBy(3.dp),
          verticalAlignment = Alignment.Bottom,
        ) {
          Box(
            modifier =
              Modifier.weight(1f)
                .fillMaxHeight(incomeBar.coerceAtLeast(0.02f))
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(incomeColor)
          )
          Box(
            modifier =
              Modifier.weight(1f)
                .fillMaxHeight(expenseBar.coerceAtLeast(0.02f))
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(expenseColor)
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().padding(top = OceanSpacing.xxs),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
    ) {
      trends.forEach { point ->
        Text(
          text = point.label,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Row(
      modifier = Modifier.padding(top = OceanSpacing.xs),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ChartLegendItem(color = incomeColor, label = "Income")
      ChartLegendItem(color = expenseColor, label = "Expenses")
    }
  }
}

@Composable
fun ChartLegendItem(color: Color, label: String, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xxs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * Portfolio allocation ring.
 *
 * Colours come from [PortfolioPalette.assign], so each category keeps its colour across
 * recompositions, refreshes and app restarts, and no two categories on the ring share
 * one. The whole ring carries a single content description listing every slice with its
 * share, so screen readers do not need the arcs.
 */
@Composable
fun AllocationRing(
  slices: List<HomeAllocationSlice>,
  isDark: Boolean,
  modifier: Modifier = Modifier,
  centerLabel: String? = null,
  centerCaption: String? = null,
  diameter: Dp = 156.dp,
) {
  if (slices.isEmpty()) return

  val colors = remember(slices, isDark) { PortfolioPalette.assign(slices.map { it.colorKey }, isDark) }
  val totalMinor = remember(slices) { slices.sumOf { it.value.minorUnits }.coerceAtLeast(0L) }
  val description =
    remember(slices) {
      slices.joinToString(separator = ", ") { slice ->
        "${slice.categoryName} ${slice.sharePercent}%"
      }
    }
  val emptyTrack = MaterialTheme.colorScheme.surfaceVariant

  Box(
    modifier = modifier.size(diameter).semantics { contentDescription = "Allocation: $description" },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stroke = size.minDimension * 0.17f
      val ringSize = Size(size.width - stroke, size.height - stroke)
      val topLeft = Offset(stroke / 2f, stroke / 2f)

      drawArc(
        color = emptyTrack,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        style = Stroke(width = stroke),
        topLeft = topLeft,
        size = ringSize,
      )

      if (totalMinor <= 0L) return@Canvas

      var startAngle = -90f
      slices.forEach { slice ->
        val sweep = (slice.value.minorUnits.toFloat() / totalMinor.toFloat()) * 360f
        val drawnSweep = (sweep - 2f).coerceAtLeast(0.6f)
        val color = colors[slice.colorKey] ?: PortfolioPalette.colorFor(slice.colorKey, isDark)
        drawArc(
          color = color,
          startAngle = startAngle,
          sweepAngle = drawnSweep,
          useCenter = false,
          style = Stroke(width = stroke, cap = StrokeCap.Butt),
          topLeft = topLeft,
          size = ringSize,
        )
        startAngle += sweep
      }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      if (centerLabel != null) {
        Text(
          text = centerLabel,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
        )
      }
      if (centerCaption != null) {
        Text(
          text = centerCaption,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

private fun fractionOf(value: Long, max: Long): Float {
  if (max <= 0L) return 0f
  return (value.toDouble() / max.toDouble()).toFloat().coerceIn(0f, 1f)
}
