package com.example.domain.usecase

import com.example.domain.model.CategoryAllocationItem
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.InvestmentHoldingPerformance
import com.example.domain.model.InvestmentPriceHistory
import com.example.domain.model.Money
import com.example.domain.model.PortfolioCurrencySummary
import com.example.domain.model.PortfolioGrowthPoint
import com.example.domain.model.ProviderPolicyInfo
import com.example.domain.model.ProviderSupportLevel
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Precision investment calculation engine.
 *
 * Strictly uses [BigDecimal] and exact [Money] minor units ([Long]).
 * Floating-point types ([Float], [Double]) are strictly prohibited.
 *
 * Cost-Basis Method: **Weighted Average Cost (WAC)**.
 * - Every buy adds `(qty * unitPrice) + charges` to open cost basis and increases total units.
 * - Partial sales allocate open cost basis proportionally via
 *   `round_half_even(openCostBasisMinor * (sellQty / currentQty))`, preserving exact minor-unit
 *   conservation between sold cost basis and remaining open cost basis.
 * - Complete sales (when `sellQty == currentQty`) allocate 100% of remaining open cost basis to
 *   eliminate rounding dust, leaving `0` remaining units and `0` open cost basis.
 */
@Singleton
class InvestmentCalculationEngine @Inject constructor() {

  companion object {
    const val STALE_PRICE_THRESHOLD_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L
    private val HUNDRED = BigDecimal("100")
    private val ZERO_PERCENT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)

    val PROVIDER_POLICIES: List<ProviderPolicyInfo> =
      listOf(
        ProviderPolicyInfo(
          category = InvestmentCategory.MUTUAL_FUNDS,
          supportLevel = ProviderSupportLevel.FREE_OPT_IN_PUBLIC_FEED,
          providerName = "MFapi.in third-party API • AMFI-sourced EOD NAV",
          summaryReason =
            "Supports Indian mutual funds identified by 5–7 digit AMFI scheme code. End-of-day NAVs only; no API key required; rate-limited with 12h local cache.",
        ),
        ProviderPolicyInfo(
          category = InvestmentCategory.STOCKS,
          supportLevel = ProviderSupportLevel.MANUAL_ONLY_LICENSE_RESTRICTED,
          providerName = "Manual Entry (No bundled commercial feed)",
          summaryReason =
            "Alpha Vantage free tier (25 req/day), Finnhub free tier, and Twelve Data free tier prohibit commercial/distributed app use without paid licenses; Yahoo unofficial endpoints violate TOS.",
        ),
        ProviderPolicyInfo(
          category = InvestmentCategory.ETFS,
          supportLevel = ProviderSupportLevel.MANUAL_ONLY_LICENSE_RESTRICTED,
          providerName = "Manual Entry (No bundled commercial feed)",
          summaryReason =
            "Exchange equity/ETF feeds require paid commercial redistribution licensing and server-side key protection.",
        ),
        ProviderPolicyInfo(
          category = InvestmentCategory.GOLD,
          supportLevel = ProviderSupportLevel.MANUAL_ONLY_LICENSE_RESTRICTED,
          providerName = "Manual Entry (No unencumbered feed)",
          summaryReason =
            "Free commodity APIs like Metals-API forbid public/commercial display on free plans or require paid attribution-free tiers.",
        ),
        ProviderPolicyInfo(
          category = InvestmentCategory.FIXED_DEPOSITS,
          supportLevel = ProviderSupportLevel.MANUAL_ONLY_PRIVATE_CONTRACT,
          providerName = "Manual Entry",
          summaryReason =
            "Bank Fixed Deposits are private contractual instruments with issuer-specific rates and no public exchange quote.",
        ),
        ProviderPolicyInfo(
          category = InvestmentCategory.OTHER,
          supportLevel = ProviderSupportLevel.MANUAL_ONLY_PRIVATE_CONTRACT,
          providerName = "Manual Entry",
          summaryReason = "Custom or private assets are valued directly by manual updates.",
        ),
      )

