package com.example.feature.dashboard

import com.example.data.local.DashboardLayoutSnapshot

/**
 * A validated dashboard layout: every card exactly once in [order], and [hidden] only ever
 * contains cards that exist in [order].
 */
data class DashboardLayout(
  val order: List<HomeCardId>,
  val hidden: Set<HomeCardId>,
) {
  val visible: List<HomeCardId>
    get() = order.filterNot { hidden.contains(it) }
}

/**
 * All dashboard layout rules in one pure object: encoding, decoding, normalisation and the four
 * user actions. No Android type is touched, so every rule here is unit tested on the JVM.
 */
object DashboardLayoutPolicy {

  fun default(): DashboardLayout =
    DashboardLayout(order = HomeCardId.entries.toList(), hidden = emptySet())

  fun encodeOrder(order: List<HomeCardId>): String = order.joinToString(separator = ",") { it.name }

  fun encodeHidden(hidden: Set<HomeCardId>): String =
    hidden.sortedBy { it.ordinal }.joinToString(separator = ",") { it.name }

  /**
   * Reads whatever is on disk and returns a layout that is always safe to render.
   *
   * Missing, blank, unknown or duplicated entries are dropped, cards that were never saved are
   * appended in the default order, and a saved layout that would hide every card is repaired by
   * showing everything. A corrupt preference can therefore never blank the dashboard.
   */
  fun decode(snapshot: DashboardLayoutSnapshot?): DashboardLayout = decode(snapshot?.orderRaw, snapshot?.hiddenRaw)

  fun decode(orderRaw: String?, hiddenRaw: String?): DashboardLayout {
    val known = HomeCardId.entries.toSet()

    val savedOrder =
      orderRaw
        ?.split(',')
        ?.mapNotNull { name -> name.trim().takeIf { it.isNotEmpty() } }
        ?.mapNotNull { name -> known.firstOrNull { it.name == name } }
        ?.distinct()
        ?: emptyList()

    val order = savedOrder + HomeCardId.entries.filterNot { savedOrder.contains(it) }

    val hidden =
      hiddenRaw
        ?.split(',')
        ?.mapNotNull { name -> name.trim().takeIf { it.isNotEmpty() } }
        ?.mapNotNull { name -> known.firstOrNull { it.name == name } }
        ?.toSet()
        ?: emptySet()

    return DashboardLayout(order = order, hidden = normalizeHidden(order, hidden))
  }

  /** Shows the card when it is hidden, and hides it only while at least one card stays visible. */
  fun toggle(layout: DashboardLayout, card: HomeCardId): DashboardLayout {
    if (!layout.order.contains(card)) return layout
    val currentlyHidden = layout.hidden.contains(card)
    if (currentlyHidden) {
      return layout.copy(hidden = layout.hidden - card)
    }
    val visibleAfterHiding = layout.visible.size - 1
    if (visibleAfterHiding < 1) return layout
    return layout.copy(hidden = layout.hidden + card)
  }

  /** Moves a card one slot earlier or later. Out-of-range moves are ignored. */
  fun move(layout: DashboardLayout, card: HomeCardId, moveUp: Boolean): DashboardLayout {
    val index = layout.order.indexOf(card)
    if (index < 0) return layout
    val target = if (moveUp) index - 1 else index + 1
    if (target !in layout.order.indices) return layout
    val reordered = layout.order.toMutableList()
    reordered.add(target, reordered.removeAt(index))
    return layout.copy(order = reordered)
  }

  /** Restores the default order and shows every card. */
  fun reset(layout: DashboardLayout): DashboardLayout = default()

  private fun normalizeHidden(
    order: List<HomeCardId>,
    hidden: Set<HomeCardId>,
  ): Set<HomeCardId> {
    val inOrder = hidden.filter { order.contains(it) }.toSet()
    if (inOrder.size >= order.size) return emptySet()
    return inOrder
  }
}
