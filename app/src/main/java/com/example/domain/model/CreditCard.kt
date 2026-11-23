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

enum class CreditCardStatementStatus {
  UNPAID,
  PARTIALLY_PAID,
  PAID,
  OVERDUE,
}

enum class CreditCardRewardEventType {
  EARN,
  REDEEM,
  ADJUSTMENT,
}

/**
 * A credit card is its own entity, but it never owns a ledger.
 *
 * The money still moves through the existing CREDIT_CARD FinancialAccount: purchases are ordinary
 * expense transactions on that account and payments are ordinary paired transfers into it.
 * Card-specific metadata and reminder preferences live here.
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
  val annualFeeMinor: Long = 0L,
  /** Month in which the annual fee is expected (1..12). */
  val annualFeeMonth: Int = 1,
  val rewardProgramName: String? = null,
  val isActive: Boolean = true,
  /** Local tracker state only; this does not freeze the physical card at the issuer. */
  val isFrozen: Boolean = false,
  val reminderEnabled: Boolean = true,
  /** Number of days before statement due date to notify. */
  val reminderDaysBefore: Int = 3,
  val notes: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
) {
  val creditLimit: Money
    get() = Money.fromMinorUnits(creditLimitMinor, currencyCode)

  val annualFee: Money
    get() = Money.fromMinorUnits(annualFeeMinor, currencyCode)
}

/** A statement imported or entered by the user; payments are reconciled from paired ledger transfers. */
data class CreditCardStatement(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val cardId: String,
  val currencyCode: String,
  val statementDate: Long,
  val dueDate: Long,
  val statementBalanceMinor: Long,
  val minimumDueMinor: Long,
  val feesMinor: Long = 0L,
  val interestMinor: Long = 0L,
  val notes: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
) {
  val statementBalance: Money
    get() = Money.fromMinorUnits(statementBalanceMinor, currencyCode)
}

/** Statement with ledger-derived payment status; it never trusts a manually toggled paid flag. */
data class CreditCardStatementSummary(
  val statement: CreditCardStatement,
  val currencyCode: String,
  val paidAmountMinor: Long,
  val remainingDueMinor: Long,
  val status: CreditCardStatementStatus,
) {
  val statementBalance: Money
    get() = Money.fromMinorUnits(statement.statementBalanceMinor, currencyCode)

  val minimumDue: Money
    get() = Money.fromMinorUnits(statement.minimumDueMinor, currencyCode)

  val remainingDue: Money
    get() = Money.fromMinorUnits(remainingDueMinor, currencyCode)
}

/** Immutable reward ledger event. Positive pointsDelta earns points; negative redeems/adjusts them. */
data class CreditCardRewardEvent(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val cardId: String,
  val eventDate: Long,
  val eventType: CreditCardRewardEventType,
  val pointsDelta: Long,
  val cashValueMinor: Long = 0L,
  val currencyCode: String,
  val description: String,
  val createdAt: Long = System.currentTimeMillis(),
)

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

data class CreditCardSpendingAnalytics(
  val currencyCode: String,
  val periodStart: Long,
  val periodEnd: Long,
  val totalSpentMinor: Long,
  val transactionCount: Int,
  val categoryTotalsMinor: Map<String, Long>,
) {
  val totalSpent: Money
    get() = Money.fromMinorUnits(totalSpentMinor, currencyCode)
}
