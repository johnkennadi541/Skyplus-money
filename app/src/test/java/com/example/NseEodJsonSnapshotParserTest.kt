package com.example

import com.example.data.remote.NseEodJsonSnapshotParser
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NseEodJsonSnapshotParserTest {
  private val today = LocalDate.parse("2026-11-23")
  private val now = Instant.parse("2026-11-23T16:00:00Z")

  @Test
  fun `parses snapshot and resolves symbols case insensitively without fuzzy matching`() {
    val snapshot =
      NseEodJsonSnapshotParser.parse(
        """
        {
          "asOf": "2026-11-20",
          "sources": ["NSE", "BSE"],
          "generatedAt": "2026-11-20T19:02:11+05:30",
          "count": 3,
          "prices": {"RELIANCE": 1412.3, "TCS": 3020.5, "BAD": 0}
        }
        """.trimIndent(),
        today = today,
        now = now,
      )

    assertEquals("1412.3", snapshot.quoteFor(" reliance ")?.toPlainString())
    assertEquals("3020.5", snapshot.quoteFor("tcs")?.toPlainString())
    assertNull(snapshot.quoteFor("RELIANCE INDUSTRIES"))
    assertEquals(1, snapshot.rejectedPriceCount)
    assertFalse(snapshot.isStale(today))
  }

  @Test
  fun `marks snapshot older than six calendar days stale`() {
    val snapshot =
      NseEodJsonSnapshotParser.parse(
        """
        {
          "asOf": "2026-11-16",
          "sources": ["NSE"],
          "generatedAt": "2026-11-16T19:02:11+05:30",
          "count": 1,
          "prices": {"INFY": 1500.25}
        }
        """.trimIndent(),
        today = today,
        now = now,
      )

    assertTrue(snapshot.isStale(today))
    assertEquals("1500.25", snapshot.quoteFor("INFY")?.toPlainString())
  }

  @Test
  fun `rejects future dates and future generated timestamps`() {
    val futureDate =
      """
      {"asOf":"2026-11-24","sources":["NSE"],"generatedAt":"2026-11-23T19:00:00+05:30","count":1,"prices":{"TCS":10}}
      """.trimIndent()
    val futureGeneratedAt =
      """
      {"asOf":"2026-11-20","sources":["NSE"],"generatedAt":"2026-11-25T19:00:00+05:30","count":1,"prices":{"TCS":10}}
      """.trimIndent()

    assertInvalid(futureDate)
    assertInvalid(futureGeneratedAt)
  }

  @Test
  fun `rejects missing source metadata invalid json and empty valid price map`() {
    assertInvalid("{broken")
    assertInvalid(
      """{"asOf":"2026-11-20","generatedAt":"2026-11-20T19:00:00+05:30","count":1,"prices":{"TCS":100}}"""
    )
    assertInvalid(
      """{"asOf":"2026-11-20","sources":["NSE"],"generatedAt":"2026-11-20T19:00:00+05:30","count":1,"prices":{"TCS":0}}"""
    )
  }

  @Test
  fun `rejects duplicate symbols after normalization`() {
    assertInvalid(
      """
      {
        "asOf":"2026-11-20",
        "sources":["NSE"],
        "generatedAt":"2026-11-20T19:00:00+05:30",
        "count":2,
        "prices":{"tcs":100,"TCS":101}
      }
      """.trimIndent()
    )
  }

  private fun assertInvalid(payload: String) {
    val result =
      runCatching {
        NseEodJsonSnapshotParser.parse(payload, today = today, now = now)
      }
    assertTrue("Expected invalid snapshot to be rejected", result.isFailure)
  }
}
