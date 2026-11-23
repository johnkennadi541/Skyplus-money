package com.example.feature.smartdetection.engine

import com.example.domain.model.DetectionConfidence
import com.example.domain.model.Money
import com.example.domain.model.ParsedFinancialMessage
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.math.BigDecimal
import java.util.regex.Pattern

object SmartTransactionParser {

  private val SENSITIVE_PATTERN =
    Pattern.compile(
      "\\b(?:O[\\s.]*T[\\s.]*P|one[\\s-]+time[\\s-]+password|secret\\s+code|verification\\s+code|security\\s+code|auth(?:entication)?\\s+code|login\\s+code|2\\s*F\\s*A|two[\\s-]+factor|do\\s+not\\s+share|C[\\s.]*V[\\s.]*[VC]|M[\\s.]*P[\\s.]*I[\\s.]*N|T[\\s.]*P[\\s.]*I[\\s.]*N|UPI\\s+PIN|ATM\\s+PIN|P[\\s.]+I[\\s.]+N|PIN|password|pass\\s*code)\\b",
      Pattern.CASE_INSENSITIVE,
    )

  private val LONG_DIGITS_PATTERN = Regex("[0-9]{6,}")

  // Build the code matcher from the Android ISO 4217 registry so notification
  // parsing follows the same full currency support as the rest of the finance app.
  private val SUPPORTED_CURRENCY_CODES_PATTERN =
    java.util.Currency.getAvailableCurrencies()
      .map { it.currencyCode }
      .filter { Money.isSupportedCurrency(it) }
      .distinct()
      .sortedByDescending { it.length }
      .joinToString("|")

  private val AMOUNT_PATTERN =
    Pattern.compile(
      "(?:(?:₹|Rs\\.?|(?:$SUPPORTED_CURRENCY_CODES_PATTERN)\\b)\\s*([0-9,]+(?:\\.[0-9]{1,4})?))|(?:([0-9,]+(?:\\.[0-9]{1,4})?)\\s*(?:$SUPPORTED_CURRENCY_CODES_PATTERN)\\b)",
      Pattern.CASE_INSENSITIVE,
    )

  private val DEBIT_KEYWORDS =
    listOf(
      "debited",
      "spent",
      "paid",
      "sent to",
      "purchase of",
      "purchase at",
      "withdrawn",
      "atm withdrawal",
      "transferred to",
      "payment to",
    )

  private val DEBIT_WORD_PATTERN = Pattern.compile("\\bdr\\b", Pattern.CASE_INSENSITIVE)

  private val CREDIT_KEYWORDS =
    listOf(
      "credited",
      "received",
      "deposited",
      "salary",
      "added to wallet",
    )

  private val CREDIT_WORD_PATTERN = Pattern.compile("\\bcr\\b", Pattern.CASE_INSENSITIVE)

  private val PENDING_KEYWORDS =
    listOf("pending", "processing", "in progress", "awaiting confirmation")

  private val FAILED_KEYWORDS =
    listOf("failed", "declined", "unsuccessful", "timed out")

  private val REVERSAL_KEYWORDS =
    listOf("reversed", "reversal", "chargeback")

  private val REFUND_KEYWORDS =
    listOf("refund", "cashback")

  private val TRANSFER_KEYWORDS =
    listOf("self transfer", "transferred between")

  private val ACCOUNT_LAST4_PATTERN =
    Pattern.compile(
      "(?:A/c|Account|card|ending(?:\\s+in)?|acct|XX+)[^\\d]*([0-9]{3,4})\\b",
      Pattern.CASE_INSENSITIVE,
    )

  private val REFERENCE_ID_PATTERN =
    Pattern.compile(
      "(?:UPI Ref(?: no)?|Ref(?: no)?|UTR|Txn ID|Transaction ID|Ref ID)[:\\s#]+([0-9A-Za-z]{6,22})",
      Pattern.CASE_INSENSITIVE,
    )

  private val MERCHANT_PATTERNS =
    listOf(
      Pattern.compile("\\b(?:paid to|sent to|at|to|from)\\b\\s+([A-Za-z0-9&.,'\\-\\s]{2,32}?)(?:\\s+on|\\s+via|\\s+using|\\s+for|\\s+upi|\\s+ref|\\s+utr|\\s+vpa|\\.|,|$)", Pattern.CASE_INSENSITIVE),
      Pattern.compile("\\b(?:vpa|merchant)\\b\\s+([A-Za-z0-9@.\\-_]{3,32})", Pattern.CASE_INSENSITIVE),
      Pattern.compile("Info:\\s*([A-Za-z0-9\\-_/]{3,24})", Pattern.CASE_INSENSITIVE),
    )

