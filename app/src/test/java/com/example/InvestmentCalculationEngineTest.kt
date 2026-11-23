package com.example

import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.InvestmentPriceHistory
import com.example.domain.model.PriceSourceType
import com.example.domain.usecase.InvestmentCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InvestmentCalculationEngineTest {

  private val engine = InvestmentCalculationEngine()

  @Test
  fun `initial purchase and additional buy accumulate weighted average cost basis accurately`() {
    // Initial buy: 10 units @ 100.00 INR + 25.00 INR charges = 1025.00 INR
    val firstBuy =
      engine.applyBuy(
        currentQuantityDecimal = "0",
        currentOpenCostBasisMinor = 0L,
        currentTotalBuyCostMinor = 0L,
        currentTotalChargesMinor = 0L,
        buyQuantityDecimal = "10",
        buyUnitPriceDecimal = "100.00",
        chargesMinor = 25_00L,
        currentMarketPriceDecimal = "110.00",
        currencyCode = "INR",
      )
    assertEquals("10", firstBuy.newQuantityDecimal)
    assertEquals(1025_00L, firstBuy.newOpenCostBasisMinor)
    assertEquals(1100_00L, firstBuy.newValuationMinor)

    // Additional buy: 10 units @ 120.00 INR + 15.00 INR charges = 1215.00 INR
    // Cumulative open cost = 2240.00 INR across 20 units (WAC = 112.00 / unit)
    val secondBuy =
      engine.applyBuy(
        currentQuantityDecimal = firstBuy.newQuantityDecimal,
        currentOpenCostBasisMinor = firstBuy.newOpenCostBasisMinor,
        currentTotalBuyCostMinor = firstBuy.newTotalBuyCostMinor,
        currentTotalChargesMinor = firstBuy.newTotalChargesMinor,
        buyQuantityDecimal = "10",
        buyUnitPriceDecimal = "120.00",
        chargesMinor = 15_00L,
        currentMarketPriceDecimal = "130.00",
        currencyCode = "INR",
      )
    assertEquals("20", secondBuy.newQuantityDecimal)
    assertEquals(2240_00L, secondBuy.newOpenCostBasisMinor)
    assertEquals(2240_00L, secondBuy.newTotalBuyCostMinor)
    assertEquals(40_00L, secondBuy.newTotalChargesMinor)
    assertEquals(2600_00L, secondBuy.newValuationMinor)
  }

  @Test
  fun `partial and complete sales allocate weighted average cost basis and exact realized pnl`() {
    // 20 units, open cost 2240.00 INR (112.00 / unit). Market price 130.00 INR.
    // Partial sell: 5 units @ 130.00 INR, charges 10.00 INR
    // Gross proceeds = 650.00 INR; net proceeds = 640.00 INR
    // Allocated WAC cost = 2240.00 * (5 / 20) = 560.00 INR
    // Realized P&L delta = 640.00 - 560.00 = +80.00 INR; remaining open cost = 1680.00 INR for 15 units
    val partialSell =
      engine.applyWeightedAverageSell(
        currentQuantityDecimal = "20",
        currentOpenCostBasisMinor = 2240_00L,
        currentRealizedPnlMinor = 0L,
        currentTotalChargesMinor = 40_00L,
        sellQuantityDecimal = "5",
        sellUnitPriceDecimal = "130.00",
        chargesMinor = 10_00L,
        currentMarketPriceDecimal = "130.00",
        currencyCode = "INR",
      )
    assertFalse(partialSell.isCompleteExit)
    assertEquals(650_00L, partialSell.grossProceedsMinor)
    assertEquals(640_00L, partialSell.netProceedsMinor)
    assertEquals(560_00L, partialSell.costBasisSoldMinor)
    assertEquals(80_00L, partialSell.realizedPnlDeltaMinor)
    assertEquals("15", partialSell.remainingQuantityDecimal)
    assertEquals(1680_00L, partialSell.remainingOpenCostBasisMinor)
    assertEquals(1950_00L, partialSell.newValuationMinor)

    // Complete sell: remaining 15 units @ 140.00 INR, charges 20.00 INR
    // Gross = 2100.00, Net = 2080.00, Cost Sold = 1680.00, Realized Delta = +400.00, Cumulative = +480.00
    val completeSell =
      engine.applyWeightedAverageSell(
        currentQuantityDecimal = partialSell.remainingQuantityDecimal,
        currentOpenCostBasisMinor = partialSell.remainingOpenCostBasisMinor,
        currentRealizedPnlMinor = partialSell.newCumulativeRealizedPnlMinor,
        currentTotalChargesMinor = partialSell.newTotalChargesMinor,
        sellQuantityDecimal = "15",
        sellUnitPriceDecimal = "140.00",
        chargesMinor = 20_00L,
        currentMarketPriceDecimal = "140.00",
        currencyCode = "INR",
      )
    assertTrue(completeSell.isCompleteExit)
    assertEquals("0", completeSell.remainingQuantityDecimal)
    assertEquals(0L, completeSell.remainingOpenCostBasisMinor)
    assertEquals(0L, completeSell.newValuationMinor)
    assertEquals(400_00L, completeSell.realizedPnlDeltaMinor)
    assertEquals(480_00L, completeSell.newCumulativeRealizedPnlMinor)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `sale quantity exceeding held units throws IllegalArgumentException`() {
    engine.applyWeightedAverageSell(
      currentQuantityDecimal = "5",
      currentOpenCostBasisMinor = 500_00L,
      currentRealizedPnlMinor = 0L,
      currentTotalChargesMinor = 0L,
      sellQuantityDecimal = "6",
      sellUnitPriceDecimal = "100.00",
      chargesMinor = 0L,
      currentMarketPriceDecimal = "100.00",
      currencyCode = "USD",
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun `zero or negative buy quantity throws IllegalArgumentException`() {
    engine.parsePositiveDecimal("0", "Units")
  }

  @Test
  fun `multi currency portfolio summaries remain strictly separated and never mix currencies`() {
    val now = 1_750_000_000_000L
    val holdings =
      listOf(
        InvestmentHolding(
          id = "h_inr",
          userId = "u1",
          name = "Nifty 50 ETF",
          category = InvestmentCategory.ETFS,
          currencyCode = "INR",
          purchaseDate = now,
          quantityDecimal = "10",
          totalInvestedMinor = 2000_00L,
          totalBuyCostMinor = 2000_00L,
          currentMarketPriceDecimal = "250.00",
          currentValuationMinor = 2500_00L,
          lastPriceUpdateDate = now,
        ),
        InvestmentHolding(
          id = "h_usd",
          userId = "u1",
          name = "S&P 500 Index",
          category = InvestmentCategory.STOCKS,
          currencyCode = "USD",
          purchaseDate = now,
          quantityDecimal = "2",
          totalInvestedMinor = 800_00L,
          totalBuyCostMinor = 800_00L,
          currentMarketPriceDecimal = "450.00",
          currentValuationMinor = 900_00L,
          lastPriceUpdateDate = now,
        ),
      )

    val summaries = engine.calculateCurrencySummaries(holdings, nowMillis = now)
    assertEquals(setOf("INR", "USD"), summaries.keys)
    assertEquals(2500_00L, summaries["INR"]?.totalValuationMinor)
    assertEquals(500_00L, summaries["INR"]?.unrealizedPnlMinor)
    assertEquals(BigDecimal("25.00"), summaries["INR"]?.returnPercent)
    assertEquals(900_00L, summaries["USD"]?.totalValuationMinor)
    assertEquals(100_00L, summaries["USD"]?.unrealizedPnlMinor)
    assertEquals(BigDecimal("12.50"), summaries["USD"]?.returnPercent)
  }

  @Test
  fun `stale price warning triggers only for active market traded categories older than 7 days`() {
    val now = 1_750_000_000_000L
    val eightDaysAgo = now - (8L * 24L * 60L * 60L * 1000L)

    val staleStock =
      InvestmentHolding(
        id = "h_stock",
        userId = "u1",
        name = "Reliance",
        category = InvestmentCategory.STOCKS,
        currencyCode = "INR",
        purchaseDate = eightDaysAgo,
        quantityDecimal = "1",
        totalInvestedMinor = 100_00L,
        totalBuyCostMinor = 100_00L,
        currentMarketPriceDecimal = "100.00",
        currentValuationMinor = 100_00L,
        lastPriceUpdateDate = eightDaysAgo,
      )
    val fixedDeposit =
      staleStock.copy(
        id = "h_fd",
        name = "SBI FD",
        category = InvestmentCategory.FIXED_DEPOSITS,
      )

    assertTrue(engine.evaluateHoldingPerformance(staleStock, now).isPriceStale)
    assertFalse(engine.evaluateHoldingPerformance(staleStock.copy(lastPriceUpdateDate = 0L), now).isPriceStale)
    assertFalse(engine.evaluateHoldingPerformance(fixedDeposit, now).isPriceStale)
  }

  @Test
  fun `authentic growth timeline returns empty when fewer than two distinct dated snapshots exist`() {
    val t1 = 1_750_000_000_000L
    val t2 = t1 + 86_400_000L
    val singlePoint =
      listOf(
        InvestmentPriceHistory(
          holdingId = "h1",
          userId = "u1",
          priceDate = t1,
          unitPriceDecimal = "100",
          valuationMinor = 100_00L,
          totalInvestedMinor = 100_00L,
          realizedPnlMinor = 0L,
          currencyCode = "INR",
          source = PriceSourceType.MANUAL,
          providerLabel = "Manual",
        )
      )
    assertTrue(engine.buildAuthenticGrowthTimeline(singlePoint, 1).isEmpty())

    val twoPoints =
      singlePoint +
        InvestmentPriceHistory(
          holdingId = "h1",
          userId = "u1",
          priceDate = t2,
          unitPriceDecimal = "115",
          valuationMinor = 115_00L,
          totalInvestedMinor = 100_00L,
          realizedPnlMinor = 0L,
          currencyCode = "INR",
          source = PriceSourceType.MANUAL,
          providerLabel = "Manual",
        )
    val timeline = engine.buildAuthenticGrowthTimeline(twoPoints, 1)
    assertEquals(2, timeline.size)
    assertEquals(100_00L, timeline[0].valuationMinor)
    assertEquals(115_00L, timeline[1].valuationMinor)
    assertEquals(15_00L, timeline[1].unrealizedPnlMinor)
  }
}
