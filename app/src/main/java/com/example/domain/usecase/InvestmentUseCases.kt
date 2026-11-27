package com.example.domain.usecase

import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.InvestmentHoldingPerformance
import com.example.domain.model.InvestmentPriceHistory
import com.example.domain.model.InvestmentTransaction
import com.example.domain.model.MarketSyncSettings
import com.example.domain.model.MarketSyncSummary
import com.example.domain.model.Money
import com.example.domain.model.PortfolioCurrencySummary
import com.example.domain.repository.InvestmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class InvestmentPortfolioSnapshot(
  val holdings: List<InvestmentHolding> = emptyList(),
  val performances: List<InvestmentHoldingPerformance> = emptyList(),
  val summariesByCurrency: Map<String, PortfolioCurrencySummary> = emptyMap(),
)

data class InvestmentHoldingDetailState(
  val holding: InvestmentHolding? = null,
  val performance: InvestmentHoldingPerformance? = null,
  val transactions: List<InvestmentTransaction> = emptyList(),
  val priceHistory: List<InvestmentPriceHistory> = emptyList(),
)

class ObserveArchivedInvestmentsUseCase @Inject constructor(
  private val repository: InvestmentRepository,
) {
  operator fun invoke(userId: String): Flow<List<InvestmentHolding>> =
    repository.getArchivedHoldings(userId)
}

class RestoreInvestmentUseCase @Inject constructor(
  private val repository: InvestmentRepository,
) {
  suspend operator fun invoke(authenticatedUserId: String, holdingId: String): Result<Unit> =
    repository.restoreHolding(authenticatedUserId, holdingId)
}

class ObservePortfolioUseCase @Inject constructor(
  private val repository: InvestmentRepository,
  private val calculationEngine: InvestmentCalculationEngine,
) {
  operator fun invoke(userId: String): Flow<InvestmentPortfolioSnapshot> {
    return combine(
      repository.getHoldings(userId),
      repository.getAllPriceHistory(userId),
    ) { holdings, priceHistory ->
      val now = System.currentTimeMillis()
      val performances = holdings.map { calculationEngine.evaluateHoldingPerformance(it, now) }
      val summaries = calculationEngine.calculateCurrencySummaries(holdings, priceHistory, now)
      InvestmentPortfolioSnapshot(
        holdings = holdings,
        performances = performances,
        summariesByCurrency = summaries,
      )
    }
  }
}

class ObserveHoldingDetailUseCase @Inject constructor(
  private val repository: InvestmentRepository,
  private val calculationEngine: InvestmentCalculationEngine,
) {
  operator fun invoke(userId: String, holdingId: String): Flow<InvestmentHoldingDetailState> {
    return combine(
      repository.getHoldingByIdFlow(userId, holdingId),
      repository.getTransactionsForHolding(userId, holdingId),
      repository.getPriceHistoryForHolding(userId, holdingId),
    ) { holding, transactions, history ->
      val performance =
        holding?.let { calculationEngine.evaluateHoldingPerformance(it, System.currentTimeMillis()) }
      InvestmentHoldingDetailState(
        holding = holding,
        performance = performance,
        transactions = transactions,
        priceHistory = history,
      )
    }
  }
}

class CreateInvestmentUseCase @Inject constructor(
  private val repository: InvestmentRepository,
  private val calculationEngine: InvestmentCalculationEngine,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
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
  ): Result<InvestmentHolding> {
    val chargesMinor =
      try {
        val chargesBd = calculationEngine.parseNonNegativeDecimal(chargesDecimal, "Transaction charges")
        Money.fromBigDecimal(chargesBd, currencyCode, RoundingMode.HALF_UP).minorUnits
      } catch (e: Throwable) {
        return Result.failure(e)
      }

    return repository.createHoldingWithInitialBuy(
      authenticatedUserId = authenticatedUserId,
      name = name,
      category = category,
      symbolOrTicker = symbolOrTicker,
      isin = isin,
      brokerOrPlatform = brokerOrPlatform,
      currencyCode = currencyCode,
      purchaseDate = purchaseDate,
      quantityDecimal = quantityDecimal,
      purchasePriceDecimal = purchasePriceDecimal,
      chargesMinor = chargesMinor,
      currentMarketPriceDecimal = currentMarketPriceDecimal,
      autoPriceUpdateEnabled = autoPriceUpdateEnabled,
      notes = notes,
    )
  }
}

