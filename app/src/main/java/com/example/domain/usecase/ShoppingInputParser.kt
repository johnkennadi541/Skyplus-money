package com.example.domain.usecase

import java.math.BigDecimal
import java.util.Locale

/**
 * Converts text captured from keyboard, speech recognition or OCR into reviewable shopping drafts.
 * Imported text is never saved automatically: callers must show the drafts for user confirmation.
 */
data class ShoppingItemDraft(
  val name: String,
  val quantityDecimal: String = "1",
  val unit: String = "piece",
  val estimatedUnitPriceDecimal: String? = null,
  val needsReview: Boolean = true,
)

object ShoppingInputParser {
  private val quantityFirst = Regex(
    """^(\d+(?:[.,]\d+)?)\s*(kg|kgs|kilo(?:gram)?s?|g|grams?|l|lit(?:re|er)s?|ml|millilit(?:re|er)s?|pieces?|pcs|packets?|packs?|boxes|box|dozen|bottles?)?\s+(.+?)(?:\s+(?:@|at|price)\s*(\d+(?:[.,]\d{1,3})?))?$""",
    RegexOption.IGNORE_CASE,
  )
  private val nameFirst = Regex(
    """^(.+?)\s+(\d+(?:[.,]\d+)?)\s*(kg|kgs|kilo(?:gram)?s?|g|grams?|l|lit(?:re|er)s?|ml|millilit(?:re|er)s?|pieces?|pcs|packets?|packs?|boxes|box|dozen|bottles?)(?:\s+(?:@|at|price)\s*(\d+(?:[.,]\d{1,3})?))?$""",
    RegexOption.IGNORE_CASE,
  )
  private val explicitPrice = Regex("""^(.*?)\s+(?:@|at|price)\s*(\d+(?:[.,]\d{1,3})?)$""", RegexOption.IGNORE_CASE)

  fun parse(text: String): List<ShoppingItemDraft> {
    if (text.isBlank()) return emptyList()
    val segments = text
      .replace('•', '\n')
      .replace('\r', '\n')
      .split(Regex("[\n;,]+|\s+(?:and|&)\s+|\s+மற்றும்\s+", RegexOption.IGNORE_CASE))
      .map { it.trim().trimStart('-', '*', '•', ' ').trimEnd('.', ' ') }
      .filter { it.isNotBlank() }

    return mergeDuplicates(segments.map(::parseSegment))
  }

  private fun parseSegment(raw: String): ShoppingItemDraft {
    val priceMatch = explicitPrice.matchEntire(raw)
    val withoutPrice = priceMatch?.groupValues?.get(1)?.trim() ?: raw
    val explicitPriceValue = priceMatch?.groupValues?.get(2)?.replace(',', '.')

    val first = quantityFirst.matchEntire(withoutPrice)
    if (first != null) {
      return ShoppingItemDraft(
        name = first.groupValues[3].trim(),
        quantityDecimal = first.groupValues[1].replace(',', '.'),
        unit = normalizeUnit(first.groupValues[2]),
        estimatedUnitPriceDecimal = explicitPriceValue,
      )
    }

    val last = nameFirst.matchEntire(withoutPrice)
    if (last != null) {
      return ShoppingItemDraft(
        name = last.groupValues[1].trim(),
        quantityDecimal = last.groupValues[2].replace(',', '.'),
        unit = normalizeUnit(last.groupValues[3]),
        estimatedUnitPriceDecimal = explicitPriceValue ?: last.groupValues[4].takeIf(String::isNotBlank)?.replace(',', '.'),
      )
    }

    return ShoppingItemDraft(
      name = withoutPrice,
      estimatedUnitPriceDecimal = explicitPriceValue,
      needsReview = true,
    )
  }

  private fun normalizeUnit(raw: String): String = when (raw.lowercase(Locale.ROOT)) {
    "kg", "kgs", "kilo", "kilos", "kilogram", "kilograms" -> "kg"
    "g", "gram", "grams" -> "g"
    "l", "litre", "litres", "liter", "liters" -> "litre"
    "ml", "millilitre", "millilitres", "milliliter", "milliliters" -> "ml"
    "piece", "pieces", "pc", "pcs" -> "piece"
    "packet", "packets" -> "packet"
    "pack", "packs" -> "pack"
    "box", "boxes" -> "box"
    "dozen" -> "dozen"
    "bottle", "bottles" -> "bottle"
    else -> "piece"
  }

  /** Merge only exact normalized name + unit matches; quantity is summed with BigDecimal. */
  fun mergeDuplicates(items: List<ShoppingItemDraft>): List<ShoppingItemDraft> {
    val merged = linkedMapOf<Pair<String, String>, ShoppingItemDraft>()
    items.forEach { item ->
      val key = item.name.trim().lowercase(Locale.ROOT) to item.unit.lowercase(Locale.ROOT)
      val existing = merged[key]
      if (existing == null) {
        merged[key] = item
      } else {
        val quantity = BigDecimal(existing.quantityDecimal).add(BigDecimal(item.quantityDecimal)).stripTrailingZeros().toPlainString()
        val price = if (existing.estimatedUnitPriceDecimal == item.estimatedUnitPriceDecimal) existing.estimatedUnitPriceDecimal else null
        merged[key] = existing.copy(
          quantityDecimal = quantity,
          estimatedUnitPriceDecimal = price,
          needsReview = true,
        )
      }
    }
    return merged.values.toList()
  }
}
