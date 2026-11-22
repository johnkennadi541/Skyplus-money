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
