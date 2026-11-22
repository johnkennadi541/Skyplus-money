package com.example

import com.example.domain.model.AccountType
import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardNetwork
import com.example.domain.model.FinancialAccount
import com.example.domain.usecase.CreditCardCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Credit card maths: outstanding, available credit and utilisation, all in exact minor units, with
 * explicit behaviour for every invalid or extreme input.
 */
class CreditCardCalculationEngineTest {

  private lateinit var engine: CreditCardCalculationEngine

  private val uid = "uid_cards"

  @Before
  fun setUp() {
    engine = CreditCardCalculationEngine()
  }

  private fun card(
    accountId: String = "acc_card",
    limitMinor: Long = 100_000L,
    currency: String = "INR",
    userId: String = uid,
  ): CreditCard =
    CreditCard(
      id = "card_1",
      userId = userId,
      accountId = accountId,
      issuerName = "HDFC Bank",
      network = CreditCardNetwork.VISA,
      last4 = "1234",
      creditLimitMinor = limitMinor,
      currencyCode = currency,
    )

  private fun account(
    id: String = "acc_card",
    balanceMinor: Long,
    currency: String = "INR",
    type: AccountType = AccountType.CREDIT_CARD,
    userId: String = uid,
  ): FinancialAccount =
    FinancialAccount(
      id = id,
      userId = userId,
      name = "Card account",
      type = type,
      currencyCode = currency,
      initialBalanceMinor = 0L,
      currentBalanceMinor = balanceMinor,
    )

  private fun expectIllegalArgument(block: () -> Unit): IllegalArgumentException {
    try {
      block()
    } catch (error: IllegalArgumentException) {
      return error
    }
    throw AssertionError("Expected IllegalArgumentException, but the call succeeded")
  }

  // ------------------------------------------------------------- outstanding

  @Test
  fun `outstanding is the absolute value of the account balance`() {
    assertEquals(50_000L, engine.outstandingMinor(account(balanceMinor = -50_000L)))
    assertEquals(50_000L, engine.outstandingMinor(account(balanceMinor = 50_000L)))
    assertEquals(0L, engine.outstandingMinor(account(balanceMinor = 0L)))
  }

  @Test
  fun `Long MIN_VALUE never produces a negative outstanding amount`() {
    // abs(Long.MIN_VALUE) is still Long.MIN_VALUE, which would report negative debt.
    assertEquals(Long.MAX_VALUE, engine.outstandingMinor(account(balanceMinor = Long.MIN_VALUE)))
  }

  // --------------------------------------------------------- available credit

  @Test
  fun `available credit never goes below zero`() {
    assertEquals(50_000L, engine.availableCreditMinor(100_000L, 50_000L))
    assertEquals(0L, engine.availableCreditMinor(100_000L, 100_000L))
    assertEquals(0L, engine.availableCreditMinor(100_000L, 150_000L))
  }

  @Test
  fun `available credit cannot overflow at the extremes`() {
    assertEquals(0L, engine.availableCreditMinor(0L, Long.MAX_VALUE))
    assertEquals(Long.MAX_VALUE, engine.availableCreditMinor(Long.MAX_VALUE, 0L))
  }

  @Test
  fun `available credit rejects negative inputs`() {
    expectIllegalArgument { engine.availableCreditMinor(-1L, 0L) }
    expectIllegalArgument { engine.availableCreditMinor(100L, -1L) }
  }

  // ------------------------------------------------------------- utilisation

  @Test
  fun `utilisation is exact and zero when no limit is set`() {
    assertEquals(0, BigDecimal("50.00").compareTo(engine.utilisationPercent(100_000L, 50_000L)))
    assertEquals(0, BigDecimal("100.00").compareTo(engine.utilisationPercent(100_000L, 100_000L)))
    assertEquals(0, BigDecimal.ZERO.compareTo(engine.utilisationPercent(0L, 50_000L)))
  }

  @Test
  fun `an over-limit card reports utilisation above one hundred and is not capped`() {
    assertEquals(0, BigDecimal("150.00").compareTo(engine.utilisationPercent(100_000L, 150_000L)))
    assertEquals(0, BigDecimal("250.00").compareTo(engine.utilisationPercent(100_000L, 250_000L)))
  }

