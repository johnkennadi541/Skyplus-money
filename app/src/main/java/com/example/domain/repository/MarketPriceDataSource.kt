package com.example.domain.repository

import com.example.domain.model.InvestmentCategory
import com.example.domain.model.PriceSourceType

data class MarketQuoteRequest(
  val holdingId: String,
  val category: InvestmentCategory,
  val symbolOrTicker: String?,
  val isin: String?,
  val currencyCode: String,
)

sealed interface MarketQuoteFetchResult {
  data class Success(
    val unitPriceDecimal: String,
    val asOfMillis: Long,
    val source: PriceSourceType,
    val providerLabel: String,
    val instrumentName: String? = null,
  ) : MarketQuoteFetchResult

  data class RateLimited(
    val retryAfterMillis: Long?,
    val message: String,
  ) : MarketQuoteFetchResult

  data class UnsupportedCategoryOrIdentifier(
    val message: String,
  ) : MarketQuoteFetchResult

  data class NetworkError(
    val message: String,
  ) : MarketQuoteFetchResult

  data class InvalidResponse(
    val message: String,
  ) : MarketQuoteFetchResult
}

interface MarketPriceDataSource {
  suspend fun fetchLatestQuote(request: MarketQuoteRequest): MarketQuoteFetchResult
}
