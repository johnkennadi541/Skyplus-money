package com.example.feature.budgets

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach

/**
 * Re-subscribes to a cold source whenever [retrySignal] changes.
 *
 * A failure is converted into a caller-provided safe value for the current attempt only.
 * The outer retry flow remains active so a later explicit retry can collect the source again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Flow<T>.retryOnSignal(
  retrySignal: Flow<*>,
  onSuccess: () -> Unit,
  onFailure: (Throwable) -> T,
): Flow<T> =
  retrySignal.flatMapLatest {
    this
      .onEach { onSuccess() }
      .catch { cause -> emit(onFailure(cause)) }
  }