  /**
   * Parses notification or financial alert text strictly on-device.
   * Returns null immediately if sensitive security tokens (OTP/CVV/PIN) are detected
   * or if no valid monetary amount is found.
   */
  fun parse(
    text: String,
    packageNameHint: String? = null,
    timestamp: Long = System.currentTimeMillis(),
  ): ParsedFinancialMessage? {
    // Zero-tolerance privacy protection: immediately reject sensitive auth/security messages
    if (SENSITIVE_PATTERN.matcher(text).find()) {
      return null
    }

    val amountMatcher = AMOUNT_PATTERN.matcher(text)
    if (!amountMatcher.find()) {
      return null
    }

    val rawAmountStr = amountMatcher.group(1) ?: amountMatcher.group(2) ?: return null
    val cleanedAmount = rawAmountStr.replace(",", "").trim()
    val bigDecimalAmount = runCatching { BigDecimal(cleanedAmount) }.getOrNull() ?: return null

    val matchedSubstring = amountMatcher.group(0)?.uppercase().orEmpty()
    val codeMatcher = Pattern.compile("\\b[A-Z]{3}\\b").matcher(matchedSubstring)
    val currencyCode =
      when {
        codeMatcher.find() -> codeMatcher.group()
        matchedSubstring.contains("₹") || matchedSubstring.contains("RS") -> "INR"
        matchedSubstring.contains("€") -> "EUR"
        matchedSubstring.contains("£") -> "GBP"
        else -> return null
      }
    if (!Money.isSupportedCurrency(currencyCode)) return null

    val fractionDigits = Money.resolveFractionDigits(currencyCode)
    // Do not silently round a notification amount to the currency's minor-unit scale.
    if (bigDecimalAmount.stripTrailingZeros().scale().coerceAtLeast(0) > fractionDigits) return null
    val amountMinor =
      runCatching {
        bigDecimalAmount.movePointRight(fractionDigits).longValueExact()
      }.getOrNull() ?: return null

    if (amountMinor <= 0L) return null

    val lowerText = text.lowercase()
    val confidenceReasons = mutableListOf<String>()
    var confidenceScore = 0.35f // Rule 1: Valid amount + currency parsed
    confidenceReasons.add("Valid amount ($cleanedAmount $currencyCode) parsed")

    // Status detection
    val isFailed = FAILED_KEYWORDS.any { lowerText.contains(it) }
    val isPending = !isFailed && PENDING_KEYWORDS.any { lowerText.contains(it) }
    val status =
      when {
        isFailed -> {
          confidenceScore -= 0.10f
          confidenceReasons.add("Status detected as Failed")
          TransactionStatus.FAILED
        }
        isPending -> {
          confidenceReasons.add("Status detected as Pending")
          TransactionStatus.PENDING
        }
        else -> {
          confidenceScore += 0.15f
          confidenceReasons.add("Status verified as Completed")
          TransactionStatus.COMPLETED
        }
      }

    // Transaction type & direction detection
    val isReversal = REVERSAL_KEYWORDS.any { lowerText.contains(it) }
    val isRefund = REFUND_KEYWORDS.any { lowerText.contains(it) }
    val isTransfer = TRANSFER_KEYWORDS.any { lowerText.contains(it) }
    val isDebit = DEBIT_KEYWORDS.any { lowerText.contains(it) } || DEBIT_WORD_PATTERN.matcher(text).find()
    val isCredit = CREDIT_KEYWORDS.any { lowerText.contains(it) } || CREDIT_WORD_PATTERN.matcher(text).find()

    var directionAmbiguous = false
    val type =
      when {
        isFailed -> TransactionType.FAILED
        isPending -> TransactionType.PENDING
        isReversal -> {
          confidenceScore += 0.25f
          confidenceReasons.add("Direction identified as Reversal")
          TransactionType.REVERSAL
        }
        isRefund -> {
          confidenceScore += 0.25f
          confidenceReasons.add("Direction identified as Refund/Cashback")
          TransactionType.REFUND
        }
        isTransfer -> {
          confidenceScore += 0.20f
          confidenceReasons.add("Direction identified as Account Transfer")
          TransactionType.TRANSFER
        }
        isCredit && !isDebit -> {
          confidenceScore += 0.25f
          confidenceReasons.add("Direction identified as Credit/Income")
          TransactionType.INCOME
        }
        isDebit && !isCredit -> {
          confidenceScore += 0.25f
          confidenceReasons.add("Direction identified as Debit/Expense")
          TransactionType.EXPENSE
        }
        else -> {
          directionAmbiguous = true
          confidenceReasons.add("Direction ambiguous")
          TransactionType.PENDING
        }
      }

    // Extract Account Last 4
    val accountMatcher = ACCOUNT_LAST4_PATTERN.matcher(text)
    val accountLast4Hint =
      if (accountMatcher.find()) {
        confidenceScore += 0.15f
        confidenceReasons.add("Account hint last 4 detected: ${accountMatcher.group(1)}")
        accountMatcher.group(1)
      } else null

    // Extract Reference ID
    val refMatcher = REFERENCE_ID_PATTERN.matcher(text)
    val referenceId =
      if (refMatcher.find()) {
        confidenceScore += 0.10f
        confidenceReasons.add("Reference ID detected: ${refMatcher.group(1)}")
        refMatcher.group(1)
      } else null

    // Extract Merchant / Payee
    var merchantOrPayee: String? = null
    for (merchantPattern in MERCHANT_PATTERNS) {
      val m = merchantPattern.matcher(text)
      if (m.find()) {
        val candidate = sanitizeMerchant(m.group(1))
        if (
          !candidate.isNullOrBlank() &&
            candidate.length > 1 &&
            !candidate.equals("your", ignoreCase = true) &&
            !candidate.equals("account", ignoreCase = true)
        ) {
          merchantOrPayee = candidate
          confidenceScore += 0.10f
          confidenceReasons.add("Merchant/Payee identified: $candidate")
          break
        }
      }
    }

    val finalConfidence = confidenceScore.coerceIn(0.0f, 1.0f)
    val confidenceLevel =
      when {
        finalConfidence >= 0.80f &&
          status == TransactionStatus.COMPLETED &&
          !directionAmbiguous &&
          type != TransactionType.TRANSFER -> DetectionConfidence.HIGH
        finalConfidence >= 0.50f -> DetectionConfidence.MEDIUM
        else -> DetectionConfidence.LOW
      }

    val suggestedCategory = inferCategory(lowerText, type, merchantOrPayee)

    return ParsedFinancialMessage(
      amountMinor = amountMinor,
      currencyCode = currencyCode,
      type = type,
      status = status,
      merchantOrPayee = merchantOrPayee,
      accountLast4Hint = accountLast4Hint,
      referenceId = referenceId,
      confidenceScore = finalConfidence,
      confidenceLevel = confidenceLevel,
      confidenceReasons = confidenceReasons,
      suggestedCategory = suggestedCategory,
      timestamp = timestamp,
    )
  }

