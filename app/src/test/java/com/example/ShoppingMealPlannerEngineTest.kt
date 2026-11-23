package com.example

import com.example.domain.model.ShoppingMealIngredient
import com.example.domain.model.ShoppingPantryItem
import com.example.domain.usecase.ShoppingMealPlannerEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingMealPlannerEngineTest {
  @Test fun combinesDuplicateMealIngredientsExactly() {
    val items = listOf(
      ShoppingMealIngredient(userId = "u", mealPlanId = "m", name = "Rice", quantityDecimal = "1.25", unit = "kg"),
      ShoppingMealIngredient(userId = "u", mealPlanId = "m", name = "rice", quantityDecimal = "0.75", unit = "kg"),
    )
    val result = ShoppingMealPlannerEngine.requirements(items)
    assertEquals(1, result.size)
    assertEquals("2", result.single().quantityDecimal)
  }

  @Test fun subtractsOnlySameUnitPantryStock() {
    val requirement = ShoppingMealPlannerEngine.requirements(
      listOf(ShoppingMealIngredient(userId = "u", mealPlanId = "m", name = "Rice", quantityDecimal = "3", unit = "kg"))
    )
    val pantry = listOf(ShoppingPantryItem(userId = "u", name = "rice", quantityDecimal = "1.25", unit = "kg"))
    assertEquals("1.75", ShoppingMealPlannerEngine.remainingAfterPantry(requirement, pantry).single().quantityDecimal)
  }
}
