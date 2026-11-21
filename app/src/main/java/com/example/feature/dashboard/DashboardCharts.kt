package com.example.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.core.designsystem.theme.PortfolioPalette
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/** Which of the two series in a month group is emphasised. */
enum class TrendSeries { INCOME, EXPENSES }

/** The selected month plus the series the user is looking at. */
data class TrendSelection(val monthKey: String, val series: TrendSeries)

/**
 * Grouped income-versus-expense bars, one group per month.
 *
 * Touch targets: each month is ONE tap target at least [MonthColumnWidth] wide and the
 * full chart height tall, which satisfies the 48 dp guideline on every screen size. The
 * chart scrolls horizontally rather than squeezing two sub-48 dp bars per month, so the
 * target size never depends on how many months are shown. The income/expense series is
 * chosen with the chips above the chart, so a month and a series are both selectable
 * without a narrow target. Content descriptions are additional information, not a
 * substitute for the target size.
 */
@Composable
fun IncomeExpenseTrendChart(
  trends: List<HomeTrendPoint>,
  selection: TrendSelection?,
  onSelect: (TrendSelection) -> Unit,
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
  val scrollState = rememberScrollState()

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier.fillMaxWidth().height(chartHeight).horizontalScroll(scrollState),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
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
        val isSelectedMonth = selection?.monthKey == point.monthKey
        val description =
          "${point.label}. Income ${point.income.formatted}, expenses " +
            "${point.expenses.formatted}. Select to show the exact figures."

        Column(
          modifier =
            Modifier.width(MonthColumnWidth)
              .fillMaxHeight()
              .testTag("home_trend_month_${point.monthKey}")
              .semantics {
                contentDescription = description
                role = Role.Button
                selected = isSelectedMonth
              }
              .clickable {
                val series = selection?.series ?: TrendSeries.INCOME
                onSelect(TrendSelection(point.monthKey, series))
              },
          verticalArrangement = Arrangement.Bottom,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
          ) {
            Box(
              modifier =
                Modifier.weight(1f)
                  .fillMaxHeight(incomeBar.coerceAtLeast(0.02f))
                  .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                  .background(
                    if (!isSelectedMonth || selection?.series == TrendSeries.INCOME) incomeColor
                    else incomeColor.copy(alpha = 0.45f)
                  )
            )
            Box(
              modifier =
                Modifier.weight(1f)
                  .fillMaxHeight(expenseBar.coerceAtLeast(0.02f))
                  .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                  .background(
                    if (!isSelectedMonth || selection?.series == TrendSeries.EXPENSES) expenseColor
                    else expenseColor.copy(alpha = 0.45f)
                  )
            )
          }
          Text(
            text = point.label,
            modifier = Modifier.fillMaxWidth().padding(top = OceanSpacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            color =
              if (isSelectedMonth) MaterialTheme.colorScheme.onSurface
              else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelectedMonth) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    Row(
      modifier = Modifier.padding(top = OceanSpacing.xs),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ChartLegendItem(color = incomeColor, label = "Income")
      ChartLegendItem(color = expenseColor, label = "Expenses")
      Text(
        text = "Swipe for more months",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Width of one month tap target. Wider than the 48 dp minimum on purpose. */
private val MonthColumnWidth = 56.dp

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
 * Portfolio allocation ring with real segment selection.
 *
 * Colours come from [PortfolioPalette.assign], so each category keeps its colour across
 * recompositions, refreshes and app restarts, and no two categories on the ring share
 * one. Tapping a segment selects that category and reports its canonical key; the
 * selected arc is drawn thicker and fully opaque. The centre total always stays visible.
 *
 * TalkBack: the ring carries a summary, and the caller's legend rows are the per-category
 * accessible interface — each is a focusable button with its own description, selection
 * state and click label. The ring itself is not the only way to reach a category.
 */
@Composable
fun AllocationRing(
  slices: List<HomeAllocationSlice>,
  isDark: Boolean,
  selectedKey: String?,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
  centerLabel: String? = null,
  centerCaption: String? = null,
  diameter: Dp = 156.dp,
) {
  if (slices.isEmpty()) return

  val colors =
    remember(slices, isDark) {
      PortfolioPalette.assign(slices.map { it.colorKey }, isDark)
    }
  val totalMinor = remember(slices) { slices.sumOf { it.value.minorUnits }.coerceAtLeast(0L) }
  val description =
    remember(slices) {
      slices.joinToString(separator = ", ") { slice ->
        "${slice.categoryName} ${slice.sharePercent}%"
      }
    }
  val emptyTrack = MaterialTheme.colorScheme.surfaceVariant

  Box(
    modifier =
      modifier
        .size(diameter)
        .semantics { contentDescription = "Allocation: $description" }
        .pointerInput(slices, totalMinor) {
          detectTapGestures { offset ->
            val center = Offset(size.width / 2f, size.height / 2f)
            val dx = offset.x - center.x
            val dy = offset.y - center.y
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val outer = min(size.width, size.height) / 2f
            val stroke = outer * 0.34f
            val inner = outer - stroke
            if (distance < inner || distance > outer || totalMinor <= 0L) return@detectTapGestures

            val fromTop = ((Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f) + 360f) % 360f
            var swept = 0f
            slices.forEach { slice ->
              val sweep = (slice.value.minorUnits.toFloat() / totalMinor.toFloat()) * 360f
              if (fromTop >= swept && fromTop < swept + sweep) {
                onSelect(slice.colorKey)
                return@detectTapGestures
              }
              swept += sweep
            }
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokeWidth = size.minDimension * 0.17f
      val ringSize = Size(size.width - strokeWidth, size.height - strokeWidth)
      val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

      drawArc(
        color = emptyTrack,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        style = Stroke(width = strokeWidth),
        topLeft = topLeft,
        size = ringSize,
      )

      if (totalMinor <= 0L) return@Canvas

      var startAngle = -90f
      slices.forEach { slice ->
        val sweep = (slice.value.minorUnits.toFloat() / totalMinor.toFloat()) * 360f
        val drawnSweep = (sweep - 2f).coerceAtLeast(0.6f)
        val canonical = PortfolioPalette.canonical(slice.colorKey)
        val baseColour = colors[canonical] ?: PortfolioPalette.colorFor(slice.colorKey, isDark)
        val isSelected = canonical == selectedKey
        val colour =
          when {
            selectedKey == null -> baseColour
            isSelected -> baseColour
            else -> baseColour.copy(alpha = 0.35f)
          }
        drawArc(
          color = colour,
          startAngle = startAngle,
          sweepAngle = drawnSweep,
          useCenter = false,
          style =
            Stroke(
              width = if (isSelected) strokeWidth * 1.25f else strokeWidth,
              cap = StrokeCap.Butt,
            ),
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
