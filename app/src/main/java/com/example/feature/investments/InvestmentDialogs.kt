package com.example.feature.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.InvestmentTransactionType
import com.example.domain.model.MarketSyncSettings
import com.example.domain.model.Money
import com.example.domain.model.ProviderSupportLevel
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.domain.usecase.InvestmentCalculationEngine
import com.example.feature.calculator.SmartCalculatorAmountDialog
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatDateIso(millis: Long): String {
  val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
  return fmt.format(Date(millis))
}

internal fun parseDateIsoOrNull(text: String): Long? {
  val trimmed = text.trim()
  if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(trimmed)) return null
  val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
  val parsed = runCatching { fmt.parse(trimmed) }.getOrNull() ?: return null
  // Store midday timestamp to avoid timezone day-shift boundary issues
  return parsed.time + (12L * 60L * 60L * 1000L)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvestmentFormDialog(
  existingHolding: InvestmentHolding?,
  defaultCurrency: String,
  availableCurrencies: List<String>,
  calculationEngine: InvestmentCalculationEngine,
  onDismiss: () -> Unit,
  onCreateSubmit: (
    name: String,
    category: InvestmentCategory,
    symbolOrTicker: String?,
    isin: String?,
    brokerOrPlatform: String?,
    currencyCode: String,
    purchaseDate: Long,
    quantityDecimal: String,
    purchasePriceDecimal: String,
    chargesDecimal: String,
    currentMarketPriceDecimal: String?,
    autoPriceUpdateEnabled: Boolean,
    notes: String?,
  ) -> Unit,
  onEditSubmit: (InvestmentHolding) -> Unit,
) {
  val isEditing = existingHolding != null
  var name by remember(existingHolding) { mutableStateOf(existingHolding?.name ?: "") }
  var selectedCategory by remember(existingHolding) {
    mutableStateOf(existingHolding?.category ?: InvestmentCategory.STOCKS)
  }
  var symbolOrTicker by remember(existingHolding) {
    mutableStateOf(existingHolding?.symbolOrTicker ?: "")
  }
  var isin by remember(existingHolding) { mutableStateOf(existingHolding?.isin ?: "") }
  var broker by remember(existingHolding) {
    mutableStateOf(existingHolding?.brokerOrPlatform ?: "")
  }
  var selectedCurrency by remember(existingHolding) {
    mutableStateOf(existingHolding?.currencyCode ?: defaultCurrency)
  }
  var dateText by remember(existingHolding) {
    mutableStateOf(formatDateIso(existingHolding?.purchaseDate ?: System.currentTimeMillis()))
  }
  var quantityText by remember { mutableStateOf("") }
  var purchasePriceText by remember { mutableStateOf("") }
  var chargesText by remember { mutableStateOf("0") }
  var currentMarketPriceText by remember { mutableStateOf("") }
  var autoPriceOptIn by remember(existingHolding) {
    mutableStateOf(existingHolding?.autoPriceUpdateEnabled ?: false)
  }
  var notes by remember(existingHolding) { mutableStateOf(existingHolding?.notes ?: "") }
  var validationError by remember { mutableStateOf<String?>(null) }
  var calculatorTarget by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (isEditing) "Edit Investment Details" else "Add Investment Holding",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        Text(
          text = "Asset Category",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          InvestmentCategory.entries.forEach { category ->
            FilterChip(
              selected = selectedCategory == category,
              onClick = {
                selectedCategory = category
                if (category != InvestmentCategory.MUTUAL_FUNDS) {
                  autoPriceOptIn = false
                }
              },
              label = { Text(category.displayName) },
              modifier = Modifier.testTag("category_chip_${category.name.lowercase()}"),
            )
          }
        }

        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Investment Name *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("investment_name_input"),
        )

        OutlinedTextField(
          value = symbolOrTicker,
          onValueChange = { symbolOrTicker = it },
          label = {
            Text(
              if (selectedCategory == InvestmentCategory.MUTUAL_FUNDS) {
                "AMFI Scheme Code / Symbol (optional)"
              } else {
                "Symbol / Ticker (optional)"
              }
            )
          },
          supportingText =
            if (selectedCategory == InvestmentCategory.MUTUAL_FUNDS) {
              { Text("Optional: 5–7 digit AMFI code (e.g. 119598) enables opt-in EOD NAV sync.") }
            } else {
              null
            },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("investment_symbol_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          OutlinedTextField(
            value = isin,
            onValueChange = { isin = it },
            label = { Text("ISIN (optional)") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("investment_isin_input"),
          )
          OutlinedTextField(
            value = broker,
            onValueChange = { broker = it },
            label = { Text("Broker / Platform") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("investment_broker_input"),
          )
        }

        if (!isEditing) {
          Text(
            text = "Currency (ISO 4217)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            availableCurrencies.forEach { curr ->
              FilterChip(
                selected = selectedCurrency == curr,
                onClick = { selectedCurrency = curr },
                label = { Text(curr) },
                modifier = Modifier.testTag("investment_currency_chip_$curr"),
              )
            }
          }
        }

        OutlinedTextField(
          value = dateText,
          onValueChange = { dateText = it },
          label = { Text("Purchase Date (YYYY-MM-DD) *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("investment_date_input"),
        )

        if (!isEditing) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          ) {
            OutlinedTextField(
              value = quantityText,
              onValueChange = { quantityText = it },
              label = { Text("Units / Qty *") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("investment_quantity_input"),
            )
            OutlinedTextField(
              value = purchasePriceText,
              onValueChange = { purchasePriceText = it },
              label = { Text("Buy Price *") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              trailingIcon = {
                IconButton(onClick = { calculatorTarget = "buy" }, modifier = Modifier.testTag("investment_buy_price_calculator_button")) {
                  Icon(Icons.Rounded.Calculate, contentDescription = "Calculate buy price")
                }
              },
              modifier = Modifier.weight(1f).testTag("investment_buy_price_input"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          ) {
            OutlinedTextField(
              value = chargesText,
              onValueChange = { chargesText = it },
              label = { Text("Charges ($selectedCurrency)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              trailingIcon = {
                IconButton(onClick = { calculatorTarget = "charges" }, modifier = Modifier.testTag("investment_charges_calculator_button")) {
                  Icon(Icons.Rounded.Calculate, contentDescription = "Calculate investment charges")
                }
              },
              modifier = Modifier.weight(1f).testTag("investment_charges_input"),
            )
            OutlinedTextField(
              value = currentMarketPriceText,
              onValueChange = { currentMarketPriceText = it },
              label = { Text("Market Price (opt)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              trailingIcon = {
                IconButton(onClick = { calculatorTarget = "market" }, modifier = Modifier.testTag("investment_market_price_calculator_button")) {
                  Icon(Icons.Rounded.Calculate, contentDescription = "Calculate market price")
                }
              },
              modifier = Modifier.weight(1f).testTag("investment_market_price_input"),
            )
          }
        }

        if (selectedCategory == InvestmentCategory.MUTUAL_FUNDS) {
          Row(
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = autoPriceOptIn,
              onCheckedChange = { autoPriceOptIn = it },
              modifier = Modifier.testTag("investment_auto_nav_checkbox"),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Allow opt-in EOD NAV updates from AMFI / MFapi.in (requires global sync consent)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (optional)") },
          maxLines = 2,
          modifier = Modifier.fillMaxWidth().testTag("investment_notes_input"),
        )

        calculatorTarget?.let { target ->
          val initialExpression = when (target) {
            "buy" -> purchasePriceText
            "charges" -> chargesText
            else -> currentMarketPriceText
          }
          SmartCalculatorAmountDialog(
            initialExpression = initialExpression,
            decimalScale = Money.resolveFractionDigits(selectedCurrency),
            onApply = { calculatedAmount ->
              when (target) {
                "buy" -> purchasePriceText = calculatedAmount
                "charges" -> chargesText = calculatedAmount
                else -> currentMarketPriceText = calculatedAmount
              }
              calculatorTarget = null
            },
            onDismiss = { calculatorTarget = null },
          )
        }

        if (validationError != null) {
          Text(
            text = validationError!!,
            style = MaterialTheme.typography.bodySmall,
            color = OceanTheme.colors.negative,
            modifier = Modifier.testTag("investment_form_error"),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val trimmedName = name.trim()
          val parsedDate = parseDateIsoOrNull(dateText)
          if (trimmedName.isEmpty()) {
            validationError = "Investment name is required"
            return@Button
          }
          if (parsedDate == null) {
            validationError = "Enter a valid date in YYYY-MM-DD format"
            return@Button
          }

          if (isEditing && existingHolding != null) {
            onEditSubmit(
              existingHolding.copy(
                name = trimmedName,
                category = selectedCategory,
                symbolOrTicker = symbolOrTicker.trim().ifEmpty { null },
                isin = isin.trim().uppercase().ifEmpty { null },
                brokerOrPlatform = broker.trim().ifEmpty { null },
                purchaseDate = parsedDate,
                autoPriceUpdateEnabled =
                  autoPriceOptIn && selectedCategory == InvestmentCategory.MUTUAL_FUNDS,
                notes = notes.trim().ifEmpty { null },
              )
            )
            return@Button
          }

          val validateResult =
            runCatching {
              calculationEngine.parsePositiveDecimal(quantityText, "Units / quantity")
              calculationEngine.parsePositiveDecimal(purchasePriceText, "Purchase price")
              val chargesBd =
                calculationEngine.parseNonNegativeDecimal(chargesText, "Transaction charges")
              Money.fromBigDecimal(chargesBd, selectedCurrency, RoundingMode.HALF_UP)
              if (currentMarketPriceText.isNotBlank()) {
                calculationEngine.parsePositiveDecimal(
                  currentMarketPriceText,
                  "Current market price",
                )
              }
            }

          validateResult.fold(
            onSuccess = {
              onCreateSubmit(
                trimmedName,
                selectedCategory,
                symbolOrTicker.trim().ifEmpty { null },
                isin.trim().uppercase().ifEmpty { null },
                broker.trim().ifEmpty { null },
                selectedCurrency,
                parsedDate,
                quantityText.trim(),
                purchasePriceText.trim(),
                chargesText.trim().ifEmpty { "0" },
                currentMarketPriceText.trim().ifEmpty { null },
                autoPriceOptIn && selectedCategory == InvestmentCategory.MUTUAL_FUNDS,
                notes.trim().ifEmpty { null },
              )
            },
            onFailure = { err ->
              validationError = err.message ?: "Check the investment values entered"
            },
          )
        },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("submit_investment_button"),
      ) {
        Text(if (isEditing) "Save Changes" else "Save Investment")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("cancel_investment_dialog"),
      ) {
        Text("Cancel")
      }
    },
  )
}

@Composable
fun RecordTradeDialog(
  holding: InvestmentHolding,
  initialType: InvestmentTransactionType,
  calculationEngine: InvestmentCalculationEngine,
  onDismiss: () -> Unit,
  onSubmit: (
    type: InvestmentTransactionType,
    transactionDate: Long,
    quantityDecimal: String,
    unitPriceDecimal: String,
    chargesDecimal: String,
    notes: String?,
  ) -> Unit,
) {
  var tradeType by remember(initialType) { mutableStateOf(initialType) }
  var dateText by remember { mutableStateOf(formatDateIso(System.currentTimeMillis())) }
  var quantityText by remember { mutableStateOf("") }
  var unitPriceText by remember(holding) { mutableStateOf(holding.currentMarketPriceDecimal) }
  var chargesText by remember { mutableStateOf("0") }
  var notes by remember { mutableStateOf("") }
  var validationError by remember { mutableStateOf<String?>(null) }
  var calculatorTarget by remember { mutableStateOf<String?>(null) }

  val livePreview =
    remember(tradeType, quantityText, unitPriceText, chargesText) {
      runCatching {
        val qty = calculationEngine.parsePositiveDecimal(quantityText, "Quantity")
        val price = calculationEngine.parseNonNegativeDecimal(unitPriceText, "Unit price")
        val charges =
          Money.fromBigDecimal(
            calculationEngine.parseNonNegativeDecimal(chargesText, "Charges"),
            holding.currencyCode,
            RoundingMode.HALF_UP,
          )
        val grossMinor = calculationEngine.computeValuationMinor(qty, price, holding.currencyCode)
        val netMinor =
          if (tradeType == InvestmentTransactionType.BUY) Math.addExact(grossMinor, charges.minorUnits)
          else Math.subtractExact(grossMinor, charges.minorUnits)
        Money.fromMinorUnits(netMinor, holding.currencyCode)
      }.getOrNull()
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text =
          if (tradeType == InvestmentTransactionType.BUY) "Record Additional Purchase"
          else "Record Partial / Complete Sale",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
          FilterChip(
            selected = tradeType == InvestmentTransactionType.BUY,
            onClick = { tradeType = InvestmentTransactionType.BUY },
            label = { Text("Buy Units") },
            modifier = Modifier.testTag("trade_type_buy_chip"),
          )
          FilterChip(
            selected = tradeType == InvestmentTransactionType.SELL,
            onClick = { tradeType = InvestmentTransactionType.SELL },
            label = { Text("Sell Units") },
            modifier = Modifier.testTag("trade_type_sell_chip"),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Held Units: ${holding.quantityDecimal}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (tradeType == InvestmentTransactionType.SELL) {
            TextButton(
              onClick = { quantityText = holding.quantityDecimal },
              modifier = Modifier.testTag("sell_all_units_button"),
            ) {
              Text("Sell All")
            }
          }
        }

        OutlinedTextField(
          value = dateText,
          onValueChange = { dateText = it },
          label = { Text("Trade Date (YYYY-MM-DD) *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("trade_date_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          OutlinedTextField(
            value = quantityText,
            onValueChange = { quantityText = it },
            label = { Text("Quantity *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("trade_quantity_input"),
          )
          OutlinedTextField(
            value = unitPriceText,
            onValueChange = { unitPriceText = it },
            label = { Text("Unit Price *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            trailingIcon = {
              IconButton(onClick = { calculatorTarget = "price" }, modifier = Modifier.testTag("trade_price_calculator_button")) {
                Icon(Icons.Rounded.Calculate, contentDescription = "Calculate unit price")
              }
            },
            modifier = Modifier.weight(1f).testTag("trade_price_input"),
          )
        }

        OutlinedTextField(
          value = chargesText,
          onValueChange = { chargesText = it },
          label = { Text("Transaction Charges (${holding.currencyCode})") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          trailingIcon = {
            IconButton(onClick = { calculatorTarget = "charges" }, modifier = Modifier.testTag("trade_charges_calculator_button")) {
              Icon(Icons.Rounded.Calculate, contentDescription = "Calculate trade charges")
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("trade_charges_input"),
        )

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("trade_notes_input"),
        )
        if (calculatorTarget != null) {
          SmartCalculatorAmountDialog(
            initialExpression = if (calculatorTarget == "price") unitPriceText else chargesText,
            decimalScale = Money.resolveFractionDigits(holding.currencyCode),
            onApply = { calculatedAmount ->
              if (calculatorTarget == "price") unitPriceText = calculatedAmount else chargesText = calculatedAmount
              calculatorTarget = null
            },
            onDismiss = { calculatorTarget = null },
          )
        }

        if (livePreview != null) {
          Surface(
            color = OceanTheme.colors.cardSubtle,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text =
                if (tradeType == InvestmentTransactionType.BUY) {
                  "Total Buy Cost (incl. charges): ${FormatMoneyUseCase().format(livePreview)}"
                } else {
                  "Net Sale Proceeds (after charges): ${FormatMoneyUseCase().format(livePreview)}"
                },
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(OceanSpacing.xs),
            )
          }
        }

        if (validationError != null) {
          Text(
            text = validationError!!,
            style = MaterialTheme.typography.bodySmall,
            color = OceanTheme.colors.negative,
            modifier = Modifier.testTag("trade_form_error"),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedDate = parseDateIsoOrNull(dateText)
          if (parsedDate == null) {
            validationError = "Enter a valid trade date (YYYY-MM-DD)"
            return@Button
          }
          val check =
            runCatching {
              val qty = calculationEngine.parsePositiveDecimal(quantityText, "Quantity")
              if (tradeType == InvestmentTransactionType.BUY) {
                calculationEngine.parsePositiveDecimal(unitPriceText, "Unit price")
              } else {
                val held = BigDecimal(holding.quantityDecimal)
                require(qty <= held) {
                  "Sale quantity exceeds held units (${holding.quantityDecimal})"
                }
                calculationEngine.parseNonNegativeDecimal(unitPriceText, "Sale price")
              }
              val chargesBd = calculationEngine.parseNonNegativeDecimal(chargesText, "Charges")
              Money.fromBigDecimal(chargesBd, holding.currencyCode, RoundingMode.HALF_UP)
            }
          check.fold(
            onSuccess = {
              onSubmit(
                tradeType,
                parsedDate,
                quantityText.trim(),
                unitPriceText.trim(),
                chargesText.trim().ifEmpty { "0" },
                notes.trim().ifEmpty { null },
              )
            },
            onFailure = { err -> validationError = err.message ?: "Invalid trade input" },
          )
        },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("submit_trade_button"),
      ) {
        Text(if (tradeType == InvestmentTransactionType.BUY) "Record Buy" else "Record Sale")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("cancel_trade_dialog"),
      ) {
        Text("Cancel")
      }
    },
  )
}

@Composable
fun UpdateMarketPriceDialog(
  holding: InvestmentHolding,
  calculationEngine: InvestmentCalculationEngine,
  onDismiss: () -> Unit,
  onSubmit: (newPriceDecimal: String, priceDate: Long) -> Unit,
) {
  var priceText by remember(holding) { mutableStateOf(holding.currentMarketPriceDecimal) }
  var dateText by remember { mutableStateOf(formatDateIso(System.currentTimeMillis())) }
  var errorText by remember { mutableStateOf<String?>(null) }
  var showPriceCalculator by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Update Manual Market Price") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
        Text(
          text =
            "Manual prices always work offline and are never overwritten by automatic updates without your explicit consent.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = priceText,
          onValueChange = { priceText = it },
          label = { Text("Current Price per Unit (${holding.currencyCode}) *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          trailingIcon = {
            IconButton(onClick = { showPriceCalculator = true }, modifier = Modifier.testTag("manual_price_calculator_button")) {
              Icon(Icons.Rounded.Calculate, contentDescription = "Calculate manual market price")
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("manual_price_input"),
        )
        if (showPriceCalculator) {
          SmartCalculatorAmountDialog(
            initialExpression = priceText,
            decimalScale = Money.resolveFractionDigits(holding.currencyCode),
            onApply = { calculatedAmount ->
              priceText = calculatedAmount
              showPriceCalculator = false
            },
            onDismiss = { showPriceCalculator = false },
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text("Price Date (YYYY-MM-DD) *") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("manual_price_date_input"),
          )
          OutlinedButton(
            onClick = { dateText = formatDateIso(System.currentTimeMillis()) },
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Text("Today")
          }
        }
        if (errorText != null) {
          Text(
            text = errorText!!,
            style = MaterialTheme.typography.bodySmall,
            color = OceanTheme.colors.negative,
            modifier = Modifier.testTag("manual_price_error"),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedDate = parseDateIsoOrNull(dateText)
          if (parsedDate == null) {
            errorText = "Enter a valid price date (YYYY-MM-DD)"
            return@Button
          }
          runCatching {
            calculationEngine.parsePositiveDecimal(priceText, "Market price")
          }.fold(
            onSuccess = { onSubmit(priceText.trim(), parsedDate) },
            onFailure = { err -> errorText = err.message ?: "Enter a valid positive price" },
          )
        },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("submit_manual_price_button"),
      ) {
        Text("Update Price")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("cancel_manual_price_dialog"),
      ) {
        Text("Cancel")
      }
    },
  )
}

@Composable
fun MarketSyncSettingsDialog(
  syncSettings: MarketSyncSettings,
  isSyncing: Boolean,
  onToggleAutoConsent: (Boolean) -> Unit,
  onToggleWifiOnly: (Boolean) -> Unit,
  onSyncNow: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Optional Market Price Feeds") },
    text = {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        Surface(
          color = OceanTheme.colors.cardSubtle,
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, OceanTheme.colors.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(OceanSpacing.sm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Opt-In EOD Market Updates",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "Disabled by default. Only opted-in Mutual Funds with AMFI scheme codes fetch EOD NAVs.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = syncSettings.autoUpdateConsentEnabled,
                onCheckedChange = onToggleAutoConsent,
                modifier = Modifier.testTag("auto_sync_consent_switch"),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Background Sync on Unmetered (Wi-Fi) Only",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
              )
              Switch(
                checked = syncSettings.syncOnlyOnWifi,
                onCheckedChange = onToggleWifiOnly,
                enabled = syncSettings.autoUpdateConsentEnabled,
                modifier = Modifier.testTag("wifi_only_sync_switch"),
              )
            }

            Button(
              onClick = onSyncNow,
              enabled = !isSyncing && syncSettings.autoUpdateConsentEnabled,
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("sync_market_prices_now_button"),
            ) {
              Text(if (isSyncing) "Syncing Eligible Funds…" else "Sync Eligible Mutual Funds Now")
            }

            if (syncSettings.lastSyncStatusMessage != null) {
              Text(
                text = "Last status: ${syncSettings.lastSyncStatusMessage}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        Text(
          text = "Provider & Licensing Transparency",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )

        InvestmentCalculationEngine.PROVIDER_POLICIES.forEach { policy ->
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, OceanTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(OceanSpacing.xs), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = policy.category.displayName,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                )
                OceanStatusBadge(
                  text =
                    if (policy.supportLevel == ProviderSupportLevel.FREE_OPT_IN_PUBLIC_FEED) {
                      "OPT-IN FEED"
                    } else {
                      "MANUAL ONLY"
                    },
                  tone =
                    if (policy.supportLevel == ProviderSupportLevel.FREE_OPT_IN_PUBLIC_FEED) {
                      BadgeTone.POSITIVE
                    } else {
                      BadgeTone.INFO
                    },
                )
              }
              Text(
                text = policy.providerName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = policy.summaryReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("close_market_sync_dialog"),
      ) {
        Text("Done")
      }
    },
  )
}
