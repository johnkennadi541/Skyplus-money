package com.example.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Deterministic chart palette for portfolio categories, holdings and allocation.
 *
 * Rules this file exists to enforce:
 * - the same key always gets the same colour, across recompositions, refreshes and
 *   app restarts, so a user learns which slice is which;
 * - two keys on the same chart never share a colour, so neighbouring categories stay
 *   distinguishable;
 * - the palette never overrides the income/expense semantics. Green and red mean
 *   money in and money out, and are not available to portfolio series.
 *
 * The hash is FNV-1a over the lower-cased key: a small, stable, dependency-free
 * function whose output does not depend on the process or the JVM version.
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

  /** The colour for one key, ignoring any other key on screen. */
  fun colorFor(key: String, isDark: Boolean): Color {
    val palette = seriesFor(isDark)
    return palette[Math.floorMod(stableHash(key), palette.size)]
  }

  /**
   * Assigns a distinct colour to every key in one chart.
   *
   * The base colour comes from [colorFor]. If two keys hash to the same slot, the
   * later one in sorted order takes the next free slot, so the result never repeats a
   * colour inside the returned map. Keys are sorted first, so the assignment depends
   * only on the set of keys, not on their input order.
   */
  fun assign(keys: List<String>, isDark: Boolean): Map<String, Color> {
    val palette = seriesFor(isDark)
    val ordered = keys.map { it.trim() }.distinct().sorted()
    val used = mutableSetOf<Int>()
    val result = linkedMapOf<String, Color>()
    for (key in ordered) {
      var slot = Math.floorMod(stableHash(key), palette.size)
      var attempts = 0
      while (slot in used && attempts < palette.size) {
        slot = (slot + 1) % palette.size
        attempts++
      }
      used.add(slot)
      result[key] = palette[slot]
    }
    return result
  }
}
