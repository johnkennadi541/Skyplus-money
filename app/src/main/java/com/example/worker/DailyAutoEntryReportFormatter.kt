package com.example.worker

import com.example.domain.model.DetectionEventStatus
import com.example.domain.model.TransactionDetectionEvent
import com.example.domain.model.TransactionType
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class DailyAutoEntryReportContent(
  val dateLabel: String,
  val postedCount: Int,
  val automaticCount: Int,
  val reviewedCount: Int,
  val exceptionCount: Int,
  val duplicateCount: Int,
  val body: String,
)

internal object DailyAutoEntryReportFormatter {

  fun format(reportDayStart: Long, events: List<TransactionDetectionEvent>): DailyAutoEntryReportContent {
    val posted = events.filter {
      it.status in setOf(DetectionEventStatus.AUTO_SAVED, DetectionEventStatus.APPROVED) &&
        it.createdTransactionId != null
    }
    val automatic = posted.filter { it.status == DetectionEventStatus.AUTO_SAVED }
    val reviewed = posted.filter { it.status == DetectionEventStatus.APPROVED }
    val exceptions = events.count { it.status == DetectionEventStatus.IN_REVIEW }
    val duplicates = events.count { it.status == DetectionEventStatus.DUPLICATE }

    val totals = posted.groupBy { it.detectedCurrency }.toSortedMap().map { (currency, currencyEvents) ->
      val incomeEvents = currencyEvents.filter {
        it.detectedType in setOf(TransactionType.INCOME, TransactionType.REFUND, TransactionType.REVERSAL)
      }
      val expenseEvents = currencyEvents.filter { it.detectedType == TransactionType.EXPENSE }
      val income = sumMinor(incomeEvents)
      val expense = sumMinor(expenseEvents)
      "$currency  Income: ${formatMinor(income, currency)} (${incomeEvents.size})  •  Expense: ${formatMinor(expense, currency)} (${expenseEvents.size})"
    }

    val entryLines = posted.sortedBy { it.receivedAt }.take(MAX_ENTRY_LINES).map { event ->
      val direction = when (event.detectedType) {
        TransactionType.INCOME, TransactionType.REFUND, TransactionType.REVERSAL -> "INCOME"
        TransactionType.EXPENSE -> "EXPENSE"
        else -> event.detectedType.name
      }
      val origin = if (event.status == DetectionEventStatus.AUTO_SAVED) "AUTO" else "REVIEWED"
      val title = event.detectedMerchant ?: event.senderOrTitle
      val accountHint = event.detectedAccountLast4?.let { " • A/c ••$it" }.orEmpty()
      "$origin $direction • $title • ${formatMinor(event.detectedAmountMinor, event.detectedCurrency)} ${event.detectedCurrency}$accountHint"
    }

    val body = buildString {
      append("Entries posted: ${posted.size} (automatic: ${automatic.size}, reviewed: ${reviewed.size})")
      if (totals.isNotEmpty()) {
        append("\n")
        append(totals.joinToString("\n"))
      } else {
        append("\nNo entries were posted.")
      }
      if (entryLines.isNotEmpty()) {
        append("\n\nEntries:")
        append("\n")
        append(entryLines.joinToString("\n"))
        if (posted.size > entryLines.size) append("\n+ ${posted.size - entryLines.size} more entries")
      }
      append("\n\nNeeds attention: $exceptions")
      append("\nDuplicates skipped: $duplicates")
      append("\nSource: SMS notifications only")
    }

    return DailyAutoEntryReportContent(
      dateLabel = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(reportDayStart)),
      postedCount = posted.size,
      automaticCount = automatic.size,
      reviewedCount = reviewed.size,
      exceptionCount = exceptions,
      duplicateCount = duplicates,
      body = body,
    )
  }

  private fun sumMinor(events: List<TransactionDetectionEvent>): Long =
    events.fold(0L) { total, event -> Math.addExact(total, event.detectedAmountMinor) }

  private fun formatMinor(amountMinor: Long, currencyCode: String): String {
    val digits = runCatching { java.util.Currency.getInstance(currencyCode).defaultFractionDigits }
      .getOrDefault(2)
      .coerceAtLeast(0)
    return BigDecimal.valueOf(amountMinor, digits).toPlainString()
  }

  private const val MAX_ENTRY_LINES = 8
}
