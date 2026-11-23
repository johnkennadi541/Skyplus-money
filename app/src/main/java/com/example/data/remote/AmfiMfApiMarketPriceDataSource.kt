package com.example.data.remote

import com.example.core.common.DispatcherProvider
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.PriceSourceType
import com.example.domain.repository.MarketPriceDataSource
import com.example.domain.repository.MarketQuoteFetchResult
import com.example.domain.repository.MarketQuoteRequest
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Request
import okio.Buffer
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Public, zero-API-key Mutual Fund NAV provider backed by MFapi.in / AMFI India Official Daily Feed.
 *
 * Terms & Safety Guardrails:
 * - Supports ONLY [InvestmentCategory.MUTUAL_FUNDS] identified by 5-7 digit AMFI scheme code.
 * - Transmits ZERO user identity, portfolio quantities, cost basis, or personal data.
 * - Handles HTTP 429 rate limits, offline failures, and malformed payloads safely, falling back
 *   to manual entry without corrupting user records.
 */
@Singleton
class AmfiMfApiMarketPriceDataSource internal constructor(
  private val callFactory: Call.Factory,
  private val baseUrl: String,
  private val dispatchers: DispatcherProvider,
) : MarketPriceDataSource {

  @Inject
  constructor(
    callFactory: Call.Factory,
    dispatchers: DispatcherProvider,
  ) : this(
    callFactory = callFactory,
    baseUrl = DEFAULT_BASE_URL,
    dispatchers = dispatchers,
  )

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  override suspend fun fetchLatestQuote(request: MarketQuoteRequest): MarketQuoteFetchResult =
    withContext(dispatchers.io) {
      if (request.category != InvestmentCategory.MUTUAL_FUNDS) {
        return@withContext MarketQuoteFetchResult.UnsupportedCategoryOrIdentifier(
          message =
            "Automatic public feed currently supports AMFI India Mutual Funds only. Use manual entry for ${request.category.displayName}."
        )
      }

      if (!request.currencyCode.trim().equals(INDIAN_CURRENCY_CODE, ignoreCase = true)) {
        return@withContext MarketQuoteFetchResult.UnsupportedCategoryOrIdentifier(
          message = "AMFI mutual fund NAVs are denominated in INR; refusing to apply an INR quote to a non-INR holding."
        )
      }

      val schemeCode = request.symbolOrTicker?.trim().orEmpty()
      if (!AMFI_SCHEME_CODE_REGEX.matches(schemeCode)) {
        return@withContext MarketQuoteFetchResult.UnsupportedCategoryOrIdentifier(
          message =
            "Enter a valid 5–7 digit AMFI scheme code in Symbol/Ticker for optional mutual fund NAV updates."
        )
      }

      val url = "${baseUrl.trimEnd('/')}/mf/$schemeCode/latest"
      val httpRequest =
        Request.Builder()
          .url(url)
          .get()
          .header("Accept", "application/json")
          .header("User-Agent", "SkyplusMoney-Android/1.0")
          .build()

      try {
        callFactory.newCall(httpRequest).execute().use { response ->
          if (response.code == 429) {
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
            return@withContext MarketQuoteFetchResult.RateLimited(
              retryAfterMillis = (retryAfterSeconds ?: 60L) * 1000L,
              message = "MFapi.in rate limit reached (HTTP 429). Retry later or update NAV manually.",
            )
          }

          if (!response.isSuccessful) {
            return@withContext MarketQuoteFetchResult.NetworkError(
              message = "Market feed returned HTTP ${response.code}. Manual price preserved."
            )
          }

          val body = response.body
            ?: return@withContext MarketQuoteFetchResult.InvalidResponse(
              message = "Market feed returned an empty response."
            )
          if (body.contentLength() > MAX_RESPONSE_BYTES) {
            return@withContext MarketQuoteFetchResult.InvalidResponse(
              message = "Market feed response exceeded the permitted size; existing price was preserved."
            )
          }

          // Stream at most MAX_RESPONSE_BYTES + 1 bytes. This also handles
          // chunked/unknown-length responses without an unbounded allocation.
          val boundedBuffer = Buffer()
          val source = body.source()
          var totalRead = 0L
          while (totalRead <= MAX_RESPONSE_BYTES) {
            val remaining = MAX_RESPONSE_BYTES + 1L - totalRead
            val read = source.read(boundedBuffer, minOf(8_192L, remaining))
            if (read == -1L) break
            totalRead += read
          }
          if (totalRead > MAX_RESPONSE_BYTES) {
            boundedBuffer.clear()
            return@withContext MarketQuoteFetchResult.InvalidResponse(
              message = "Market feed response exceeded the permitted size; existing price was preserved."
            )
          }
          val responseBytes = boundedBuffer.readByteArray()
          val rawBody = try {
            responseBytes.toString(Charsets.UTF_8).trim()
          } finally {
            responseBytes.fill(0)
          }
          if (rawBody.isEmpty()) {
            return@withContext MarketQuoteFetchResult.InvalidResponse(
              message = "Market feed returned an empty response."
            )
          }

          parseMfApiResponse(rawBody)
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: IOException) {
        MarketQuoteFetchResult.NetworkError(
          message = "Market feed unavailable while offline or unreachable. Manual entry remains active."
        )
      } catch (_: Exception) {
        MarketQuoteFetchResult.InvalidResponse(
          message = "Unexpected error processing market feed response."
        )
      }
    }

  internal fun parseMfApiResponse(rawJson: String): MarketQuoteFetchResult {
    val payload =
      try {
        json.decodeFromString(MfApiLatestResponse.serializer(), rawJson)
      } catch (_: Exception) {
        return MarketQuoteFetchResult.InvalidResponse("Unable to parse MFapi.in JSON payload")
      }

    if (!payload.status.equals("SUCCESS", ignoreCase = true)) {
      return MarketQuoteFetchResult.InvalidResponse(
        message = "Market feed status '${payload.status ?: "UNKNOWN"}' for scheme."
      )
    }

    val latestPoint =
      payload.data.firstOrNull()
        ?: return MarketQuoteFetchResult.InvalidResponse("Market feed contained no NAV entries.")

    val navText = latestPoint.nav?.trim().orEmpty()
    val navDecimal =
      try {
        BigDecimal(navText)
      } catch (_: Exception) {
        return MarketQuoteFetchResult.InvalidResponse("Invalid NAV decimal value received.")
      }

    if (navDecimal <= BigDecimal.ZERO) {
      return MarketQuoteFetchResult.InvalidResponse("Market feed returned non-positive NAV.")
    }

    val asOfMillis =
      parseAmfiDateMillis(latestPoint.date)
        ?: return MarketQuoteFetchResult.InvalidResponse(
          "Market feed returned a missing, malformed, or future NAV date; existing price was preserved."
        )

    return MarketQuoteFetchResult.Success(
      unitPriceDecimal = navDecimal.stripTrailingZeros().toPlainString(),
      asOfMillis = asOfMillis,
      source = PriceSourceType.MFAPI_AMFI_EOD,
      providerLabel = PROVIDER_LABEL,
      instrumentName = payload.meta?.schemeName?.trim()?.takeIf { it.isNotEmpty() },
    )
  }

  private fun parseAmfiDateMillis(dateText: String?): Long? {
    val normalizedDate = dateText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val publishedDate =
      try {
        LocalDate.parse(normalizedDate, AMFI_DATE_FORMATTER)
      } catch (_: DateTimeParseException) {
        return null
      }
    // AMFI publishes end-of-day NAVs; anchor the timestamp at 18:00 India Standard Time.
    val asOfMillis =
      publishedDate.atTime(LocalTime.of(18, 0)).atZone(INDIA_ZONE_ID).toInstant().toEpochMilli()
    if (asOfMillis > System.currentTimeMillis() + MAX_FUTURE_CLOCK_SKEW_MILLIS) return null
    return asOfMillis
  }

  @Serializable
  internal data class MfApiLatestResponse(
    val meta: MfApiMeta? = null,
    val data: List<MfApiNavEntry> = emptyList(),
    val status: String? = null,
  )

  @Serializable
  internal data class MfApiMeta(
    @SerialName("scheme_name") val schemeName: String? = null,
    @SerialName("fund_house") val fundHouse: String? = null,
  )

  @Serializable
  internal data class MfApiNavEntry(
    val date: String? = null,
    val nav: String? = null,
  )

  companion object {
    const val DEFAULT_BASE_URL = "https://api.mfapi.in"
    const val PROVIDER_LABEL = "MFapi.in / AMFI India (EOD)"
    private const val MAX_RESPONSE_BYTES = 64 * 1024
    private const val INDIAN_CURRENCY_CODE = "INR"
    private const val MAX_FUTURE_CLOCK_SKEW_MILLIS = 5L * 60L * 1000L
    private val INDIA_ZONE_ID: ZoneId = ZoneId.of("Asia/Kolkata")
    private val AMFI_DATE_FORMATTER: DateTimeFormatter =
      DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.US).withResolverStyle(ResolverStyle.STRICT)
    private val AMFI_SCHEME_CODE_REGEX = Regex("^\\d{5,7}$")
  }
}
