package com.example.feature.reports

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.CategoryBreakdownRow
import com.example.domain.model.Money
import com.example.domain.model.MonthlyTrendRow
import com.example.domain.usecase.FormatMoneyUseCase

@Composable
fun IncomeVsExpenseBarChart(
  trends: List<MonthlyTrendRow>,
  currencyCode: String,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val incomeColor = extended.positive
  val expenseColor = extended.negative
  val gridColor = extended.cardBorder

  OceanCard(modifier = modifier.fillMaxWidth().testTag("income_vs_expense_chart_card")) {
    Text(
      text = "Monthly Income vs Expenses ($currencyCode)",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.xs))

    if (trends.isEmpty()) {
      Text(
        text = "No completed income or expense transactions recorded in this date window.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      return@OceanCard
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ChartLegendDot(color = incomeColor, label = "Income")
      ChartLegendDot(color = expenseColor, label = "Net Expenses")
    }

    Spacer(modifier = Modifier.height(OceanSpacing.sm))

    Canvas(
      modifier = Modifier.fillMaxWidth().height(150.dp).testTag("income_vs_expense_canvas"),
    ) {
      val visible = trends.takeLast(6)
      val maxVal =
        visible.maxOfOrNull { maxOf(it.incomeMinor, it.netExpenseMinor) }?.coerceAtLeast(1L) ?: 1L
      val padX = 8.dp.toPx()
      val padY = 10.dp.toPx()
      val plotW = (size.width - padX * 2f).coerceAtLeast(1f)
      val plotH = (size.height - padY * 2f).coerceAtLeast(1f)

      for (i in 0..2) {
        val y = padY + plotH * (i / 2f)
        drawLine(
          color = gridColor.copy(alpha = 0.5f),
          start = Offset(padX, y),
          end = Offset(padX + plotW, y),
          strokeWidth = 1.dp.toPx(),
        )
      }

      val groupW = plotW / visible.size.toFloat()
      val barW = (groupW * 0.28f).coerceIn(8f, 26f)
      val gap = 4.dp.toPx()

      visible.forEachIndexed { idx, row ->
        val centerX = padX + groupW * idx + groupW / 2f
        val incHeight = (row.incomeMinor.toFloat() / maxVal.toFloat()) * plotH
        val expHeight = (row.netExpenseMinor.toFloat() / maxVal.toFloat()) * plotH

        if (row.incomeMinor > 0L) {
          drawRoundRect(
            color = incomeColor,
            topLeft = Offset(centerX - barW - gap / 2f, padY + plotH - incHeight),
            size = Size(barW, incHeight.coerceAtLeast(3f)),
            cornerRadius = CornerRadius(4f, 4f),
          )
        }
        if (row.netExpenseMinor > 0L) {
          drawRoundRect(
            color = expenseColor,
            topLeft = Offset(centerX + gap / 2f, padY + plotH - expHeight),
            size = Size(barW, expHeight.coerceAtLeast(3f)),
            cornerRadius = CornerRadius(4f, 4f),
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      val visible = trends.takeLast(6)
      visible.forEach { row ->
        Text(
          text = row.monthLabel.take(6),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun CategoryBreakdownChartCard(
  title: String,
  rows: List<CategoryBreakdownRow>,
  currencyCode: String,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val palette =
    listOf(
      MaterialTheme.colorScheme.primary,
      MaterialTheme.colorScheme.secondary,
      extended.gold,
      extended.positive,
      extended.warning,
      extended.negative,
    )

  OceanCard(modifier = modifier.fillMaxWidth().testTag("category_breakdown_chart_card")) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.xs))

    if (rows.isEmpty()) {
      Text(
        text = "No category entries recorded for this window.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      return@OceanCard
    }

    val totalPositive = rows.sumOf { maxOf(0L, it.totalMinor) }
    if (totalPositive > 0L) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = OceanSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      ) {
        val trackColor = extended.cardBorder
        Canvas(modifier = Modifier.size(96.dp).testTag("category_donut_canvas")) {
          val strokeW = 16.dp.toPx()
          val arcSize = Size(size.width - strokeW, size.height - strokeW)
          val topLeft = Offset(strokeW / 2f, strokeW / 2f)
          drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeW),
          )
          var startAngle = -90f
          rows.forEachIndexed { idx, row ->
            if (row.totalMinor > 0L) {
              val sweep = (row.totalMinor.toFloat() / totalPositive.toFloat()) * 360f
              drawArc(
                color = palette[idx % palette.size],
                startAngle = startAngle,
                sweepAngle = sweep.coerceAtLeast(2f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Butt),
              )
              startAngle += sweep
            }
          }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          rows.take(4).forEachIndexed { idx, row ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              ChartLegendDot(color = palette[idx % palette.size], label = row.category)
              Text(
                text = "${row.percentage.toPlainString()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
      rows.forEachIndexed { idx, row ->
        val barColor = palette[idx % palette.size]
        val fraction = (row.percentage.toFloat() / 100f).coerceIn(0f, 1f)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "${row.category} (${row.transactionCount})",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "${FormatMoneyUseCase().format(Money.fromMinorUnits(row.totalMinor, currencyCode))} • ${row.percentage.toPlainString()}%",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          LinearProgressIndicator(
            progress = { fraction },
            color = barColor,
            trackColor = extended.cardBorder,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
          )
        }
      }
    }
  }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
