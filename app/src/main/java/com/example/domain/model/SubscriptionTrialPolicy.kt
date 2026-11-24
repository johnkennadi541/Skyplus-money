package com.example.domain.model

/**
 * Pure, deterministic timing policy for the SKYPLUS MONEY 30-day trial and 15-day reminder window.
 *
 * The activation timestamp must come from the trusted subscription backend. Do not pass a
 * client-created timestamp here as an entitlement source.
 */
object SubscriptionTrialPolicy {
  const val TRIAL_DAYS = 30
  const val REMINDER_DAYS = 15
  const val ANNUAL_PRICE_INR = 499
  const val DAY_MILLIS = 24L * 60L * 60L * 1_000L

  enum class Access {
    TRIAL,
    REMINDER_WINDOW,
    LOCKED,
    SUBSCRIBED,
  }

  /**
   * Returns the access state at [nowMillis]. A verified active subscription always takes priority.
   * Full access remains available for 30 days plus the complete 15-day reminder window.
   */
  fun access(
    firstActivationAtMillis: Long,
    nowMillis: Long,
    hasVerifiedActiveSubscription: Boolean,
  ): Access {
    require(firstActivationAtMillis > 0L) { "A trusted activation timestamp is required." }
    require(nowMillis >= 0L) { "Current time cannot be negative." }

    if (hasVerifiedActiveSubscription) return Access.SUBSCRIBED

    val trialEndsAt = firstActivationAtMillis + TRIAL_DAYS * DAY_MILLIS
    val lockAt = trialEndsAt + REMINDER_DAYS * DAY_MILLIS

    return when {
      nowMillis < trialEndsAt -> Access.TRIAL
      nowMillis < lockAt -> Access.REMINDER_WINDOW
      else -> Access.LOCKED
    }
  }

  /**
   * Returns the reminder sequence day (30..44) when a daily reminder is due, otherwise null.
   * Scheduling/delivery must persist an idempotency marker per UID and returned day.
   */
  fun reminderDay(firstActivationAtMillis: Long, nowMillis: Long): Int? {
    require(firstActivationAtMillis > 0L) { "A trusted activation timestamp is required." }
    require(nowMillis >= 0L) { "Current time cannot be negative." }

    val firstReminderAt = firstActivationAtMillis + TRIAL_DAYS * DAY_MILLIS
    val finalLockAt = firstReminderAt + REMINDER_DAYS * DAY_MILLIS
    if (nowMillis < firstReminderAt || nowMillis >= finalLockAt) return null

    val elapsedReminderDays = (nowMillis - firstReminderAt) / DAY_MILLIS
    return TRIAL_DAYS + elapsedReminderDays.toInt()
  }

  /** The final notice is due once, at or after the end of the 15-day reminder window. */
  fun isFinalNoticeDue(firstActivationAtMillis: Long, nowMillis: Long): Boolean {
    require(firstActivationAtMillis > 0L) { "A trusted activation timestamp is required." }
    require(nowMillis >= 0L) { "Current time cannot be negative." }

    val finalLockAt =
      firstActivationAtMillis + (TRIAL_DAYS + REMINDER_DAYS) * DAY_MILLIS
    return nowMillis >= finalLockAt
  }
}
