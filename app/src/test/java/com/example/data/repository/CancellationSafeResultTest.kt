package com.example.data.repository

import kotlinx.coroutines.CancellationException
import org.junit.Test

class CancellationSafeResultTest {

  @Test(expected = CancellationException::class)
  fun `cancellation is rethrown rather than converted into a failure result`() {
    runCatchingPreservingCancellation<Int> {
      throw CancellationException("test cancellation")
    }
  }
}
