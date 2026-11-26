package com.example.domain.model

/**
 * Shared validation for savings goals and their contributions.
 *
 * Pure Kotlin, so every rule is unit testable, and the repository calls it before any write so the
 * create, update and contribute paths can never drift apart.
 */
object SavingsGoalValidation {

  fun requireValidGoal(
    goal: SavingsGoal,
    authenticatedUserId: String,
    ownershipMessage: String = "Goal ownership cannot be assigned across users",
  ) {
    require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
    require(goal.userId == authenticatedUserId) { ownershipMessage }
    require(goal.name.isNotBlank()) { "Goal name cannot be blank" }
    require(goal.name.length <= MAX_NAME_LENGTH) { "Goal name is too long" }
    require(Money.isSupportedCurrency(goal.currencyCode)) {
      "'${goal.currencyCode.trim().uppercase()}' is not a supported ISO 4217 currency"
    }
    require(goal.targetMinor > 0L) { "Target amount must be greater than zero" }
    goal.deadlineMillis?.let { require(it > 0L) { "Deadline must be a positive timestamp" } }
  }

  /**
   * A contribution must be positive, in the goal's currency, for the same owner and goal.
   *
   * @throws IllegalArgumentException on the first failure; there is never an implicit conversion.
   */
  fun requireValidContribution(
    contribution: SavingsContribution,
    goal: SavingsGoal,
    authenticatedUserId: String,
  ) {
    require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
    require(contribution.userId == authenticatedUserId) {
      "Contribution ownership cannot be assigned across users"
    }
    require(contribution.goalId == goal.id) { "Contribution must belong to this goal" }
    require(contribution.amountMinor > 0L) { "Contribution amount must be greater than zero" }
    require(contribution.currencyCode.equals(goal.currencyCode, ignoreCase = true)) {
      "Contribution currency must match the goal currency"
    }
  }

  /** A goal's currency is fixed once money has been put aside against it. */
  fun requireCurrencyUnchangedWhenFunded(goal: SavingsGoal, existing: SavingsGoal, funded: Boolean) {
    if (!funded) return
    require(goal.currencyCode.equals(existing.currencyCode, ignoreCase = true)) {
      "The currency of a goal with contributions cannot be changed"
    }
  }

  const val MAX_NAME_LENGTH = 80
}
