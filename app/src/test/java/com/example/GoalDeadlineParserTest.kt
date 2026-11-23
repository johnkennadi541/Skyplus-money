package com.example

import com.example.feature.goals.GoalDeadlineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * The optional goal deadline: blank means none, a real future date becomes the end of that day,
 * and anything else is rejected with a readable message.
 */
class GoalDeadlineParserTest {

  private fun at(year: Int, monthZeroBased: Int, day: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.clear()
    calendar.set(year, monthZeroBased, day, 12, 0, 0)
    return calendar.timeInMillis
  }

  private fun messageFor(block: () -> Unit): String {
    try {
      block()
    } catch (error: IllegalArgumentException) {
      return error.message.orEmpty()
    }
    throw AssertionError("Expected IllegalArgumentException, but the call succeeded")
  }

  @Test
  fun `a blank deadline means no deadline`() {
    assertNull(GoalDeadlineParser.parse("", nowMillis = at(2026, 0, 1)))
    assertNull(GoalDeadlineParser.parse("   ", nowMillis = at(2026, 0, 1)))
  }

  @Test
  fun `a future date is stored as the last millisecond of that day`() {
    val parsed = GoalDeadlineParser.parse("31/12/2026", nowMillis = at(2026, 0, 1))

    val calendar = Calendar.getInstance().apply { timeInMillis = parsed!! }
    assertEquals(2026, calendar.get(Calendar.YEAR))
    assertEquals(Calendar.DECEMBER, calendar.get(Calendar.MONTH))
    assertEquals(31, calendar.get(Calendar.DAY_OF_MONTH))
    assertEquals(23, calendar.get(Calendar.HOUR_OF_DAY))
    assertEquals(59, calendar.get(Calendar.MINUTE))
  }

  @Test
  fun `a date in the past is rejected`() {
    val error = messageFor { GoalDeadlineParser.parse("01/01/2020", nowMillis = at(2026, 0, 1)) }
    assertTrue(error.contains("future"))
  }

  @Test
  fun `an impossible date is rejected`() {
    val error = messageFor { GoalDeadlineParser.parse("31/02/2026", nowMillis = at(2026, 0, 1)) }
    assertTrue(error.contains("real date"))
  }

  @Test
  fun `a malformed date is rejected`() {
    assertTrue(
      messageFor { GoalDeadlineParser.parse("2026-12-31", nowMillis = at(2026, 0, 1)) }
        .contains("real date")
    )
    assertTrue(
      messageFor { GoalDeadlineParser.parse("31/12/2026extra", nowMillis = at(2026, 0, 1)) }
        .contains("real date")
    )
    assertTrue(
      messageFor { GoalDeadlineParser.parse("not a date", nowMillis = at(2026, 0, 1)) }
        .contains("real date")
    )
  }

  @Test
  fun `a stored deadline formats back into the field pattern`() {
    val parsed = GoalDeadlineParser.parse("31/12/2026", nowMillis = at(2026, 0, 1))

    assertEquals("31/12/2026", GoalDeadlineParser.format(parsed))
    assertEquals("", GoalDeadlineParser.format(null))
  }
}
