package com.example

import com.example.data.local.DashboardLayoutSnapshot
import com.example.feature.dashboard.DashboardLayout
import com.example.feature.dashboard.DashboardLayoutPolicy
import com.example.feature.dashboard.HomeCardId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dashboard layout rules: encoding, safe decoding, ordering, visibility, reset and the
 * "at least one card stays visible" rule. These are the rules that decide what the user sees
 * after a restart, so they are pinned here.
 */
class DashboardLayoutPolicyTest {

  private val defaultOrder = HomeCardId.entries.toList()

  @Test
  fun `default layout shows every card in the declared order`() {
    val layout = DashboardLayoutPolicy.default()

    assertEquals(defaultOrder, layout.order)
    assertTrue(layout.hidden.isEmpty())
    assertEquals(defaultOrder, layout.visible)
  }

  @Test
  fun `missing saved preferences restore the default layout`() {
    val layout = DashboardLayoutPolicy.decode(null)

    assertEquals(defaultOrder, layout.order)
    assertTrue(layout.hidden.isEmpty())
  }

  @Test
  fun `blank and malformed preferences restore the default layout`() {
    assertEquals(defaultOrder, DashboardLayoutPolicy.decode("", "").order)
    assertEquals(defaultOrder, DashboardLayoutPolicy.decode("  ,  ,", " , ").order)
    assertTrue(DashboardLayoutPolicy.decode("not-a-card", "also-not").hidden.isEmpty())
  }

  @Test
  fun `saved order is restored exactly`() {
    val savedOrder =
      listOf(
        HomeCardId.RECENT_TRANSACTIONS,
        HomeCardId.CASH_FLOW,
        HomeCardId.SUMMARY,
        HomeCardId.NET_WORTH,
        HomeCardId.CATEGORY_BREAKDOWN,
      )

    val layout =
      DashboardLayoutPolicy.decode(
        DashboardLayoutPolicy.encodeOrder(savedOrder),
        DashboardLayoutPolicy.encodeHidden(emptySet()),
      )

    assertEquals(savedOrder, layout.order)
  }

  @Test
  fun `cards added in a later version are appended instead of lost`() {
    val partialOrder = listOf(HomeCardId.NET_WORTH, HomeCardId.SUMMARY)

    val layout = DashboardLayoutPolicy.decode(DashboardLayoutPolicy.encodeOrder(partialOrder), null)

    assertEquals(defaultOrder.size, layout.order.size)
    assertEquals(partialOrder, layout.order.take(2))
    assertEquals(
      defaultOrder.filterNot { partialOrder.contains(it) },
      layout.order.drop(2),
    )
  }

  @Test
  fun `unknown and duplicated card names are ignored`() {
    val layout = DashboardLayoutPolicy.decode("SUMMARY,BOGUS,SUMMARY,NET_WORTH", "BOGUS,SUMMARY")

    assertEquals(HomeCardId.SUMMARY, layout.order.first())
    assertEquals(defaultOrder.size, layout.order.size)
    assertEquals(setOf(HomeCardId.SUMMARY), layout.hidden)
  }

  @Test
  fun `a saved layout that hides every card is repaired`() {
    val allHidden = HomeCardId.entries.joinToString(",") { it.name }

    val layout = DashboardLayoutPolicy.decode(null, allHidden)

    assertTrue(layout.hidden.isEmpty())
    assertEquals(defaultOrder, layout.visible)
  }

  @Test
  fun `encode then decode round trips a custom layout`() {
    val custom =
      DashboardLayout(
        order =
          listOf(
            HomeCardId.CASH_FLOW,
            HomeCardId.SUMMARY,
            HomeCardId.RECENT_TRANSACTIONS,
            HomeCardId.NET_WORTH,
            HomeCardId.CATEGORY_BREAKDOWN,
          ),
        hidden = setOf(HomeCardId.NET_WORTH, HomeCardId.CATEGORY_BREAKDOWN),
      )

    val restored =
      DashboardLayoutPolicy.decode(
        DashboardLayoutPolicy.encodeOrder(custom.order),
        DashboardLayoutPolicy.encodeHidden(custom.hidden),
      )

    assertEquals(custom.order, restored.order)
    assertEquals(custom.hidden, restored.hidden)
  }

  @Test
  fun `toggling hides a card and shows it again`() {
    val hidden = DashboardLayoutPolicy.toggle(DashboardLayoutPolicy.default(), HomeCardId.NET_WORTH)
    assertTrue(hidden.hidden.contains(HomeCardId.NET_WORTH))
    assertFalse(hidden.visible.contains(HomeCardId.NET_WORTH))

    val shown = DashboardLayoutPolicy.toggle(hidden, HomeCardId.NET_WORTH)
    assertFalse(shown.hidden.contains(HomeCardId.NET_WORTH))
    assertTrue(shown.visible.contains(HomeCardId.NET_WORTH))
  }

  @Test
  fun `the last visible card can never be hidden`() {
    var layout = DashboardLayoutPolicy.default()
    HomeCardId.entries.dropLast(1).forEach { card ->
      layout = DashboardLayoutPolicy.toggle(layout, card)
    }

    assertEquals(1, layout.visible.size)

    val attempted = DashboardLayoutPolicy.toggle(layout, layout.visible.single())

    assertEquals(layout, attempted)
    assertEquals(1, attempted.visible.size)
  }

  @Test
  fun `moving a card up and down changes only its position`() {
    val layout = DashboardLayoutPolicy.default()
    val movedDown = DashboardLayoutPolicy.move(layout, HomeCardId.SUMMARY, moveUp = false)

    assertEquals(HomeCardId.NET_WORTH, movedDown.order.first())
    assertEquals(HomeCardId.SUMMARY, movedDown.order[1])
    assertEquals(layout.order.toSet(), movedDown.order.toSet())

    val backUp = DashboardLayoutPolicy.move(movedDown, HomeCardId.SUMMARY, moveUp = true)
    assertEquals(layout.order, backUp.order)
  }

  @Test
  fun `moving past either end is ignored`() {
    val layout = DashboardLayoutPolicy.default()

    assertEquals(layout, DashboardLayoutPolicy.move(layout, HomeCardId.SUMMARY, moveUp = true))
    assertEquals(
      layout,
      DashboardLayoutPolicy.move(layout, HomeCardId.RECENT_TRANSACTIONS, moveUp = false),
    )
  }

  @Test
  fun `reset restores the default order and shows every card`() {
    val messy =
      DashboardLayout(
        order = defaultOrder.reversed(),
        hidden = setOf(HomeCardId.SUMMARY, HomeCardId.NET_WORTH),
      )

    val reset = DashboardLayoutPolicy.reset(messy)

    assertEquals(defaultOrder, reset.order)
    assertTrue(reset.hidden.isEmpty())
  }

  @Test
  fun `a reset is persisted as the default layout`() {
    val reset = DashboardLayoutPolicy.reset(DashboardLayoutPolicy.default())

    val restored =
      DashboardLayoutPolicy.decode(
        DashboardLayoutSnapshot(
          orderRaw = DashboardLayoutPolicy.encodeOrder(reset.order),
          hiddenRaw = DashboardLayoutPolicy.encodeHidden(reset.hidden),
        )
      )

    assertEquals(defaultOrder, restored.order)
    assertTrue(restored.hidden.isEmpty())
  }
}
