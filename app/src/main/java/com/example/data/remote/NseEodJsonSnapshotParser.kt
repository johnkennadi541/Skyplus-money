package com.example.data.remote

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Read-only parser for the community nse-eod-data JSON snapshot.
 *
 * Deliberately not wired into the app market-data source: the repository does not publish
 * a data licence that establishes permission for redistribution or production app display.
 * This parser is limited to offline contract tests and schema/validation work.
 */
internal object NseEodJsonSnapshotParser {
  private val json = Json { ignoreUnknownKeys = true }
  private val indiaZone = ZoneId.of("Asia/Kolkata")
  private const val FUTURE_CLOCK_SKEW_SECONDS = 300L
  private val symbolPattern = Regex("^[A-Z0-9][A-Z0-9&._-]{0,31}$")

  fun parse(
    rawJson: String,
    today: LocalDate = LocalDate.now(indiaZone),
    now: Instant = Instant.now(),
  ): NseEodJsonSnapshot {
    require(rawJson.isNotBlank()) { "EOD snapshot is empty" }
    val root =
      try {
        json.parseToJsonElement(rawJson).jsonObject
      } catch (error: Exception) {
        throw IllegalArgumentException("EOD snapshot is not valid JSON", error)
      }

    val asOfText = root["asOf"]?.jsonPrimitive?.content
      ?: throw IllegalArgumentException("EOD snapshot is missing asOf date")
    val asOf =
      try {
        LocalDate.parse(asOfText)
      } catch (error: java.time.format.DateTimeParseException) {
        throw IllegalArgumentException("EOD snapshot has an invalid asOf date", error)
      }
    require(!asOf.isAfter(today)) { "EOD snapshot date is in the future" }

    val generatedAtText = root["generatedAt"]?.jsonPrimitive?.content
      ?: throw IllegalArgumentException("EOD snapshot is missing generatedAt timestamp")
    val generatedAt =
      try {
        OffsetDateTime.parse(generatedAtText)
      } catch (error: java.time.format.DateTimeParseException) {
        throw IllegalArgumentException("EOD snapshot has an invalid generatedAt timestamp", error)
      }
    require(!generatedAt.toInstant().isAfter(now.plusSeconds(FUTURE_CLOCK_SKEW_SECONDS))) {
      "EOD snapshot generatedAt timestamp is in the future"
    }

    val sources =
      root["sources"]?.jsonArray?.map { it.jsonPrimitive.content.uppercase(Locale.ROOT) }
        ?: throw IllegalArgumentException("EOD snapshot is missing source metadata")
    require(sources.any { it == "NSE" || it == "BSE" }) {
      "EOD snapshot does not identify NSE or BSE as a source"
    }

    val declaredCount =
      root["count"]?.jsonPrimitive?.content?.toIntOrNull()
        ?: throw IllegalArgumentException("EOD snapshot has an invalid count")
    require(declaredCount > 0) { "EOD snapshot count must be positive" }

    val rawPrices =
      root["prices"]?.jsonObject
        ?: throw IllegalArgumentException("EOD snapshot is missing prices")

    val validPrices = linkedMapOf<String, BigDecimal>()
    var rejectedPriceCount = 0
    rawPrices.forEach { (rawSymbol, element) ->
      val symbol = normalizeSymbol(rawSymbol)
      val price =
        try {
          element.jsonPrimitive.content.toBigDecimal()
        } catch (_: NumberFormatException) {
          null
        }
      if (symbol == null || price == null || price <= BigDecimal.ZERO) {
        rejectedPriceCount++
      } else {
        require(!validPrices.containsKey(symbol)) {
          "EOD snapshot contains duplicate normalized symbol: $symbol"
        }
        validPrices[symbol] = price.stripTrailingZeros()
      }
    }

    require(validPrices.isNotEmpty()) { "EOD snapshot contains no valid positive prices" }
    require(declaredCount >= validPrices.size) {
      "EOD snapshot count is smaller than its valid price map"
    }

    return NseEodJsonSnapshot(
      asOf = asOf,
      generatedAt = generatedAt,
      sources = sources.toSet(),
      declaredCount = declaredCount,
      prices = validPrices.toMap(),
      rejectedPriceCount = rejectedPriceCount,
    )
  }

  fun normalizeSymbol(symbol: String?): String? {
    val normalized = symbol?.trim()?.uppercase(Locale.ROOT) ?: return null
    return normalized.takeIf(symbolPattern::matches)
  }
}

internal data class NseEodJsonSnapshot(
  val asOf: LocalDate,
  val generatedAt: OffsetDateTime,
  val sources: Set<String>,
  val declaredCount: Int,
  val prices: Map<String, BigDecimal>,
  val rejectedPriceCount: Int,
) {
  fun quoteFor(symbolOrTicker: String?): BigDecimal? =
    NseEodJsonSnapshotParser.normalizeSymbol(symbolOrTicker)?.let(prices::get)

  fun isStale(today: LocalDate): Boolean =
    ChronoUnit.DAYS.between(asOf, today) > 6L
}
