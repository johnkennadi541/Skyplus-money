package com.example

import com.example.domain.model.TransactionType
import com.example.feature.smartdetection.engine.MerchantCategoryResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantCategoryResolverTest {
  @Test
  fun maps_food_and_grocery_aliases() {
    assertEquals("Food & Dining", MerchantCategoryResolver.resolve("Swiggy", TransactionType.EXPENSE))
    assertEquals("Food & Dining", MerchantCategoryResolver.resolve("ZOMATO Online", TransactionType.EXPENSE))
    assertEquals("Groceries", MerchantCategoryResolver.resolve("D-Mart", TransactionType.EXPENSE))
    assertEquals("Groceries", MerchantCategoryResolver.resolve("Reliance Smart Bazaar", TransactionType.EXPENSE))
  }

  @Test
  fun maps_utility_transport_health_and_shopping_merchants() {
    assertEquals("Bills & Utilities", MerchantCategoryResolver.resolve("TANGEDCO", TransactionType.EXPENSE))
    assertEquals("Bills & Utilities", MerchantCategoryResolver.resolve("Airtel Payments", TransactionType.EXPENSE))
    assertEquals("Transport", MerchantCategoryResolver.resolve("Uber Trip", TransactionType.EXPENSE))
    assertEquals("Fuel", MerchantCategoryResolver.resolve("Indian Oil", TransactionType.EXPENSE))
    assertEquals("Fuel", MerchantCategoryResolver.resolve("HPCL Fuel", TransactionType.EXPENSE))
    assertEquals("Medical & Healthcare", MerchantCategoryResolver.resolve("Apollo Pharmacy", TransactionType.EXPENSE))
    assertEquals("Entertainment", MerchantCategoryResolver.resolve("Netflix", TransactionType.EXPENSE))
    assertEquals("Shopping", MerchantCategoryResolver.resolve("Amazon Marketplace", TransactionType.EXPENSE))
  }

  @Test
  fun unknown_merchants_and_non_expenses_are_not_classified() {
    assertNull(MerchantCategoryResolver.resolve("Local Store 42", TransactionType.EXPENSE))
    assertNull(MerchantCategoryResolver.resolve("Swiggy", TransactionType.REFUND))
    assertNull(MerchantCategoryResolver.resolve("Amazon", TransactionType.INCOME))
    assertNull(MerchantCategoryResolver.resolve(null, TransactionType.EXPENSE))
  }

  @Test
  fun aliases_do_not_match_inside_unrelated_names() {
    assertNull(MerchantCategoryResolver.resolve("NotAmazonic Store", TransactionType.EXPENSE))
    assertNull(MerchantCategoryResolver.resolve("JioMartial Arts", TransactionType.EXPENSE))
  }
}
