package com.example.data.repository

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like `runCatching`, but never swallows coroutine cancellation.
 *
 * A plain `runCatching` catches [CancellationException] too, turning a cancelled coroutine into a
 * `Result.failure` and letting the caller carry on as if the work had merely failed. That breaks
 * structured concurrency. Here cancellation is rethrown, ordinary [Exception] values become
 * failures, and fatal JVM [Error] values are allowed to propagate.
 */
internal inline fun <T> runCatchingPreservingCancellation(block: () -> T): Result<T> =
  try {
    Result.success(block())
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (throwable: Exception) {
    Result.failure(throwable)
  }
