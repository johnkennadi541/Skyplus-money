package com.example.feature.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.Money
import com.example.domain.model.ShoppingCategories
import com.example.domain.model.ShoppingItemComparisonSummary
import com.example.domain.model.ShoppingList
import com.example.domain.model.ShoppingListItem
import com.example.domain.model.ShoppingUnit
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.domain.usecase.ShoppingCalculationEngine
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val COMMON_SHOPPING_CURRENCIES = listOf("INR", "USD", "EUR", "GBP", "CAD", "AUD", "SGD", "AED")

private fun minorUnitsToPlainDecimal(minor: Long, currencyCode: String): String {
  val scale = Money.resolveFractionDigits(currencyCode)
  return BigDecimal.valueOf(minor, scale).stripTrailingZeros().toPlainString()
}

private fun formatShoppingTimestamp(epochMillis: Long): String {
  val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
  return fmt.format(Date(epochMillis))
}

private fun formatShoppingDateOnly(epochMillis: Long): String {
  val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
  return fmt.format(Date(epochMillis))
}

private fun parseIsoDateOrNull(dateText: String): Long? {
  val trimmed = dateText.trim()
  if (trimmed.isEmpty()) return System.currentTimeMillis()
  return try {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val parsed = fmt.parse(trimmed) ?: return null
    val now = java.util.Calendar.getInstance()
    val cal = java.util.Calendar.getInstance().apply {
      time = parsed
      set(java.util.Calendar.HOUR_OF_DAY, now.get(java.util.Calendar.HOUR_OF_DAY))
      set(java.util.Calendar.MINUTE, now.get(java.util.Calendar.MINUTE))
      set(java.util.Calendar.SECOND, now.get(java.util.Calendar.SECOND))
    }
    cal.timeInMillis
  } catch (_: Exception) {
    null
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShoppingListFormDialog(
  existingList: ShoppingList?,
  hasItemsInList: Boolean,
  defaultCurrency: String,
  availableCurrencies: List<String>,
  onDismiss: () -> Unit,
  onSave: (name: String, currencyCode: String, budgetDecimal: String?, warningThresholdPercent: Int) -> Unit,
) {
  val currencyOptions = remember(availableCurrencies, existingList) {
    (listOfNotNull(existingList?.currencyCode, defaultCurrency) + availableCurrencies + COMMON_SHOPPING_CURRENCIES)
      .map { it.trim().uppercase() }
      .filter { it.length == 3 }
      .distinct()
  }

  var name by remember(existingList) { mutableStateOf(existingList?.name ?: "") }
  var selectedCurrency by remember(existingList) {
    mutableStateOf(existingList?.currencyCode ?: defaultCurrency.ifBlank { "USD" })
  }
  var budgetText by remember(existingList) {
    mutableStateOf(
      existingList?.budgetMinor?.let { minorUnitsToPlainDecimal(it, existingList.currencyCode) } ?: ""
    )
  }
  var warningThreshold by remember(existingList) {
    mutableIntStateOf(existingList?.warningThresholdPercent ?: 80)
  }
  var validationError by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(text = if (existingList == null) "Create Shopping List" else "Edit Shopping List")
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            validationError = null
          },
          label = { Text("List Name (e.g., Grocery, Kitchen)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("shopping_list_name_input"),
        )

        Text(
          text =
            if (existingList != null && hasItemsInList)
              "Currency (locked while list contains items)"
            else "List Currency (ISO 4217)",
          style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          currencyOptions.forEach { code ->
            FilterChip(
              selected = selectedCurrency == code,
              enabled = !(existingList != null && hasItemsInList),
              onClick = { selectedCurrency = code },
              label = { Text(code) },
              modifier = Modifier.testTag("shopping_list_currency_$code"),
            )
          }
        }

        OutlinedTextField(
          value = budgetText,
          onValueChange = {
            budgetText = it
            validationError = null
          },
          label = { Text("Optional Budget ($selectedCurrency)") },
          placeholder = { Text("Leave blank for no budget cap") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("shopping_list_budget_input"),
        )

        Text(text = "Budget Warning Alert Threshold", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          listOf(75, 80, 90).forEach { threshold ->
            FilterChip(
              selected = warningThreshold == threshold,
              onClick = { warningThreshold = threshold },
              label = { Text("$threshold%") },
            )
          }
        }

        validationError?.let { msg ->
          Text(
            text = msg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isBlank()) {
            validationError = "List name is required"
            return@Button
          }
          if (budgetText.isNotBlank()) {
            val validBudget = try {
              val parsed = BigDecimal(budgetText.trim())
              parsed > BigDecimal.ZERO
            } catch (_: Exception) {
              false
            }
            if (!validBudget) {
              validationError = "Budget must be a positive number or left blank"
              return@Button
            }
          }
          onSave(name.trim(), selectedCurrency, budgetText.trim().ifBlank { null }, warningThreshold)
        },
        modifier = Modifier.testTag("save_shopping_list_button"),
      ) {
        Text(if (existingList == null) "Create" else "Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShoppingItemFormDialog(
  existingItem: ShoppingListItem?,
  currencyCode: String,
  calculationEngine: ShoppingCalculationEngine,
  formatMoneyUseCase: FormatMoneyUseCase,
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    category: String,
    quantityDecimal: String,
    unit: String,
    estimatedUnitPriceDecimal: String,
    storeName: String?,
    notes: String?,
  ) -> Unit,
) {
  var name by remember(existingItem) { mutableStateOf(existingItem?.name ?: "") }
  var category by remember(existingItem) {
    mutableStateOf(existingItem?.category ?: ShoppingCategories.DEFAULT_CATEGORIES.first())
  }
  var categoryExpanded by remember { mutableStateOf(false) }
  var quantityText by remember(existingItem) { mutableStateOf(existingItem?.quantityDecimal ?: "1") }
  var unitText by remember(existingItem) { mutableStateOf(existingItem?.unit ?: ShoppingUnit.PIECE.label) }
  var estUnitPriceText by remember(existingItem) {
    mutableStateOf(
      existingItem?.let { minorUnitsToPlainDecimal(it.estimatedUnitPriceMinor, currencyCode) } ?: ""
    )
  }
  var storeName by remember(existingItem) { mutableStateOf(existingItem?.storeName ?: "") }
  var notes by remember(existingItem) { mutableStateOf(existingItem?.notes ?: "") }
  var validationError by remember { mutableStateOf<String?>(null) }

  val liveEstimatedTotalText = remember(quantityText, estUnitPriceText, currencyCode) {
    try {
      val qty = calculationEngine.parsePositiveQuantity(quantityText, "Quantity")
      val unitMinor = calculationEngine.parseNonNegativeMoneyMinor(estUnitPriceText.ifBlank { "0" }, currencyCode, "Price")
      val totalMinor = calculationEngine.computeLineTotalMinor(qty, unitMinor, currencyCode)
      formatMoneyUseCase.format(Money.fromMinorUnits(totalMinor, currencyCode))
    } catch (_: Exception) {
      "--"
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (existingItem == null) "Add Shopping Item" else "Edit Shopping Item") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            validationError = null
          },
          label = { Text("Item Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("shopping_item_name_input"),
        )

        ExposedDropdownMenuBox(
          expanded = categoryExpanded,
          onExpandedChange = { categoryExpanded = !categoryExpanded },
        ) {
          OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
            modifier =
              Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
          )
          ExposedDropdownMenu(
            expanded = categoryExpanded,
            onDismissRequest = { categoryExpanded = false },
          ) {
            ShoppingCategories.DEFAULT_CATEGORIES.forEach { cat ->
              DropdownMenuItem(
                text = { Text(cat) },
                onClick = {
                  category = cat
                  categoryExpanded = false
                },
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          OutlinedTextField(
            value = quantityText,
            onValueChange = {
              quantityText = it
              validationError = null
            },
            label = { Text("Quantity") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).testTag("shopping_item_quantity_input"),
          )
          OutlinedTextField(
            value = estUnitPriceText,
            onValueChange = {
              estUnitPriceText = it
              validationError = null
            },
            label = { Text("Est. Unit Price ($currencyCode)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.3f).testTag("shopping_item_unit_price_input"),
          )
        }

        Text(text = "Unit", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          ShoppingUnit.entries.forEach { preset ->
            FilterChip(
              selected = unitText.equals(preset.label, ignoreCase = true),
              onClick = { unitText = preset.label },
              label = { Text(preset.label) },
              modifier = Modifier.testTag("shopping_unit_${preset.label}"),
            )
          }
        }
        OutlinedTextField(
          value = unitText,
          onValueChange = { unitText = it },
          label = { Text("Unit (or custom)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("shopping_item_unit_input"),
        )

        OceanCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Estimated Total",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = liveEstimatedTotalText,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        OutlinedTextField(
          value = storeName,
          onValueChange = { storeName = it },
          label = { Text("Store Name (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("shopping_item_store_input"),
        )

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth().testTag("shopping_item_notes_input"),
        )

        validationError?.let { msg ->
          Text(
            text = msg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isBlank()) {
            validationError = "Item name is required"
            return@Button
          }
          if (unitText.isBlank()) {
            validationError = "Unit is required"
            return@Button
          }
          try {
            calculationEngine.parsePositiveQuantity(quantityText, "Quantity")
            calculationEngine.parseNonNegativeMoneyMinor(
              estUnitPriceText.ifBlank { "0" },
              currencyCode,
              "Estimated unit price",
            )
          } catch (e: Exception) {
            validationError = e.message ?: "Invalid quantity or price"
            return@Button
          }
          onSave(
            name.trim(),
            category.trim().ifBlank { "Other" },
            quantityText.trim(),
            unitText.trim(),
            estUnitPriceText.trim().ifBlank { "0" },
            storeName.trim().ifBlank { null },
            notes.trim().ifBlank { null },
          )
        },
        modifier = Modifier.testTag("save_shopping_item_button"),
      ) {
        Text(if (existingItem == null) "Add Item" else "Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun RecordPurchaseDialog(
  item: ShoppingListItem,
  currencyCode: String,
  calculationEngine: ShoppingCalculationEngine,
  formatMoneyUseCase: FormatMoneyUseCase,
  onDismiss: () -> Unit,
  onConfirmPurchase: (
    actualUnitPriceDecimal: String,
    actualTotalOverrideDecimal: String?,
    purchasedAt: Long,
    storeNameOverride: String?,
  ) -> Unit,
) {
  var actualUnitPriceText by remember(item) {
    val seedMinor = item.actualUnitPriceMinor ?: item.estimatedUnitPriceMinor
    mutableStateOf(minorUnitsToPlainDecimal(seedMinor, currencyCode))
  }
  var actualTotalOverrideText by remember(item) {
    mutableStateOf(
      item.actualTotalMinor?.let { minorUnitsToPlainDecimal(it, currencyCode) } ?: ""
    )
  }
  var storeNameText by remember(item) { mutableStateOf(item.storeName ?: "") }
  var dateText by remember(item) {
    mutableStateOf(formatShoppingDateOnly(item.purchasedAt ?: System.currentTimeMillis()))
  }
  var validationError by remember { mutableStateOf<String?>(null) }

  val estimatedFormatted = formatMoneyUseCase.format(
    Money.fromMinorUnits(item.estimatedTotalMinor, currencyCode)
  )

  val previewOutcome = remember(actualUnitPriceText, actualTotalOverrideText, item) {
    try {
      val actualTotalMinor =
        if (actualTotalOverrideText.isNotBlank()) {
          calculationEngine.parseNonNegativeMoneyMinor(actualTotalOverrideText, currencyCode, "Actual total")
        } else {
          val unitMinor = calculationEngine.parseNonNegativeMoneyMinor(actualUnitPriceText.ifBlank { "0" }, currencyCode, "Unit price")
          calculationEngine.computeLineTotalMinor(item.quantityDecimal, unitMinor, currencyCode)
        }
      val diff = Math.subtractExact(item.estimatedTotalMinor, actualTotalMinor)
      Pair(
        formatMoneyUseCase.format(Money.fromMinorUnits(actualTotalMinor, currencyCode)),
        diff,
      )
    } catch (_: Exception) {
      null
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Mark Purchased: ${item.name}") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        Text(
          text = "Qty: ${item.quantityDecimal} ${item.unit} • Estimated Total: $estimatedFormatted",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = actualUnitPriceText,
          onValueChange = {
            actualUnitPriceText = it
            validationError = null
          },
          label = { Text("Actual Unit Price ($currencyCode)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("purchase_actual_unit_price_input"),
        )

        OutlinedTextField(
          value = actualTotalOverrideText,
          onValueChange = {
            actualTotalOverrideText = it
            validationError = null
          },
          label = { Text("Actual Total Override (Optional)") },
          placeholder = { Text("Auto-calculated from qty × unit price if blank") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("purchase_actual_total_input"),
        )

        OutlinedTextField(
          value = storeNameText,
          onValueChange = { storeNameText = it },
          label = { Text("Store Purchased From (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("purchase_store_input"),
        )

        OutlinedTextField(
          value = dateText,
          onValueChange = {
            dateText = it
            validationError = null
          },
          label = { Text("Purchase Date (YYYY-MM-DD)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("purchase_date_input"),
        )

        if (previewOutcome != null) {
          val (actualFormatted, diffMinor) = previewOutcome
          val varianceMoney = Money.fromMinorUnits(kotlin.math.abs(diffMinor), currencyCode)
          val varianceFormatted = formatMoneyUseCase.format(varianceMoney)
          OceanCard {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "Actual Total",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = actualFormatted,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
              OceanStatusBadge(
                text =
                  when {
                    diffMinor > 0L -> "Saved $varianceFormatted"
                    diffMinor < 0L -> "Over $varianceFormatted"
                    else -> "On Estimate"
                  },
                tone =
                  when {
                    diffMinor > 0L -> BadgeTone.POSITIVE
                    diffMinor < 0L -> BadgeTone.NEGATIVE
                    else -> BadgeTone.INFO
                  },
              )
            }
          }
        }

        validationError?.let { msg ->
          Text(
            text = msg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedDate = parseIsoDateOrNull(dateText)
          if (parsedDate == null) {
            validationError = "Enter purchase date as YYYY-MM-DD"
            return@Button
          }
          try {
            calculationEngine.parseNonNegativeMoneyMinor(
              actualUnitPriceText.ifBlank { "0" },
              currencyCode,
              "Actual unit price",
            )
            if (actualTotalOverrideText.isNotBlank()) {
              calculationEngine.parseNonNegativeMoneyMinor(
                actualTotalOverrideText,
                currencyCode,
                "Actual total",
              )
            }
          } catch (e: Exception) {
            validationError = e.message ?: "Invalid actual price"
            return@Button
          }
          onConfirmPurchase(
            actualUnitPriceText.trim().ifBlank { "0" },
            actualTotalOverrideText.trim().ifBlank { null },
            parsedDate,
            storeNameText.trim().ifBlank { null },
          )
        },
        modifier = Modifier.testTag("confirm_purchase_button"),
      ) {
        Text("Confirm Purchase")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun StorePriceComparisonDialog(
  comparison: ShoppingItemComparisonSummary,
  currencyCode: String,
  calculationEngine: ShoppingCalculationEngine,
  formatMoneyUseCase: FormatMoneyUseCase,
  onDismiss: () -> Unit,
  onRecordPrice: (storeName: String, unitPriceDecimal: String, recordedAt: Long, notes: String?) -> Unit,
  onDeletePrice: (priceId: String) -> Unit,
) {
  val item = comparison.item
  var storeName by remember { mutableStateOf("") }
  var unitPrice by remember { mutableStateOf("") }
  var dateText by remember { mutableStateOf(formatShoppingDateOnly(System.currentTimeMillis())) }
  var notes by remember { mutableStateOf("") }
  var validationError by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Store Price Compare: ${item.name}") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        Text(
          text = "Manually log store quotes per ${item.unit}. Latest and lowest prices are highlighted.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = storeName,
          onValueChange = {
            storeName = it
            validationError = null
          },
          label = { Text("Store Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("compare_store_name_input"),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          OutlinedTextField(
            value = unitPrice,
            onValueChange = {
              unitPrice = it
              validationError = null
            },
            label = { Text("Unit Price ($currencyCode)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).testTag("compare_unit_price_input"),
          )
          OutlinedTextField(
            value = dateText,
            onValueChange = {
              dateText = it
              validationError = null
            },
            label = { Text("Date (YYYY-MM-DD)") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("compare_date_input"),
          )
        }
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("compare_notes_input"),
        )

        validationError?.let { msg ->
          Text(
            text = msg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        Button(
          onClick = {
            if (storeName.isBlank()) {
              validationError = "Store name is required"
              return@Button
            }
            val parsedDate = parseIsoDateOrNull(dateText)
            if (parsedDate == null) {
              validationError = "Enter date as YYYY-MM-DD"
              return@Button
            }
            try {
              calculationEngine.parseNonNegativeMoneyMinor(unitPrice, currencyCode, "Unit price")
            } catch (e: Exception) {
              validationError = e.message ?: "Enter valid unit price"
              return@Button
            }
            onRecordPrice(
              storeName.trim(),
              unitPrice.trim(),
              parsedDate,
              notes.trim().ifBlank { null },
            )
            storeName = ""
            unitPrice = ""
            notes = ""
          },
          modifier = Modifier.fillMaxWidth().testTag("save_store_price_button"),
        ) {
          Text("Record Store Price")
        }

        Spacer(modifier = Modifier.height(OceanSpacing.xxs))
        Text(
          text = "Recorded Quotes (${comparison.storePrices.size})",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )

        if (comparison.storePrices.isEmpty()) {
          Text(
            text = "No store prices recorded yet for this item.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          comparison.storePrices.forEach { quote ->
            val isLatest = comparison.latestStorePrice?.id == quote.id
            val isLowest = comparison.lowestStorePrice?.id == quote.id
            val formattedPrice = formatMoneyUseCase.format(
              Money.fromMinorUnits(quote.unitPriceMinor, quote.currencyCode)
            )
            OceanCard(
              modifier = Modifier.testTag("store_price_quote_${quote.id}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
                  ) {
                    Text(
                      text = quote.storeName,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                    )
                    if (isLatest) {
                      OceanStatusBadge(text = "LATEST", tone = BadgeTone.INFO)
                    }
                    if (isLowest) {
                      OceanStatusBadge(text = "LOWEST", tone = BadgeTone.POSITIVE)
                    }
                  }
                  Text(
                    text = "$formattedPrice / ${item.unit} • ${formatShoppingTimestamp(quote.recordedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  quote.notes?.let { n ->
                    Text(
                      text = n,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                IconButton(
                  onClick = { onDeletePrice(quote.id) },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_store_price_${quote.id}"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete store quote",
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Done") }
    },
  )
}

@Composable
fun ConfirmDeleteShoppingEntityDialog(
  title: String,
  message: String,
  confirmTag: String,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.testTag(confirmTag),
      ) {
        Text("Delete")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
