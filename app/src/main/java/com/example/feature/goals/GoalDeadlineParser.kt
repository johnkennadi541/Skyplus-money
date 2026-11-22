package com.example.feature.goals

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Parses the optional goal deadline typed as `dd/MM/yyyy`.
 *
 * A blank string means "no deadline" and returns null. Anything else must be a complete, real,
 * future date; the value stored is the last millisecond of that day so a goal due "today" is still
 * counted as due until the day ends.
 */
object GoalDeadlineParser {

  const val PATTERN = "dd/MM/yyyy"

  fun parse(text: String, nowMillis: Long = System.currentTimeMillis()): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    val format = SimpleDateFormat(PATTERN, Locale.getDefault()).apply { isLenient = false }
    val position = ParsePosition(0)
    val parsed = format.parse(trimmed, position)
    require(parsed != null && position.index == trimmed.length) {
      "Deadline must be a real date in $PATTERN format"
    }

    val calendar =
      Calendar.getInstance().apply {
        time = parsed
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
      }
    val endOfDay = calendar.timeInMillis
    require(endOfDay > nowMillis) { "Deadline must be in the future" }
    return endOfDay
  }

  /** Formats a stored deadline back into the field's format, or blank when there is none. */
  fun format(deadlineMillis: Long?): String {
    if (deadlineMillis == null) return ""
    val format = SimpleDateFormat(PATTERN, Locale.getDefault())
    return format.format(java.util.Date(deadlineMillis))
  }
}
