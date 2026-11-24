package com.example

import com.example.domain.model.SubscriptionTrialPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionTrialPolicyTest {
  private val start = 1_800_000_000_000L

  @Test
  fun `first 30 days are fully free and no subscription reminders are due`() {
    assertEquals(
      SubscriptionTrialPolicy.Access.TRIAL,
      SubscriptionTrialPolicy.access(start, start + 29L * SubscriptionTrialPolicy.DAY_MILLIS, false),
    )
    assertNull(SubscriptionTrialPolicy.reminderDay(start, start + 29L * SubscriptionTrialPolicy.DAY_MILLIS))
    assertFalse(SubscriptionTrialPolicy.isFinalNoticeDue(start, start + 29L * SubscriptionTrialPolicy.DAY_MILLIS))
  }

  @Test
  fun `day 30 begins reminder window but retains full access`() {
    val day30 = start + 30L * SubscriptionTrialPolicy.DAY_MILLIS
    assertEquals(SubscriptionTrialPolicy.Access.REMINDER_WINDOW, SubscriptionTrialPolicy.access(start, day30, false))
    assertEquals(30, SubscriptionTrialPolicy.reminderDay(start, day30))
    assertFalse(SubscriptionTrialPolicy.isFinalNoticeDue(start, day30))
  }

  @Test
  fun `15 daily reminder days are numbered 30 through 44`() {
    for (offset in 0 until SubscriptionTrialPolicy.REMINDER_DAYS) {
      val now = start + (SubscriptionTrialPolicy.TRIAL_DAYS + offset) * SubscriptionTrialPolicy.DAY_MILLIS
      assertEquals(SubscriptionTrialPolicy.TRIAL_DAYS + offset, SubscriptionTrialPolicy.reminderDay(start, now))
      assertEquals(SubscriptionTrialPolicy.Access.REMINDER_WINDOW, SubscriptionTrialPolicy.access(start, now, false))
    }
  }

  @Test
  fun `day 45 locks access and makes final notice due`() {
    val day45 = start + 45L * SubscriptionTrialPolicy.DAY_MILLIS
    assertEquals(SubscriptionTrialPolicy.Access.LOCKED, SubscriptionTrialPolicy.access(start, day45, false))
    assertNull(SubscriptionTrialPolicy.reminderDay(start, day45))
    assertTrue(SubscriptionTrialPolicy.isFinalNoticeDue(start, day45))
  }

  @Test
  fun `verified active subscription restores access even after trial expiry`() {
    val day100 = start + 100L * SubscriptionTrialPolicy.DAY_MILLIS
    assertEquals(SubscriptionTrialPolicy.Access.SUBSCRIBED, SubscriptionTrialPolicy.access(start, day100, true))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `activation timestamp must be trusted and positive`() {
    SubscriptionTrialPolicy.access(0L, start, false)
  }
}
