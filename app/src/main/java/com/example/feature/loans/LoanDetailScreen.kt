package com.example.feature.loans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.domain.model.Loan
import com.example.domain.model.LoanPayment
import com.example.domain.model.LoanPaymentStatus
import com.example.domain.model.LoanStatus
import com.example.domain.model.Money
import com.example.domain.usecase.EmiCalculationEngine
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.feature.calculator.SmartCalculatorAmountDialog
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LoanDetailScreen(
  loanId: String,
  viewModel: LoansViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val detailFlow = remember(loanId) { viewModel.observeLoanDetail(loanId) }
  val detailPair by detailFlow.collectAsStateWithLifecycle(initialValue = null to emptyList())
  val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
  val isErrorMessage by viewModel.isErrorMessage.collectAsStateWithLifecycle()
  val accounts by viewModel.accounts.collectAsStateWithLifecycle()

  val availableCurrencies =
    remember(accounts, detailPair.first) {
      val curr = detailPair.first?.currencyCode
      (listOfNotNull(curr) + accounts.map { it.currencyCode } + listOf("USD", "INR", "EUR", "GBP")).distinct()
    }

  LoanDetailContent(
    loan = detailPair.first,
    payments = detailPair.second,
    availableCurrencies = availableCurrencies,
    userMessage = userMessage,
    isErrorMessage = isErrorMessage,
    calculationEngine = viewModel.emiCalculationEngine,
    onDismissMessage = { viewModel.dismissMessage() },
    onNavigateBack = onNavigateBack,
    onUpdateLoan = { updated, onSuccess ->
      viewModel.updateLoan(updated, onSuccess)
    },
    onRecordPayment = { loan, dateMillis, amountDecimal, notes, onSuccess ->
      viewModel.recordPayment(loan, dateMillis, amountDecimal, notes, onSuccess)
    },
    onMarkCompleted = { id ->
      viewModel.markLoanCompleted(id)
    },
    onDeleteLoan = { id ->
      viewModel.deleteLoan(id) { onNavigateBack() }
    },
    modifier = modifier,
  )
}

