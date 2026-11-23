package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.InvestmentPriceHistory
import com.example.domain.model.PriceSourceType

@Entity(
  tableName = "investment_price_history",
  foreignKeys = [
    ForeignKey(
      entity = InvestmentHoldingEntity::class,
      parentColumns = ["id"],
      childColumns = ["holdingId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["holdingId"]),
    Index(value = ["userId", "holdingId", "priceDate"]),
    Index(value = ["userId", "currencyCode", "priceDate"]),
  ],
)
data class InvestmentPriceHistoryEntity(
  @PrimaryKey val id: String,
  val holdingId: String,
  val userId: String,
  val priceDate: Long,
  val unitPriceDecimal: String,
  val valuationMinor: Long,
  val totalInvestedMinor: Long,
  val realizedPnlMinor: Long,
  val currencyCode: String,
  val source: String,
  val providerLabel: String,
  val createdAt: Long,
) {
  fun toDomain(): InvestmentPriceHistory =
    InvestmentPriceHistory(
      id = id,
      holdingId = holdingId,
      userId = userId,
      priceDate = priceDate,
      unitPriceDecimal = unitPriceDecimal,
      valuationMinor = valuationMinor,
      totalInvestedMinor = totalInvestedMinor,
      realizedPnlMinor = realizedPnlMinor,
      currencyCode = currencyCode,
      source =
        try {
          PriceSourceType.valueOf(source)
        } catch (_: IllegalArgumentException) {
          PriceSourceType.MANUAL
        },
      providerLabel = providerLabel,
      createdAt = createdAt,
    )

  companion object {
    fun fromDomain(history: InvestmentPriceHistory): InvestmentPriceHistoryEntity =
      InvestmentPriceHistoryEntity(
        id = history.id,
        holdingId = history.holdingId,
        userId = history.userId,
        priceDate = history.priceDate,
        unitPriceDecimal = history.unitPriceDecimal,
        valuationMinor = history.valuationMinor,
        totalInvestedMinor = history.totalInvestedMinor,
        realizedPnlMinor = history.realizedPnlMinor,
        currencyCode = history.currencyCode,
        source = history.source.name,
        providerLabel = history.providerLabel,
        createdAt = history.createdAt,
      )
  }
}
