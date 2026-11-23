package com.example

import com.example.domain.model.DetectionConfidence
import com.example.domain.model.DetectionEventStatus
import com.example.domain.model.TransactionDetectionEvent
import com.example.domain.model.TransactionType
import com.example.worker.DailyAutoEntryReportFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyAutoEntryReportFormatterTest {

  @Test
  fun `daily report separates currencies and summarizes income expenses duplicates and exceptions`() {
    val events = listOf(
      event(
        id = "expense-inr",
        amount = 1_500L,
        currency = "INR",
        type = TransactionType.EXPENSE,
        status = DetectionEventStatus.AUTO_SAVED,
        merchant = "Cafe",
        accountLast4 = "4321",
      ),
      event(
        id = "income-inr",
        amount = 10_000L,
        currency = "INR",
        type = TransactionType.INCOME,
        status = DetectionEventStatus.AUTO_SAVED,
        merchant = "Salary",
        accountLast4 = "4321",
      ),
      event(
        id = "expense-kwd",
        amount = 1_250L,
        currency = "KWD",
        type = TransactionType.EXPENSE,
        status = DetectionEventStatus.AUTO_SAVED,
        merchant = "Market",
        accountLast4 = "7788",
      ),
      event(
        id = "exception",
        amount = 2_000L,
        currency = "INR",
        type = TransactionType.EXPENSE,
        status = DetectionEventStatus.IN_REVIEW,
      ),
      event(
        id = "duplicate",
        amount = 1_500L,
        currency = "INR",
        type = TransactionType.EXPENSE,
        status = DetectionEventStatus.DUPLICATE,
      ),
    )

    val report = DailyAutoEntryReportFormatter.format(0L, events)

    assertEquals(3, report.autoSavedCount)
    assertEquals(1, report.exceptionCount)
    assertEquals(1, report.duplicateCount)
    assertTrue(report.body.contains("INR  Income: 100.00 (1)  •  Expense: 15.00 (1)"))
    assertTrue(report.body.contains("KWD  Income: 0.000 (0)  •  Expense: 1.250 (1)"))
    assertTrue(report.body.contains("EXPENSE • Cafe • 15.00 INR • A/c ••4321"))
    assertTrue(report.body.contains("INCOME • Salary • 100.00 INR • A/c ••4321"))
    assertTrue(report.body.contains("Needs attention: 1"))
    assertTrue(report.body.contains("Duplicates skipped: 1"))
    assertTrue(report.body.contains("Source: SMS notifications only"))
  }

  @Test
  fun `report never lists non-auto-saved events as posted entries`() {
    val events = listOf(
      event(
        id = "review",
        amount = 5_000L,
        currency = "INR",
        type = TransactionType.INCOME,
        status = DetectionEventStatus.IN_REVIEW,
        merchant = "Unmapped sender",
      ),
    )

    val report = DailyAutoEntryReportFormatter.format(0L, events)

    assertEquals(0, report.autoSavedCount)
    assertTrue(report.body.contains("No automatic entries were posted."))
    assertTrue(report.body.contains("Needs attention: 1"))
    assertTrue(!report.body.contains("INCOME • Unmapped sender"))
  }

  private fun event(
    id: String,
    amount: Long,
    currency: String,
    type: TransactionType,
    status: DetectionEventStatus,
    merchant: String? = null,
    accountLast4: String? = null,
  ) = TransactionDetectionEvent(
    id = id,
    userId = "uid_report_test",
    rawPackageName = "com.android.messaging",
    senderOrTitle = "Bank",
    detectedAmountMinor = amount,
    detectedCurrency = currency,
    detectedType = type,
    detectedMerchant = merchant,
    detectedAccountLast4 = accountLast4,
    confidence = 0.95f,
    confidenceLevel = DetectionConfidence.HIGH,
    dedupFingerprint = id,
    status = status,
    mappedAccountId = if (status == DetectionEventStatus.AUTO_SAVED) "account-$id" else null,
    createdTransactionId = if (status == DetectionEventStatus.AUTO_SAVED) "transaction-$id" else null,
    receivedAt = 1_700_000_000_000L,
  )
}
