package com.example.feature.smartdetection.engine

import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * Short-lived, bounded, in-memory cache for exact Android notification redeliveries.
 * It stores only a digest of notification key + post time, never notification content.
 */
class NotificationDeduplicationCache(
  private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
  private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
  private val seenAt = LinkedHashMap<String, Long>(16, 0.75f, true)

  init {
    require(ttlMillis > 0L)
    require(maxEntries > 0)
  }

  @Synchronized
  fun isDuplicate(
    notificationKey: String?,
    postTime: Long,
    nowElapsedRealtimeMillis: Long,
  ): Boolean {
    if (notificationKey.isNullOrBlank()) return false

    val expired =
      seenAt.entries
        .filter { (_, seenAtMillis) ->
          nowElapsedRealtimeMillis < seenAtMillis ||
            nowElapsedRealtimeMillis - seenAtMillis > ttlMillis
        }
        .map { it.key }
    expired.forEach(seenAt::remove)

    val identity = sha256("$notificationKey|$postTime")
    val previous = seenAt[identity]
    if (previous != null && nowElapsedRealtimeMillis >= previous &&
      nowElapsedRealtimeMillis - previous <= ttlMillis
    ) {
      return true
    }

    seenAt[identity] = nowElapsedRealtimeMillis
    while (seenAt.size > maxEntries) {
      val eldest = seenAt.entries.iterator()
      if (!eldest.hasNext()) break
      eldest.next()
      eldest.remove()
    }
    return false
  }

  @Synchronized
  fun clear() {
    seenAt.clear()
  }

  @Synchronized
  internal fun sizeForTest(): Int = seenAt.size

  private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  companion object {
    const val DEFAULT_TTL_MILLIS = 120_000L
    const val DEFAULT_MAX_ENTRIES = 256
  }
}
