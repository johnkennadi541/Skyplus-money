package com.example

import com.example.core.common.CurrencyCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CurrencyCatalogTest {

  @Test
  fun `catalogue contains common currencies with ISO fraction digits`() {
    val currencies = CurrencyCatalog.all(Locale.US).associateBy { it.code }
    assertEquals(2, currencies.getValue("USD").fractionDigits)
    assertEquals(2, currencies.getValue("INR").fractionDigits)
    assertEquals(3, currencies.getValue("KWD").fractionDigits)
    assertEquals(0, currencies.getValue("JPY").fractionDigits)
  }

  @Test
  fun `catalogue excludes pseudo currency and invalid codes`() {
    val codes = CurrencyCatalog.all().map { it.code }
    assertFalse(codes.contains("XXX"))
    assertNull(CurrencyCatalog.find("XXX"))
    assertNull(CurrencyCatalog.find("ZZZ"))
  }

  @Test
  fun `lookup normalizes code and returns display metadata`() {
    val option = CurrencyCatalog.find(" inr ", Locale.US)
    assertNotNull(option)
    assertEquals("INR", option?.code)
    assertTrue(option?.displayName?.isNotBlank() == true)
    assertTrue(option?.symbol?.isNotBlank() == true)
  }

  @Test
  fun `catalogue is unique and sorted`() {
    val currencies = CurrencyCatalog.all(Locale.US)
    assertEquals(currencies.size, currencies.map { it.code }.distinct().size)
    assertEquals(
      currencies.map { it.displayName.lowercase(Locale.US) to it.code }.sortedWith(
        compareBy<Pair<String, String>>({ it.first }, { it.second })
      ),
      currencies.map { it.displayName.lowercase(Locale.US) to it.code },
    )
  }
}
