package com.example.feature.smartdetection.engine

/**
 * Fast, local-only rejection of notifications that may contain authentication secrets.
 *
 * This filter intentionally rejects the entire notification when a sensitive marker is
 * present, even if the same notification also contains a transaction amount.
 */
object SensitiveNotificationFilter {
  private const val MAX_NOTIFICATION_CHARS = 8_192

  private val latinPatterns =
    listOf(
      Regex("""(?<![A-Za-z0-9])(?:O[\s._-]*T[\s._-]*P|one[\s-]*time[\s-]*password|verification[\s-]*code|security[\s-]*code|authentication[\s-]*code|auth[\s-]*code|login[\s-]*code|two[\s-]*factor|2[\s._-]*F[\s._-]*A)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE),
      Regex("""(?<![A-Za-z0-9])(?:C[\s._-]*V[\s._-]*V|C[\s._-]*V[\s._-]*C|M[\s._-]*P[\s._-]*I[\s._-]*N|T[\s._-]*P[\s._-]*I[\s._-]*N|U[\s._-]*P[\s._-]*I[\s._-]*P[\s._-]*I[\s._-]*N|A[\s._-]*T[\s._-]*M[\s._-]*P[\s._-]*I[\s._-]*N|P[\s._-]*I[\s._-]*N)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE),
      Regex("""(?<![A-Za-z0-9])(?:password|pass[\s_-]*code|secret[\s_-]*code|do[\s_-]+not[\s_-]+share|never[\s_-]+share)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE),
    )

  private val tamilPattern =
    Regex(
      """(?:ஓடிபி|ஒருமுறை\s*(?:கடவுச்சொல்|குறியீடு)|சரிபார்ப்பு\s*குறியீடு|பாதுகாப்பு\s*குறியீடு|ரகசிய\s*குறியீடு|உள்நுழைவு\s*குறியீடு|கடவுச்சொல்|பின்\s*(?:எண்|நம்பர்))""",
      RegexOption.IGNORE_CASE,
    )

  private val arabicPattern =
    Regex(
      """(?:رمز\s*(?:التحقق|التأكيد|الأمان|الدخول|التوثيق)|كلمة\s*المرور|الرقم\s*السري)""",
      RegexOption.IGNORE_CASE,
    )

  /**
   * Returns true for sensitive content or oversized input. Oversized notifications fail closed.
   */
  fun containsSensitiveContent(text: String): Boolean {
    if (text.length > MAX_NOTIFICATION_CHARS) return true
    return latinPatterns.any { it.containsMatchIn(text) } ||
      tamilPattern.containsMatchIn(text) ||
      arabicPattern.containsMatchIn(text)
  }
}
