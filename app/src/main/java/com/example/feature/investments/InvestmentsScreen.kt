package com.example.feature.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.domain.model.FinancialAccount
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.InvestmentHoldingPerformance
import com.example.domain.model.MarketSyncSettings
import com.example.domain.model.Money
import com.example.domain.model.PortfolioCurrencySummary
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.domain.usecase.InvestmentCalculationEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InvestmentsScreen(
  viewModel: InvestmentsViewModel,
  onNavigateBack: () -> Unit,
  onOpenHoldingDetail: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val performances by viewModel.performances.collectAsStateWithLifecycle()
  val summaries by viewModel.summariesByCurrency.collectAsStateWithLifecycle()
  val accounts by viewModel.accounts.collectAsStateWithLifecycle()
  val syncSettings by viewModel.syncSettings.collectAsStateWithLifecycle()
  val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
  val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
  val isErrorMessage by viewModel.isErrorMessage.collectAsStateWithLifecycle()
  val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
  val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

  InvestmentsContent(
    performances = performances,
    summaries = summaries,
    accounts = accounts,
    syncSettings = syncSettings,
    isSyncing = isSyncing,
    userMessage = userMessage,
    isErrorMessage = isErrorMessage,
    selectedCurrency = selectedCurrency,
    selectedCategory = selectedCategory,
    calculationEngine = viewModel.calculationEngine,
    onSelectCurrency = viewModel::selectCurrency,
    onSelectCategory = viewModel::selectCategory,
    onToggleAutoConsent = viewModel::setAutoUpdateConsent,
    onToggleWifiOnly = viewModel::setSyncOnlyOnWifi,
    onSyncNow = { viewModel.syncMarketPricesNow() },
    onDismissMessage = viewModel::dismissMessage,
    onNavigateBack = onNavigateBack,
    onOpenHoldingDetail = onOpenHoldingDetail,
    onCreateInvestment = { name, cat, sym, isin, broker, curr, date, qty, buyPrice, charges, mktPrice, autoOptIn, notes, onSuccess ->
      viewModel.createInvestment(
        name = name,
        category = cat,
        symbolOrTicker = sym,
        isin = isin,
        brokerOrPlatform = broker,
        currencyCode = curr,
        purchaseDate = date,
        quantityDecimal = qty,
        purchasePriceDecimal = buyPrice,
        chargesDecimal = charges,
        currentMarketPriceDecimal = mktPrice,
        autoPriceUpdateEnabled = autoOptIn,
        notes = notes,
        onSuccess = onSuccess,
      )
    },
    onUpdateManualPrice = { holdingId, newPrice, priceDate, onSuccess ->
      viewModel.updateManualMarketPrice(holdingId, newPrice, priceDate, onSuccess)
    },
    modifier = modifier,
  )
}

