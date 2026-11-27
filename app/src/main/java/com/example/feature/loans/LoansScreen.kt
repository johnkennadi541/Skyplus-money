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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanNumericStyle
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.EmiPrefillDraft
import com.example.domain.model.FinancialAccount
import com.example.domain.model.Loan
import com.example.domain.model.LoanCurrencySummary
import com.example.domain.model.LoanStatus
import com.example.domain.model.LoanType
import com.example.domain.model.Money
import com.example.domain.usecase.EmiCalculationEngine
import com.example.domain.usecase.FormatMoneyUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LoansScreen(
  viewModel: LoansViewModel,
  onNavigateBack: () -> Unit,
  onOpenLoanDetail: (String) -> Unit,
  onOpenEmiCalculator: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val loans by viewModel.loans.collectAsStateWithLifecycle()
  val archivedLoans by viewModel.archivedLoans.collectAsStateWithLifecycle()
  val summaries by viewModel.summariesByCurrency.collectAsStateWithLifecycle()
  val accounts by viewModel.accounts.collectAsStateWithLifecycle()
  val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
  val isErrorMessage by viewModel.isErrorMessage.collectAsStateWithLifecycle()
  val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
  val emiDraft by viewModel.emiPrefillDraft.collectAsStateWithLifecycle()

  LoansContent(
    loans = loans,
    archivedLoans = archivedLoans,
    onRestoreLoan = viewModel::restoreLoan,
    summaries = summaries,
    accounts = accounts,
    userMessage = userMessage,
    isErrorMessage = isErrorMessage,
    selectedCurrency = selectedCurrency,
    emiPrefillDraft = emiDraft,
    calculationEngine = viewModel.emiCalculationEngine,
    onConsumeEmiDraft = { viewModel.consumeEmiDraft() },
    onSelectCurrency = { viewModel.selectCurrency(it) },
    onDismissMessage = { viewModel.dismissMessage() },
    onNavigateBack = onNavigateBack,
    onOpenLoanDetail = onOpenLoanDetail,
    onOpenEmiCalculator = onOpenEmiCalculator,
    onCreateLoan = { name, lender, type, principal, currency, rate, tenureMonths, paid, notes, onSuccess ->
      viewModel.createLoan(
        name = name,
        lenderName = lender,
        loanType = type,
        principalDecimal = principal,
        currencyCode = currency,
        annualInterestRateText = rate,
        tenureMonths = tenureMonths,
        startDate = System.currentTimeMillis(),
        alreadyPaidDecimal = paid,
        notes = notes,
        onSuccess = onSuccess,
      )
    },
    modifier = modifier,
  )
}

