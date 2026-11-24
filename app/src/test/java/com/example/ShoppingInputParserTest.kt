package com.example

import com.example.domain.usecase.ShoppingInputParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingInputParserTest {
  @Test
  fun `parses name first and quantity first speech style entries`() {
    val items = ShoppingInputParser.parse("Rice 5 kg, 2 litre milk; chicken 1.5 kg")
    assertEquals(3, items.size)
    assertEquals("Rice", items[0].name)
    assertEquals("5", items[0].quantityDecimal)
    assertEquals("kg", items[0].unit)
    assertEquals("milk", items[1].name)
    assertEquals("2", items[1].quantityDecimal)
    assertEquals("litre", items[1].unit)
    assertEquals("chicken", items[2].name)
    assertEquals("1.5", items[2].quantityDecimal)
  }

  @Test
  fun `parses explicit price only when clearly marked`() {
    val item = ShoppingInputParser.parse("Olive oil 1 litre @ 3.250").single()
    assertEquals("Olive oil", item.name)
    assertEquals("3.250", item.estimatedUnitPriceDecimal)
  }

  @Test
  fun `keeps uncertain OCR text as reviewable draft instead of guessing`() {
    val item = ShoppingInputParser.parse("பால் வாங்க வேண்டும்").single()
    assertEquals("பால் வாங்க வேண்டும்", item.name)
    assertEquals("1", item.quantityDecimal)
    assertTrue(item.needsReview)
    assertNull(item.estimatedUnitPriceDecimal)
  }

  @Test
  fun `merges duplicate names only when units match and sums exact decimal quantities`() {
    val items = ShoppingInputParser.parse("Rice 1.25 kg, rice 0.75 kg, rice 2 packet")
    assertEquals(2, items.size)
    assertEquals("2", items.first().quantityDecimal)
    assertEquals("kg", items.first().unit)
    assertEquals("2", items.last().quantityDecimal)
    assertEquals("packet", items.last().unit)
  }

  @Test
  fun `does not invent quantity or price for plain item names`() {
    val item = ShoppingInputParser.parse("Dish soap").single()
    assertEquals("Dish soap", item.name)
    assertEquals("1", item.quantityDecimal)
    assertNull(item.estimatedUnitPriceDecimal)
    assertTrue(item.needsReview)
  }
}
