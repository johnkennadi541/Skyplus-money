package com.example

import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shared card validation, used by both create and update so the two can never drift apart.
 */
class CreditCardValidationTest {

  private val uid = "uid_cards"

  private fun card(
    userId: String = uid,
    accountId: String = "acc_card",
    issuerName: String = "HDFC Bank",
    last4: String = "1234",
    creditLimitMinor: Long = 100_000L,
    billingCycleDay: Int = 1,
    paymentDueDayOffset: Int = 20,
    aprPercent: String = "42.5",
    currencyCode: String = "INR",
  ): CreditCard =
    CreditCard(
      id = "card_1",
      userId = userId,
      accountId = accountId,
      issuerName = issuerName,
      last4 = last4,
      creditLimitMinor = creditLimitMinor,
      currencyCode = currencyCode,
      billingCycleDay = billingCycleDay,
      paymentDueDayOffset = paymentDueDayOffset,
      aprPercent = aprPercent,
    )

  private fun messageFor(block: () -> Unit): String {
    try {
      block()
    } catch (error: IllegalArgumentException) {
      return error.message.orEmpty()
    }
    throw AssertionError("Expected IllegalArgumentException, but the call succeeded")
  }

  @Test
  fun `a valid card passes`() {
    CreditCardValidation.requireValidCard(card(), uid)
  }

  @Test
  fun `a blank authenticated user is rejected`() {
    assertTrue(messageFor { CreditCardValidation.requireValidCard(card(), "") }.contains("user ID"))
  }

  @Test
  fun `a card owned by someone else is rejected with the caller's message`() {
    val message =
      messageFor {
        CreditCardValidation.requireValidCard(
          card = card(userId = "someone_else"),
          authenticatedUserId = uid,
          ownershipMessage = "Card ownership cannot be modified or transferred across users",
        )
      }
    assertTrue(message.contains("modified or transferred"))
  }

  @Test
  fun `a card with no account is rejected`() {
    assertTrue(
      messageFor { CreditCardValidation.requireValidCard(card(accountId = ""), uid) }
        .contains("linked to an account")
    )
  }

  @Test
  fun `a blank issuer is rejected`() {
    assertTrue(
      messageFor { CreditCardValidation.requireValidCard(card(issuerName = "   "), uid) }
        .contains("Issuer name")
    )
  }

  @Test
  fun `a negative limit is rejected`() {
    assertTrue(
      messageFor { CreditCardValidation.requireValidCard(card(creditLimitMinor = -1L), uid) }
        .contains("cannot be negative")
    )
  }

  @Test
  fun `billing cycle day outside one to twenty eight is rejected`() {
    assertTrue(
      messageFor { CreditCardValidation.requireValidCard(card(billingCycleDay = 0), uid) }
        .contains("Billing cycle day")
    )
    assertTrue(
      messageFor { CreditCardValidation.requireValidCard(card(billingCycleDay = 29), uid) }
        .contains("Billing cycle day")
    )
  }

  @Test
  fun `payment due offset outside zero to forty five is rejected`() {
    assertTrue(
      messageFor { CreditCardValidation.requireValidCard(card(paymentDueDayOffset = -1), uid) }
        .contains("Payment due offset")
    )
    assertTrue(
      messageFor { CreditCardValidation.requireValidCard(card(paymentDueDayOffset = 46), uid) }
        .contains("Payment due offset")
    )
  }

  @Test
  fun `an empty last four is allowed`() {
    CreditCardValidation.requireValidCard(card(last4 = ""), uid)
  }

  @Test
  fun `exactly four digits are allowed`() {
    CreditCardValidation.requireValidCard(card(last4 = "1234"), uid)
    CreditCardValidation.requireValidCard(card(last4 = "0000"), uid)
  }

  @Test
  fun `fewer than four digits are rejected`() {
    listOf("1", "12", "123").forEach { partial ->
      assertTrue(
        messageFor { CreditCardValidation.requireValidCard(card(last4 = partial), uid) }
          .contains("empty or exactly four digits")
      )
    }
  }

  @Test
  fun `more than four digits are rejected`() {
    assertTrue(
      messageFor { CreditCardValidation.requireValidCard(card(last4 = "12345"), uid) }
        .contains("empty or exactly four digits")
    )
  }

  @Test
  fun `four characters that are not all digits are rejected`() {
    listOf("12ab", "12-4", "12 4", "abcd").forEach { mixed ->
      assertTrue(
        messageFor { CreditCardValidation.requireValidCard(card(last4 = mixed), uid) }
          .contains("empty or exactly four digits")
      )
    }
  }

  @Test
  fun `a blank last four is rejected because it is neither empty nor four digits`() {
    assertTrue(
      messageFor { CreditCardValidation.requireValidCard(card(last4 = "    "), uid) }
        .contains("empty or exactly four digits")
    )
  }

  @Test
  fun `an APR that is not a decimal is rejected`() {
    assertTrue(
      messageFor { CreditCardValidation.requireValidCard(card(aprPercent = "abc"), uid) }
        .contains("decimal number")
    )
    assertTrue(
      messageFor { CreditCardValidation.requireValidCard(card(aprPercent = "42%"), uid) }
        .contains("decimal number")
    )
  }

  @Test
  fun `a negative APR is rejected`() {
    assertTrue(
      messageFor { CreditCardValidation.requireValidCard(card(aprPercent = "-1"), uid) }
        .contains("cannot be negative")
    )
  }

  @Test
  fun `an empty APR is rejected`() {
    assertTrue(
      messageFor { CreditCardValidation.requireValidCard(card(aprPercent = "  "), uid) }
        .contains("required")
    )
  }

  @Test
  fun `a zero APR is valid`() {
    CreditCardValidation.requireValidApr("0")
    assertEquals("0", "0".trim())
  }
}