@Composable
fun InvestmentsContent(
  performances: List<InvestmentHoldingPerformance>,
  summaries: Map<String, PortfolioCurrencySummary>,
  accounts: List<FinancialAccount>,
  syncSettings: MarketSyncSettings = MarketSyncSettings(),
  isSyncing: Boolean = false,
  userMessage: String? = null,
  isErrorMessage: Boolean = false,
  selectedCurrency: String? = null,
  selectedCategory: InvestmentCategory? = null,
  calculationEngine: InvestmentCalculationEngine = remember { InvestmentCalculationEngine() },
  onSelectCurrency: (String?) -> Unit = {},
  onSelectCategory: (InvestmentCategory?) -> Unit = {},
  onToggleAutoConsent: (Boolean) -> Unit = {},
  onToggleWifiOnly: (Boolean) -> Unit = {},
  onSyncNow: () -> Unit = {},
  onDismissMessage: () -> Unit = {},
  onNavigateBack: () -> Unit = {},
  onOpenHoldingDetail: (String) -> Unit = {},
  onCreateInvestment: (
    name: String,
    category: InvestmentCategory,
    symbolOrTicker: String?,
    isin: String?,
    brokerOrPlatform: String?,
    currencyCode: String,
    purchaseDate: Long,
    quantityDecimal: String,
    purchasePriceDecimal: String,
    chargesDecimal: String,
    currentMarketPriceDecimal: String?,
    autoPriceUpdateEnabled: Boolean,
    notes: String?,
    onSuccess: () -> Unit,
  ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
  onUpdateManualPrice: (String, String, Long, () -> Unit) -> Unit = { _, _, _, _ -> },
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)

  var showCreateDialog by remember { mutableStateOf(false) }
  var showSyncSettingsDialog by remember { mutableStateOf(false) }
  var priceUpdateTarget by remember { mutableStateOf<InvestmentHolding?>(null) }

  // New investment instruments are India-only. Existing legacy currencies remain
  // filterable so older records are visible, but account/global currencies are not offered.
  val availableCurrencies =
    remember(performances) {
      (performances.map { it.holding.currencyCode } + listOf("INR")).distinct()
    }

  val activeCurrency =
    selectedCurrency
      ?: performances.firstOrNull()?.holding?.currencyCode
      ?: "INR"
      ?: "INR"

  val filteredPerformances =
    remember(performances, activeCurrency, selectedCategory) {
      performances.filter { perf ->
        perf.holding.currencyCode.equals(activeCurrency, ignoreCase = true) &&
          (selectedCategory == null || perf.holding.category == selectedCategory)
      }
    }

  val activeSummary = summaries[activeCurrency]
  val extended = OceanTheme.colors

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = OceanSpacing.md, vertical = OceanSpacing.sm)
        .testTag("investments_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Pinned Top Header
    Row(
      modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        IconButton(
          onClick = onNavigateBack,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("investments_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.cd_navigate_back),
          )
        }
        Column {
          Text(
            text = stringResource(R.string.investments_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = stringResource(R.string.investments_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xxs)) {
        IconButton(
          onClick = { showSyncSettingsDialog = true },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("open_market_sync_settings_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.CloudSync,
            contentDescription = "Optional market price sync settings",
            tint = if (syncSettings.autoUpdateConsentEnabled) extended.positive else extended.gold,
          )
        }
        Button(
          onClick = { showCreateDialog = true },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("add_investment_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Add")
        }
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.xs))

    LazyColumn(
      modifier = Modifier.weight(1f).fillMaxWidth().widthIn(max = 760.dp).testTag("investments_list"),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (userMessage != null) {
        item {
          Surface(
            modifier = Modifier.fillMaxWidth().testTag("investments_user_message"),
            shape = MaterialTheme.shapes.small,
            color =
              if (isErrorMessage) extended.negativeContainer
              else MaterialTheme.colorScheme.primaryContainer,
            border =
              BorderStroke(
                1.dp,
                if (isErrorMessage) extended.negative else MaterialTheme.colorScheme.primary,
              ),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(OceanSpacing.sm),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                  imageVector = if (isErrorMessage) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = if (isErrorMessage) extended.negative else MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(OceanSpacing.xs))
                Text(text = userMessage, style = MaterialTheme.typography.bodySmall)
              }
              IconButton(onClick = onDismissMessage) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
              }
            }
          }
        }
      }

      // Currency selector (never mixes across currencies)
      item {
        LazyRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          items(availableCurrencies) { curr ->
            FilterChip(
              selected = activeCurrency == curr,
              onClick = { onSelectCurrency(curr) },
              label = { Text(curr) },
              modifier = Modifier.testTag("investments_currency_chip_$curr"),
            )
          }
        }
      }

      // Portfolio Summary Card
      item {
        val totalInvested = activeSummary?.totalInvestedMinor ?: 0L
        val totalValuation = activeSummary?.totalValuationMinor ?: 0L
        val absoluteReturn = activeSummary?.absoluteReturnMinor ?: 0L
        val returnPct = activeSummary?.returnPercent?.toPlainString() ?: "0.00"
        val staleCount = activeSummary?.staleHoldingCount ?: 0

        OceanCard(modifier = Modifier.fillMaxWidth().testTag("portfolio_summary_card")) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Portfolio Valuation ($activeCurrency)",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OceanStatusBadge(
              text = if (syncSettings.autoUpdateConsentEnabled) "EOD Sync Opt-In" else "Manual Pricing",
              tone = if (syncSettings.autoUpdateConsentEnabled) BadgeTone.POSITIVE else BadgeTone.GOLD,
            )
          }
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(
            text = FormatMoneyUseCase().format(Money.fromMinorUnits(totalValuation, activeCurrency)),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column {
              Text("Open Invested Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(
                text = FormatMoneyUseCase().format(Money.fromMinorUnits(totalInvested, activeCurrency)),
                style = OceanNumericStyle,
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text("Total Return", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(
                text = "${FormatMoneyUseCase().format(Money.fromMinorUnits(absoluteReturn, activeCurrency))} ($returnPct%)",
                style = OceanNumericStyle,
                color = if (absoluteReturn >= 0L) extended.positive else extended.negative,
              )
            }
          }

          if (staleCount > 0) {
            Spacer(modifier = Modifier.height(OceanSpacing.xs))
            OceanStatusBadge(
              text = "$staleCount holding(s) have stale market prices (>7 days)",
              tone = BadgeTone.WARNING,
              modifier = Modifier.testTag("stale_price_warning_badge"),
            )
          }

          val lastMarketUpdate = activeSummary?.lastMarketUpdateMillis
          if (lastMarketUpdate != null) {
            Spacer(modifier = Modifier.height(OceanSpacing.xxs))
            val dateFormatted = remember(lastMarketUpdate) {
              SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(lastMarketUpdate))
            }
            Text(
              text = "Latest price update: $dateFormatted • Attribution: Manual / MFapi.in AMFI",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Charts (shown when holdings exist in currency)
      if (activeSummary != null) {
        item {
          AssetAllocationDonutChart(
            allocations = activeSummary.categoryAllocations,
            totalValuationMinor = activeSummary.totalValuationMinor,
            currencyCode = activeCurrency,
          )
        }
        item {
          PortfolioGrowthChart(
            growthPoints = activeSummary.growthPoints,
            unrealizedPnlMinor = activeSummary.unrealizedPnlMinor,
            realizedPnlMinor = activeSummary.realizedPnlMinor,
            currencyCode = activeCurrency,
          )
        }
      }

      // Category Filters
      item {
        LazyRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          item {
            FilterChip(
              selected = selectedCategory == null,
              onClick = { onSelectCategory(null) },
              label = { Text("All") },
              modifier = Modifier.testTag("filter_category_all"),
            )
          }
          items(InvestmentCategory.entries) { cat ->
            FilterChip(
              selected = selectedCategory == cat,
              onClick = { onSelectCategory(cat) },
              label = { Text(cat.displayName) },
              modifier = Modifier.testTag("filter_category_${cat.name.lowercase()}"),
            )
          }
        }
      }

      // Holdings List or Empty State
      if (filteredPerformances.isEmpty()) {
        item {
          OceanCard(modifier = Modifier.fillMaxWidth().testTag("investments_empty_card")) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(OceanSpacing.sm),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                contentDescription = null,
                tint = extended.gold,
                modifier = Modifier.size(36.dp),
              )
              Text(
                text = "No $activeCurrency Investments Tracked Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text =
                  "Add Stocks, Mutual Funds, ETFs, Gold, Fixed Deposits, or custom holdings. Manual price updates work 100% offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("empty_add_investment_button"),
              ) {
                Text("Add First Investment")
              }
            }
          }
        }
      } else {
        items(filteredPerformances, key = { it.holding.id }) { perf ->
          InvestmentHoldingCard(
            performance = perf,
            onOpenDetail = { onOpenHoldingDetail(perf.holding.id) },
            onQuickUpdatePrice = { priceUpdateTarget = perf.holding },
          )
        }
      }
    }
  }

  if (showCreateDialog) {
    InvestmentFormDialog(
      existingHolding = null,
      defaultCurrency = "INR",
      availableCurrencies = listOf("INR"),
      calculationEngine = calculationEngine,
      onDismiss = { showCreateDialog = false },
      onCreateSubmit = { name, cat, sym, isin, broker, curr, date, qty, buyPrice, charges, mktPrice, autoOptIn, notes ->
        onCreateInvestment(
          name,
          cat,
          sym,
          isin,
          broker,
          curr,
          date,
          qty,
          buyPrice,
          charges,
          mktPrice,
          autoOptIn,
          notes,
        ) {
          showCreateDialog = false
        }
      },
      onEditSubmit = {},
    )
  }

  if (priceUpdateTarget != null) {
    val target = priceUpdateTarget!!
    UpdateMarketPriceDialog(
      holding = target,
      calculationEngine = calculationEngine,
      onDismiss = { priceUpdateTarget = null },
      onSubmit = { newPrice, priceDate ->
        onUpdateManualPrice(target.id, newPrice, priceDate) {
          priceUpdateTarget = null
        }
      },
    )
  }

  if (showSyncSettingsDialog) {
    MarketSyncSettingsDialog(
      syncSettings = syncSettings,
      isSyncing = isSyncing,
      onToggleAutoConsent = onToggleAutoConsent,
      onToggleWifiOnly = onToggleWifiOnly,
      onSyncNow = onSyncNow,
      onDismiss = { showSyncSettingsDialog = false },
    )
  }
}

