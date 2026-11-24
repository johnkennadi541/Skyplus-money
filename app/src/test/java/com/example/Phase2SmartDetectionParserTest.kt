package com.example

import com.example.domain.model.DetectionConfidence
import com.example.domain.model.ParsedFinancialMessage
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.feature.smartdetection.engine.DeduplicationEngine
import com.example.feature.smartdetection.engine.SmartTransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2SmartDetectionParserTest {

  @Test
  fun `parses UPI debit INR transaction with high confidence`() {
    val msg = "Rs. 1,450.50 debited from A/c XX4321 on 19-Nov paid to Swiggy via UPI Ref 432187654321"
    val parsed = SmartTransactionParser.parse(msg, "com.google.android.apps.nbu.paisa.user", 1_700_000_000_000L)

    assertNotNull(parsed)
    assertEquals(145050L, parsed!!.amountMinor)
    assertEquals("INR", parsed.currencyCode)
    assertEquals(TransactionType.EXPENSE, parsed.type)
    assertEquals(TransactionStatus.COMPLETED, parsed.status)
    assertEquals("4321", parsed.accountLast4Hint)
    assertEquals("432187654321", parsed.referenceId)
    assertEquals("Swiggy", parsed.merchantOrPayee)
    assertEquals(DetectionConfidence.HIGH, parsed.confidenceLevel)
    assertEquals("Food & Dining", parsed.suggestedCategory)
  }

  @Test
  fun `confidence reasons contain no amount merchant account or reference values`() {
    val parsed =
      SmartTransactionParser.parse(
        "Rs. 1,450.50 debited from A/c XX4321 paid to Swiggy Ref 432187654321",
        "com.google.android.apps.nbu.paisa.user",
        1_700_000_000_000L,
      )!!

    val reasons = parsed.confidenceReasons.joinToString(" ")
    assertFalse(reasons.contains("1,450.50"))
    assertFalse(reasons.contains("1450.50"))
    assertFalse(reasons.contains("4321"))
    assertFalse(reasons.contains("432187654321"))
    assertFalse(reasons.contains("Swiggy", ignoreCase = true))
    assertEquals(
      listOf(
        "Valid amount and currency parsed",
        "Status verified as Completed",
        "Direction identified as Debit/Expense",
        "Account hint detected",
        "Reference ID detected",
        "Merchant/Payee identified",
      ),
      parsed.confidenceReasons,
    )
  }

  @Test
  fun `maps fixed merchant categories for grocery and utility providers`() {
    val grocery = SmartTransactionParser.parse("INR 1,250.00 debited from A/c XX4321 paid to D-Mart Ref GROC123456")!!
    assertEquals("Groceries", grocery.suggestedCategory)
    val utility = SmartTransactionParser.parse("INR 850.00 debited from A/c XX4321 paid to TANGEDCO Ref BILL123456")!!
    assertEquals("Bills & Utilities", utility.suggestedCategory)
  }

  @Test
  fun `parses credit card purchase without misclassifying credit substring as income`() {
    val msg = "Spent USD 89.95 on your Credit Card ending 7788 at Amazon Marketplace Ref TXN99887766"
    val parsed = SmartTransactionParser.parse(msg, "com.chase.sig.android", 1_700_000_000_000L)

    assertNotNull(parsed)
    assertEquals(8995L, parsed!!.amountMinor)
    assertEquals("USD", parsed.currencyCode)
    assertEquals(TransactionType.EXPENSE, parsed.type)
    assertEquals(TransactionStatus.COMPLETED, parsed.status)
    assertEquals("7788", parsed.accountLast4Hint)
    assertEquals(DetectionConfidence.HIGH, parsed.confidenceLevel)
  }

  @Test
  fun `parses multi currency KWD 3-decimal EUR AED SAR GBP accurately`() {
    val kwdMsg = "KWD 12.345 debited from A/c XX9900 at Lulu Hypermarket Ref KWD123456"
    val kwd = SmartTransactionParser.parse(kwdMsg, "com.nbk.mobile")!!
    assertEquals("KWD", kwd.currencyCode)
    assertEquals(12345L, kwd.amountMinor)

    val aed = SmartTransactionParser.parse("AED 250.00 paid to Careem from A/c XX1122 Ref 88776655")!!
    assertEquals("AED", aed.currencyCode)
    assertEquals(25000L, aed.amountMinor)

    val sar = SmartTransactionParser.parse("SAR 99.50 spent at Jarir from card XX5544 Ref 77665544")!!
    assertEquals("SAR", sar.currencyCode)
    assertEquals(9950L, sar.amountMinor)

    val eur = SmartTransactionParser.parse("€ 42.10 paid to Bolt on card 3322 Ref 66554433")!!
    assertEquals("EUR", eur.currencyCode)
    assertEquals(4210L, eur.amountMinor)

    val gbp = SmartTransactionParser.parse("£ 19.99 spent at Tesco card ending 2211 Ref 55443322")!!
    assertEquals("GBP", gbp.currencyCode)
    assertEquals(1999L, gbp.amountMinor)
  }

  @Test
  fun `parses salary credit ATM withdrawal wallet refund reversal pending and failed events`() {
    val salary = SmartTransactionParser.parse("INR 85,000.00 credited to A/c XX1001 as Salary from Acme Corp Ref SAL123456")!!
    assertEquals(TransactionType.INCOME, salary.type)
    assertEquals("Salary", salary.suggestedCategory)
    assertEquals(8500000L, salary.amountMinor)

    val atm = SmartTransactionParser.parse("Rs 5000 withdrawn via ATM withdrawal on A/c XX1001 Ref ATM987654")!!
    assertEquals(TransactionType.EXPENSE, atm.type)
    assertEquals(500000L, atm.amountMinor)

    val walletCredit = SmartTransactionParser.parse("INR 500 added to wallet A/c XX2002 Ref WAL123456")!!
    assertEquals(TransactionType.INCOME, walletCredit.type)

    val refund = SmartTransactionParser.parse("Refund of Rs 399.00 credited to A/c XX1001 from Zomato Ref REF123456")!!
    assertEquals(TransactionType.REFUND, refund.type)
    assertEquals(TransactionStatus.COMPLETED, refund.status)

    val reversal = SmartTransactionParser.parse("Transaction of INR 1,200.00 reversed to A/c XX1001 UTR 9988776655")!!
    assertEquals(TransactionType.REVERSAL, reversal.type)

    val pending = SmartTransactionParser.parse("Payment of Rs. 750.00 to Uber is pending UPI Ref 1122334455")!!
    assertEquals(TransactionType.PENDING, pending.type)
    assertEquals(TransactionStatus.PENDING, pending.status)
    assertNotEquals(DetectionConfidence.HIGH, pending.confidenceLevel)

    val failed = SmartTransactionParser.parse("Payment of Rs. 999.00 failed at Netflix Ref FLD123456")!!
    assertEquals(TransactionType.FAILED, failed.type)
    assertEquals(TransactionStatus.FAILED, failed.status)
    assertNotEquals(DetectionConfidence.HIGH, failed.confidenceLevel)
  }

  @Test
  fun `parses Indian rupee symbol and rejects ambiguous dollar currency symbol`() {
    val rupee = SmartTransactionParser.parse("₹ 1,250.00 debited from A/c XX4321 paid to Grocery Ref INR123456")!!
    assertEquals("INR", rupee.currencyCode)
    assertEquals(125000L, rupee.amountMinor)
    assertNull(SmartTransactionParser.parse("$ 25.00 spent at Store on card ending 7788 Ref USD123456"))
  }

  @Test
  fun `parses additional ISO currencies and rejects excess fractional precision`() {
    val cad = SmartTransactionParser.parse("CAD 15.25 debited from A/c XX4321 paid to Store Ref CAD123456")!!
    assertEquals("CAD", cad.currencyCode)
    assertEquals(1_525L, cad.amountMinor)

    val jpy = SmartTransactionParser.parse("JPY 1,500 debited from A/c XX4321 paid to Store Ref JPY123456")!!
    assertEquals("JPY", jpy.currencyCode)
    assertEquals(1_500L, jpy.amountMinor)

    assertNull(SmartTransactionParser.parse("JPY 100.50 debited from A/c XX4321 paid to Store Ref JPY654321"))
  }

  @Test
  fun `strictly rejects OTP PIN MPIN TPIN CVV password and security notifications`() {
    val sensitiveAlerts =
      listOf(
        "Your OTP for INR 5,000.00 transaction is 482910. Do not share with anyone.",
        "One Time Password 991234 for payment of USD 120.00 at Apple Store.",
        "Use verification code 123456 to approve Rs. 2,500 debited request.",
        "Enter your UPI PIN to pay Rs. 450.00 to Merchant.",
        "Never share your CVV, MPIN, TPIN, or password for INR 10,000 transfer.",
        "Your security code for card XX1234 Rs 300 transaction is 778899.",
      )

    for (alert in sensitiveAlerts) {
      assertNull("Sensitive alert must be rejected: $alert", SmartTransactionParser.parse(alert))
    }
  }

  @Test
  fun `rejects non-financial promo alerts without monetary amounts`() {
    assertNull(SmartTransactionParser.parse("Earn 10% cashback rewards on your next order with Skyplus!"))
    assertNull(SmartTransactionParser.parse("Your monthly statement is ready to download."))
  }

  @Test
  fun `ambiguous direction routes safely to PENDING rather than HIGH auto-save`() {
    val parsed = SmartTransactionParser.parse("Transaction of INR 500.00 on A/c XX1234 completed Ref 1122334455")!!
    assertEquals(TransactionType.PENDING, parsed.type)
    assertNotEquals(DetectionConfidence.HIGH, parsed.confidenceLevel)
  }

  @Test
  fun `amount-only or debit-request notifications are not treated as completed transactions`() {
    val unconfirmed = SmartTransactionParser.parse(
      "Debit request for INR 250.00 from A/c XX4321 at Store Ref REQ123456",
    )!!

    assertEquals(TransactionStatus.PENDING, unconfirmed.status)
    assertEquals(TransactionType.PENDING, unconfirmed.type)
    assertNotEquals(DetectionConfidence.HIGH, unconfirmed.confidenceLevel)
  }

  @Test
  fun `deduplication fingerprint is deterministic inside 10 minute bucket and distinct across users`() {
    val parsed =
      ParsedFinancialMessage(
        amountMinor = 15000L,
        currencyCode = "INR",
        type = TransactionType.EXPENSE,
        status = TransactionStatus.COMPLETED,
        merchantOrPayee = "Swiggy",
        accountLast4Hint = "4321",
        referenceId = "4123456789",
        confidenceScore = 0.9f,
        confidenceLevel = DetectionConfidence.HIGH,
        confidenceReasons = listOf("test"),
        timestamp = 1_700_000_000_000L,
      )

    val fp1 = DeduplicationEngine.generateFingerprint("uid_1", parsed)
    val fp2 = DeduplicationEngine.generateFingerprint("uid_1", parsed.copy(timestamp = 1_700_000_050_000L))
    val fpOtherUser = DeduplicationEngine.generateFingerprint("uid_2", parsed)

    assertEquals(fp1, fp2)
    assertNotEquals(fp1, fpOtherUser)
    assertTrue(fp1.length == 64) // SHA-256 hex digest
  }
}
