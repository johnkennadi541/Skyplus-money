package com.example

import com.example.feature.smartdetection.engine.NotificationDeduplicationCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDeduplicationCacheTest {
  @Test
  fun `same notification key and post time is duplicate within TTL`() {
    val cache = NotificationDeduplicationCache(ttlMillis = 1_000L, maxEntries = 8)
    assertFalse(cache.isDuplicate("notification-key", 100L, 500L))
    assertTrue(cache.isDuplicate("notification-key", 100L, 700L))
  }

  @Test
  fun `updated notification post time is not exact redelivery`() {
    val cache = NotificationDeduplicationCache()
    assertFalse(cache.isDuplicate("notification-key", 100L, 500L))
    assertFalse(cache.isDuplicate("notification-key", 101L, 600L))
  }

  @Test
  fun `expired identity can be processed again`() {
    val cache = NotificationDeduplicationCache(ttlMillis = 1_000L, maxEntries = 8)
    assertFalse(cache.isDuplicate("notification-key", 100L, 500L))
    assertFalse(cache.isDuplicate("notification-key", 100L, 1_501L))
  }

  @Test
  fun `cache remains bounded and stores no notification body`() {
    val cache = NotificationDeduplicationCache(ttlMillis = 10_000L, maxEntries = 2)
    cache.isDuplicate("key-1", 1L, 1L)
    cache.isDuplicate("key-2", 2L, 2L)
    cache.isDuplicate("key-3", 3L, 3L)
    assertEquals(2, cache.sizeForTest())
  }

  @Test
  fun `missing notification key does not suppress unrelated alerts`() {
    val cache = NotificationDeduplicationCache()
    assertFalse(cache.isDuplicate(null, 100L, 500L))
    assertFalse(cache.isDuplicate(null, 100L, 600L))
  }
}
