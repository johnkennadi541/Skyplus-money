package com.example.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.OceanSpacing

/**
 * SKYPLUS MONEY — chart container (Phase 1).
 *
 * Presentation only: it supplies the card, the title row and the optional badge,
 * and takes the chart as a slot. The existing hand-built chart composables in
 * `feature/investments/InvestmentCharts.kt` and `feature/reports/ReportsCharts.kt`
 * are NOT modified and are not re-implemented here — this only wraps them.
 */
@Composable
fun OceanChartCard(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  badgeText: String? = null,
  badgeTone: BadgeTone = BadgeTone.INFO,
  content: @Composable () -> Unit,
) {
  OceanCard(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (badgeText != null) {
        OceanStatusBadge(text = badgeText, tone = badgeTone)
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.sm))
    content()
  }
}
