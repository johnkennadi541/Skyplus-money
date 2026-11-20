package com.example.feature.smartdetection.engine

import com.example.domain.model.ParsedFinancialMessage
import java.security.MessageDigest

object DeduplicationEngine {

  /**
   * Generates a stable deduplication fingerprint for a transaction detection event.
   * Uses 10-minute time buckets to detect repeated notifications or concurrent SMS/Notification events.
   */
  fun generateFingerprint(
    userId: String,
    parsed: ParsedFinancialMessage,
    timeBucketMinutes: Long = 10L,
  ): String {
    val bucket = parsed.timestamp / (timeBucketMinutes * 60 * 1000L)
    val normMerchant = (parsed.merchantOrPayee ?: "UNKNOWN").trim().lowercase().take(12)
    val last4 = parsed.accountLast4Hint ?: "NONE"
    val ref = parsed.referenceId ?: "NOREF"

    val raw = "$userId|${parsed.currencyCode}|${parsed.amountMinor}|${parsed.type.name}|$last4|$normMerchant|$bucket|$ref"
    return sha256(raw)
  }

  private fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }
}
