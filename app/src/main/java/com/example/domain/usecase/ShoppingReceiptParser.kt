package com.example.domain.usecase

import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.TimeZone
import com.example.domain.model.Money

data class ShoppingReceiptDraft(
  val fingerprint: String,
  val merchantCandidate: String?,
  val totalDecimal: String?,
  val currencyCodeCandidate: String?,
  val purchaseDateMillis: Long?,
  val needsReview: Boolean,
  val warnings: List<String>,
)

/** Extracts only explicit total labels; ambiguous totals remain a review-only draft. */
object ShoppingReceiptParser {
  private val totalLine = Regex(
    """(?i)^\s*(grand\s+total|total\s+due|amount\s+due|total)\s*[:\-]?\s*(?:([A-Z]{3}|[₹$€£]|د\.ك)\s*)?([0-9][0-9,]*(?:\.[0-9]{1,3})?)(?:\s*([A-Z]{3}))?\s*$"""
  )


  fun parse(ocrText: String): ShoppingReceiptDraft {
    val normalized = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
    val canonical = normalized.joinToString("\n") { it.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") }
    val fingerprint = MessageDigest.getInstance("SHA-256")
      .digest(canonical.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }

    val totals = normalized.mapNotNull { line ->
      val match = totalLine.matchEntire(line) ?: return@mapNotNull null
      val amount = runCatching { BigDecimal(match.groupValues[3].replace(",", "")) }.getOrNull()
        ?: return@mapNotNull null
      if (amount <= BigDecimal.ZERO) return@mapNotNull null
      Triple(match.groupValues[1].lowercase(Locale.ROOT), amount, (match.groupValues[2].takeIf { it.length == 3 } ?: match.groupValues[4]).uppercase(Locale.ROOT).ifBlank { null })
    }
    val preferred = totals.filter { it.first in setOf("grand total", "total due", "amount due") }
    val candidates = if (preferred.isNotEmpty()) preferred else totals
    val distinctAmounts = candidates.map { it.second.stripTrailingZeros() }.distinct()
    val amount = distinctAmounts.singleOrNull()
    val code = candidates.mapNotNull { it.third?.takeIf { codeValue -> Money.isSupportedCurrency(codeValue) } }.distinct().singleOrNull()
    val isoDates = normalized.mapNotNull { line ->
      Regex("""\b(20\d{2}-\d{2}-\d{2})\b""").find(line)?.groupValues?.get(1)
    }.distinct()
    val purchaseDate = isoDates.singleOrNull()?.let { dateText ->
      runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
          isLenient = false
          timeZone = TimeZone.getTimeZone("UTC")
        }.parse(dateText)?.time
      }.getOrNull()
    }
    val merchant = normalized.firstOrNull()?.takeIf { line ->
      line.length in 2..60 && !line.contains(Regex("""(?i)(receipt|invoice|tax|date|time)"""))
    }
    val warnings = buildList {
      if (amount == null) add("A clear, unambiguous receipt total was not found.")
      if (candidates.size > 1 && distinctAmounts.size > 1) add("Multiple possible totals were found; verify the correct total.")
      if (code == null) add("Currency was not confidently identified; select the account currency.")
    }
    return ShoppingReceiptDraft(
      fingerprint = fingerprint,
      merchantCandidate = merchant,
      totalDecimal = amount?.stripTrailingZeros()?.toPlainString(),
      currencyCodeCandidate = code,
      purchaseDateMillis = purchaseDate,
      needsReview = warnings.isNotEmpty(),
      warnings = warnings,
    )
  }
}
