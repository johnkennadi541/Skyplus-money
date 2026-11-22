package com.example

import com.example.feature.dashboard.DashboardLayout
import com.example.feature.dashboard.DashboardLayoutEditor
import com.example.feature.dashboard.DashboardLayoutPolicy
import com.example.feature.dashboard.HomeCardId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rapid dashboard layout updates must never lose a change and must never leak across accounts.
 * Everything here runs on the test scheduler with virtual time, so there is no flakiness.
 */
class DashboardLayoutEditorTest {

  private companion object {
    const val UID_A = "uid-account-a"
    const val UID_B = "uid-account-b"
  }

  private fun defaultLayout(): DashboardLayout = DashboardLayoutPolicy.default()

  @Test
  fun `two rapid toggles both survive`() = runTest {
    val writes = mutableListOf<Pair<String, DashboardLayout>>()
    val editor = DashboardLayoutEditor { uid, layout -> writes.add(uid to layout) }
    editor.seed(UID_A, defaultLayout())

    launch { editor.update(UID_A) { DashboardLayoutPolicy.toggle(it, HomeCardId.SUMMARY) } }
    launch { editor.update(UID_A) { DashboardLayoutPolicy.toggle(it, HomeCardId.NET_WORTH) } }
    advanceUntilIdle()

    val final = editor.currentLayout(UID_A)!!
    assertTrue(final.hidden.contains(HomeCardId.SUMMARY))
    assertTrue(final.hidden.contains(HomeCardId.NET_WORTH))
    assertEquals(2, writes.size)
    assertEquals(final, writes.last().second)
  }

  @Test
  fun `rapid moves keep both positions`() = runTest {
    val writes = mutableListOf<DashboardLayout>()
    val editor = DashboardLayoutEditor { _, layout -> writes.add(layout) }
    editor.seed(UID_A, defaultLayout())

    launch { editor.update(UID_A) { DashboardLayoutPolicy.move(it, HomeCardId.SUMMARY, false) } }
    launch { editor.update(UID_A) { DashboardLayoutPolicy.move(it, HomeCardId.SUMMARY, false) } }
    advanceUntilIdle()

    val final = editor.currentLayout(UID_A)!!
    assertEquals(HomeCardId.CASH_FLOW, final.order[0])
    assertEquals(HomeCardId.SUMMARY, final.order[2])
    assertEquals(defaultLayout().order.toSet(), final.order.toSet())
    assertEquals(final, writes.last())
  }

  @Test
  fun `a reset followed by another action keeps the later action`() = runTest {
    val editor = DashboardLayoutEditor { _, _ -> }
    editor.seed(UID_A, defaultLayout())

    editor.update(UID_A) { DashboardLayoutPolicy.toggle(it, HomeCardId.SUMMARY) }
    editor.update(UID_A) { DashboardLayoutPolicy.reset(it) }
    editor.update(UID_A) { DashboardLayoutPolicy.toggle(it, HomeCardId.NET_WORTH) }

    val final = editor.currentLayout(UID_A)!!
    assertEquals(HomeCardId.entries.toList(), final.order)
    assertEquals(setOf(HomeCardId.NET_WORTH), final.hidden)
  }

  @Test
  fun `writes are serialised so the newest layout is the one left on disk`() = runTest {
    val writes = mutableListOf<DashboardLayout>()
    // A deliberately slow write: without the lock the second update would land first.
    val editor = DashboardLayoutEditor { _, layout ->
      delay(10)
      writes.add(layout)
    }
    editor.seed(UID_A, defaultLayout())

    launch { editor.update(UID_A) { DashboardLayoutPolicy.move(it, HomeCardId.SUMMARY, false) } }
    launch { editor.update(UID_A) { DashboardLayoutPolicy.toggle(it, HomeCardId.NET_WORTH) } }
    advanceUntilIdle()

    val final = editor.currentLayout(UID_A)!!
    assertEquals(2, writes.size)
    assertEquals(final, writes.last())
    // The second write must contain the first change as well.
    assertEquals(HomeCardId.NET_WORTH, writes.last().order.first())
    assertTrue(writes.last().hidden.contains(HomeCardId.NET_WORTH))
  }

  @Test
  fun `two accounts never share a layout`() = runTest {
    val writes = mutableListOf<Pair<String, DashboardLayout>>()
    val editor = DashboardLayoutEditor { uid, layout -> writes.add(uid to layout) }
    editor.seed(UID_A, defaultLayout())
    editor.seed(UID_B, defaultLayout())

    launch { editor.update(UID_A) { DashboardLayoutPolicy.toggle(it, HomeCardId.SUMMARY) } }
    launch { editor.update(UID_B) { DashboardLayoutPolicy.toggle(it, HomeCardId.CASH_FLOW) } }
    advanceUntilIdle()

    assertEquals(setOf(HomeCardId.SUMMARY), editor.currentLayout(UID_A)!!.hidden)
    assertEquals(setOf(HomeCardId.CASH_FLOW), editor.currentLayout(UID_B)!!.hidden)
    assertTrue(writes.all { (uid, layout) ->
      when (uid) {
        UID_A -> layout.hidden == setOf(HomeCardId.SUMMARY)
        UID_B -> layout.hidden == setOf(HomeCardId.CASH_FLOW)
        else -> false
      }
    })
  }

  @Test
  fun `an account that never changed anything is untouched`() = runTest {
    val writes = mutableListOf<Pair<String, DashboardLayout>>()
    val editor = DashboardLayoutEditor { uid, layout -> writes.add(uid to layout) }
    editor.seed(UID_A, defaultLayout())
    editor.seed(UID_B, defaultLayout())

    editor.update(UID_A) { DashboardLayoutPolicy.toggle(it, HomeCardId.SUMMARY) }

    assertEquals(defaultLayout(), editor.currentLayout(UID_B))
    assertEquals(listOf(UID_A), writes.map { it.first })
  }

  @Test
  fun `the last visible card still cannot be hidden`() = runTest {
    val editor = DashboardLayoutEditor { _, _ -> }
    editor.seed(UID_A, defaultLayout())

    HomeCardId.entries.dropLast(1).forEach { card ->
      editor.update(UID_A) { DashboardLayoutPolicy.toggle(it, card) }
    }
    val afterHidingFour = editor.currentLayout(UID_A)!!
    assertEquals(1, afterHidingFour.visible.size)

    editor.update(UID_A) { DashboardLayoutPolicy.toggle(it, it.visible.single()) }

    val final = editor.currentLayout(UID_A)!!
    assertEquals(1, final.visible.size)
    assertFalse(final.hidden.isEmpty())
  }

  @Test
  fun `a late disk read never overwrites a change already made`() = runTest {
    val editor = DashboardLayoutEditor { _, _ -> }
    editor.seed(UID_A, defaultLayout())
    editor.update(UID_A) { DashboardLayoutPolicy.toggle(it, HomeCardId.SUMMARY) }

    // Simulate the DataStore flow emitting the old saved layout after the user acted.
    editor.seed(UID_A, defaultLayout())

    assertTrue(editor.currentLayout(UID_A)!!.hidden.contains(HomeCardId.SUMMARY))
  }

  @Test
  fun `seeded layout is observable for the screen`() = runTest {
    val editor = DashboardLayoutEditor { _, _ -> }
    val saved = DashboardLayout(order = HomeCardId.entries.toList().reversed(), hidden = emptySet())
    editor.seed(UID_A, saved)

    assertEquals(saved, editor.currentLayout(UID_A))
  }
}
