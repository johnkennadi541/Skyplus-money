package com.example.domain.model

import java.math.BigDecimal
import java.util.UUID

/**
 * A savings target in exactly one ISO-4217 currency.
 *
 * The saved amount is deliberately **not** stored here. It is always derived from the
 * contribution records, so it can never drift away from the ledger of what was actually put aside.
 */
data class SavingsGoal(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val name: String,
  val currencyCode: String,
  /** Target in minor units of [currencyCode]. Always positive. */
  val targetMinor: Long,
  val deadlineMillis: Long? = null,
  val note: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val isArchived: Boolean = false,
) {
  val target: Money
    get() = Money.fromMinorUnits(targetMinor, currencyCode)
}

/** One amount put aside for a goal. Its currency must always equal the goal's currency. */
data class SavingsContribution(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val goalId: String,
  /** Always positive. A withdrawal is a negative contribution and is not allowed. */
  val amountMinor: Long,
  val currencyCode: String,
  val contributedAt: Long = System.currentTimeMillis(),
  val note: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
) {
  val amount: Money
    get() = Money.fromMinorUnits(amountMinor, currencyCode)
}

/** A goal joined with the totals derived from its contributions. */
data class SavingsGoalSummary(
  val goal: SavingsGoal,
  val savedMinor: Long,
  val remainingMinor: Long,
  val progressPercent: BigDecimal,
  val contributionCount: Int,
) {
  val saved: Money
    get() = Money.fromMinorUnits(savedMinor, goal.currencyCode)

  val remaining: Money
    get() = Money.fromMinorUnits(remainingMinor, goal.currencyCode)

  val isComplete: Boolean
    get() = savedMinor >= goal.targetMinor

  val isOverTarget: Boolean
    get() = savedMinor > goal.targetMinor
}

/**
 * Raised when a contribution is not valid for its goal: another currency, another owner, a goal
 * that does not exist, or a non-positive amount. Controlled and user-safe.
 */
class SavingsGoalConflictException(
  message: String
) : IllegalStateException(message)
