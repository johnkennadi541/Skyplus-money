package com.example.feature.loans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanNumericStyle
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.EmiCalculationResult
import com.example.domain.model.EmiPrefillDraft
import com.example.domain.model.TenureUnit
import com.example.domain.usecase.EmiCalculationEngine
import com.example.domain.usecase.FormatMoneyUseCase

@Composable
fun EmiCalculatorScreen(
  viewModel: LoansViewModel,
  onNavigateBack: () -> Unit,
  onSaveAsLoan: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val accounts by viewModel.accounts.collectAsStateWithLifecycle()
  val defaultCurrency = accounts.firstOrNull()?.currencyCode ?: "USD"

  EmiCalculatorContent(
    defaultCurrency = defaultCurrency,
    calculationEngine = viewModel.emiCalculationEngine,
    onNavigateBack = onNavigateBack,
    onSaveAsLoan = { draft ->
      viewModel.stageEmiDraftForLoanCreation(draft)
      onSaveAsLoan()
    },
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmiCalculatorContent(
  defaultCurrency: String = "USD",
  calculationEngine: EmiCalculationEngine = remember { EmiCalculationEngine() },
  onNavigateBack: () -> Unit = {},
  onSaveAsLoan: (EmiPrefillDraft) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)

  val currencies = remember(defaultCurrency) {
    (listOf(defaultCurrency) + listOf("USD", "INR", "EUR", "GBP", "AED", "SGD", "JPY", "KWD")).distinct()
  }

  var principalText by remember { mutableStateOf("10000") }
  var rateText by remember { mutableStateOf("8.5") }
  var tenureText by remember { mutableStateOf("24") }
  var tenureUnit by remember { mutableStateOf(TenureUnit.MONTHS) }
  var selectedCurrency by remember { mutableStateOf(defaultCurrency) }
  var currencyExpanded by remember { mutableStateOf(false) }

  val calcOutcome = remember(principalText, rateText, tenureText, tenureUnit, selectedCurrency) {
    calculationEngine.calculateEmiFromDecimal(
      principalDecimal = principalText,
      annualInterestRateText = rateText,
      tenureValueText = tenureText,
      tenureUnit = tenureUnit,
      currencyCode = selectedCurrency,
    )
  }

  val result: EmiCalculationResult? = calcOutcome.getOrNull()
  val errorMessage: String? = calcOutcome.exceptionOrNull()?.message

  Column(
    modifier = modifier.fillMaxSize().padding(OceanSpacing.md).testTag("emi_calculator_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(bottom = OceanSpacing.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onNavigateBack,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("emi_back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = stringResource(R.string.cd_navigate_back),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.emi_calculator_title),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = stringResource(R.string.emi_calculator_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(
        imageVector = Icons.Rounded.Calculate,
        contentDescription = null,
        tint = OceanTheme.colors.gold,
        modifier = Modifier.size(28.dp),
      )
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("emi_calculator_list"),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {

    item {
      OceanCard(modifier = Modifier.widthIn(max = 760.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
          Text(
            text = "Loan Parameters",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          ) {
            OutlinedTextField(
              value = principalText,
              onValueChange = { principalText = it },
              label = { Text("Principal Amount") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.weight(2f).testTag("emi_principal_input"),
            )

            ExposedDropdownMenuBox(
              expanded = currencyExpanded,
              onExpandedChange = { currencyExpanded = !currencyExpanded },
              modifier = Modifier.weight(1f),
            ) {
              OutlinedTextField(
                value = selectedCurrency,
                onValueChange = {},
                readOnly = true,
                label = { Text("Currency") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                modifier =
                  Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .testTag("emi_currency_selector"),
              )
              ExposedDropdownMenu(
                expanded = currencyExpanded,
                onDismissRequest = { currencyExpanded = false },
              ) {
                currencies.forEach { curr ->
                  DropdownMenuItem(
                    text = { Text(curr) },
                    onClick = {
                      selectedCurrency = curr
                      currencyExpanded = false
                    },
                  )
                }
              }
            }
          }

          OutlinedTextField(
            value = rateText,
            onValueChange = { rateText = it },
            label = { Text("Annual Interest Rate (% p.a., 0 for zero-interest)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("emi_rate_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedTextField(
              value = tenureText,
              onValueChange = { tenureText = it },
              label = { Text("Loan Tenure") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("emi_tenure_input"),
            )
            FilterChip(
              selected = tenureUnit == TenureUnit.MONTHS,
              onClick = { tenureUnit = TenureUnit.MONTHS },
              label = { Text("Months") },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("emi_unit_months"),
            )
            FilterChip(
              selected = tenureUnit == TenureUnit.YEARS,
              onClick = { tenureUnit = TenureUnit.YEARS },
              label = { Text("Years") },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("emi_unit_years"),
            )
          }
        }
      }
    }

    if (errorMessage != null) {
      item {
        Surface(
          modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).testTag("emi_error_banner"),
          shape = MaterialTheme.shapes.small,
          color = OceanTheme.colors.negativeContainer,
          border = BorderStroke(1.dp, OceanTheme.colors.negative),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(OceanSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = OceanTheme.colors.negative)
            Spacer(modifier = Modifier.width(OceanSpacing.xs))
            Text(
              text = errorMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = OceanTheme.colors.negative,
            )
          }
        }
      }
    }

    if (result != null) {
      item {
        OceanCard(modifier = Modifier.widthIn(max = 760.dp).testTag("emi_results_card")) {
          Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Estimated Monthly EMI",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              OceanStatusBadge(
                text = "${result.tenureMonths} Months",
                tone = BadgeTone.INFO,
              )
            }

            Text(
              text = FormatMoneyUseCase().format(result.monthlyEmiMoney),
              style = OceanNumericStyle.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize),
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.testTag("emi_monthly_result"),
            )

            HorizontalDivider(color = OceanTheme.colors.cardBorder)

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column {
                Text(
                  text = "Principal Amount",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = FormatMoneyUseCase().format(result.principalMoney),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "Total Interest",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = FormatMoneyUseCase().format(result.totalInterestMoney),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = OceanTheme.colors.gold,
                  modifier = Modifier.testTag("emi_total_interest_result"),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Total Repayment",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = FormatMoneyUseCase().format(result.totalRepaymentMoney),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("emi_total_repayment_result"),
              )
            }

            // Principal vs Interest Visual Breakdown Bar
            val principalWeight =
              (result.principalSharePercent.toString().toFloatOrNull() ?: 100f).coerceIn(1f, 100f)
            val interestWeight = (100f - principalWeight).coerceAtLeast(0f)

            Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.xxs)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Principal: ${result.principalSharePercent}%",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                  text = "Interest: ${result.interestSharePercent}%",
                  style = MaterialTheme.typography.labelSmall,
                  color = OceanTheme.colors.gold,
                )
              }
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
              ) {
                Box(
                  modifier =
                    Modifier.fillMaxHeight()
                      .weight(principalWeight)
                      .background(MaterialTheme.colorScheme.secondary)
                )
                if (interestWeight > 0f) {
                  Box(
                    modifier =
                      Modifier.fillMaxHeight()
                        .weight(interestWeight)
                        .background(OceanTheme.colors.gold)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(OceanSpacing.xs))

            Button(
              onClick = {
                onSaveAsLoan(
                  EmiPrefillDraft(
                    principalDecimal = result.principalMoney.toBigDecimal().toPlainString(),
                    annualRatePercent = result.annualInterestRatePercent.toPlainString(),
                    tenureMonths = result.tenureMonths,
                    currencyCode = result.principalMoney.currencyCode,
                  )
                )
              },
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("emi_save_as_loan_button"),
            ) {
              Icon(Icons.Rounded.AddCircleOutline, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Save Result as Loan")
            }
          }
        }
      }
    }
  }
  }
}