@Composable
private fun InvestmentHoldingCard(
  performance: InvestmentHoldingPerformance,
  onOpenDetail: () -> Unit,
  onQuickUpdatePrice: () -> Unit,
) {
  val holding = performance.holding
  val extended = OceanTheme.colors
  val valuationMoney = Money.fromMinorUnits(holding.currentValuationMinor, holding.currencyCode)
  val investedMoney = Money.fromMinorUnits(holding.totalInvestedMinor, holding.currencyCode)
  val returnColor =
    if (performance.absoluteReturnMoney.minorUnits >= 0L) extended.positive else extended.negative

  OceanCard(
    onClick = onOpenDetail,
    modifier = Modifier.fillMaxWidth().testTag("holding_card_${holding.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = holding.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text =
            buildString {
              append(holding.category.displayName)
              if (!holding.symbolOrTicker.isNullOrBlank()) append(" • ${holding.symbolOrTicker}")
              if (!holding.brokerOrPlatform.isNullOrBlank()) append(" • ${holding.brokerOrPlatform}")
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = FormatMoneyUseCase().format(valuationMoney),
          style = OceanNumericStyle,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "${FormatMoneyUseCase().format(performance.absoluteReturnMoney)} (${performance.totalReturnPercent.toPlainString()}%)",
          style = MaterialTheme.typography.labelMedium,
          color = returnColor,
        )
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.xs))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "${holding.quantityDecimal} units • Cost: ${FormatMoneyUseCase().format(investedMoney)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedButton(
        onClick = onQuickUpdatePrice,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("quick_price_button_${holding.id}"),
      ) {
        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Price")
      }
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OceanStatusBadge(
        text = holding.lastPriceProviderLabel ?: holding.lastPriceSource.displayName,
        tone = BadgeTone.INFO,
      )
      if (performance.isPriceStale) {
        OceanStatusBadge(text = "Stale >7d", tone = BadgeTone.WARNING)
      }
      if (performance.isFullyExited) {
        OceanStatusBadge(text = "Exited", tone = BadgeTone.GOLD)
      }
    }
  }
}
