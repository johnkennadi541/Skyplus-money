package com.example

import com.example.domain.model.SavingsContribution
import com.example.domain.model.SavingsGoal
import com.example.domain.usecase.SavingsGoalCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Savings goal maths: every total derived from the contributions, exactly, in minor units.
 */
class SavingsGoalCalculationEngineTest {

  private lateinit var engine: SavingsGoalCalculationEngine

  private val uid = "uid_goals"

  @Before
  fun setUp() {
    engine = SavingsGoalCalculationEngine()
  }

  private fun goal(
    id: String = "goal_1",
    targetMinor: Long = 100_000L,
    currency: String = "INR",
    userId: String = uid,
    name: String = "Emergency fund",
  ): SavingsGoal =
    SavingsGoal(
      id = id,
      userId = userId,
      name = name,
      currencyCode = currency,
      targetMinor = targetMinor,
    )

  private fun contribution(
    amountMinor: Long,
    goalId: String = "goal_1",
    currency: String = "INR",
    userId: String = uid,
    id: String = "c_$amountMinor",
  ): SavingsContribution =
    SavingsContribution(
      id = id,
      userId = userId,
      goalId = goalId,
      amountMinor = amountMinor,
      currencyCode = currency,
    )

  private fun expectIllegalArgument(block: () -> Unit): IllegalArgumentException {
    try {
      block()
    } catch (error: IllegalArgumentException) {
      return error
    }
    throw AssertionError("Expected IllegalArgumentException, but the call succeeded")
  }

  @Test
  fun `saved is the exact sum of contributions`() {
    val saved =
      engine.savedMinor(
        goal(),
        listOf(contribution(25_000L), contribution(15_000L, id = "c2"), contribution(10_000L, id = "c3")),
      )

    assertEquals(50_000L, saved)
  }

  @Test
  fun `saved is zero without contributions`() {
    assertEquals(0L, engine.savedMinor(goal(), emptyList()))
  }

  @Test
  fun `remaining is the target minus saved`() {
    assertEquals(40_000L, engine.remainingMinor(goal(), 60_000L))
  }

  @Test
  fun `remaining never goes negative when the goal is over-funded`() {
    assertEquals(0L, engine.remainingMinor(goal(), 100_000L))
    assertEquals(0L, engine.remainingMinor(goal(), 250_000L))
  }

  @Test
  fun `progress is exact and can exceed one hundred`() {
    assertEquals(0, BigDecimal("50.00").compareTo(engine.progressPercent(goal(), 50_000L)))
    assertEquals(0, BigDecimal("100.00").compareTo(engine.progressPercent(goal(), 100_000L)))
    assertEquals(0, BigDecimal("150.00").compareTo(engine.progressPercent(goal(), 150_000L)))
  }

  @Test
  fun `progress is zero when the target is not positive`() {
    assertEquals(0, BigDecimal.ZERO.compareTo(engine.progressPercent(goal(targetMinor = 0L), 5_000L)))
  }

  @Test
  fun `a summary reports saved, remaining, progress and completion`() {
    val summary = engine.summarise(goal(), listOf(contribution(60_000L), contribution(20_000L, id = "c2")))

    assertEquals(80_000L, summary.savedMinor)
    assertEquals(20_000L, summary.remainingMinor)
    assertEquals(2, summary.contributionCount)
    assertFalse(summary.isComplete)
    assertFalse(summary.isOverTarget)
    assertEquals("INR", summary.saved.currencyCode)
  }

  @Test
  fun `a goal funded beyond its target is complete and over target`() {
    val summary = engine.summarise(goal(), listOf(contribution(120_000L)))

    assertTrue(summary.isComplete)
    assertTrue(summary.isOverTarget)
    assertEquals(0L, summary.remainingMinor)
  }

  @Test
  fun `a contribution in another currency is rejected, never converted`() {
    val error =
      expectIllegalArgument {
        engine.savedMinor(goal(currency = "INR"), listOf(contribution(1_000L, currency = "USD")))
      }
    assertTrue(error.message.orEmpty().contains("currency"))
  }

  @Test
  fun `a contribution belonging to another user is rejected`() {
    val error =
      expectIllegalArgument {
        engine.savedMinor(goal(), listOf(contribution(1_000L, userId = "someone_else")))
      }
    assertTrue(error.message.orEmpty().contains("another user"))
  }

  @Test
  fun `a contribution belonging to another goal is rejected`() {
    val error =
      expectIllegalArgument {
        engine.savedMinor(goal(), listOf(contribution(1_000L, goalId = "other_goal")))
      }
    assertTrue(error.message.orEmpty().contains("another goal"))
  }

  @Test
  fun `saved fails fast instead of wrapping on overflow`() {
    val failed =
      runCatching {
        engine.savedMinor(
          goal(targetMinor = Long.MAX_VALUE),
          listOf(
            contribution(Long.MAX_VALUE, id = "big1"),
            contribution(Long.MAX_VALUE, id = "big2"),
          ),
        )
      }

    assertTrue(failed.isFailure)
    assertTrue(failed.exceptionOrNull() is ArithmeticException)
  }

  @Test
  fun `summariseAll groups contributions by goal and sorts by progress`() {
    val goals =
      listOf(
        goal(id = "g1", name = "Holiday", targetMinor = 100_000L),
        goal(id = "g2", name = "Laptop", targetMinor = 100_000L),
        goal(id = "g3", name = "Bike", targetMinor = 100_000L),
      )
    val contributions =
      listOf(
        contribution(90_000L, goalId = "g1", id = "a"),
        contribution(10_000L, goalId = "g2", id = "b"),
        contribution(10_000L, goalId = "g2", id = "c"),
      )

    val summaries = engine.summariseAll(goals, contributions)

    assertEquals(3, summaries.size)
    assertEquals(listOf("g1", "g2", "g3"), summaries.map { it.goal.id })
    assertEquals(90_000L, summaries[0].savedMinor)
    assertEquals(20_000L, summaries[1].savedMinor)
    assertEquals(0L, summaries[2].savedMinor)
  }
}
