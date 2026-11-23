package com.example.feature.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanNumericStyle
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.InvestmentHoldingPerformance
import com.example.domain.model.InvestmentPriceHistory
import com.example.domain.model.InvestmentTransaction
import com.example.domain.model.InvestmentTransactionType
import com.example.domain.model.Money
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.domain.usecase.InvestmentCalculationEngine
import com.example.domain.usecase.InvestmentHoldingDetailState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InvestmentDetailScreen(
  holdingId: String,
  viewModel: InvestmentsViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val detailFlow = remember(holdingId) { viewModel.observeHoldingDetail(holdingId) }
  val detailState by detailFlow.collectAsStateWithLifecycle(initialValue = InvestmentHoldingDetailState())

  InvestmentDetailContent(
    holding = detailState.holding,
    performance = detailState.performance,
    transactions = detailState.transactions,
    priceHistory = detailState.priceHistory,
    calculationEngine = viewModel.calculationEngine,
    onNavigateBack = onNavigateBack,
    onEditMetadata = { updated, onSuccess ->
      viewModel.updateHoldingMetadata(updated, onSuccess)
    },
    onToggleAutoPrice = { enabled ->
      viewModel.setHoldingAutoPriceUpdate(holdingId, enabled)
    },
    onRecordBuy = { holding, date, qty, price, charges, notes, onSuccess ->
      viewModel.recordAdditionalPurchase(holding, date, qty, price, charges, notes, onSuccess)
    },
    onRecordSell = { holding, date, qty, price, charges, notes, onSuccess ->
      viewModel.recordSale(holding, date, qty, price, charges, notes, onSuccess)
    },
    onUpdateManualPrice = { price, date, onSuccess ->
      viewModel.updateManualMarketPrice(holdingId, price, date, onSuccess)
    },
    onDeleteHolding = {
      viewModel.deleteInvestment(holdingId, onSuccess = onNavigateBack)
    },
    modifier = modifier,
  )
}

