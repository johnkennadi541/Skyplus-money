package com.example.feature.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.LoanType
import com.example.domain.model.TenureUnit
import com.example.domain.usecase.EmiCalculationEngine
import com.example.domain.usecase.FormatMoneyUseCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanFormDialog(
  title: String,
  availableCurrencies: List<String>,
  initialName: String = "",
  initialLender: String = "",
  initialType: LoanType = LoanType.PERSONAL,
  initialPrincipal: String = "",
  initialCurrency: String = "USD",
  initialRate: String = "8.5",
  initialTenureMonths: String = "12",
  initialAlreadyPaid: String = "0",
  initialNotes: String = "",
  isEditMode: Boolean = false,
  calculationEngine: EmiCalculationEngine = remember { EmiCalculationEngine() },
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    lenderName: String,
    loanType: LoanType,
    principalDecimal: String,
    currencyCode: String,
    annualRateText: String,
    tenureMonths: Int,
    alreadyPaidDecimal: String,
    notes: String,
  ) -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  var lender by remember { mutableStateOf(initialLender) }
  var loanType by remember { mutableStateOf(initialType) }
  var principal by remember { mutableStateOf(initialPrincipal) }
  var currency by remember { mutableStateOf(initialCurrency) }
  var rate by remember { mutableStateOf(initialRate) }
  var tenureText by remember { mutableStateOf(initialTenureMonths) }
  var tenureUnit by remember { mutableStateOf(TenureUnit.MONTHS) }
  var alreadyPaid by remember { mutableStateOf(initialAlreadyPaid) }
  var notes by remember { mutableStateOf(initialNotes) }
  var validationError by remember { mutableStateOf<String?>(null) }

  var typeExpanded by remember { mutableStateOf(false) }
  var currencyExpanded by remember { mutableStateOf(false) }

  val previewCalc = remember(principal, rate, tenureText, tenureUnit, currency) {
    calculationEngine.calculateEmiFromDecimal(
      principalDecimal = principal,
      annualInterestRateText = rate,
      tenureValueText = tenureText,
      tenureUnit = tenureUnit,
      currencyCode = currency,
    ).getOrNull()
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
      ) {
        if (validationError != null) {
          Surface(
            color = OceanTheme.colors.negativeContainer,
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = validationError ?: "",
              color = OceanTheme.colors.negative,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(OceanSpacing.xs),
            )
          }
        }

        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Loan Name") },
          placeholder = { Text("e.g., Home Mortgage") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("loan_name_input"),
        )

        OutlinedTextField(
          value = lender,
          onValueChange = { lender = it },
          label = { Text("Lender / Institution") },
          placeholder = { Text("e.g., HDFC Bank / Chase") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("loan_lender_input"),
        )

        ExposedDropdownMenuBox(
          expanded = typeExpanded,
          onExpandedChange = { typeExpanded = !typeExpanded },
        ) {
          OutlinedTextField(
            value = loanType.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Loan Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
            modifier =
              Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("loan_type_dropdown"),
          )
          ExposedDropdownMenu(
            expanded = typeExpanded,
            onDismissRequest = { typeExpanded = false },
          ) {
            LoanType.entries.forEach { type ->
              DropdownMenuItem(
                text = { Text(type.displayName) },
                onClick = {
                  loanType = type
                  typeExpanded = false
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
            value = principal,
            onValueChange = { principal = it },
            label = { Text("Principal") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(2f).testTag("loan_principal_input"),
          )
          ExposedDropdownMenuBox(
            expanded = currencyExpanded,
            onExpandedChange = { currencyExpanded = !currencyExpanded },
            modifier = Modifier.weight(1f),
          ) {
            OutlinedTextField(
              value = currency,
              onValueChange = {},
              readOnly = true,
              label = { Text("Currency") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
              expanded = currencyExpanded,
              onDismissRequest = { currencyExpanded = false },
            ) {
              availableCurrencies.forEach { curr ->
                DropdownMenuItem(
                  text = { Text(curr) },
                  onClick = {
                    currency = curr
                    currencyExpanded = false
                  },
                )
              }
            }
          }
        }

        OutlinedTextField(
          value = rate,
          onValueChange = { rate = it },
          label = { Text("Annual Interest Rate (%)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("loan_rate_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          OutlinedTextField(
            value = tenureText,
            onValueChange = { tenureText = it },
            label = { Text("Tenure") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("loan_tenure_input"),
          )
          FilterChip(
            selected = tenureUnit == TenureUnit.MONTHS,
            onClick = { tenureUnit = TenureUnit.MONTHS },
            label = { Text("Mo") },
          )
          FilterChip(
            selected = tenureUnit == TenureUnit.YEARS,
            onClick = { tenureUnit = TenureUnit.YEARS },
            label = { Text("Yr") },
          )
        }

        if (!isEditMode) {
          OutlinedTextField(
            value = alreadyPaid,
            onValueChange = { alreadyPaid = it },
            label = { Text("Principal Already Paid (Optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("loan_paid_input"),
          )
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          singleLine = false,
          maxLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )

        if (previewCalc != null) {
          Surface(
            color = OceanTheme.colors.cardSubtle,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(OceanSpacing.xs)) {
              Text(
                text = "Estimated Monthly EMI: ${FormatMoneyUseCase().format(previewCalc.monthlyEmiMoney)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
              )
              Text(
                text = "Total Interest: ${FormatMoneyUseCase().format(previewCalc.totalInterestMoney)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isBlank()) {
            validationError = "Loan name is required"
            return@Button
          }
          if (lender.isBlank()) {
            validationError = "Lender name is required"
            return@Button
          }
          val calc =
            calculationEngine.calculateEmiFromDecimal(
              principalDecimal = principal,
              annualInterestRateText = rate,
              tenureValueText = tenureText,
              tenureUnit = tenureUnit,
              currencyCode = currency,
            )
          if (calc.isFailure) {
            validationError = calc.exceptionOrNull()?.message ?: "Invalid loan parameters"
            return@Button
          }
          val months = calc.getOrThrow().tenureMonths
          validationError = null
          onSave(
            name.trim(),
            lender.trim(),
            loanType,
            principal.trim(),
            currency,
            rate.trim().ifEmpty { "0" },
            months,
            alreadyPaid.trim().ifEmpty { "0" },
            notes.trim(),
          )
        },
        modifier = Modifier.testTag("save_loan_button"),
      ) {
        Text("Save Loan")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}