  @Test
  fun `utilisation rejects negative inputs so it can never be negative`() {
    expectIllegalArgument { engine.utilisationPercent(-100L, 10L) }
    expectIllegalArgument { engine.utilisationPercent(100L, -10L) }
  }

  // ---------------------------------------------------------------- summaries

  @Test
  fun `a summary carries the currency of the account`() {
    val summary = engine.summarise(card(), account(balanceMinor = -25_000L))

    assertEquals(25_000L, summary.outstandingMinor)
    assertEquals("INR", summary.outstanding.currencyCode)
    assertEquals(75_000L, summary.availableCreditMinor)
    assertFalse(summary.isOverLimit)
  }

  @Test
  fun `a card over its limit is flagged`() {
    val summary = engine.summarise(card(limitMinor = 10_000L), account(balanceMinor = -15_000L))

    assertTrue(summary.isOverLimit)
    assertEquals(0L, summary.availableCreditMinor)
  }

  @Test
  fun `a card with no limit is never flagged as over limit`() {
    val summary = engine.summarise(card(limitMinor = 0L), account(balanceMinor = -15_000L))

    assertFalse(summary.isOverLimit)
    assertEquals(0, BigDecimal.ZERO.compareTo(summary.utilisationPercent))
  }

  @Test
  fun `summarise refuses a card and account owned by different users`() {
    val error =
      expectIllegalArgument {
        engine.summarise(card(userId = "uid_a"), account(balanceMinor = -1_000L, userId = "uid_b"))
      }
    assertTrue(error.message.orEmpty().contains("different users"))
  }

  @Test
  fun `summarise refuses a card that points at a different account`() {
    val error =
      expectIllegalArgument {
        engine.summarise(card(accountId = "acc_one"), account(id = "acc_two", balanceMinor = -1_000L))
      }
    assertTrue(error.message.orEmpty().contains("does not belong to this account"))
  }

  @Test
  fun `summarise refuses mismatched currencies`() {
    val error =
      expectIllegalArgument {
        engine.summarise(card(currency = "INR"), account(balanceMinor = -1_000L, currency = "USD"))
      }
    assertTrue(error.message.orEmpty().contains("currency"))
  }

  @Test
  fun `summariseAll keeps only valid same-user pairs`() {
    val cards =
      listOf(
        card(accountId = "acc_a", limitMinor = 100_000L),
        card(accountId = "acc_missing"),
        card(accountId = "acc_other_user"),
        card(accountId = "acc_other_currency", currency = "INR"),
      )
    val accounts =
      listOf(
        account(id = "acc_a", balanceMinor = -20_000L),
        account(id = "acc_other_user", balanceMinor = -5_000L, userId = "someone_else"),
        account(id = "acc_other_currency", balanceMinor = -5_000L, currency = "USD"),
      )

    val summaries = engine.summariseAll(cards, accounts)

    assertEquals(1, summaries.size)
    assertEquals("acc_a", summaries.single().card.accountId)
    assertEquals(20_000L, summaries.single().outstandingMinor)
  }

  @Test
  fun `summariseAll sorts by outstanding, largest first`() {
    val cards =
      listOf(
        card(accountId = "small", limitMinor = 100_000L),
        card(accountId = "large", limitMinor = 100_000L),
      )
    val accounts =
      listOf(
        account(id = "small", balanceMinor = -1_000L),
        account(id = "large", balanceMinor = -90_000L),
      )

    val summaries = engine.summariseAll(cards, accounts)

    assertEquals(listOf("large", "small"), summaries.map { it.card.accountId })
  }

  @Test
  fun `currency is preserved through the summary`() {
    val summary =
      engine.summarise(
        card(limitMinor = 1_000_00L, currency = "USD"),
        account(balanceMinor = -250_00L, currency = "USD"),
      )

    assertEquals("USD", summary.card.currencyCode)
    assertEquals("USD", summary.outstanding.currencyCode)
    assertEquals(250_00L, summary.outstandingMinor)
  }
}
