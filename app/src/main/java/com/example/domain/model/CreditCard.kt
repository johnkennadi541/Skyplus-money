package com.example.domain.model

import java.math.BigDecimal
import java.util.UUID

/**
 * Raised when an account already has a credit card. Controlled and user-safe: the database UNIQUE
 * index is the final authority, and this is how a racing second insert surfaces to the caller.
 */
class DuplicateCreditCardException(
  message: String = "This account already has a credit card"
) : IllegalStateException(message)

enum class CreditCardNetwork {
  VISA,
  MASTERCARD,
  RUPAY,
  AMEX,
  DINERS,
  OTHER,
}

/**
 * A credit card is its own entity, but it never owns a ledger.
 *
 * The money still moves through the existing `CREDIT_CARD` [FinancialAccount]: purchases are
 * ordinary expense transactions on that account and a payment is an ordinary transfer into it.
 * This record only carries the card-specific attributes that a plain account cannot hold.
 *
 * [accountId] is unique, so one card per account and one account per card.
 */
data class CreditCard(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val accountId: String,
  val issuerName: String,
  val network: CreditCardNetwork = CreditCardNetwork.OTHER,
  val last4: String = "",
  /** Zero means "not set yet"; it never blocks a card from being recorded. */
  val creditLimitMinor: Long = 0L,
  val currencyCode: String,
  /** Day of month the statement closes, 1..28. */
  val billingCycleDay: Int = 1,
  /** Days after the cycle day that payment is due. */
  val paymentDueDayOffset: Int = 20,
  /** Annual percentage rate as an exact decimal string; never a Double. */
  val aprPercent: String = "0",
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
) {
  val creditLimit: Money
    get() = Money.fromMinorUnits(creditLimitMinor, currencyCode)
}

/** A card joined with its ledger account, ready for display. */
data class CreditCardSummary(
  val card: CreditCard,
  val account: FinancialAccount,
  /** Always positive: what the user owes right now. */
  val outstandingMinor: Long,
  val availableCreditMinor: Long,
  val utilisationPercent: BigDecimal,
) {
  val outstanding: Money
    get() = Money.fromMinorUnits(outstandingMinor, card.currencyCode)

  val availableCredit: Money
    get() = Money.fromMinorUnits(availableCreditMinor, card.currencyCode)

  val isOverLimit: Boolean
    get() = card.creditLimitMinor > 0L && outstandingMinor > card.creditLimitMinor
}
