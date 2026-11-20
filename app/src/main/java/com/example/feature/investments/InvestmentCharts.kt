package com.example.feature.investments

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.theme.OceanNumericStyle
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.CategoryAllocationItem
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.Money
import com.example.domain.model.PortfolioGrowthPoint
import com.example.domain.usecase.FormatMoneyUseCase

@Composable
fun AssetAllocationDonutChart(
  allocations: List<CategoryAllocationItem>,
  totalValuationMinor: Long,
  currencyCode: String,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val categoryColors =
    mapOf(
      InvestmentCategory.STOCKS to MaterialTheme.colorScheme.primary,
      InvestmentCategory.MUTUAL_FUNDS to MaterialTheme.colorScheme.secondary,
      InvestmentCategory.ETFS to MaterialTheme.colorScheme.tertiary,
      InvestmentCategory.GOLD to extended.gold,
      InvestmentCategory.FIXED_DEPOSITS to extended.positive,
      InvestmentCategory.OTHER to extended.warning,
    )

  OceanCard(modifier = modifier.fillMaxWidth().testTag("asset_allocation_chart_card")) {
    Text(
      text = "Asset Allocation ($currencyCode)",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.xs))

    if (allocations.isEmpty() || totalValuationMinor <= 0L) {
      Text(
        text = "Add active investments with positive valuation to view your asset allocation breakdown.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("asset_allocation_empty_state"),
      )
      return@OceanCard
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.md),
    ) {
      Box(
        modifier = Modifier.size(132.dp),
        contentAlignment = Alignment.Center,
      ) {
        val trackColor = extended.cardBorder
        Canvas(
          modifier = Modifier.size(124.dp).testTag("asset_allocation_canvas"),
        ) {
          val strokeWidth = 22.dp.toPx()
          val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
          val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

          drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth),
          )

          var startAngle = -90f
          val totalFloat = totalValuationMinor.toFloat().coerceAtLeast(1f)
          allocations.forEach { item ->
            if (item.valuationMinor > 0L) {
              val sweep = (item.valuationMinor.toFloat() / totalFloat) * 360f
              val sliceColor = categoryColors[item.category] ?: Color.Gray
              drawArc(
                color = sliceColor,
                startAngle = startAngle,
                sweepAngle = sweep.coerceAtLeast(2f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
              )
              startAngle += sweep
            }
          }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "${allocations.sumOf { it.holdingCount }}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Holdings",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
      ) {
        allocations.forEach { item ->
          val dotColor = categoryColors[item.category] ?: MaterialTheme.colorScheme.primary
          val valMoney = Money.fromMinorUnits(item.valuationMinor, currencyCode)
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f),
            ) {
              Box(
                modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${item.category.displayName} (${item.allocationPercent.toPlainString()}%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Text(
              text = FormatMoneyUseCase().format(valMoney),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
fun PortfolioGrowthChart(
  growthPoints: List<PortfolioGrowthPoint>,
  unrealizedPnlMinor: Long,
  realizedPnlMinor: Long,
  currencyCode: String,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val valuationColor = MaterialTheme.colorScheme.secondary
  val investedColor = extended.gold
  val gridColor = extended.cardBorder

  OceanCard(modifier = modifier.fillMaxWidth().testTag("portfolio_growth_chart_card")) {
    Text(
      text = "Portfolio Growth & Returns ($currencyCode)",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.xs))

    // Explicit distinction between Invested Capital, Valuation, Unrealized P&L & Realized P&L
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Unrealized P&L",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = FormatMoneyUseCase().format(Money.fromMinorUnits(unrealizedPnlMinor, currencyCode)),
          style = OceanNumericStyle,
          color = if (unrealizedPnlMinor >= 0L) extended.positive else extended.negative,
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Realized P&L",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = FormatMoneyUseCase().format(Money.fromMinorUnits(realizedPnlMinor, currencyCode)),
          style = OceanNumericStyle,
          color = if (realizedPnlMinor >= 0L) extended.positive else extended.negative,
        )
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.sm))

    if (growthPoints.size < 2) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .background(extended.cardSubtle, MaterialTheme.shapes.small)
            .padding(OceanSpacing.md)
            .testTag("portfolio_growth_empty_state"),
      ) {
        Text(
          text =
            "Record at least 2 dated price updates or transactions to view authentic portfolio growth over time. Historical points are never fabricated.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      return@OceanCard
    }

    // Legend
    Row(
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(valuationColor))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Valuation",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(investedColor))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Invested Cost",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.xs))

    Canvas(
      modifier =
        Modifier.fillMaxWidth()
          .height(150.dp)
          .testTag("portfolio_growth_canvas"),
    ) {
      val padX = 10.dp.toPx()
      val padY = 12.dp.toPx()
      val plotW = (size.width - padX * 2f).coerceAtLeast(1f)
      val plotH = (size.height - padY * 2f).coerceAtLeast(1f)

      val allValues = growthPoints.flatMap { listOf(it.valuationMinor, it.investedMinor) }
      val minVal = allValues.minOrNull() ?: 0L
      val maxVal = allValues.maxOrNull() ?: 1L
      val span = (maxVal - minVal).coerceAtLeast(1L).toFloat()

      // Subtle horizontal grid lines
      for (i in 0..2) {
        val y = padY + plotH * (i / 2f)
        drawLine(
          color = gridColor.copy(alpha = 0.5f),
          start = Offset(padX, y),
          end = Offset(padX + plotW, y),
          strokeWidth = 1.dp.toPx(),
        )
      }

      fun xFor(index: Int): Float {
        if (growthPoints.size <= 1) return padX + plotW / 2f
        return padX + (index.toFloat() / (growthPoints.size - 1).toFloat()) * plotW
      }

      fun yFor(minor: Long): Float {
        val norm = (minor - minVal).toFloat() / span
        return padY + plotH * (1f - norm)
      }

      val investedPath = Path()
      val valuationPath = Path()

      growthPoints.forEachIndexed { idx, pt ->
        val x = xFor(idx)
        val yInv = yFor(pt.investedMinor)
        val yVal = yFor(pt.valuationMinor)
        if (idx == 0) {
          investedPath.moveTo(x, yInv)
          valuationPath.moveTo(x, yVal)
        } else {
          investedPath.lineTo(x, yInv)
          valuationPath.lineTo(x, yVal)
        }
      }

      drawPath(
        path = investedPath,
        color = investedColor,
        style =
          Stroke(
            width = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
          ),
      )
      drawPath(
        path = valuationPath,
        color = valuationColor,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
      )

      growthPoints.forEachIndexed { idx, pt ->
        val x = xFor(idx)
        drawCircle(color = investedColor, radius = 3.5.dp.toPx(), center = Offset(x, yFor(pt.investedMinor)))
        drawCircle(color = valuationColor, radius = 4.5.dp.toPx(), center = Offset(x, yFor(pt.valuationMinor)))
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = growthPoints.first().label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (growthPoints.size > 2) {
        Text(
          text = growthPoints[growthPoints.size / 2].label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = growthPoints.last().label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
