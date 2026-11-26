package com.example.feature.dashboard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serialises every dashboard layout change.
 *
 * The previous implementation computed each action from `uiState.value`, which is fed by the
 * DataStore flow. Two quick actions could therefore both read the same stale layout and the second
 * write silently dropped the first change.
 *
 * Here the in-memory layout is the working copy. [update] takes a lock, reads the current working
 * layout, applies the transform, publishes it immediately and only then writes it to disk. Because
 * the whole sequence happens under one [Mutex], writes can never overtake each other and an older
 * layout can never overwrite a newer one.
 *
 * Layouts are held per UID, so one account's working layout is never applied to another account.
 * [persistLayout] is injected, which keeps this class free of Android and DataStore types and
 * makes every rule here unit testable.
 */
class DashboardLayoutEditor(
  private val persistLayout: suspend (uid: String, layout: DashboardLayout) -> Unit,
) {

  private val mutex = Mutex()

  private val layouts = MutableStateFlow<Map<String, DashboardLayout>>(emptyMap())

  /** Emits the working layout for [uid] as soon as one is known. */
  fun observe(uid: String): Flow<DashboardLayout> =
    layouts.map { it[uid] }.filterNotNull()

  /**
   * Adopts the layout read from disk, but only when nothing is held in memory yet. This is what
   * makes the restore-on-open behaviour work without letting a late disk read clobber a change the
   * user has just made.
   */
  fun seed(uid: String, saved: DashboardLayout) {
    layouts.update { current ->
      if (current.containsKey(uid)) current else current + (uid to saved)
    }
  }

  /** Applies [transform] to the working layout for [uid] and persists the result, in order. */
  suspend fun update(uid: String, transform: (DashboardLayout) -> DashboardLayout) {
    mutex.withLock {
      val current = layouts.value[uid] ?: DashboardLayoutPolicy.default()
      val next = transform(current)
      layouts.update { it + (uid to next) }
      persistLayout(uid, next)
    }
  }

  /** The layout currently held for [uid], or null before anything is known. */
  fun currentLayout(uid: String): DashboardLayout? = layouts.value[uid]
}
