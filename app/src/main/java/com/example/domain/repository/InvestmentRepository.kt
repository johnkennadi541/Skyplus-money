package com.example.domain.repository

import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.InvestmentPriceHistory
import com.example.domain.model.InvestmentTransaction
import com.example.domain.model.MarketSyncSettings
import com.example.domain.model.MarketSyncSummary
import com.example.domain.model.PriceSourceType
import kotlinx.coroutines.flow.Flow

interface InvestmentRepository {

  fun getHoldings(userId: String): Flow<List<InvestmentHolding>>

  fun getHoldingByIdFlow(userId: String, holdingId: String): Flow<InvestmentHolding?>

  suspend fun getHoldingById(userId: String, holdingId: String): InvestmentHolding?

  fun getTransactionsForHolding(userId: String, holdingId: String): Flow<List<InvestmentTransaction>>

  fun getAllTransactionsForUser(userId: String): Flow<List<InvestmentTransaction>>

  fun getPriceHistoryForHolding(userId: String, holdingId: String): Flow<List<InvestmentPriceHistory>>

  fun getAllPriceHistory(userId: String): Flow<List<InvestmentPriceHistory>>

  fun observeSyncSettings(authenticatedUserId: String): Flow<MarketSyncSettings>

  suspend fun setAutoUpdateConsent(authenticatedUserId: String, enabled: Boolean)

  suspend fun setSyncOnlyOnWifi(authenticatedUserId: String, wifiOnly: Boolean)

  suspend fun createHoldingWithInitialBuy(
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
  ): Result<InvestmentHolding>

  suspend fun updateHoldingMetadata(
    authenticatedUserId: String,
    holding: InvestmentHolding,
  ): Result<Unit>

  suspend fun setHoldingAutoPriceUpdate(
    authenticatedUserId: String,
    holdingId: String,
    enabled: Boolean,
  ): Result<Unit>

  suspend fun recordAdditionalPurchase(
    authenticatedUserId: String,
    holdingId: String,
    transactionDate: Long,
    quantityDecimal: String,
    unitPriceDecimal: String,
    chargesMinor: Long,
    notes: String?,
  ): Result<InvestmentTransaction>

  suspend fun recordSale(
    authenticatedUserId: String,
    holdingId: String,
    transactionDate: Long,
    quantityDecimal: String,
    unitPriceDecimal: String,
    chargesMinor: Long,
    notes: String?,
  ): Result<InvestmentTransaction>

  suspend fun updateManualMarketPrice(
    authenticatedUserId: String,
    holdingId: String,
    newUnitPriceDecimal: String,
    priceDate: Long,
  ): Result<Unit>

  suspend fun applyAutomaticMarketPrice(
    authenticatedUserId: String,
    holdingId: String,
    newUnitPriceDecimal: String,
    priceDate: Long,
    source: PriceSourceType,
    providerLabel: String,
    allowOverwriteManual: Boolean = false,
  ): Result<Boolean>

  suspend fun syncEligibleMarketPrices(
    authenticatedUserId: String,
    allowOverwriteManual: Boolean = false,
    forceBypassCooldown: Boolean = false,
  ): Result<MarketSyncSummary>

  suspend fun deleteHolding(
    authenticatedUserId: String,
    holdingId: String,
  ): Result<Unit>
}
