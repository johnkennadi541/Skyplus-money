package com.example.feature.budgets

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetRetryFlowTest {

  @Test
  fun `retry signal recollects source after a failed calculation attempt`() = runTest {
    val retrySignal = MutableStateFlow(0)
    var attempts = 0
    val received = mutableListOf<String>()
    var successfulEmissions = 0
    val source =
      flow {
        attempts += 1
        if (attempts <= 2) throw ArithmeticException("overflow")
        emit("fresh-budget-data")
      }

    val collection =
      launch(UnconfinedTestDispatcher(testScheduler)) {
        source
          .retryOnSignal(
            retrySignal = retrySignal,
            onSuccess = { successfulEmissions += 1 },
            onFailure = { "safe-empty-state" },
          )
          .collect { received += it }
      }

    runCurrent()
    assertEquals(1, attempts)
    assertEquals(listOf("safe-empty-state"), received)
    assertEquals(0, successfulEmissions)

    retrySignal.value = 1
    runCurrent()
    assertEquals(2, attempts)
    assertEquals(listOf("safe-empty-state", "safe-empty-state"), received)
    assertEquals(0, successfulEmissions)

    retrySignal.value = 2
    runCurrent()
    assertEquals(3, attempts)
    assertEquals(1, successfulEmissions)
    assertEquals(
      listOf("safe-empty-state", "safe-empty-state", "fresh-budget-data"),
      received,
    )

    collection.cancel()
  }
}
