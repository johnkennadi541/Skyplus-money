package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.PriceSourceType

@Entity(
  tableName = "investment_holdings",
  indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "category"]),
    Index(value = ["userId", "currencyCode"]),
  ],
)
data class InvestmentHoldingEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val name: String,
  val category: String,
  val symbolOrTicker: String? = null,
  val isin: String? = null,
  val brokerOrPlatform: String? = null,
  val currencyCode: String,
  val purchaseDate: Long,
  val quantityDecimal: String,
  val totalInvestedMinor: Long,
  val totalBuyCostMinor: Long,
  val realizedPnlMinor: Long,
  val totalChargesMinor: Long,
  val currentMarketPriceDecimal: String,
  val currentValuationMinor: Long,
  val lastPriceUpdateDate: Long,
  val lastPriceSource: String,
  val lastPriceProviderLabel: String? = null,
  val autoPriceUpdateEnabled: Boolean,
  val notes: String? = null,
  val createdAt: Long,
  val updatedAt: Long,
) {
  fun toDomain(): InvestmentHolding =
    InvestmentHolding(
      id = id,
      userId = userId,
      name = name,
      category =
        try {
          InvestmentCategory.valueOf(category)
        } catch (_: IllegalArgumentException) {
          InvestmentCategory.OTHER
        },
      symbolOrTicker = symbolOrTicker,
      isin = isin,
      brokerOrPlatform = brokerOrPlatform,
      currencyCode = currencyCode,
      purchaseDate = purchaseDate,
      quantityDecimal = quantityDecimal,
      totalInvestedMinor = totalInvestedMinor,
      totalBuyCostMinor = totalBuyCostMinor,
      realizedPnlMinor = realizedPnlMinor,
      totalChargesMinor = totalChargesMinor,
      currentMarketPriceDecimal = currentMarketPriceDecimal,
      currentValuationMinor = currentValuationMinor,
      lastPriceUpdateDate = lastPriceUpdateDate,
      lastPriceSource =
        try {
          PriceSourceType.valueOf(lastPriceSource)
        } catch (_: IllegalArgumentException) {
          PriceSourceType.MANUAL
        },
      lastPriceProviderLabel = lastPriceProviderLabel,
      autoPriceUpdateEnabled = autoPriceUpdateEnabled,
      notes = notes,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )

  companion object {
    fun fromDomain(holding: InvestmentHolding): InvestmentHoldingEntity =
      InvestmentHoldingEntity(
        id = holding.id,
        userId = holding.userId,
        name = holding.name,
        category = holding.category.name,
        symbolOrTicker = holding.symbolOrTicker,
        isin = holding.isin,
        brokerOrPlatform = holding.brokerOrPlatform,
        currencyCode = holding.currencyCode,
        purchaseDate = holding.purchaseDate,
        quantityDecimal = holding.quantityDecimal,
        totalInvestedMinor = holding.totalInvestedMinor,
        totalBuyCostMinor = holding.totalBuyCostMinor,
        realizedPnlMinor = holding.realizedPnlMinor,
        totalChargesMinor = holding.totalChargesMinor,
        currentMarketPriceDecimal = holding.currentMarketPriceDecimal,
        currentValuationMinor = holding.currentValuationMinor,
        lastPriceUpdateDate = holding.lastPriceUpdateDate,
        lastPriceSource = holding.lastPriceSource.name,
        lastPriceProviderLabel = holding.lastPriceProviderLabel,
        autoPriceUpdateEnabled = holding.autoPriceUpdateEnabled,
        notes = holding.notes,
        createdAt = holding.createdAt,
        updatedAt = holding.updatedAt,
      )
  }
}
