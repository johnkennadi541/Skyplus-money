package com.example.domain.usecase

import com.example.domain.model.ShoppingMealIngredient
import com.example.domain.model.ShoppingPantryItem
import java.math.BigDecimal
import java.util.Locale

object ShoppingMealPlannerEngine {
  fun requirements(ingredients: List<ShoppingMealIngredient>): List<ShoppingItemDraft> {
    val merged = linkedMapOf<Pair<String, String>, ShoppingItemDraft>()
    ingredients.forEach { ingredient ->
      val key = ingredient.name.trim().lowercase(Locale.ROOT) to ingredient.unit.trim().lowercase(Locale.ROOT)
      val amount = BigDecimal(ingredient.quantityDecimal)
      require(amount > BigDecimal.ZERO) { "Ingredient quantity must be positive" }
      val previous = merged[key]
      val nextAmount = (previous?.let { BigDecimal(it.quantityDecimal) } ?: BigDecimal.ZERO).add(amount)
      merged[key] = ShoppingItemDraft(
        name = previous?.name ?: ingredient.name.trim(),
        quantityDecimal = nextAmount.stripTrailingZeros().toPlainString(),
        unit = previous?.unit ?: ingredient.unit.trim(),
        needsReview = true,
      )
    }
    return merged.values.toList()
  }

  fun remainingAfterPantry(
    requirements: List<ShoppingItemDraft>,
    pantry: List<ShoppingPantryItem>,
  ): List<ShoppingItemDraft> = requirements.mapNotNull { requirement ->
    val stock = pantry.firstOrNull {
      it.name.equals(requirement.name, ignoreCase = true) && it.unit.equals(requirement.unit, ignoreCase = true)
    } ?: return@mapNotNull requirement
    val remaining = (BigDecimal(requirement.quantityDecimal) - BigDecimal(stock.quantityDecimal)).max(BigDecimal.ZERO)
    if (remaining.signum() == 0) null
    else requirement.copy(quantityDecimal = remaining.stripTrailingZeros().toPlainString(), needsReview = true)
  }
}