@Composable
fun LoansContent(
  loans: List<Loan>,
  archivedLoans: List<Loan> = emptyList(),
  onRestoreLoan: (String) -> Unit = {},
  summaries: Map<String, LoanCurrencySummary>,
  accounts: List<FinancialAccount>,
  userMessage: String? = null,
  isErrorMessage: Boolean = false,
  selectedCurrency: String? = null,
  emiPrefillDraft: EmiPrefillDraft? = null,
  calculationEngine: EmiCalculationEngine = remember { EmiCalculationEngine() },
  onConsumeEmiDraft: () -> Unit = {},
  onSelectCurrency: (String?) -> Unit = {},
  onDismissMessage: () -> Unit = {},
  onNavigateBack: () -> Unit = {},
  onOpenLoanDetail: (String) -> Unit = {},
  onOpenEmiCalculator: () -> Unit = {},
  onCreateLoan: (
    String,
    String,
    LoanType,
    String,
    String,
    String,
    Int,
    String,
    String,
    () -> Unit,
  ) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onNavigateBack)

  var showCreateDialog by remember { mutableStateOf(false) }
  var showArchivedLoans by remember { mutableStateOf(false) }
  var activeDraft by remember { mutableStateOf<EmiPrefillDraft?>(null) }

  LaunchedEffect(emiPrefillDraft) {
    if (emiPrefillDraft != null) {
      activeDraft = emiPrefillDraft
      onSelectCurrency(emiPrefillDraft.currencyCode)
      showCreateDialog = true
      onConsumeEmiDraft()
    }
  }

  val availableCurrencies =
    remember(loans, accounts) {
      val fromLoans = loans.map { it.currencyCode }
      val fromAccounts = accounts.map { it.currencyCode }
      (fromLoans + fromAccounts + listOf("USD", "INR", "EUR", "GBP")).distinct()
    }

  val activeCurrency =
    selectedCurrency
      ?: loans.firstOrNull()?.currencyCode
      ?: accounts.firstOrNull()?.currencyCode
      ?: "USD"

  val filteredLoans =
    remember(loans, activeCurrency) {
      loans.filter { it.currencyCode.equals(activeCurrency, ignoreCase = true) }
    }

  val activeSummary = summaries[activeCurrency]

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = OceanSpacing.md, vertical = OceanSpacing.sm)
        .testTag("loans_screen"),
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("loans_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_navigate_back),
              )
            }
            Column {
              Text(
                text = stringResource(R.string.loans_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                text = stringResource(R.string.loans_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xxs)) {
            IconButton(
              onClick = onOpenEmiCalculator,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("loans_open_calculator_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Calculate,
                contentDescription = stringResource(R.string.cd_emi_calculator),
                tint = OceanTheme.colors.gold,
              )
            }
            Button(
              onClick = {
                activeDraft = null
                showCreateDialog = true
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("create_loan_button"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add Loan")
            }
          }
        }
      }

      // User Message Banner
      if (userMessage != null) {
        item {
          Surface(
            modifier = Modifier.fillMaxWidth().testTag("loans_user_message"),
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

      // Multi-currency filter chips (strictly isolated per currency)
      item {
        val displayCurrencies =
          remember(loans, activeCurrency) {
            val fromLoans = loans.map { it.currencyCode }
            (listOf(activeCurrency) + fromLoans).distinct()
          }
        if (displayCurrencies.size > 1) {
          LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
          ) {
            items(displayCurrencies) { curr ->
              FilterChip(
                selected = curr.equals(activeCurrency, ignoreCase = true),
                onClick = { onSelectCurrency(curr) },
                label = { Text(curr) },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("loan_currency_chip_$curr"),
              )
            }
          }
        }
      }

      // Per-currency Summary Card
      if (activeSummary != null) {
        item {
          OceanCard(modifier = Modifier.testTag("loan_summary_card")) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "Outstanding Principal (${activeSummary.currencyCode})",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text =
                    FormatMoneyUseCase()
                      .format(Money.fromMinorUnits(activeSummary.totalOutstandingMinor, activeSummary.currencyCode)),
                  style = OceanNumericStyle.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "Monthly EMI",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text =
                    FormatMoneyUseCase()
                      .format(Money.fromMinorUnits(activeSummary.totalMonthlyEmiMinor, activeSummary.currencyCode)),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }

            Spacer(modifier = Modifier.height(OceanSpacing.sm))

            val paidFraction =
              if (activeSummary.totalPrincipalMinor > 0L) {
                ((activeSummary.totalPrincipalMinor - activeSummary.totalOutstandingMinor).toFloat() /
                  activeSummary.totalPrincipalMinor.toFloat())
                  .coerceIn(0f, 1f)
              } else {
                0f
              }
            LinearProgressIndicator(
              progress = { paidFraction },
              modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
              color = MaterialTheme.colorScheme.secondary,
              trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(OceanSpacing.xs))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text =
                  "Paid: ${FormatMoneyUseCase().format(Money.fromMinorUnits(activeSummary.totalAmountPaidMinor, activeSummary.currencyCode))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xxs)) {
                if (activeSummary.overdueLoanCount > 0) {
                  OceanStatusBadge(
                    text = "${activeSummary.overdueLoanCount} Overdue",
                    tone = BadgeTone.NEGATIVE,
                  )
                }
                OceanStatusBadge(
                  text = "${activeSummary.activeLoanCount} Active",
                  tone = BadgeTone.INFO,
                )
              }
            }
          }
        }
      }

      item {
        OutlinedButton(
          onClick = { showArchivedLoans = !showArchivedLoans },
          modifier = Modifier.fillMaxWidth().testTag("toggle_archived_loans"),
        ) {
          Text(if (showArchivedLoans) "Hide archived loans" else "Archived loans (\${archivedLoans.size})")
        }
      }
      if (showArchivedLoans) {
        if (archivedLoans.isEmpty()) {
          item {
            OceanCard(modifier = Modifier.fillMaxWidth().testTag("archived_loans_empty")) {
              Text("No archived loans", modifier = Modifier.padding(OceanSpacing.md))
            }
          }
        } else {
          items(archivedLoans, key = { "archived_\${it.id}" }) { loan ->
            OceanCard(modifier = Modifier.fillMaxWidth().testTag("archived_loan_\${loan.id}")) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(loan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  Text("Archived · \${loan.currencyCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("EMI payment history retained", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { onRestoreLoan(loan.id) }, modifier = Modifier.testTag("restore_loan_\${loan.id}")) { Text("Restore") }
              }
            }
          }
        }
      }
      // Loan Cards or Empty State
      if (filteredLoans.isEmpty()) {
        item {
          OceanCard(modifier = Modifier.testTag("loans_empty_state")) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(OceanSpacing.lg),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = Icons.Rounded.AccountBalance,
                contentDescription = null,
                tint = OceanTheme.colors.gold,
                modifier = Modifier.size(48.dp),
              )
              Spacer(modifier = Modifier.height(OceanSpacing.sm))
              Text(
                text = "No Loans Tracked",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Spacer(modifier = Modifier.height(OceanSpacing.xs))
              Text(
                text = "Track personal, home, vehicle, or education loans and record EMIs in your encrypted ledger.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(OceanSpacing.md))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
              ) {
                OutlinedButton(
                  onClick = onOpenEmiCalculator,
                  modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                ) {
                  Text("EMI Calculator")
                }
                Button(
                  onClick = {
                    activeDraft = null
                    showCreateDialog = true
                  },
                  modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("add_first_loan_button"),
                ) {
                  Text("Add Loan")
                }
              }
            }
          }
        }
      } else {
        items(filteredLoans, key = { it.id }) { loan ->
          LoanSummaryCard(
            loan = loan,
            onClick = { onOpenLoanDetail(loan.id) },
          )
        }
      }
    }
  }

  if (showCreateDialog) {
    val draft = activeDraft
    LoanFormDialog(
      title = "Add New Loan",
      availableCurrencies = availableCurrencies,
      initialPrincipal = draft?.principalDecimal ?: "",
      initialCurrency = draft?.currencyCode ?: activeCurrency,
      initialRate = draft?.annualRatePercent ?: "8.5",
      initialTenureMonths = draft?.tenureMonths?.toString() ?: "12",
      calculationEngine = calculationEngine,
      onDismiss = {
        showCreateDialog = false
        activeDraft = null
      },
      onSave = { name, lender, type, principal, currency, rate, tenureMonths, paid, notes ->
        onCreateLoan(name, lender, type, principal, currency, rate, tenureMonths, paid, notes) {
          showCreateDialog = false
          activeDraft = null
        }
      },
    )
  }
}

