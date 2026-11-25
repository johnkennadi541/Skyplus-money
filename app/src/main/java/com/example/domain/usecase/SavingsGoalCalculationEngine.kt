package com.example.domain.usecase

import com.example.domain.model.SavingsContribution
import com.example.domain.model.SavingsGoal
import com.example.domain.model.SavingsGoalSummary
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Savings goal maths. Pure, exact, and never touched by a `Double`.
 *
 * Every total is derived from the contribution records, in minor units, with `Math.addExact` so an
 * overflow fails fast instead of silently reporting a smaller amount saved.
 */
@Singleton
class SavingsGoalCalculationEngine @Inject constructor() {

  /**
   * Sum of the contributions that belong to this goal, in the goal's currency.
   *
   * @throws IllegalArgumentException when a contribution belongs to another user, another goal or
   *   another currency. The write path prevents all three, so this can only fire on corrupt data,
   *   and it must fire rather than quietly produce a wrong total.
   */
  fun savedMinor(goal: SavingsGoal, contributions: List<SavingsContribution>): Long {
    var total = 0L
    for (contribution in contributions) {
      require(contribution.goalId == goal.id) { "Contribution belongs to another goal" }
      require(contribution.userId == goal.userId) {
        "Contribution belongs to another user"
      }
      require(contribution.currencyCode.equals(goal.currencyCode, ignoreCase = true)) {
        "Contribution currency must match the goal currency"
      }
      require(contribution.amountMinor > 0L) {
        "Contribution amount must be greater than zero"
      }
      if (!contribution.isVoided) total = Math.addExact(total, contribution.amountMinor)
    }
    return total
  }

  /** What is still needed. Never negative, so an over-funded goal shows zero remaining. */
  fun remainingMinor(goal: SavingsGoal, savedMinor: Long): Long =
    if (savedMinor >= goal.targetMinor) 0L else goal.targetMinor - savedMinor

  /** Progress as an exact percentage; zero when the target is not positive. */
  fun progressPercent(goal: SavingsGoal, savedMinor: Long): BigDecimal {
    if (goal.targetMinor <= 0L) return BigDecimal.ZERO
    return BigDecimal.valueOf(savedMinor)
      .multiply(HUNDRED)
      .divide(BigDecimal.valueOf(goal.targetMinor), 2, RoundingMode.HALF_EVEN)
  }

  fun summarise(
    goal: SavingsGoal,
    contributions: List<SavingsContribution>,
  ): SavingsGoalSummary {
    val saved = savedMinor(goal, contributions)
    return SavingsGoalSummary(
      goal = goal,
      savedMinor = saved,
      remainingMinor = remainingMinor(goal, saved),
      progressPercent = progressPercent(goal, saved),
      contributionCount = contributions.count { !it.isVoided },
      contributions = contributions.sortedByDescending { it.contributedAt },
    )
  }

  /** Summarises every goal, grouping contributions by goal id in one pass. */
  fun summariseAll(
    goals: List<SavingsGoal>,
    contributions: List<SavingsContribution>,
  ): List<SavingsGoalSummary> {
    val byGoal = contributions.groupBy { it.goalId }
    return goals
      .map { goal -> summarise(goal, byGoal[goal.id].orEmpty()) }
      .sortedWith(compareByDescending<SavingsGoalSummary> { it.progressPercent }.thenBy { it.goal.name })
  }

  private companion object {
    val HUNDRED: BigDecimal = BigDecimal.valueOf(100)
  }
}
