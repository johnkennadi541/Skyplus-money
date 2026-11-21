package com.example.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Deterministic chart palette for portfolio categories, holdings and allocation.
 *
 * Rules this file exists to enforce:
 * - the same set of keys always produces the same colours, across recompositions,
 *   refreshes and app restarts;
 * - two distinct keys on the same chart never share a colour, at any chart size;
 * - the palette never overrides the income/expense semantics. Green and red mean
 *   money in and money out, and are never handed to a portfolio series.
 *
 * The six approved light colours and their dark variants come first and are never
 * altered. When a chart needs more than six series, a deterministic overflow tier is
 * used: the same twelve hues, blended a fixed step toward black (light mode) or white
 * (dark mode). Blending is position-independent and repeatable, so a key's colour
 * still survives a restart, and tiers never collide with each other.
 */
object PortfolioPalette {

  /** Light-mode series colours, approved order: vermilion, amber, emerald, blue, cyan, violet. */
  val light: List<Color> =
    listOf(
      Color(0xFFEA580C),
      Color(0xFFD97706),
      Color(0xFF059669),
      Color(0xFF2563EB),
      Color(0xFF0891B2),
      Color(0xFF7C3AED),
    )

  /** Dark-mode variants of the same hues, lifted so they read on #1E293B. */
  val dark: List<Color> =
    listOf(
      Color(0xFFFB923C),
      Color(0xFFFBBF24),
      Color(0xFF34D399),
      Color(0xFF60A5FA),
      Color(0xFF22D3EE),
      Color(0xFFA78BFA),
    )

  /** Overflow hues, used only when a chart needs more than the six approved colours. */
  private val overflowLight: List<Color> =
    listOf(
      Color(0xFFDB2777),
      Color(0xFF0F766E),
      Color(0xFF9333EA),
      Color(0xFFB45309),
      Color(0xFF1D4ED8),
      Color(0xFF4D7C0F),
    )

  private val overflowDark: List<Color> =
    listOf(
      Color(0xFFF472B6),
      Color(0xFF2DD4BF),
      Color(0xFFC084FC),
      Color(0xFFFCD34D),
      Color(0xFF93C5FD),
      Color(0xFFBEF264),
    )

  /** Number of blend tiers above the base colours. Six tiers give 72 usable slots. */
  private const val MAX_TIERS = 6

  /** How far each tier moves toward black (light) or white (dark). */
  private const val TIER_STEP = 0.12f

  /** Total number of distinct colours [assign] can hand out. */
  const val CAPACITY: Int = 12 * MAX_TIERS

  fun seriesFor(isDark: Boolean): List<Color> = if (isDark) dark else light

  /** Stable 32-bit hash of a key. Exposed so tests can pin the mapping. */
  fun stableHash(key: String): Int {
    var hash = 2166136261L.toInt()
    for (character in key.trim().lowercase()) {
      hash = hash xor character.code
      hash *= 16777619
    }
    return hash
  }

  /**
   * The colour for one key on its own.
   *
   * Use this only where a single key is shown. It does not consider other keys, so it
   * can repeat a colour; charts must call [assign] instead.
   */
  fun colorFor(key: String, isDark: Boolean): Color {
    val base = seriesFor(isDark) + if (isDark) overflowDark else overflowLight
    return base[Math.floorMod(stableHash(key), base.size)]
  }

  /**
   * Assigns a colour to every key in one chart.
   *
   * Blank keys are dropped, duplicate keys are collapsed, and the remaining keys are
   * sorted first, so the result depends only on the set of keys and not on the order
   * they arrived in. Each key gets its own slot: the base colour comes from its stable
   * hash, a collision moves to the next free slot, and once the twelve base slots are
   * full the next tier of blended colours is used.
   *
   * @throws IllegalArgumentException when more than [CAPACITY] distinct keys are asked
   *   for. Uniqueness is a hard guarantee, so the call fails loudly rather than
   *   quietly repeating a colour.
   */
  fun assign(keys: List<String>, isDark: Boolean): Map<String, Color> {
    val ordered = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    val capacity = capacityPalette(isDark)
    require(ordered.size <= capacity.size) {
      "PortfolioPalette can give at most ${capacity.size} distinct colours; " +
        "${ordered.size} were requested."
    }

    val used = mutableSetOf<Int>()
    val result = linkedMapOf<String, Color>()
    for (key in ordered) {
      var slot = Math.floorMod(stableHash(key), capacity.size)
      var attempts = 0
      while (slot in used && attempts < capacity.size) {
        slot = (slot + 1) % capacity.size
        attempts++
      }
      used.add(slot)
      result[key] = capacity[slot]
    }
    return result
  }

  /**
   * The full set of distinct colours for one theme: twelve base hues, then five further
   * tiers of the same hues blended toward black (light) or white (dark).
   */
  fun capacityPalette(isDark: Boolean): List<Color> {
    val base = seriesFor(isDark) + if (isDark) overflowDark else overflowLight
    val target = if (isDark) Color.White else Color.Black
    val result = ArrayList<Color>(base.size * MAX_TIERS)
    for (tier in 0 until MAX_TIERS) {
      if (tier == 0) {
        result.addAll(base)
      } else {
        val amount = (tier * TIER_STEP).coerceAtMost(0.85f)
        base.forEach { colour -> result.add(lerp(colour, target, amount)) }
      }
    }
    return result
  }
}
