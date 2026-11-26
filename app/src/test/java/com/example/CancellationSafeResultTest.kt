package com.example

import com.example.data.repository.runCatchingPreservingCancellation
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repository wraps its work in [runCatchingPreservingCancellation]. A plain `runCatching`
 * turns coroutine cancellation into an ordinary failure, which silently breaks structured
 * concurrency; this pins the corrected behaviour.
 */
class CancellationSafeResultTest {

  @Test
  fun `a successful block returns success`() {
    val result = runCatchingPreservingCancellation { 42 }

    assertTrue(result.isSuccess)
    assertEquals(42, result.getOrNull())
  }

  @Test
  fun `an ordinary exception becomes a failure`() {
    val result =
      runCatchingPreservingCancellation<Int> {
        throw IllegalStateException("database said no")
      }

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertEquals("database said no", result.exceptionOrNull()?.message)
  }

  @Test
  fun `cancellation is rethrown and never swallowed`() {
    val thrown =
      try {
        runCatchingPreservingCancellation<Int> {
          throw CancellationException("the caller went away")
        }
        null
      } catch (cancellation: CancellationException) {
        cancellation
      }

    assertTrue(thrown != null)
    assertEquals("the caller went away", thrown?.message)
  }

  @Test
  fun `a constraint failure is still reported as a failure, not as cancellation`() {
    val result =
      runCatchingPreservingCancellation<Int> {
        throw android.database.sqlite.SQLiteConstraintException("UNIQUE constraint failed")
      }

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is android.database.sqlite.SQLiteConstraintException)
  }
}