@Composable
fun InvestmentDetailContent(
  holding: InvestmentHolding?,
  performance: InvestmentHoldingPerformance?,
  transactions: List<InvestmentTransaction>,
  priceHistory: List<InvestmentPriceHistory>,
  calculationEngine: InvestmentCalculationEngine = remember { InvestmentCalculationEngine() },
  onNavigateBack: () -> Unit = {},
  onEditMetadata: (InvestmentHolding, () -> Unit) -> Unit = { _, _ -> },
  onToggleAutoPrice: (Boolean) -> Unit = {},
  onRecordBuy: (InvestmentHolding, Long, String, String, String, String?, () -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
  onRecordSell: (InvestmentHolding, Long, String, String, String, String?, () -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
  onUpdateManualPrice: (String, Long, () -> Unit) -> Unit = { _, _, _ -> },
  onDeleteHolding: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)

  var showEditDialog by remember { mutableStateOf(false) }
  var showTradeDialogType by remember { mutableStateOf<InvestmentTransactionType?>(null) }
  var showPriceDialog by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  val extended = OceanTheme.colors
  val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = OceanSpacing.md, vertical = OceanSpacing.sm)
        .testTag("investment_detail_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Pinned Header
    Row(
      modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        IconButton(
          onClick = onNavigateBack,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("investment_detail_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.cd_navigate_back),
          )
        }
        Column {
          Text(
            text = holding?.name ?: "Investment Detail",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          if (holding != null) {
            Text(
              text = holding.category.displayName,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (holding != null) {
        Row {
          IconButton(
            onClick = { showEditDialog = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_holding_button"),
          ) {
            Icon(Icons.Rounded.Edit, contentDescription = "Edit investment details")
          }
          IconButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_holding_button"),
          ) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete investment", tint = extended.negative)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.xs))

    if (holding == null || performance == null) {
      OceanCard(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
        Text(
          text = "Holding not found or removed.",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      return@Column
    }

    LazyColumn(
      modifier = Modifier.weight(1f).fillMaxWidth().widthIn(max = 760.dp).testTag("investment_detail_list"),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
    ) {
      // Valuation & Return Summary
      item {
        val curr = holding.currencyCode
        OceanCard(modifier = Modifier.fillMaxWidth().testTag("holding_detail_summary_card")) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text("Current Valuation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(
                text = FormatMoneyUseCase().format(Money.fromMinorUnits(holding.currentValuationMinor, curr)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
              )
            }
            if (performance.isPriceStale) {
              OceanStatusBadge(text = "Stale Price >7d", tone = BadgeTone.WARNING)
            } else if (performance.isFullyExited) {
              OceanStatusBadge(text = "Fully Exited", tone = BadgeTone.GOLD)
            } else if (holding.lastPriceUpdateDate <= 0L) {
              OceanStatusBadge(
                text = holding.lastPriceProviderLabel ?: "Awaiting current price",
                tone = BadgeTone.WARNING,
              )
            } else {
              OceanStatusBadge(text = holding.lastPriceSource.displayName, tone = BadgeTone.POSITIVE)
            }
          }

          Spacer(modifier = Modifier.height(OceanSpacing.sm))

          DetailMetricRow("Held Units", holding.quantityDecimal)
          val priceDateLabel =
            if (holding.lastPriceUpdateDate > 0L) {
              "as of ${dateFormatter.format(Date(holding.lastPriceUpdateDate))}"
            } else {
              holding.lastPriceProviderLabel
                ?: if (holding.category == InvestmentCategory.MUTUAL_FUNDS && holding.autoPriceUpdateEnabled) {
                  "awaiting first NAV"
                } else {
                  "awaiting current price"
                }
            }
          DetailMetricRow("Current Unit Price", "${holding.currentMarketPriceDecimal} $curr ($priceDateLabel)")
          DetailMetricRow("Avg Cost / Unit", "${performance.averageCostPerUnitDisplay} $curr")
          DetailMetricRow("Open Invested Cost", FormatMoneyUseCase().format(Money.fromMinorUnits(holding.totalInvestedMinor, curr)))
          DetailMetricRow("Total Buy Capital", FormatMoneyUseCase().format(Money.fromMinorUnits(holding.totalBuyCostMinor, curr)))
          DetailMetricRow("Total Charges Paid", FormatMoneyUseCase().format(Money.fromMinorUnits(holding.totalChargesMinor, curr)))
          DetailMetricRow(
            label = "Unrealized P&L",
            value = "${FormatMoneyUseCase().format(performance.unrealizedPnlMoney)} (${performance.unrealizedReturnPercent.toPlainString()}%)",
            highlightColor = if (performance.unrealizedPnlMoney.minorUnits >= 0L) extended.positive else extended.negative,
          )
          DetailMetricRow(
            label = "Realized P&L (WAC)",
            value = FormatMoneyUseCase().format(performance.realizedPnlMoney),
            highlightColor = if (performance.realizedPnlMoney.minorUnits >= 0L) extended.positive else extended.negative,
          )
          DetailMetricRow(
            label = "Absolute Total Return",
            value = "${FormatMoneyUseCase().format(performance.absoluteReturnMoney)} (${performance.totalReturnPercent.toPlainString()}%)",
            highlightColor = if (performance.absoluteReturnMoney.minorUnits >= 0L) extended.positive else extended.negative,
          )
          if (!holding.symbolOrTicker.isNullOrBlank()) DetailMetricRow("Symbol / AMFI Code", holding.symbolOrTicker)
          if (!holding.isin.isNullOrBlank()) DetailMetricRow("ISIN", holding.isin)
          if (!holding.brokerOrPlatform.isNullOrBlank()) DetailMetricRow("Broker / Platform", holding.brokerOrPlatform)
          if (!holding.notes.isNullOrBlank()) DetailMetricRow("Notes", holding.notes)
        }
      }

      // Per-Holding Auto Price Opt-In Control (Mutual Funds only)
      if (holding.category == InvestmentCategory.MUTUAL_FUNDS) {
        item {
          OceanCard(modifier = Modifier.fillMaxWidth().testTag("holding_auto_sync_card")) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Opt-In Auto EOD NAV Updates",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "Uses the third-party MFapi.in API for AMFI-sourced EOD NAV for scheme code '${holding.symbolOrTicker ?: "not set"}'. Never overwrites manual NAV when disabled.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = holding.autoPriceUpdateEnabled,
                onCheckedChange = onToggleAutoPrice,
                modifier = Modifier.testTag("holding_auto_price_switch"),
              )
            }
          }
        }
      }

      // Primary Action Row
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          Button(
            onClick = { showTradeDialogType = InvestmentTransactionType.BUY },
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("detail_buy_more_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Buy More")
          }
          OutlinedButton(
            onClick = { showTradeDialogType = InvestmentTransactionType.SELL },
            enabled = !performance.isFullyExited,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("detail_sell_button"),
          ) {
            Icon(Icons.Rounded.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Sell")
          }
          OutlinedButton(
            onClick = { showPriceDialog = true },
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("detail_update_price_button"),
          ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Price")
          }
        }
      }

      // Transaction History
      item {
        Text(
          text = "Purchase & Sale History (${transactions.size})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag("holding_transactions_header"),
        )
      }

      items(transactions, key = { it.id }) { tx ->
        val curr = holding.currencyCode
        OceanCard(modifier = Modifier.fillMaxWidth().testTag("investment_tx_${tx.id}")) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              OceanStatusBadge(
                text = tx.type.displayName.uppercase(),
                tone = if (tx.type == InvestmentTransactionType.BUY) BadgeTone.INFO else BadgeTone.GOLD,
              )
              Text(
                text = dateFormatter.format(Date(tx.transactionDate)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = FormatMoneyUseCase().format(Money.fromMinorUnits(tx.netAmountMinor, curr)),
              style = OceanNumericStyle,
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "${tx.quantityDecimal} units @ ${tx.unitPriceDecimal} $curr (Charges: ${FormatMoneyUseCase().format(Money.fromMinorUnits(tx.chargesMinor, curr))})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (tx.type == InvestmentTransactionType.SELL) {
            val pnlColor = if (tx.realizedPnlMinor >= 0L) extended.positive else extended.negative
            Text(
              text = "Realized P&L (WAC): ${FormatMoneyUseCase().format(Money.fromMinorUnits(tx.realizedPnlMinor, curr))}",
              style = MaterialTheme.typography.labelMedium,
              color = pnlColor,
            )
          }
          if (!tx.notes.isNullOrBlank()) {
            Text(
              text = tx.notes,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Price Update History
      item {
        Text(
          text = "Valuation & Price Update Log (${priceHistory.size})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag("holding_price_history_header"),
        )
      }

      items(priceHistory, key = { it.id }) { point ->
        OceanCard(modifier = Modifier.fillMaxWidth().testTag("price_history_${point.id}")) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = "${point.unitPriceDecimal} ${point.currencyCode} / unit",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = "${dateFormatter.format(Date(point.priceDate))} • ${point.providerLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = FormatMoneyUseCase().format(Money.fromMinorUnits(point.valuationMinor, point.currencyCode)),
              style = OceanNumericStyle,
            )
          }
        }
      }
    }
  }

  if (holding != null) {
    if (showEditDialog) {
      InvestmentFormDialog(
        existingHolding = holding,
        defaultCurrency = holding.currencyCode,
        availableCurrencies = listOf(holding.currencyCode),
        calculationEngine = calculationEngine,
        onDismiss = { showEditDialog = false },
        onCreateSubmit = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
        onEditSubmit = { updated ->
          onEditMetadata(updated) { showEditDialog = false }
        },
      )
    }

    if (showTradeDialogType != null) {
      RecordTradeDialog(
        holding = holding,
        initialType = showTradeDialogType!!,
        calculationEngine = calculationEngine,
        onDismiss = { showTradeDialogType = null },
        onSubmit = { type, date, qty, price, charges, notes ->
          if (type == InvestmentTransactionType.BUY) {
            onRecordBuy(holding, date, qty, price, charges, notes) { showTradeDialogType = null }
          } else {
            onRecordSell(holding, date, qty, price, charges, notes) { showTradeDialogType = null }
          }
        },
      )
    }

    if (showPriceDialog) {
      UpdateMarketPriceDialog(
        holding = holding,
        calculationEngine = calculationEngine,
        onDismiss = { showPriceDialog = false },
        onSubmit = { price, date ->
          onUpdateManualPrice(price, date) { showPriceDialog = false }
        },
      )
    }

    if (showDeleteConfirm) {
      AlertDialog(
        onDismissRequest = { showDeleteConfirm = false },
        title = { Text("Delete Investment?") },
        text = {
          Text(
            "Are you sure you want to delete '${holding.name}' along with its transaction and price update history? This action cannot be undone."
          )
        },
        confirmButton = {
          Button(
            onClick = {
              showDeleteConfirm = false
              onDeleteHolding()
            },
            colors = ButtonDefaults.buttonColors(containerColor = extended.negative),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("confirm_delete_holding_button"),
          ) {
            Text("Delete")
          }
        },
        dismissButton = {
          TextButton(
            onClick = { showDeleteConfirm = false },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("cancel_delete_holding_button"),
          ) {
            Text("Cancel")
          }
        },
      )
    }
  }
}

@Composable
private fun DetailMetricRow(
  label: String,
  value: String,
  highlightColor: androidx.compose.ui.graphics.Color? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      fontWeight = FontWeight.SemiBold,
      color = highlightColor ?: MaterialTheme.colorScheme.onSurface,
    )
  }
}