    fun policyFor(category: InvestmentCategory): ProviderPolicyInfo =
      PROVIDER_POLICIES.first { it.category == category }
  }

  data class BuyMutation(
    val grossAmountMinor: Long,
    val netBuyCostMinor: Long,
    val newQuantityDecimal: String,
    val newOpenCostBasisMinor: Long,
    val newTotalBuyCostMinor: Long,
    val newTotalChargesMinor: Long,
    val newValuationMinor: Long,
  )

  data class SellMutation(
    val grossProceedsMinor: Long,
    val netProceedsMinor: Long,
    val costBasisSoldMinor: Long,
    val realizedPnlDeltaMinor: Long,
    val remainingQuantityDecimal: String,
    val remainingOpenCostBasisMinor: Long,
    val newCumulativeRealizedPnlMinor: Long,
    val newTotalChargesMinor: Long,
    val newValuationMinor: Long,
    val isCompleteExit: Boolean,
  )

  fun parsePositiveDecimal(input: String, fieldName: String): BigDecimal {
    val trimmed = input.trim()
    require(trimmed.isNotEmpty()) { "$fieldName is required" }
    val parsed =
      try {
        BigDecimal(trimmed)
      } catch (_: NumberFormatException) {
        throw IllegalArgumentException("Enter a valid numeric $fieldName")
      }
    require(parsed > BigDecimal.ZERO) { "$fieldName must be greater than zero" }
    return parsed
  }

  fun parseNonNegativeDecimal(input: String, fieldName: String): BigDecimal {
    val trimmed = input.trim().ifEmpty { "0" }
    val parsed =
      try {
        BigDecimal(trimmed)
      } catch (_: NumberFormatException) {
        throw IllegalArgumentException("Enter a valid numeric $fieldName")
      }
    require(parsed >= BigDecimal.ZERO) { "$fieldName cannot be negative" }
    return parsed
  }

  fun computeValuationMinor(
    quantity: BigDecimal,
    unitPrice: BigDecimal,
    currencyCode: String,
  ): Long {
    require(quantity >= BigDecimal.ZERO) { "Quantity cannot be negative" }
    require(unitPrice >= BigDecimal.ZERO) { "Unit price cannot be negative" }
    if (quantity.compareTo(BigDecimal.ZERO) == 0 || unitPrice.compareTo(BigDecimal.ZERO) == 0) {
      Money.resolveFractionDigits(currencyCode)
      return 0L
    }
    val rawTotal = quantity.multiply(unitPrice, MathContext.DECIMAL128)
    return Money.fromBigDecimal(rawTotal, currencyCode, RoundingMode.HALF_UP).minorUnits
  }

  fun applyBuy(
    currentQuantityDecimal: String,
    currentOpenCostBasisMinor: Long,
    currentTotalBuyCostMinor: Long,
    currentTotalChargesMinor: Long,
    buyQuantityDecimal: String,
    buyUnitPriceDecimal: String,
    chargesMinor: Long,
    currentMarketPriceDecimal: String,
    currencyCode: String,
  ): BuyMutation {
    require(chargesMinor >= 0L) { "Transaction charges cannot be negative" }
    require(currentOpenCostBasisMinor >= 0L) { "Open cost basis cannot be negative" }
    require(currentTotalBuyCostMinor >= 0L) { "Cumulative buy cost cannot be negative" }
    require(currentTotalChargesMinor >= 0L) { "Cumulative charges cannot be negative" }
    require(currentOpenCostBasisMinor <= currentTotalBuyCostMinor) {
      "Open cost basis cannot exceed cumulative buy cost"
    }
    val normalizedCurrency = currencyCode.trim().uppercase()
    val currentQty = parseNonNegativeDecimal(currentQuantityDecimal, "Current quantity")
    val addQty = parsePositiveDecimal(buyQuantityDecimal, "Purchase quantity")
    val unitPrice = parsePositiveDecimal(buyUnitPriceDecimal, "Purchase price")
    val marketPrice = parseNonNegativeDecimal(currentMarketPriceDecimal, "Current market price")

    val grossMinor = computeValuationMinor(addQty, unitPrice, normalizedCurrency)
    val netBuyCost = Math.addExact(grossMinor, chargesMinor)
    val newQty = currentQty.add(addQty).stripTrailingZeros()
    val newOpenCost = Math.addExact(currentOpenCostBasisMinor, netBuyCost)
    val newTotalBuyCost = Math.addExact(currentTotalBuyCostMinor, netBuyCost)
    val newCharges = Math.addExact(currentTotalChargesMinor, chargesMinor)
    val newValuation = computeValuationMinor(newQty, marketPrice, normalizedCurrency)

    return BuyMutation(
      grossAmountMinor = grossMinor,
      netBuyCostMinor = netBuyCost,
      newQuantityDecimal = newQty.toPlainString(),
      newOpenCostBasisMinor = newOpenCost,
      newTotalBuyCostMinor = newTotalBuyCost,
      newTotalChargesMinor = newCharges,
      newValuationMinor = newValuation,
    )
  }

  fun applyWeightedAverageSell(
    currentQuantityDecimal: String,
    currentOpenCostBasisMinor: Long,
    currentRealizedPnlMinor: Long,
    currentTotalChargesMinor: Long,
    sellQuantityDecimal: String,
    sellUnitPriceDecimal: String,
    chargesMinor: Long,
    currentMarketPriceDecimal: String,
    currencyCode: String,
  ): SellMutation {
    require(chargesMinor >= 0L) { "Transaction charges cannot be negative" }
    require(currentOpenCostBasisMinor >= 0L) { "Open cost basis cannot be negative" }
    require(currentTotalChargesMinor >= 0L) { "Cumulative charges cannot be negative" }

    val normalizedCurrency = currencyCode.trim().uppercase()
    val currentQty = parsePositiveDecimal(currentQuantityDecimal, "Available holding quantity")
    val sellQty = parsePositiveDecimal(sellQuantityDecimal, "Sale quantity")
    require(sellQty <= currentQty) {
      "Sale quantity (${sellQty.stripTrailingZeros().toPlainString()}) exceeds held units (${currentQty.stripTrailingZeros().toPlainString()})"
    }

    val sellPrice = parseNonNegativeDecimal(sellUnitPriceDecimal, "Sale price")
    val marketPrice = parseNonNegativeDecimal(currentMarketPriceDecimal, "Current market price")

    val grossProceeds = computeValuationMinor(sellQty, sellPrice, normalizedCurrency)
    val netProceeds = Math.subtractExact(grossProceeds, chargesMinor)

    val isComplete = sellQty.compareTo(currentQty) == 0
    val costSoldMinor: Long
    val remainingCostBasisMinor: Long
    val remainingQty: BigDecimal

    if (isComplete) {
      costSoldMinor = currentOpenCostBasisMinor
      remainingCostBasisMinor = 0L
      remainingQty = BigDecimal.ZERO
    } else {
      costSoldMinor =
        BigDecimal.valueOf(currentOpenCostBasisMinor)
          .multiply(sellQty, MathContext.DECIMAL128)
          .divide(currentQty, 0, RoundingMode.HALF_EVEN)
          .longValueExact()
          .coerceIn(0L, currentOpenCostBasisMinor)
      remainingCostBasisMinor = Math.subtractExact(currentOpenCostBasisMinor, costSoldMinor)
      remainingQty = currentQty.subtract(sellQty).stripTrailingZeros()
    }

    val realizedDelta = Math.subtractExact(netProceeds, costSoldMinor)
    val newCumulativeRealized = Math.addExact(currentRealizedPnlMinor, realizedDelta)
    val newCharges = Math.addExact(currentTotalChargesMinor, chargesMinor)
    val newValuation =
      if (isComplete) 0L else computeValuationMinor(remainingQty, marketPrice, normalizedCurrency)

    return SellMutation(
      grossProceedsMinor = grossProceeds,
      netProceedsMinor = netProceeds,
      costBasisSoldMinor = costSoldMinor,
      realizedPnlDeltaMinor = realizedDelta,
      remainingQuantityDecimal = remainingQty.toPlainString(),
      remainingOpenCostBasisMinor = remainingCostBasisMinor,
      newCumulativeRealizedPnlMinor = newCumulativeRealized,
      newTotalChargesMinor = newCharges,
      newValuationMinor = newValuation,
      isCompleteExit = isComplete,
    )
  }

  fun calculatePercentageReturn(
    returnMinor: Long,
    costDenominatorMinor: Long,
  ): BigDecimal {
    if (costDenominatorMinor <= 0L) return ZERO_PERCENT
    return BigDecimal.valueOf(returnMinor)
      .multiply(HUNDRED)
      .divide(BigDecimal.valueOf(costDenominatorMinor), 2, RoundingMode.HALF_UP)
  }

  fun evaluateHoldingPerformance(
    holding: InvestmentHolding,
    nowMillis: Long = System.currentTimeMillis(),
  ): InvestmentHoldingPerformance {
    val qty =
      try {
        BigDecimal(holding.quantityDecimal.trim())
      } catch (error: NumberFormatException) {
        throw IllegalStateException("Stored investment quantity is invalid", error)
      }
    require(qty >= BigDecimal.ZERO) { "Stored investment quantity cannot be negative" }
    val isExited = qty.compareTo(BigDecimal.ZERO) == 0
    val currency = holding.currencyCode.uppercase()
    val scale = Money.resolveFractionDigits(currency)

    val unrealizedMinor =
      if (isExited) 0L
      else Math.subtractExact(holding.currentValuationMinor, holding.totalInvestedMinor)
    val absoluteReturnMinor = Math.addExact(unrealizedMinor, holding.realizedPnlMinor)

    val unrealizedPct =
      if (isExited) ZERO_PERCENT
      else calculatePercentageReturn(unrealizedMinor, holding.totalInvestedMinor)
    val totalPct =
      calculatePercentageReturn(absoluteReturnMinor, holding.totalBuyCostMinor)

    val avgCostPerUnitDisplay =
      if (isExited || holding.totalInvestedMinor <= 0L) {
        BigDecimal.ZERO.setScale(scale).toPlainString()
      } else {
        BigDecimal.valueOf(holding.totalInvestedMinor, scale)
          .divide(qty, scale.coerceAtLeast(2), RoundingMode.HALF_UP)
          .toPlainString()
      }

    // A missing first quote is "awaiting price", not a stale observation. Only a
    // previously recorded quote can become stale after the seven-day threshold.
    val isStale =
      !isExited &&
        holding.category.isMarketTraded &&
        holding.lastPriceUpdateDate > 0L &&
        (nowMillis - holding.lastPriceUpdateDate) > STALE_PRICE_THRESHOLD_MILLIS

    return InvestmentHoldingPerformance(
      holding = holding,
      quantity = qty,
      averageCostPerUnitDisplay = avgCostPerUnitDisplay,
      unrealizedPnlMoney = Money.fromMinorUnits(unrealizedMinor, currency),
      realizedPnlMoney = Money.fromMinorUnits(holding.realizedPnlMinor, currency),
      absoluteReturnMoney = Money.fromMinorUnits(absoluteReturnMinor, currency),
      unrealizedReturnPercent = unrealizedPct,
      totalReturnPercent = totalPct,
      isPriceStale = isStale,
      isFullyExited = isExited,
    )
  }

  fun calculateCurrencySummaries(
    holdings: List<InvestmentHolding>,
    priceHistory: List<InvestmentPriceHistory> = emptyList(),
    nowMillis: Long = System.currentTimeMillis(),
  ): Map<String, PortfolioCurrencySummary> {
    val historyByCurrency = priceHistory.groupBy { it.currencyCode.uppercase() }

    return holdings
      .groupBy { it.currencyCode.uppercase() }
      .mapValues { (currency, currHoldings) ->
        var totalInvested = 0L
        var totalBuyCost = 0L
        var totalValuation = 0L
        var totalRealized = 0L
        var lastMarketUpdate: Long? = null
        var staleCount = 0
        var activeCount = 0

        val performances = currHoldings.map { evaluateHoldingPerformance(it, nowMillis) }

        for (perf in performances) {
          val h = perf.holding
          totalInvested = Math.addExact(totalInvested, h.totalInvestedMinor)
          totalBuyCost = Math.addExact(totalBuyCost, h.totalBuyCostMinor)
          totalValuation = Math.addExact(totalValuation, h.currentValuationMinor)
          totalRealized = Math.addExact(totalRealized, h.realizedPnlMinor)
          if (!perf.isFullyExited) activeCount++
          if (perf.isPriceStale) staleCount++
          lastMarketUpdate = maxOf(lastMarketUpdate ?: h.lastPriceUpdateDate, h.lastPriceUpdateDate)
        }

        val unrealizedPnl = Math.subtractExact(totalValuation, totalInvested)
        val absoluteReturn = Math.addExact(unrealizedPnl, totalRealized)
        val returnPercent = calculatePercentageReturn(absoluteReturn, totalBuyCost)

        val allocations =
          performances
            .groupBy { it.holding.category }
            .map { (category, catPerfs) ->
              var catInvested = 0L
              var catValuation = 0L
              var catRealized = 0L
              for (p in catPerfs) {
                catInvested = Math.addExact(catInvested, p.holding.totalInvestedMinor)
                catValuation = Math.addExact(catValuation, p.holding.currentValuationMinor)
                catRealized = Math.addExact(catRealized, p.holding.realizedPnlMinor)
              }
              val catUnrealized = Math.subtractExact(catValuation, catInvested)
              val pct =
                if (totalValuation > 0L) {
                  BigDecimal.valueOf(catValuation)
                    .multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(totalValuation), 2, RoundingMode.HALF_UP)
                } else {
                  ZERO_PERCENT
                }
              CategoryAllocationItem(
                category = category,
                investedMinor = catInvested,
                valuationMinor = catValuation,
                unrealizedPnlMinor = catUnrealized,
                realizedPnlMinor = catRealized,
                allocationPercent = pct,
                holdingCount = catPerfs.size,
              )
            }
            .sortedByDescending { it.valuationMinor }

        val growthPoints =
          buildAuthenticGrowthTimeline(
            history = historyByCurrency[currency].orEmpty(),
            holdingsCount = currHoldings.size,
          )

        PortfolioCurrencySummary(
          currencyCode = currency,
          totalInvestedMinor = totalInvested,
          totalBuyCostMinor = totalBuyCost,
          totalValuationMinor = totalValuation,
          unrealizedPnlMinor = unrealizedPnl,
          realizedPnlMinor = totalRealized,
          absoluteReturnMinor = absoluteReturn,
          returnPercent = returnPercent,
          lastMarketUpdateMillis = lastMarketUpdate,
          staleHoldingCount = staleCount,
          activeHoldingCount = activeCount,
          categoryAllocations = allocations,
          growthPoints = growthPoints,
        )
      }
  }

  /**
   * Builds portfolio growth timeline strictly from real recorded [InvestmentPriceHistory] events.
   * Never fabricates synthetic price history points.
   */
  internal fun buildAuthenticGrowthTimeline(
    history: List<InvestmentPriceHistory>,
    holdingsCount: Int,
  ): List<PortfolioGrowthPoint> {
    if (history.isEmpty() || holdingsCount == 0) return emptyList()

    val sorted = history.sortedWith(compareBy<InvestmentPriceHistory> { it.priceDate }.thenBy { it.createdAt })
    val latestPerHoldingState = mutableMapOf<String, InvestmentPriceHistory>()
    val distinctTimestamps = sorted.map { it.priceDate }.distinct()
    if (distinctTimestamps.size < 2) {
      return emptyList()
    }

    val dateFormatter = SimpleDateFormat("dd MMM", Locale.getDefault())
    val points = mutableListOf<PortfolioGrowthPoint>()

    var idx = 0
    for (timestamp in distinctTimestamps) {
      while (idx < sorted.size && sorted[idx].priceDate <= timestamp) {
        val snapshot = sorted[idx]
        latestPerHoldingState[snapshot.holdingId] = snapshot
        idx++
      }

      var investedSum = 0L
      var valuationSum = 0L
      var realizedSum = 0L
      for (state in latestPerHoldingState.values) {
        investedSum = Math.addExact(investedSum, state.totalInvestedMinor)
        valuationSum = Math.addExact(valuationSum, state.valuationMinor)
        realizedSum = Math.addExact(realizedSum, state.realizedPnlMinor)
      }
      val unrealized = Math.subtractExact(valuationSum, investedSum)
      points.add(
        PortfolioGrowthPoint(
          timestampMillis = timestamp,
          label = dateFormatter.format(Date(timestamp)),
          investedMinor = investedSum,
          valuationMinor = valuationSum,
          realizedPnlMinor = realizedSum,
          unrealizedPnlMinor = unrealized,
        )
      )
    }
    return points
  }
}
