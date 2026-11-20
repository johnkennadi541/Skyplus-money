package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.common.DispatcherProvider
import com.example.data.local.MarketPriceSyncPreferencesDataSource
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.dao.InvestmentDao
import com.example.data.local.db.entity.InvestmentHoldingEntity
import com.example.data.local.db.entity.InvestmentPriceHistoryEntity
import com.example.data.local.db.entity.InvestmentTransactionEntity
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.InvestmentPriceHistory
import com.example.domain.model.InvestmentTransaction
import com.example.domain.model.InvestmentTransactionType
import com.example.domain.model.MarketSyncSettings
import com.example.domain.model.MarketSyncSummary
import com.example.domain.model.Money
import com.example.domain.model.PriceSourceType
import com.example.domain.repository.InvestmentRepository
import com.example.domain.repository.MarketPriceDataSource
import com.example.domain.repository.MarketQuoteFetchResult
import com.example.domain.repository.MarketQuoteRequest
import com.example.domain.usecase.InvestmentCalculationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvestmentRepositoryImpl @Inject constructor(
  private val database: AppDatabase,
  private val investmentDao: InvestmentDao,
  private val calculationEngine: InvestmentCalculationEngine,
  private val marketPriceDataSource: MarketPriceDataSource,
  private val syncPreferencesDataSource: MarketPriceSyncPreferencesDataSource,
  private val dispatchers: DispatcherProvider,
) : InvestmentRepository {

  companion object {
    const val AUTO_SYNC_COOLDOWN_MILLIS: Long = 12L * 60L * 60L * 1000L
    const val MAX_REQUESTS_PER_SYNC_CYCLE: Int = 5
  }

  override fun getHoldings(userId: String): Flow<List<InvestmentHolding>> =
    investmentDao.getAllHoldings(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun getHoldingByIdFlow(userId: String, holdingId: String): Flow<InvestmentHolding?> =
    investmentDao.getHoldingByIdFlow(userId, holdingId)
      .map { it?.toDomain() }
      .flowOn(dispatchers.io)

  override suspend fun getHoldingById(userId: String, holdingId: String): InvestmentHolding? =
    withContext(dispatchers.io) {
      investmentDao.getHoldingById(userId, holdingId)?.toDomain()
    }

  override fun getTransactionsForHolding(
    userId: String,
    holdingId: String,
  ): Flow<List<InvestmentTransaction>> =
    investmentDao.getTransactionsForHolding(userId, holdingId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun getAllTransactionsForUser(userId: String): Flow<List<InvestmentTransaction>> =
    investmentDao.getAllTransactionsForUser(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun getPriceHistoryForHolding(
    userId: String,
    holdingId: String,
  ): Flow<List<InvestmentPriceHistory>> =
    investmentDao.getPriceHistoryForHolding(userId, holdingId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun getAllPriceHistory(userId: String): Flow<List<InvestmentPriceHistory>> =
    investmentDao.getAllPriceHistory(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun observeSyncSettings(): Flow<MarketSyncSettings> =
    syncPreferencesDataSource.syncSettings

  override suspend fun setAutoUpdateConsent(enabled: Boolean) {
    withContext(dispatchers.io) {
      syncPreferencesDataSource.setAutoUpdateConsent(enabled)
    }
  }

  override suspend fun setSyncOnlyOnWifi(wifiOnly: Boolean) {
    withContext(dispatchers.io) {
      syncPreferencesDataSource.setSyncOnlyOnWifi(wifiOnly)
    }
  }

  override suspend fun createHoldingWithInitialBuy(
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
    chargesMinor: Long,
    currentMarketPriceDecimal: String?,
    autoPriceUpdateEnabled: Boolean,
    notes: String?,
  ): Result<InvestmentHolding> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(name.isNotBlank()) { "Investment name is required" }
        require(purchaseDate > 0L) { "Purchase date must be valid" }
        require(chargesMinor >= 0L) { "Transaction charges cannot be negative" }

        val normalizedCurrency = currencyCode.trim().uppercase()
        Money.resolveFractionDigits(normalizedCurrency)

        val buyQty = calculationEngine.parsePositiveDecimal(quantityDecimal, "Units / quantity")
        val buyPrice = calculationEngine.parsePositiveDecimal(purchasePriceDecimal, "Purchase price")
        val marketPrice =
          if (currentMarketPriceDecimal.isNullOrBlank()) {
            buyPrice
          } else {
            calculationEngine.parsePositiveDecimal(currentMarketPriceDecimal, "Current market price")
          }
        val marketPriceText = marketPrice.stripTrailingZeros().toPlainString()

        val mutation =
          calculationEngine.applyBuy(
            currentQuantityDecimal = "0",
            currentOpenCostBasisMinor = 0L,
            currentTotalBuyCostMinor = 0L,
            currentTotalChargesMinor = 0L,
            buyQuantityDecimal = buyQty.stripTrailingZeros().toPlainString(),
            buyUnitPriceDecimal = buyPrice.stripTrailingZeros().toPlainString(),
            chargesMinor = chargesMinor,
            currentMarketPriceDecimal = marketPriceText,
            currencyCode = normalizedCurrency,
          )

        val now = System.currentTimeMillis()
        val holdingId = UUID.randomUUID().toString()
        val allowAutoForHolding =
          autoPriceUpdateEnabled && category == InvestmentCategory.MUTUAL_FUNDS

        val holding =
          InvestmentHolding(
            id = holdingId,
            userId = authenticatedUserId,
            name = name.trim(),
            category = category,
            symbolOrTicker = symbolOrTicker?.trim()?.takeIf { it.isNotEmpty() },
            isin = isin?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            brokerOrPlatform = brokerOrPlatform?.trim()?.takeIf { it.isNotEmpty() },
            currencyCode = normalizedCurrency,
            purchaseDate = purchaseDate,
            quantityDecimal = mutation.newQuantityDecimal,
            totalInvestedMinor = mutation.newOpenCostBasisMinor,
            totalBuyCostMinor = mutation.newTotalBuyCostMinor,
            realizedPnlMinor = 0L,
            totalChargesMinor = mutation.newTotalChargesMinor,
            currentMarketPriceDecimal = marketPriceText,
            currentValuationMinor = mutation.newValuationMinor,
            lastPriceUpdateDate = purchaseDate,
            lastPriceSource = PriceSourceType.MANUAL,
            lastPriceProviderLabel = "Manual",
            autoPriceUpdateEnabled = allowAutoForHolding,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = now,
            updatedAt = now,
          )

        val initialTx =
          InvestmentTransaction(
            id = UUID.randomUUID().toString(),
            holdingId = holdingId,
            userId = authenticatedUserId,
            type = InvestmentTransactionType.BUY,
            transactionDate = purchaseDate,
            quantityDecimal = buyQty.stripTrailingZeros().toPlainString(),
            unitPriceDecimal = buyPrice.stripTrailingZeros().toPlainString(),
            grossAmountMinor = mutation.grossAmountMinor,
            chargesMinor = chargesMinor,
            netAmountMinor = mutation.netBuyCostMinor,
            realizedPnlMinor = 0L,
            costBasisAllocatedMinor = 0L,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() } ?: "Initial purchase",
            createdAt = now,
          )

        val initialPricePoint =
          InvestmentPriceHistory(
            id = UUID.randomUUID().toString(),
            holdingId = holdingId,
            userId = authenticatedUserId,
            priceDate = purchaseDate,
            unitPriceDecimal = marketPriceText,
            valuationMinor = mutation.newValuationMinor,
            totalInvestedMinor = mutation.newOpenCostBasisMinor,
            realizedPnlMinor = 0L,
            currencyCode = normalizedCurrency,
            source = PriceSourceType.MANUAL,
            providerLabel = "Manual (Initial)",
            createdAt = now,
          )

        database.withTransaction {
          investmentDao.insertHolding(InvestmentHoldingEntity.fromDomain(holding))
          investmentDao.insertTransaction(InvestmentTransactionEntity.fromDomain(initialTx))
          investmentDao.insertPriceHistory(InvestmentPriceHistoryEntity.fromDomain(initialPricePoint))
        }
        holding
      }
    }

  override suspend fun updateHoldingMetadata(
    authenticatedUserId: String,
    holding: InvestmentHolding,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(holding.userId == authenticatedUserId) {
          "Investment ownership cannot be modified or transferred across users"
        }
        require(holding.name.isNotBlank()) { "Investment name cannot be blank" }
        require(holding.purchaseDate > 0L) { "Purchase date must be valid" }

        database.withTransaction {
          val existing =
            investmentDao.getHoldingById(authenticatedUserId, holding.id)
              ?: throw SecurityException("Investment holding does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) {
            "Investment ownership verification failed"
          }

          val existingDomain = existing.toDomain()
          val allowAuto =
            holding.autoPriceUpdateEnabled && holding.category == InvestmentCategory.MUTUAL_FUNDS
          val now = System.currentTimeMillis()
          val updated =
            existingDomain.copy(
              name = holding.name.trim(),
              category = holding.category,
              symbolOrTicker = holding.symbolOrTicker?.trim()?.takeIf { it.isNotEmpty() },
              isin = holding.isin?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
              brokerOrPlatform = holding.brokerOrPlatform?.trim()?.takeIf { it.isNotEmpty() },
              purchaseDate = holding.purchaseDate,
              autoPriceUpdateEnabled = allowAuto,
              notes = holding.notes?.trim()?.takeIf { it.isNotEmpty() },
              updatedAt = now,
            )

          val rowsUpdated =
            investmentDao.updateHolding(
              holding = InvestmentHoldingEntity.fromDomain(updated),
              authenticatedUserId = authenticatedUserId,
            )
          check(rowsUpdated == 1) { "Investment update rejected for authenticated user" }
        }
      }
    }

  override suspend fun setHoldingAutoPriceUpdate(
    authenticatedUserId: String,
    holdingId: String,
    enabled: Boolean,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(holdingId.isNotBlank()) { "Holding ID is required" }

        database.withTransaction {
          val existing =
            investmentDao.getHoldingById(authenticatedUserId, holdingId)?.toDomain()
              ?: throw SecurityException("Investment holding does not belong to authenticated user")
          if (enabled && existing.category != InvestmentCategory.MUTUAL_FUNDS) {
            throw IllegalStateException(
              "Automatic market price updates are only available for Mutual Funds with an AMFI scheme code"
            )
          }
          val updated =
            existing.copy(
              autoPriceUpdateEnabled = enabled,
              updatedAt = System.currentTimeMillis(),
            )
          val rows =
            investmentDao.updateHolding(
              InvestmentHoldingEntity.fromDomain(updated),
              authenticatedUserId,
            )
          check(rows == 1) { "Failed to update holding auto-sync consent" }
        }
      }
    }

  override suspend fun recordAdditionalPurchase(
    authenticatedUserId: String,
    holdingId: String,
    transactionDate: Long,
    quantityDecimal: String,
    unitPriceDecimal: String,
    chargesMinor: Long,
    notes: String?,
  ): Result<InvestmentTransaction> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(holdingId.isNotBlank()) { "Holding ID is required" }
        require(transactionDate > 0L) { "Transaction date must be valid" }
        require(chargesMinor >= 0L) { "Transaction charges cannot be negative" }

        database.withTransaction {
          val existing =
            investmentDao.getHoldingById(authenticatedUserId, holdingId)?.toDomain()
              ?: throw SecurityException("Investment holding does not belong to authenticated user")

          val buyQty = calculationEngine.parsePositiveDecimal(quantityDecimal, "Purchase quantity")
          val buyPrice = calculationEngine.parsePositiveDecimal(unitPriceDecimal, "Purchase price")

          val mutation =
            calculationEngine.applyBuy(
              currentQuantityDecimal = existing.quantityDecimal,
              currentOpenCostBasisMinor = existing.totalInvestedMinor,
              currentTotalBuyCostMinor = existing.totalBuyCostMinor,
              currentTotalChargesMinor = existing.totalChargesMinor,
              buyQuantityDecimal = buyQty.stripTrailingZeros().toPlainString(),
              buyUnitPriceDecimal = buyPrice.stripTrailingZeros().toPlainString(),
              chargesMinor = chargesMinor,
              currentMarketPriceDecimal = existing.currentMarketPriceDecimal,
              currencyCode = existing.currencyCode,
            )

          val now = System.currentTimeMillis()
          val tx =
            InvestmentTransaction(
              id = UUID.randomUUID().toString(),
              holdingId = holdingId,
              userId = authenticatedUserId,
              type = InvestmentTransactionType.BUY,
              transactionDate = transactionDate,
              quantityDecimal = buyQty.stripTrailingZeros().toPlainString(),
              unitPriceDecimal = buyPrice.stripTrailingZeros().toPlainString(),
              grossAmountMinor = mutation.grossAmountMinor,
              chargesMinor = chargesMinor,
              netAmountMinor = mutation.netBuyCostMinor,
              realizedPnlMinor = 0L,
              costBasisAllocatedMinor = 0L,
              notes = notes?.trim()?.takeIf { it.isNotEmpty() },
              createdAt = now,
            )

          val updatedHolding =
            existing.copy(
              quantityDecimal = mutation.newQuantityDecimal,
              totalInvestedMinor = mutation.newOpenCostBasisMinor,
              totalBuyCostMinor = mutation.newTotalBuyCostMinor,
              totalChargesMinor = mutation.newTotalChargesMinor,
              currentValuationMinor = mutation.newValuationMinor,
              updatedAt = now,
            )

          val snapshot =
            InvestmentPriceHistory(
              id = UUID.randomUUID().toString(),
              holdingId = holdingId,
              userId = authenticatedUserId,
              priceDate = transactionDate,
              unitPriceDecimal = existing.currentMarketPriceDecimal,
              valuationMinor = mutation.newValuationMinor,
              totalInvestedMinor = mutation.newOpenCostBasisMinor,
              realizedPnlMinor = existing.realizedPnlMinor,
              currencyCode = existing.currencyCode,
              source = PriceSourceType.MANUAL,
              providerLabel = "Additional Purchase",
              createdAt = now,
            )

          investmentDao.insertTransaction(InvestmentTransactionEntity.fromDomain(tx))
          val rows =
            investmentDao.updateHolding(
              InvestmentHoldingEntity.fromDomain(updatedHolding),
              authenticatedUserId,
            )
          check(rows == 1) { "Failed to update holding after purchase" }
          investmentDao.insertPriceHistory(InvestmentPriceHistoryEntity.fromDomain(snapshot))
          tx
        }
      }
    }

  override suspend fun recordSale(
    authenticatedUserId: String,
    holdingId: String,
    transactionDate: Long,
    quantityDecimal: String,
    unitPriceDecimal: String,
    chargesMinor: Long,
    notes: String?,
  ): Result<InvestmentTransaction> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(holdingId.isNotBlank()) { "Holding ID is required" }
        require(transactionDate > 0L) { "Transaction date must be valid" }
        require(chargesMinor >= 0L) { "Transaction charges cannot be negative" }

        database.withTransaction {
          val existing =
            investmentDao.getHoldingById(authenticatedUserId, holdingId)?.toDomain()
              ?: throw SecurityException("Investment holding does not belong to authenticated user")

          val sellQty = calculationEngine.parsePositiveDecimal(quantityDecimal, "Sale quantity")
          val sellPrice = calculationEngine.parseNonNegativeDecimal(unitPriceDecimal, "Sale price")

          val mutation =
            calculationEngine.applyWeightedAverageSell(
              currentQuantityDecimal = existing.quantityDecimal,
              currentOpenCostBasisMinor = existing.totalInvestedMinor,
              currentRealizedPnlMinor = existing.realizedPnlMinor,
              currentTotalChargesMinor = existing.totalChargesMinor,
              sellQuantityDecimal = sellQty.stripTrailingZeros().toPlainString(),
              sellUnitPriceDecimal = sellPrice.stripTrailingZeros().toPlainString(),
              chargesMinor = chargesMinor,
              currentMarketPriceDecimal = existing.currentMarketPriceDecimal,
              currencyCode = existing.currencyCode,
            )

          val now = System.currentTimeMillis()
          val tx =
            InvestmentTransaction(
              id = UUID.randomUUID().toString(),
              holdingId = holdingId,
              userId = authenticatedUserId,
              type = InvestmentTransactionType.SELL,
              transactionDate = transactionDate,
              quantityDecimal = sellQty.stripTrailingZeros().toPlainString(),
              unitPriceDecimal = sellPrice.stripTrailingZeros().toPlainString(),
              grossAmountMinor = mutation.grossProceedsMinor,
              chargesMinor = chargesMinor,
              netAmountMinor = mutation.netProceedsMinor,
              realizedPnlMinor = mutation.realizedPnlDeltaMinor,
              costBasisAllocatedMinor = mutation.costBasisSoldMinor,
              notes = notes?.trim()?.takeIf { it.isNotEmpty() },
              createdAt = now,
            )

          val updatedHolding =
            existing.copy(
              quantityDecimal = mutation.remainingQuantityDecimal,
              totalInvestedMinor = mutation.remainingOpenCostBasisMinor,
              realizedPnlMinor = mutation.newCumulativeRealizedPnlMinor,
              totalChargesMinor = mutation.newTotalChargesMinor,
              currentValuationMinor = mutation.newValuationMinor,
              updatedAt = now,
            )

          val snapshot =
            InvestmentPriceHistory(
              id = UUID.randomUUID().toString(),
              holdingId = holdingId,
              userId = authenticatedUserId,
              priceDate = transactionDate,
              unitPriceDecimal = existing.currentMarketPriceDecimal,
              valuationMinor = mutation.newValuationMinor,
              totalInvestedMinor = mutation.remainingOpenCostBasisMinor,
              realizedPnlMinor = mutation.newCumulativeRealizedPnlMinor,
              currencyCode = existing.currencyCode,
              source = PriceSourceType.MANUAL,
              providerLabel = if (mutation.isCompleteExit) "Complete Sale" else "Partial Sale",
              createdAt = now,
            )

          investmentDao.insertTransaction(InvestmentTransactionEntity.fromDomain(tx))
          val rows =
            investmentDao.updateHolding(
              InvestmentHoldingEntity.fromDomain(updatedHolding),
              authenticatedUserId,
            )
          check(rows == 1) { "Failed to update holding after sale" }
          investmentDao.insertPriceHistory(InvestmentPriceHistoryEntity.fromDomain(snapshot))
          tx
        }
      }
    }

  override suspend fun updateManualMarketPrice(
    authenticatedUserId: String,
    holdingId: String,
    newUnitPriceDecimal: String,
    priceDate: Long,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(holdingId.isNotBlank()) { "Holding ID is required" }
        require(priceDate > 0L) { "Price update date must be valid" }

        val parsedPrice = calculationEngine.parsePositiveDecimal(newUnitPriceDecimal, "Market price")
        val normalizedPriceText = parsedPrice.stripTrailingZeros().toPlainString()

        database.withTransaction {
          val existing =
            investmentDao.getHoldingById(authenticatedUserId, holdingId)?.toDomain()
              ?: throw SecurityException("Investment holding does not belong to authenticated user")

          val currentQty =
            calculationEngine.parseNonNegativeDecimal(existing.quantityDecimal, "Current quantity")
          val newValuationMinor =
            calculationEngine.computeValuationMinor(
              quantity = currentQty,
              unitPrice = parsedPrice,
              currencyCode = existing.currencyCode,
            )

          val now = System.currentTimeMillis()
          val updatedHolding =
            existing.copy(
              currentMarketPriceDecimal = normalizedPriceText,
              currentValuationMinor = newValuationMinor,
              lastPriceUpdateDate = priceDate,
              lastPriceSource = PriceSourceType.MANUAL,
              lastPriceProviderLabel = "Manual",
              updatedAt = now,
            )

          val historyEntry =
            InvestmentPriceHistory(
              id = UUID.randomUUID().toString(),
              holdingId = holdingId,
              userId = authenticatedUserId,
              priceDate = priceDate,
              unitPriceDecimal = normalizedPriceText,
              valuationMinor = newValuationMinor,
              totalInvestedMinor = existing.totalInvestedMinor,
              realizedPnlMinor = existing.realizedPnlMinor,
              currencyCode = existing.currencyCode,
              source = PriceSourceType.MANUAL,
              providerLabel = "Manual",
              createdAt = now,
            )

          val rows =
            investmentDao.updateHolding(
              InvestmentHoldingEntity.fromDomain(updatedHolding),
              authenticatedUserId,
            )
          check(rows == 1) { "Failed to update market price for holding" }
          investmentDao.insertPriceHistory(InvestmentPriceHistoryEntity.fromDomain(historyEntry))
        }
      }
    }

  override suspend fun applyAutomaticMarketPrice(
    authenticatedUserId: String,
    holdingId: String,
    newUnitPriceDecimal: String,
    priceDate: Long,
    source: PriceSourceType,
    providerLabel: String,
    allowOverwriteManual: Boolean,
  ): Result<Boolean> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(holdingId.isNotBlank()) { "Holding ID is required" }
        require(priceDate > 0L) { "Price date must be valid" }

        val parsedPrice = calculationEngine.parsePositiveDecimal(newUnitPriceDecimal, "Market price")
        val normalizedPriceText = parsedPrice.stripTrailingZeros().toPlainString()

        database.withTransaction {
          val existing =
            investmentDao.getHoldingById(authenticatedUserId, holdingId)?.toDomain()
              ?: throw SecurityException("Investment holding does not belong to authenticated user")

          // Never overwrite manual prices without explicit user consent
          if (!existing.autoPriceUpdateEnabled && !allowOverwriteManual) {
            return@withTransaction false
          }
          if (
            existing.lastPriceSource == PriceSourceType.MANUAL &&
            !existing.autoPriceUpdateEnabled &&
            !allowOverwriteManual
          ) {
            return@withTransaction false
          }
          if (
            existing.lastPriceSource == PriceSourceType.MANUAL &&
            priceDate < existing.lastPriceUpdateDate &&
            !allowOverwriteManual
          ) {
            return@withTransaction false
          }

          val currentQty =
            calculationEngine.parseNonNegativeDecimal(existing.quantityDecimal, "Current quantity")
          val newValuationMinor =
            calculationEngine.computeValuationMinor(
              quantity = currentQty,
              unitPrice = parsedPrice,
              currencyCode = existing.currencyCode,
            )

          val now = System.currentTimeMillis()
          val updatedHolding =
            existing.copy(
              currentMarketPriceDecimal = normalizedPriceText,
              currentValuationMinor = newValuationMinor,
              lastPriceUpdateDate = priceDate,
              lastPriceSource = source,
              lastPriceProviderLabel = providerLabel,
              updatedAt = now,
            )

          val historyEntry =
            InvestmentPriceHistory(
              id = UUID.randomUUID().toString(),
              holdingId = holdingId,
              userId = authenticatedUserId,
              priceDate = priceDate,
              unitPriceDecimal = normalizedPriceText,
              valuationMinor = newValuationMinor,
              totalInvestedMinor = existing.totalInvestedMinor,
              realizedPnlMinor = existing.realizedPnlMinor,
              currencyCode = existing.currencyCode,
              source = source,
              providerLabel = providerLabel,
              createdAt = now,
            )

          val rows =
            investmentDao.updateHolding(
              InvestmentHoldingEntity.fromDomain(updatedHolding),
              authenticatedUserId,
            )
          check(rows == 1) { "Failed to apply automatic market price" }
          investmentDao.insertPriceHistory(InvestmentPriceHistoryEntity.fromDomain(historyEntry))
          true
        }
      }
    }

  override suspend fun syncEligibleMarketPrices(
    authenticatedUserId: String,
    allowOverwriteManual: Boolean,
    forceBypassCooldown: Boolean,
  ): Result<MarketSyncSummary> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        val now = System.currentTimeMillis()
        val settings = syncPreferencesDataSource.syncSettings.first()

        if (!settings.autoUpdateConsentEnabled && !allowOverwriteManual) {
          val msg = "Automatic updates are off. Manual price entry remains active."
          syncPreferencesDataSource.recordSyncResult(now, msg)
          return@runCatching MarketSyncSummary(
            updatedCount = 0,
            skippedManualCount = 0,
            unsupportedCount = 0,
            rateLimitedCount = 0,
            errorCount = 0,
            message = msg,
          )
        }

        val holdings = investmentDao.getAllHoldingsSync(authenticatedUserId).map { it.toDomain() }
        var updatedCount = 0
        var skippedManualCount = 0
        var unsupportedCount = 0
        var rateLimitedCount = 0
        var errorCount = 0
        var requestsSent = 0

        for (holding in holdings) {
          val qty = runCatching { BigDecimal(holding.quantityDecimal.trim()) }.getOrDefault(BigDecimal.ZERO)
          if (qty <= BigDecimal.ZERO) continue

          if (!holding.autoPriceUpdateEnabled && !allowOverwriteManual) {
            skippedManualCount++
            continue
          }

          if (holding.category != InvestmentCategory.MUTUAL_FUNDS) {
            unsupportedCount++
            continue
          }

          val isFreshCache =
            !forceBypassCooldown &&
              holding.lastPriceSource == PriceSourceType.MFAPI_AMFI_EOD &&
              (now - holding.lastPriceUpdateDate) < AUTO_SYNC_COOLDOWN_MILLIS
          if (isFreshCache) {
            continue
          }

          if (requestsSent >= MAX_REQUESTS_PER_SYNC_CYCLE) {
            rateLimitedCount++
            continue
          }

          requestsSent++
          val quoteResult =
            marketPriceDataSource.fetchLatestQuote(
              MarketQuoteRequest(
                holdingId = holding.id,
                category = holding.category,
                symbolOrTicker = holding.symbolOrTicker,
                isin = holding.isin,
                currencyCode = holding.currencyCode,
              )
            )

          when (quoteResult) {
            is MarketQuoteFetchResult.Success -> {
              val applied =
                applyAutomaticMarketPrice(
                  authenticatedUserId = authenticatedUserId,
                  holdingId = holding.id,
                  newUnitPriceDecimal = quoteResult.unitPriceDecimal,
                  priceDate = quoteResult.asOfMillis,
                  source = quoteResult.source,
                  providerLabel = quoteResult.providerLabel,
                  allowOverwriteManual = allowOverwriteManual,
                ).getOrDefault(false)
              if (applied) updatedCount++ else skippedManualCount++
            }
            is MarketQuoteFetchResult.RateLimited -> {
              rateLimitedCount++
              break
            }
            is MarketQuoteFetchResult.UnsupportedCategoryOrIdentifier -> {
              unsupportedCount++
            }
            is MarketQuoteFetchResult.NetworkError,
            is MarketQuoteFetchResult.InvalidResponse -> {
              errorCount++
            }
          }
        }

        val statusMsg =
          buildString {
            append("Updated $updatedCount")
            if (skippedManualCount > 0) append(" • $skippedManualCount manual kept")
            if (unsupportedCount > 0) append(" • $unsupportedCount manual-only")
            if (rateLimitedCount > 0) append(" • $rateLimitedCount rate-limited")
            if (errorCount > 0) append(" • $errorCount unreachable (manual fallback active)")
          }
        syncPreferencesDataSource.recordSyncResult(now, statusMsg)

        MarketSyncSummary(
          updatedCount = updatedCount,
          skippedManualCount = skippedManualCount,
          unsupportedCount = unsupportedCount,
          rateLimitedCount = rateLimitedCount,
          errorCount = errorCount,
          message = statusMsg,
        )
      }
    }

  override suspend fun deleteHolding(
    authenticatedUserId: String,
    holdingId: String,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(holdingId.isNotBlank()) { "Holding ID is required" }

        database.withTransaction {
          val existing =
            investmentDao.getHoldingById(authenticatedUserId, holdingId)
              ?: throw SecurityException("Investment holding does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) {
            "Investment ownership verification failed"
          }

          investmentDao.deletePriceHistoryForHolding(authenticatedUserId, holdingId)
          investmentDao.deleteTransactionsForHolding(authenticatedUserId, holdingId)
          val rowsDeleted = investmentDao.deleteHolding(authenticatedUserId, holdingId)
          check(rowsDeleted == 1) { "Investment deletion rejected for authenticated user" }
        }
      }
    }
}