@Composable
fun LoanDetailContent(
  loan: Loan?,
  payments: List<LoanPayment>,
  availableCurrencies: List<String> = listOf("USD", "INR", "EUR", "GBP"),
  userMessage: String? = null,
  isErrorMessage: Boolean = false,
  calculationEngine: EmiCalculationEngine = remember { EmiCalculationEngine() },
  onDismissMessage: () -> Unit = {},
  onNavigateBack: () -> Unit = {},
  onUpdateLoan: (Loan, () -> Unit) -> Unit = { _, _ -> },
  onRecordPayment: (Loan, Long, String, String?, () -> Unit) -> Unit = { _, _, _, _, _ -> },
  onMarkCompleted: (String) -> Unit = {},
  onDeleteLoan: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)

  var showPaymentDialog by remember { mutableStateOf(false) }
  var showEditDialog by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }

  val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = OceanSpacing.md, vertical = OceanSpacing.sm)
        .testTag("loan_detail_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(bottom = OceanSpacing.xs),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        IconButton(
          onClick = onNavigateBack,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("loan_detail_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.cd_navigate_back),
          )
        }
        Text(
          text = loan?.name ?: "Loan Details",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }
      if (loan != null) {
        Row {
          IconButton(
            onClick = { showEditDialog = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_loan_button"),
          ) {
            Icon(Icons.Rounded.Edit, contentDescription = "Edit loan")
          }
          IconButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_loan_button"),
          ) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete loan", tint = OceanTheme.colors.negative)
          }
        }
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 760.dp).testTag("loan_detail_list"),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (userMessage != null) {
        item {
          Surface(
            modifier = Modifier.fillMaxWidth().testTag("loan_detail_user_message"),
            shape = MaterialTheme.shapes.small,
            color =
              if (isErrorMessage) OceanTheme.colors.negativeContainer
              else MaterialTheme.colorScheme.primaryContainer,
            border =
              BorderStroke(
                1.dp,
                if (isErrorMessage) OceanTheme.colors.negative else MaterialTheme.colorScheme.primary,
              ),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(OceanSpacing.sm),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                  imageVector = if (isErrorMessage) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = if (isErrorMessage) OceanTheme.colors.negative else MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(OceanSpacing.xs))
                Text(
                  text = userMessage,
                  style = MaterialTheme.typography.bodyMedium,
                  color =
                    if (isErrorMessage) OceanTheme.colors.negative
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
              TextButton(onClick = onDismissMessage) { Text("Dismiss") }
            }
          }
        }
      }

      if (loan == null) {
        item {
          OceanCard {
            Text(
              text = "Loan record not found or unavailable for this account.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        val statusTone =
          when (loan.status) {
            LoanStatus.ACTIVE -> BadgeTone.INFO
            LoanStatus.COMPLETED -> BadgeTone.POSITIVE
            LoanStatus.OVERDUE -> BadgeTone.NEGATIVE
          }
        val progress =
          if (loan.principalMinor > 0L) {
            ((loan.principalMinor - loan.outstandingPrincipalMinor).toFloat() / loan.principalMinor.toFloat())
              .coerceIn(0f, 1f)
          } else {
            0f
          }

        item {
          OceanCard(modifier = Modifier.testTag("loan_overview_card")) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "${loan.lenderName} • ${loan.loanType.displayName}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = "Outstanding Principal",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text =
                    FormatMoneyUseCase()
                      .format(Money.fromMinorUnits(loan.outstandingPrincipalMinor, loan.currencyCode)),
                  style = OceanNumericStyle.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize),
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.testTag("loan_outstanding_balance"),
                )
              }
              OceanStatusBadge(text = loan.status.displayName, tone = statusTone)
            }

            Spacer(modifier = Modifier.height(OceanSpacing.sm))
            LinearProgressIndicator(
              progress = { progress },
              modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
              color =
                if (loan.status == LoanStatus.COMPLETED) OceanTheme.colors.positive
                else MaterialTheme.colorScheme.secondary,
              trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.height(OceanSpacing.sm))

            DetailMetricRow(
              label = "Original Principal",
              value = FormatMoneyUseCase().format(Money.fromMinorUnits(loan.principalMinor, loan.currencyCode)),
            )
            DetailMetricRow(
              label = "Monthly EMI",
              value = FormatMoneyUseCase().format(Money.fromMinorUnits(loan.monthlyEmiMinor, loan.currencyCode)),
            )
            DetailMetricRow(
              label = "Annual Interest Rate",
              value = "${loan.annualInterestRatePercent}% p.a. (${loan.tenureMonths} months)",
            )
            DetailMetricRow(
              label = "Total Interest Payable",
              value = FormatMoneyUseCase().format(Money.fromMinorUnits(loan.totalInterestMinor, loan.currencyCode)),
            )
            DetailMetricRow(
              label = "Total Amount Payable",
              value = FormatMoneyUseCase().format(Money.fromMinorUnits(loan.totalPayableMinor, loan.currencyCode)),
            )
            DetailMetricRow(
              label = "Amount Already Paid",
              value = FormatMoneyUseCase().format(Money.fromMinorUnits(loan.amountPaidMinor, loan.currencyCode)),
            )
            DetailMetricRow(
              label = "Loan Start Date",
              value = dateFormatter.format(Date(loan.startDate)),
            )
            DetailMetricRow(
              label = "Next EMI Due Date",
              value =
                if (loan.status == LoanStatus.COMPLETED) "Completed"
                else dateFormatter.format(Date(loan.nextDueDate)),
            )
          }
        }

        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          ) {
            Button(
              onClick = { showPaymentDialog = true },
              enabled = loan.status != LoanStatus.COMPLETED && loan.outstandingPrincipalMinor > 0L,
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("record_payment_button"),
            ) {
              Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Record EMI")
            }
            if (loan.status != LoanStatus.COMPLETED && loan.outstandingPrincipalMinor == 0L) {
              OutlinedButton(
                onClick = { onMarkCompleted(loan.id) },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("mark_completed_button"),
              ) {
                Text("Mark Completed")
              }
            }
          }
        }

        // Payment History Header
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Payment History (${payments.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        if (payments.isEmpty()) {
          item {
            OceanCard(modifier = Modifier.testTag("payment_history_empty")) {
              Text(
                text = "No EMI payments recorded yet. Tap 'Record EMI' to log a payment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          items(payments, key = { it.id }) { payment ->
            OceanCard(modifier = Modifier.testTag("loan_payment_item_${payment.id}")) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text(
                    text = FormatMoneyUseCase().format(Money.fromMinorUnits(payment.amountMinor, payment.currencyCode)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = dateFormatter.format(Date(payment.paymentDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                OceanStatusBadge(
                  text = payment.status.displayName,
                  tone =
                    when (payment.status) {
                      LoanPaymentStatus.COMPLETED -> BadgeTone.POSITIVE
                      LoanPaymentStatus.PENDING -> BadgeTone.WARNING
                      LoanPaymentStatus.FAILED -> BadgeTone.NEGATIVE
                    },
                )
              }
              Spacer(modifier = Modifier.height(OceanSpacing.xs))
              HorizontalDivider(color = OceanTheme.colors.cardBorder)
              Spacer(modifier = Modifier.height(OceanSpacing.xs))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text =
                    "Principal: ${FormatMoneyUseCase().format(Money.fromMinorUnits(payment.principalComponentMinor, payment.currencyCode))}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                  text =
                    "Interest: ${FormatMoneyUseCase().format(Money.fromMinorUnits(payment.interestComponentMinor, payment.currencyCode))}",
                  style = MaterialTheme.typography.bodySmall,
                  color = OceanTheme.colors.gold,
                )
              }
              Text(
                text =
                  "Remaining Balance: ${FormatMoneyUseCase().format(Money.fromMinorUnits(payment.remainingBalanceMinor, payment.currencyCode))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }

  if (showPaymentDialog && loan != null) {
    val defaultPaymentText = remember(loan.id, loan.monthlyEmiMinor, loan.outstandingPrincipalMinor) {
      Money.fromMinorUnits(loan.monthlyEmiMinor, loan.currencyCode).toBigDecimal().toPlainString()
    }
    var amountText by remember { mutableStateOf(defaultPaymentText) }
    var showPaymentCalculator by remember { mutableStateOf(false) }
    var notesText by remember { mutableStateOf("") }
    var paymentDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    val previewBreakdown = remember(amountText, loan) {
      runCatching {
        val bd = BigDecimal(amountText.trim())
        val money = Money.fromBigDecimal(bd, loan.currencyCode, RoundingMode.HALF_UP)
        calculationEngine.calculatePaymentBreakdown(
          outstandingPrincipalMinor = loan.outstandingPrincipalMinor,
          annualInterestRatePercent = loan.annualInterestRatePercent,
          paymentAmountMinor = money.minorUnits,
          currencyCode = loan.currencyCode,
        )
      }
    }

    AlertDialog(
      onDismissRequest = { showPaymentDialog = false },
      title = { Text("Record EMI Payment", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
          if (dialogError != null) {
            Text(
              text = dialogError ?: "",
              color = OceanTheme.colors.negative,
              style = MaterialTheme.typography.bodySmall,
            )
          }
          OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Payment Amount (${loan.currencyCode})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            trailingIcon = {
              IconButton(
                onClick = { showPaymentCalculator = true },
                modifier = Modifier.testTag("payment_amount_calculator_button"),
              ) {
                Icon(Icons.Rounded.Calculate, contentDescription = "Calculate payment amount")
              }
            },
            modifier = Modifier.fillMaxWidth().testTag("payment_amount_input"),
          )
          if (showPaymentCalculator) {
            SmartCalculatorAmountDialog(
              initialExpression = amountText,
              onApply = { calculatedAmount ->
                amountText = calculatedAmount
                showPaymentCalculator = false
              },
              onDismiss = { showPaymentCalculator = false },
            )
          }
          Text(
            text = "Payment Date: ${dateFormatter.format(Date(paymentDate))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          previewBreakdown.getOrNull()?.let { bd ->
            Surface(
              color = OceanTheme.colors.cardSubtle,
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(OceanSpacing.xs)) {
                Text(
                  text = "Principal Component: ${FormatMoneyUseCase().format(bd.principalComponentMoney)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                  text = "Interest Component: ${FormatMoneyUseCase().format(bd.interestComponentMoney)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = OceanTheme.colors.gold,
                )
                Text(
                  text = "New Remaining Balance: ${FormatMoneyUseCase().format(bd.remainingBalanceMoney)}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          } ?: previewBreakdown.exceptionOrNull()?.let { err ->
            Text(
              text = err.message ?: "Invalid payment amount",
              style = MaterialTheme.typography.bodySmall,
              color = OceanTheme.colors.negative,
            )
          }

          OutlinedTextField(
            value = notesText,
            onValueChange = { notesText = it },
            label = { Text("Notes (Optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (previewBreakdown.isFailure) {
              dialogError = previewBreakdown.exceptionOrNull()?.message ?: "Invalid payment amount"
              return@Button
            }
            dialogError = null
            onRecordPayment(loan, paymentDate, amountText.trim(), notesText.trim()) {
              showPaymentDialog = false
            }
          },
          modifier = Modifier.testTag("confirm_record_payment_button"),
        ) {
          Text("Confirm Payment")
        }
      },
      dismissButton = {
        TextButton(onClick = { showPaymentDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showEditDialog && loan != null) {
    val initialPrincipal =
      remember(loan) {
        Money.fromMinorUnits(loan.principalMinor, loan.currencyCode).toBigDecimal().toPlainString()
      }
    LoanFormDialog(
      title = "Edit Loan",
      availableCurrencies = availableCurrencies,
      initialName = loan.name,
      initialLender = loan.lenderName,
      initialType = loan.loanType,
      initialPrincipal = initialPrincipal,
      initialCurrency = loan.currencyCode,
      initialRate = loan.annualInterestRatePercent,
      initialTenureMonths = loan.tenureMonths.toString(),
      initialNotes = loan.notes ?: "",
      isEditMode = true,
      calculationEngine = calculationEngine,
      onDismiss = { showEditDialog = false },
      onSave = { name, lender, type, principalStr, currency, rate, tenureMonths, _, notes ->
        val newPrincipalMinor =
          runCatching {
            Money.fromBigDecimal(BigDecimal(principalStr), currency, RoundingMode.HALF_UP).minorUnits
          }.getOrDefault(loan.principalMinor)
        val clampedOutstanding = loan.outstandingPrincipalMinor.coerceAtMost(newPrincipalMinor)

        onUpdateLoan(
          loan.copy(
            name = name,
            lenderName = lender,
            loanType = type,
            principalMinor = newPrincipalMinor,
            currencyCode = currency,
            annualInterestRatePercent = rate,
            tenureMonths = tenureMonths,
            outstandingPrincipalMinor = clampedOutstanding,
            notes = notes.ifBlank { null },
          )
        ) { showEditDialog = false }
      },
    )
  }

  if (showDeleteDialog && loan != null) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text("Delete Loan") },
      text = {
        Text("Are you sure you want to delete '${loan.name}' and all its recorded EMI payment history? This cannot be undone.")
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteDialog = false
            onDeleteLoan(loan.id)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("confirm_delete_loan_button"),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun DetailMetricRow(
  label: String,
  value: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