class RecordInvestmentBuyUseCase @Inject constructor(
  private val repository: InvestmentRepository,
  private val calculationEngine: InvestmentCalculationEngine,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    holdingId: String,
    currencyCode: String,
    transactionDate: Long,
    quantityDecimal: String,
    unitPriceDecimal: String,
    chargesDecimal: String,
    notes: String?,
  ): Result<InvestmentTransaction> {
    val chargesMinor =
      try {
        val chargesBd = calculationEngine.parseNonNegativeDecimal(chargesDecimal, "Transaction charges")
        Money.fromBigDecimal(chargesBd, currencyCode, RoundingMode.HALF_UP).minorUnits
      } catch (e: Throwable) {
        return Result.failure(e)
      }

    return repository.recordAdditionalPurchase(
      authenticatedUserId = authenticatedUserId,
      holdingId = holdingId,
      transactionDate = transactionDate,
      quantityDecimal = quantityDecimal,
      unitPriceDecimal = unitPriceDecimal,
      chargesMinor = chargesMinor,
      notes = notes,
    )
  }
}

class RecordInvestmentSellUseCase @Inject constructor(
  private val repository: InvestmentRepository,
  private val calculationEngine: InvestmentCalculationEngine,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    holdingId: String,
    currencyCode: String,
    transactionDate: Long,
    quantityDecimal: String,
    unitPriceDecimal: String,
    chargesDecimal: String,
    notes: String?,
  ): Result<InvestmentTransaction> {
    val chargesMinor =
      try {
        val chargesBd = calculationEngine.parseNonNegativeDecimal(chargesDecimal, "Transaction charges")
        Money.fromBigDecimal(chargesBd, currencyCode, RoundingMode.HALF_UP).minorUnits
      } catch (e: Throwable) {
        return Result.failure(e)
      }

    return repository.recordSale(
      authenticatedUserId = authenticatedUserId,
      holdingId = holdingId,
      transactionDate = transactionDate,
      quantityDecimal = quantityDecimal,
      unitPriceDecimal = unitPriceDecimal,
      chargesMinor = chargesMinor,
      notes = notes,
    )
  }
}

class UpdateManualMarketPriceUseCase @Inject constructor(
  private val repository: InvestmentRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    holdingId: String,
    newUnitPriceDecimal: String,
    priceDate: Long,
  ): Result<Unit> =
    repository.updateManualMarketPrice(
      authenticatedUserId = authenticatedUserId,
      holdingId = holdingId,
      newUnitPriceDecimal = newUnitPriceDecimal,
      priceDate = priceDate,
    )
}

class UpdateInvestmentMetadataUseCase @Inject constructor(
  private val repository: InvestmentRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    holding: InvestmentHolding,
  ): Result<Unit> = repository.updateHoldingMetadata(authenticatedUserId, holding)
}

class SetHoldingAutoPriceUseCase @Inject constructor(
  private val repository: InvestmentRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    holdingId: String,
    enabled: Boolean,
  ): Result<Unit> =
    repository.setHoldingAutoPriceUpdate(
      authenticatedUserId = authenticatedUserId,
      holdingId = holdingId,
      enabled = enabled,
    )
}

class SyncMarketPricesUseCase @Inject constructor(
  private val repository: InvestmentRepository,
) {
  fun observeSettings(): Flow<MarketSyncSettings> = repository.observeSyncSettings()

  suspend fun setAutoUpdateConsent(enabled: Boolean) = repository.setAutoUpdateConsent(enabled)

  suspend fun setSyncOnlyOnWifi(wifiOnly: Boolean) = repository.setSyncOnlyOnWifi(wifiOnly)

  suspend fun syncNow(
    authenticatedUserId: String,
    allowOverwriteManual: Boolean = false,
    forceBypassCooldown: Boolean = true,
  ): Result<MarketSyncSummary> =
    repository.syncEligibleMarketPrices(
      authenticatedUserId = authenticatedUserId,
      allowOverwriteManual = allowOverwriteManual,
      forceBypassCooldown = forceBypassCooldown,
    )
}

class DeleteInvestmentUseCase @Inject constructor(
  private val repository: InvestmentRepository,
) {
  suspend operator fun invoke(authenticatedUserId: String, holdingId: String): Result<Unit> =
    repository.deleteHolding(authenticatedUserId, holdingId)
}
