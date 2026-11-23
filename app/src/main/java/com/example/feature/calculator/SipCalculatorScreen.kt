package com.example.feature.calculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.domain.usecase.SipCalculationEngine
import com.example.domain.usecase.SipCalculationResult

@Composable
fun SipCalculatorScreen(
  defaultCurrency: String = "INR",
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)
  val engine = remember { SipCalculationEngine() }
  var monthlyInvestment by remember { mutableStateOf("500") }
  var annualRate by remember { mutableStateOf("12") }
  var tenureYears by remember { mutableStateOf("10") }
  var currencyCode by remember { mutableStateOf(defaultCurrency) }
  var showAmountCalculator by remember { mutableStateOf(false) }

  val tenureMonths = tenureYears.toIntOrNull()?.let { runCatching { Math.multiplyExact(it, 12) }.getOrNull() } ?: 0
  val outcome = remember(monthlyInvestment, annualRate, tenureMonths, currencyCode) {
    engine.calculate(monthlyInvestment, annualRate, tenureMonths, currencyCode)
  }
  val result: SipCalculationResult? = outcome.getOrNull()
  val error = outcome.exceptionOrNull()?.message

  Column(
    modifier = modifier.fillMaxSize().padding(20.dp).testTag("sip_calculator_screen"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onNavigateBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
      }
      Column(modifier = Modifier.weight(1f)) {
        Text("SIP Calculator", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Estimate long-term monthly investment growth", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Icon(Icons.Rounded.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }

    OutlinedTextField(
      value = monthlyInvestment,
      onValueChange = { monthlyInvestment = it },
      label = { Text("Monthly investment") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      singleLine = true,
      trailingIcon = {
        IconButton(onClick = { showAmountCalculator = true }, modifier = Modifier.testTag("sip_amount_calculator_button")) {
          Icon(Icons.Rounded.Calculate, contentDescription = "Calculate monthly investment")
        }
      },
      modifier = Modifier.fillMaxWidth().testTag("sip_monthly_investment_input"),
    )
    OutlinedTextField(
      value = annualRate,
      onValueChange = { annualRate = it },
      label = { Text("Expected annual return (%)") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("sip_annual_rate_input"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = tenureYears,
        onValueChange = { tenureYears = it },
        label = { Text("Tenure (years)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("sip_tenure_input"),
      )
      OutlinedTextField(
        value = currencyCode,
        onValueChange = { currencyCode = it.trim().uppercase() },
        label = { Text("Currency") },
        singleLine = true,
        modifier = Modifier.width(112.dp).testTag("sip_currency_input"),
      )
    }

    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

    result?.let { value ->
      SipResultCard(value)
    }

    Text(
      "Illustrative estimate only. Actual returns vary with market performance, fees and taxes; returns are not guaranteed.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.weight(1f))
  }

  if (showAmountCalculator) {
    SmartCalculatorAmountDialog(
      initialExpression = monthlyInvestment,
      onApply = { calculatedAmount ->
        monthlyInvestment = calculatedAmount
        showAmountCalculator = false
      },
      onDismiss = { showAmountCalculator = false },
    )
  }
}

@Composable
private fun SipResultCard(result: SipCalculationResult) {
  val formatter = remember { FormatMoneyUseCase() }
  val invested = result.totalInvested.toBigDecimal()
  val maturity = result.maturityValue.toBigDecimal()
  val investedShare = if (maturity.signum() > 0) {
    invested.divide(maturity, 6, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
  } else 1f

  Card(modifier = Modifier.fillMaxWidth().testTag("sip_result_card")) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Estimated maturity value", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(
        formatter.format(result.maturityValue),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("sip_maturity_value"),
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Invested", style = MaterialTheme.typography.bodySmall)
        Text(formatter.format(result.totalInvested), fontWeight = FontWeight.SemiBold)
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Estimated returns", style = MaterialTheme.typography.bodySmall)
        Text(formatter.format(result.estimatedReturns), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
      }
      Box(
        modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Row(Modifier.fillMaxSize()) {
          Box(Modifier.weight(investedShare.coerceAtLeast(0.001f)).fillMaxSize().background(MaterialTheme.colorScheme.secondary))
          if (investedShare < 1f) {
            Box(Modifier.weight((1f - investedShare).coerceAtLeast(0.001f)).fillMaxSize().background(MaterialTheme.colorScheme.primary))
          }
        }
      }
      Text(
        "Monthly ${formatter.format(result.monthlyInvestment)} • ${result.tenureMonths / 12} years • ${result.annualReturnRatePercent}% p.a.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
