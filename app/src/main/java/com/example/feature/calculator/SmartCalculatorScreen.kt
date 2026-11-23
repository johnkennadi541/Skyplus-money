package com.example.feature.calculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.common.SmartCalculatorEngine
import java.math.BigDecimal

@Composable
fun SmartCalculatorScreen(
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)
  var expression by remember { mutableStateOf("") }
  var result by remember { mutableStateOf<BigDecimal?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  val keys = listOf("AC", "(", ")", "÷", "7", "8", "9", "×", "4", "5", "6", "−", "1", "2", "3", "+", "%", "0", ".", "=")

  Column(
    modifier = modifier.fillMaxSize().padding(20.dp).testTag("smart_calculator_screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onNavigateBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
      }
      Column(modifier = Modifier.weight(1f)) {
        Text("Smart Calculator", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("One calculator for your money tasks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Icon(Icons.Rounded.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
    Card(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(expression.ifBlank { "0" }, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.End)
        Text(
          result?.toPlainString() ?: "—",
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag("smart_calculator_result"),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      keys.chunked(4).forEach { rowKeys ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          rowKeys.forEach { key ->
            val action = {
              when (key) {
                "AC" -> { expression = ""; result = null; error = null }
                "=" -> try {
                  result = SmartCalculatorEngine.evaluate(expression)
                  error = null
                } catch (failure: IllegalArgumentException) {
                  result = null
                  error = failure.message ?: "Invalid calculation"
                }
                "−" -> expression += "-"
                "±" -> expression = if (expression.isBlank()) "-" else "-($expression)"
                else -> expression += key
              }
            }
            if (key == "=") {
              Button(onClick = action, modifier = Modifier.weight(1f).height(56.dp).testTag("smart_calculator_equals")) {
                Text(key)
              }
            } else {
              OutlinedButton(onClick = action, modifier = Modifier.weight(1f).height(56.dp)) {
                Text(key)
              }
            }
          }
          repeat(4 - rowKeys.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
      }
    }
    OutlinedButton(
      onClick = { expression = ""; result = null; error = null },
      modifier = Modifier.fillMaxWidth().height(54.dp).testTag("smart_calculator_clear_button"),
    ) {
      Text("Clear calculation")
    }
    Text(
      "Standalone calculator. Amount-field integration and custom settings are next.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}


/**
 * Shared calculator dialog for monetary inputs. Applying a result only returns a
 * decimal string to the caller; it never persists or posts a transaction.
 */
@Composable
fun SmartCalculatorAmountDialog(
  initialExpression: String,
  onApply: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var expression by remember(initialExpression) { mutableStateOf(initialExpression) }
  var result by remember { mutableStateOf<BigDecimal?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  val keys = listOf("AC", "(", ")", "÷", "7", "8", "9", "×", "4", "5", "6", "−", "1", "2", "3", "+", "%", "0", ".", "=")

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Calculate amount") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(expression.ifBlank { "0" }, style = MaterialTheme.typography.titleLarge)
        Text(
          result?.stripTrailingZeros()?.toPlainString() ?: "—",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.testTag("shared_calculator_result"),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        keys.chunked(4).forEach { rowKeys ->
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rowKeys.forEach { key ->
              androidx.compose.material3.TextButton(
                onClick = {
                  when (key) {
                    "AC" -> { expression = ""; result = null; error = null }
                    "=" -> try {
                      result = SmartCalculatorEngine.evaluate(expression)
                      error = null
                    } catch (failure: IllegalArgumentException) {
                      result = null
                      error = failure.message ?: "Invalid calculation"
                    }
                    "−" -> expression += "-"
                    else -> expression += key
                  }
                },
                modifier = Modifier.weight(1f).testTag("shared_calculator_key_$key"),
              ) { Text(key) }
            }
            repeat(4 - rowKeys.size) { Spacer(Modifier.weight(1f)) }
          }
        }
      }
    },
    confirmButton = {
      androidx.compose.material3.TextButton(
        enabled = result != null && result!! > BigDecimal.ZERO,
        onClick = { onApply(result!!.stripTrailingZeros().toPlainString()) },
      ) { Text("Use amount") }
    },
    dismissButton = {
      androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
