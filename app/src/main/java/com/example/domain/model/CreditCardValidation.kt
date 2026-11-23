package com.example.domain.model

import java.math.BigDecimal

/**
 * Shared validation for creating and updating a credit card.
 *
 * Pure Kotlin with no Android types, so every rule is unit testable. The repository calls this
 * before it touches the database, which means create and update can never drift apart.
 */
object CreditCardValidation {

  /**
   * Validates everything about a card that does not depend on the database.
   *
   * @param ownershipMessage the message to use when the card's owner is not the caller; create and
   *   update word it slightly differently.
   * @throws IllegalArgumentException with a specific, user-safe message on the first failure.
   */
  fun requireValidCard(
    card: CreditCard,
    authenticatedUserId: String,
    ownershipMessage: String = "Card ownership cannot be assigned across users",
  ) {
    require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
    require(card.userId == authenticatedUserId) { ownershipMessage }
    require(card.accountId.isNotBlank()) { "Card must be linked to an account" }
    require(card.issuerName.isNotBlank()) { "Issuer name cannot be blank" }
    require(card.currencyCode.isNotBlank()) { "Card currency is required" }
    require(card.creditLimitMinor >= 0L) { "Credit limit cannot be negative" }
    require(card.annualFeeMinor >= 0L) { "Annual fee cannot be negative" }
    require(card.annualFeeMonth in 1..12) { "Annual fee month must be between 1 and 12" }
    require(card.reminderDaysBefore in 1..30) { "Reminder lead time must be between 1 and 30 days" }
    require(card.rewardProgramName == null || card.rewardProgramName.length <= 80) { "Reward program name is too long" }
    require(card.notes == null || card.notes.length <= 500) { "Card notes are too long" }
    require(card.billingCycleDay in 1..28) { "Billing cycle day must be between 1 and 28" }
    require(card.paymentDueDayOffset in 0..45) { "Payment due offset must be between 0 and 45" }
    require(card.last4.isEmpty() || isFourDigits(card.last4)) {
      "Card last four must be empty or exactly four digits"
    }
    requireValidApr(card.aprPercent)
  }

  /** True only for exactly four numeric characters; a blank string is not four digits. */
  private fun isFourDigits(value: String): Boolean =
    value.length == 4 && value.all { it.isDigit() }

  /** APR must be a plain non-negative decimal string; never a Double, never a percentage sign. */
  fun requireValidApr(aprPercent: String) {
    val trimmed = aprPercent.trim()
    require(trimmed.isNotEmpty()) { "APR is required" }
    val parsed =
      runCatching { BigDecimal(trimmed) }.getOrNull()
        ?: throw IllegalArgumentException("APR must be a decimal number")
    require(parsed >= BigDecimal.ZERO) { "APR cannot be negative" }
  }
}

object CreditCardStatementValidation {
  fun requireValid(statement: CreditCardStatement, authenticatedUserId: String) {
    require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
    require(statement.userId == authenticatedUserId) { "Statement ownership cannot be assigned across users" }
    require(statement.cardId.isNotBlank()) { "A credit card is required" }
    require(Money.isSupportedCurrency(statement.currencyCode)) { "Statement currency is not supported" }
    require(statement.statementDate > 0L) { "Statement date is required" }
    require(statement.dueDate >= statement.statementDate) { "Payment due date cannot be before statement date" }
    require(statement.statementBalanceMinor >= 0L) { "Statement balance cannot be negative" }
    require(statement.minimumDueMinor >= 0L) { "Minimum due cannot be negative" }
    require(statement.minimumDueMinor <= statement.statementBalanceMinor) {
      "Minimum due cannot exceed the statement balance"
    }
    require(statement.feesMinor >= 0L) { "Fees cannot be negative" }
    require(statement.interestMinor >= 0L) { "Interest cannot be negative" }
    require(statement.notes == null || statement.notes.length <= 500) { "Statement notes are too long" }
  }
}

object CreditCardRewardValidation {
  fun requireValid(event: CreditCardRewardEvent, authenticatedUserId: String) {
    require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
    require(event.userId == authenticatedUserId) { "Reward event ownership cannot be assigned across users" }
    require(event.cardId.isNotBlank()) { "A credit card is required" }
    require(event.eventDate > 0L) { "Reward event date is required" }
    require(event.pointsDelta != 0L) { "Reward points change cannot be zero" }
    require(event.cashValueMinor >= 0L) { "Reward cash value cannot be negative" }
    require(Money.isSupportedCurrency(event.currencyCode)) { "Reward currency is not supported" }
    require(event.description.isNotBlank() && event.description.length <= 160) {
      "Enter a reward description up to 160 characters"
    }
    when (event.eventType) {
      CreditCardRewardEventType.EARN -> require(event.pointsDelta > 0L) { "Earned points must be positive" }
      CreditCardRewardEventType.REDEEM -> require(event.pointsDelta < 0L) { "Redeemed points must be negative" }
      CreditCardRewardEventType.ADJUSTMENT -> Unit
    }
  }
}