  private fun sanitizeMerchant(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val masked = trimmed.replace(LONG_DIGITS_PATTERN, "••••").trim()
    return masked.take(36).trim().ifBlank { null }
  }

  private fun inferCategory(
    lowerText: String,
    type: TransactionType,
    merchant: String?,
  ): String {
    val combined = "$lowerText ${merchant.orEmpty().lowercase()}"
    return when (type) {
      TransactionType.INCOME ->
        when {
          combined.contains("salary") || combined.contains("payroll") -> "Salary"
          combined.contains("interest") -> "Interest"
          combined.contains("cashback") -> "Cashback"
          combined.contains("freelance") -> "Freelance"
          else -> "Other Income"
        }
      TransactionType.REFUND -> if (combined.contains("cashback")) "Cashback" else "Refunds"
      TransactionType.REVERSAL -> "Refunds"
      TransactionType.TRANSFER -> "Transfer"
      else ->
        when {
          combined.contains("starbucks") ||
            combined.contains("swiggy") ||
            combined.contains("zomato") ||
            combined.contains("coffee") ||
            combined.contains("cafe") ||
            combined.contains("restaurant") ||
            combined.contains("dining") -> "Food & Dining"
          combined.contains("uber") ||
            combined.contains("ola") ||
            combined.contains("metro") ||
            combined.contains("fuel") ||
            combined.contains("petrol") -> "Transport"
          combined.contains("amazon") ||
            combined.contains("flipkart") ||
            combined.contains("walmart") ||
            combined.contains("mall") -> "Shopping"
          combined.contains("emi") || combined.contains("loan") -> "EMI Payments"
          combined.contains("electricity") ||
            combined.contains("water") ||
            combined.contains("broadband") ||
            combined.contains("recharge") -> "Utilities"
          combined.contains("hospital") ||
            combined.contains("pharmacy") ||
            combined.contains("medical") -> "Medical & Healthcare"
          combined.contains("netflix") ||
            combined.contains("spotify") ||
            combined.contains("cinema") -> "Entertainment"
          else -> "Other Expenses"
        }
    }
  }
}
