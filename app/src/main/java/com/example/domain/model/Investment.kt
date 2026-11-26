package com.example.domain.model

import java.math.BigDecimal
import java.util.UUID

enum class InvestmentCategory(val displayName: String, val isMarketTraded: Boolean) {
  STOCKS("Stocks / Equities", true),
  MUTUAL_FUNDS("Mutual Funds", true),
  ETFS("ETFs", true),
  GOLD("Gold", true),
  FIXED_DEPOSITS("Fixed Deposits", false),
  OTHER("Other Custom", false),
}

enum class InvestmentTransactionType(val displayName: String) {
  BUY("Buy"),
  SELL("Sell"),
}

enum class PriceSourceType(val displayName: String) {
  MANUAL("Manual Entry"),
  MFAPI_AMFI_EOD("MFapi.in / AMFI India (EOD)"),
}

enum class ProviderSupportLevel(val label: String) {
  FREE_OPT_IN_PUBLIC_FEED("Free Public Feed (Opt-In Only)"),
  MANUAL_ONLY_LICENSE_RESTRICTED("Manual Only (Free-Tier Commercial Restricted)"),
  MANUAL_ONLY_PRIVATE_CONTRACT("Manual Only (Private / Contractual Instrument)"),
}

data class ProviderPolicyInfo(
  val category: InvestmentCategory,
  val supportLevel: ProviderSupportLevel,
  val providerName: String,
  val summaryReason: String,
)

data class InvestmentHolding(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val name: String,
  val category: InvestmentCategory,
  val symbolOrTicker: String? = null,
  val isin: String? = null,
  val brokerOrPlatform: String? = null,
  val currencyCode: String,
  val purchaseDate: Long,
  val quantityDecimal: String,
  val totalInvestedMinor: Long,
  val totalBuyCostMinor: Long,
  val realizedPnlMinor: Long = 0L,
  val totalChargesMinor: Long = 0L,
  val currentMarketPriceDecimal: String,
  val currentValuationMinor: Long,
  val lastPriceUpdateDate: Long,
  val lastPriceSource: PriceSourceType = PriceSourceType.MANUAL,
  val lastPriceProviderLabel: String? = "Manual",
  val autoPriceUpdateEnabled: Boolean = false,
  val notes: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

data class InvestmentTransaction(
  val id: String = UUID.randomUUID().toString(),
  val holdingId: String,
  val userId: String,
  val type: InvestmentTransactionType,
  val transactionDate: Long,
  val quantityDecimal: String,
  val unitPriceDecimal: String,
  val grossAmountMinor: Long,
  val chargesMinor: Long,
  val netAmountMinor: Long,
  val realizedPnlMinor: Long = 0L,
  val costBasisAllocatedMinor: Long = 0L,
  val notes: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
)

data class InvestmentPriceHistory(
  val id: String = UUID.randomUUID().toString(),
  val holdingId: String,
  val userId: String,
  val priceDate: Long,
  val unitPriceDecimal: String,
  val valuationMinor: Long,
  val totalInvestedMinor: Long,
  val realizedPnlMinor: Long,
  val currencyCode: String,
  val source: PriceSourceType,
  val providerLabel: String,
  val createdAt: Long = System.currentTimeMillis(),
)

data class InvestmentHoldingPerformance(
  val holding: InvestmentHolding,
  val quantity: BigDecimal,
  val averageCostPerUnitDisplay: String,
  val unrealizedPnlMoney: Money,
  val realizedPnlMoney: Money,
  val absoluteReturnMoney: Money,
  val unrealizedReturnPercent: BigDecimal,
  val totalReturnPercent: BigDecimal,
  val isPriceStale: Boolean,
  val isFullyExited: Boolean,
)

data class CategoryAllocationItem(
  val category: InvestmentCategory,
  val investedMinor: Long,
  val valuationMinor: Long,
  val unrealizedPnlMinor: Long,
  val realizedPnlMinor: Long,
  val allocationPercent: BigDecimal,
  val holdingCount: Int,
)

data class PortfolioGrowthPoint(
  val timestampMillis: Long,
  val label: String,
  val investedMinor: Long,
  val valuationMinor: Long,
  val realizedPnlMinor: Long,
  val unrealizedPnlMinor: Long,
)

data class PortfolioCurrencySummary(
  val currencyCode: String,
  val totalInvestedMinor: Long,
  val totalBuyCostMinor: Long,
  val totalValuationMinor: Long,
  val unrealizedPnlMinor: Long,
  val realizedPnlMinor: Long,
  val absoluteReturnMinor: Long,
  val returnPercent: BigDecimal,
  val lastMarketUpdateMillis: Long?,
  val staleHoldingCount: Int,
  val activeHoldingCount: Int,
  val categoryAllocations: List<CategoryAllocationItem>,
  val growthPoints: List<PortfolioGrowthPoint>,
)

data class MarketSyncSettings(
  val autoUpdateConsentEnabled: Boolean = false,
  val syncOnlyOnWifi: Boolean = true,
  val lastSyncAttemptMillis: Long? = null,
  val lastSyncStatusMessage: String? = null,
)

data class MarketSyncSummary(
  val updatedCount: Int,
  val skippedManualCount: Int,
  val unsupportedCount: Int,
  val rateLimitedCount: Int,
  val errorCount: Int,
  val message: String,
)