@Composable
private fun LoanSummaryCard(
  loan: Loan,
  onClick: () -> Unit,
) {
  val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
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

  OceanCard(
    onClick = onClick,
    modifier = Modifier.testTag("loan_card_${loan.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = loan.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "${loan.lenderName} • ${loan.loanType.displayName}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      OceanStatusBadge(text = loan.status.displayName, tone = statusTone)
    }

    Spacer(modifier = Modifier.height(OceanSpacing.sm))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text(
          text = "Outstanding",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text =
            FormatMoneyUseCase()
              .format(Money.fromMinorUnits(loan.outstandingPrincipalMinor, loan.currencyCode)),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "Monthly EMI",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text =
            FormatMoneyUseCase()
              .format(Money.fromMinorUnits(loan.monthlyEmiMinor, loan.currencyCode)),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.secondary,
        )
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
      color =
        when (loan.status) {
          LoanStatus.COMPLETED -> OceanTheme.colors.positive
          LoanStatus.OVERDUE -> OceanTheme.colors.negative
          LoanStatus.ACTIVE -> MaterialTheme.colorScheme.secondary
        },
      trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )

    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "${loan.annualInterestRatePercent}% p.a. • ${loan.tenureMonths} mo",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text =
          if (loan.status == LoanStatus.COMPLETED) "Paid off"
          else "Next due: ${dateFormatter.format(Date(loan.nextDueDate))}",
        style = MaterialTheme.typography.labelSmall,
        color =
          if (loan.status == LoanStatus.OVERDUE) OceanTheme.colors.negative
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
