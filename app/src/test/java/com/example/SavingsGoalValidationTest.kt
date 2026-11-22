package com.example

import com.example.domain.model.SavingsContribution
import com.example.domain.model.SavingsGoal
import com.example.domain.model.SavingsGoalValidation
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shared validation for goals and contributions, used by create, update and contribute.
 */
class SavingsGoalValidationTest {

  private val uid = "uid_goals"

  private fun goal(
    userId: String = uid,
    name: String = "Emergency fund",
    currency: String = "INR",
    targetMinor: Long = 100_000L,
    deadlineMillis: Long? = null,
  ): SavingsGoal =
    SavingsGoal(
      id = "goal_1",
      userId = userId,
      name = name,
      currencyCode = currency,
      targetMinor = targetMinor,
      deadlineMillis = deadlineMillis,
    )

  private fun contribution(
    amountMinor: Long = 5_000L,
    currency: String = "INR",
    goalId: String = "goal_1",
    userId: String = uid,
  ): SavingsContribution =
    SavingsContribution(
      id = "c_1",
      userId = userId,
      goalId = goalId,
      amountMinor = amountMinor,
      currencyCode = currency,
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
  fun `a valid goal passes`() {
    SavingsGoalValidation.requireValidGoal(goal(), uid)
    SavingsGoalValidation.requireValidGoal(goal(deadlineMillis = 1_700_000_000_000L), uid)
  }

  @Test
  fun `a blank authenticated user is rejected`() {
    assertTrue(messageFor { SavingsGoalValidation.requireValidGoal(goal(), "") }.contains("user ID"))
  }

  @Test
  fun `a goal owned by someone else is rejected`() {
    assertTrue(
      messageFor { SavingsGoalValidation.requireValidGoal(goal(userId = "other"), uid) }
        .contains("ownership")
    )
  }

  @Test
  fun `a blank goal name is rejected`() {
    assertTrue(
      messageFor { SavingsGoalValidation.requireValidGoal(goal(name = "   "), uid) }
        .contains("name cannot be blank")
    )
  }

  @Test
  fun `an over-long goal name is rejected`() {
    val longName = "a".repeat(SavingsGoalValidation.MAX_NAME_LENGTH + 1)
    assertTrue(
      messageFor { SavingsGoalValidation.requireValidGoal(goal(name = longName), uid) }
        .contains("too long")
    )
  }

  @Test
  fun `real currencies are accepted`() {
    SavingsGoalValidation.requireValidGoal(goal(currency = "INR"), uid)
    SavingsGoalValidation.requireValidGoal(goal(currency = "KWD"), uid)
    SavingsGoalValidation.requireValidGoal(goal(currency = "JPY"), uid)
    SavingsGoalValidation.requireValidGoal(goal(currency = "inr"), uid)
  }

  @Test
  fun `an invented three letter code is rejected as unsupported`() {
    assertTrue(
      messageFor { SavingsGoalValidation.requireValidGoal(goal(currency = "ABC"), uid) }
        .contains("not a supported ISO 4217 currency")
    )
  }

  @Test
  fun `XXX is rejected because it means no currency`() {
    assertTrue(
      messageFor { SavingsGoalValidation.requireValidGoal(goal(currency = "XXX"), uid) }
        .contains("not a supported ISO 4217 currency")
    )
  }

  @Test
  fun `a malformed currency is rejected`() {
    assertTrue(
      messageFor { SavingsGoalValidation.requireValidGoal(goal(currency = "IN"), uid) }
        .contains("not a supported ISO 4217 currency")
    )
    assertTrue(
      messageFor { SavingsGoalValidation.requireValidGoal(goal(currency = "I1R"), uid) }
        .contains("not a supported ISO 4217 currency")
    )
  }

  @Test
  fun `a zero or negative target is rejected`() {
    assertTrue(
      messageFor { SavingsGoalValidation.requireValidGoal(goal(targetMinor = 0L), uid) }
        .contains("greater than zero")
    )
    assertTrue(
      messageFor { SavingsGoalValidation.requireValidGoal(goal(targetMinor = -1L), uid) }
        .contains("greater than zero")
    )
  }

  @Test
  fun `a non positive deadline is rejected`() {
    assertTrue(
      messageFor { SavingsGoalValidation.requireValidGoal(goal(deadlineMillis = 0L), uid) }
        .contains("Deadline")
    )
  }

  @Test
  fun `a valid contribution passes`() {
    SavingsGoalValidation.requireValidContribution(contribution(), goal(), uid)
  }

  @Test
  fun `a zero or negative contribution is rejected`() {
    assertTrue(
      messageFor { SavingsGoalValidation.requireValidContribution(contribution(amountMinor = 0L), goal(), uid) }
        .contains("greater than zero")
    )
    assertTrue(
      messageFor { SavingsGoalValidation.requireValidContribution(contribution(amountMinor = -500L), goal(), uid) }
        .contains("greater than zero")
    )
  }

  @Test
  fun `a contribution in another currency is rejected`() {
    assertTrue(
      messageFor {
        SavingsGoalValidation.requireValidContribution(contribution(currency = "USD"), goal(), uid)
      }.contains("currency must match")
    )
  }

  @Test
  fun `a contribution for another goal or another user is rejected`() {
    assertTrue(
      messageFor {
        SavingsGoalValidation.requireValidContribution(contribution(goalId = "other"), goal(), uid)
      }.contains("belong to this goal")
    )
    assertTrue(
      messageFor {
        SavingsGoalValidation.requireValidContribution(contribution(userId = "other"), goal(), uid)
      }.contains("ownership")
    )
  }

  @Test
  fun `the currency of a funded goal cannot change`() {
    val error =
      messageFor {
        SavingsGoalValidation.requireCurrencyUnchangedWhenFunded(
          goal = goal(currency = "USD"),
          existing = goal(currency = "INR"),
          funded = true,
        )
      }
    assertTrue(error.contains("cannot be changed"))
  }

  @Test
  fun `the currency of an unfunded goal may change`() {
    SavingsGoalValidation.requireCurrencyUnchangedWhenFunded(
      goal = goal(currency = "USD"),
      existing = goal(currency = "INR"),
      funded = false,
    )
  }
}
