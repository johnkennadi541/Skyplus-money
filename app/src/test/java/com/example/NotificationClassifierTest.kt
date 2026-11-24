package com.example

import com.example.domain.model.TransactionType
import com.example.feature.smartdetection.engine.NotificationClassification
import com.example.feature.smartdetection.engine.NotificationClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationClassifierTest {
  @Test
  fun `classifies completed high confidence debit as candidate`() {
    val result =
      NotificationClassifier.classify(
        content = "INR 1,250.00 debited from A/c XX1234 paid to Swiggy Ref TXN123456",
        packageName = "com.unlisted.wallet",
        timestamp = 1_700_000_000_000L,
      )

    assertTrue(result is NotificationClassification.FinancialCandidate)
    assertEquals(
      TransactionType.EXPENSE,
      (result as NotificationClassification.FinancialCandidate).transaction.type,
    )
  }

  @Test
  fun `routes pending transaction to review not candidate`() {
    val result =
      NotificationClassifier.classify(
        content = "Payment of INR 500.00 to Swiggy is pending Ref TXN123456",
        packageName = "com.unlisted.wallet",
        timestamp = 1_700_000_000_000L,
      )

    assertTrue(result is NotificationClassification.NeedsReview)
  }

  @Test
  fun `rejects mixed transaction and OTP message`() {
    val result =
      NotificationClassifier.classify(
        content = "INR 1,250.00 debited from A/c XX1234. Your OTP is 123456.",
        packageName = "com.bank.app",
        timestamp = 1_700_000_000_000L,
      )

    assertEquals(NotificationClassification.Sensitive, result)
  }

  @Test
  fun `discards non financial notification`() {
    val result =
      NotificationClassifier.classify(
        content = "Your monthly statement is ready to download",
        packageName = "com.bank.app",
        timestamp = 1_700_000_000_000L,
      )

    assertEquals(NotificationClassification.NotFinancial, result)
  }
}
