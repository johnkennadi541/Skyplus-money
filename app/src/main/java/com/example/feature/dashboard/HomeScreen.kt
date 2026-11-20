package com.example.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanSectionHeader
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanNumericStyle
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.AuthenticatedUser
import com.example.domain.model.AutoSavedTransactionNotice
import com.example.domain.model.FinancialAccount
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.Money
import com.example.domain.model.PhaseDeliveryState
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.feature.transactions.TransactionListItem

@Composable
fun HomeScreen(
  uiState: HomeFoundationUiState,
  onOpenAuthReadiness: () -> Unit,
  modifier: Modifier = Modifier,
  authenticatedUser: AuthenticatedUser? = null,
  accounts: List<FinancialAccount> = emptyList(),
  recentTransactions: List<FinancialTransaction> = emptyList(),
  reviewQueueCount: Int = 0,
  autoSavedNotice: AutoSavedTransactionNotice? = null,
  onDismissAutoSavedNotice: () -> Unit = {},
  onOpenAccounts: () -> Unit = {},
  onOpenTransactions: () -> Unit = {},
  onOpenBudgets: () -> Unit = {},
  onOpenIncomeManagement: () -> Unit = {},
  onOpenExpenseManagement: () -> Unit = {},
  onOpenSmartDetection: () -> Unit = {},
  onOpenReviewQueue: () -> Unit = {},
  onOpenLoans: () -> Unit = {},
  onOpenEmiCalculator: () -> Unit = {},
  onOpenInvestments: () -> Unit = {},
  onOpenReports: () -> Unit = {},
  budgets: List<com.example.domain.model.BudgetStatus> = emptyList(),
  loans: List<com.example.domain.model.Loan> = emptyList(),
  investments: List<com.example.domain.model.InvestmentHolding> = emptyList(),
) {
  val extended = OceanTheme.colors
  val activeAccounts = remember(accounts) { accounts.filter { it.isActive } }
  val balancesByCurrency =
    remember(activeAccounts) {
      activeAccounts.groupBy { it.currencyCode }.mapValues { (curr, list) ->
        Money.fromMinorUnits(list.sumOf { it.currentBalanceMinor }, curr)
      }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("home_screen"),
    contentPadding = PaddingValues(OceanSpacing.md),
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // In-App Auto-Saved Transaction Notification Banner
    if (autoSavedNotice != null) {
      item {
        OceanCard(modifier = Modifier.widthIn(max = 760.dp).testTag("auto_saved_notice_banner")) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = extended.positive)
              Spacer(modifier = Modifier.width(OceanSpacing.xs))
              Column {
                Text(
                  text = "Auto-Saved: ${FormatMoneyUseCase().format(autoSavedNotice.money)}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = extended.positive,
                )
                Text(
                  text = "${autoSavedNotice.merchantOrPayee} • ${autoSavedNotice.accountName}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            IconButton(onClick = onDismissAutoSavedNotice) {
              Icon(Icons.Rounded.Close, contentDescription = "Dismiss notification")
            }
          }
        }
      }
    }

    // Hero Card
    item {
      Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, extended.cardBorder),
        color = Color.Transparent,
      ) {
        Box(
          modifier =
            Modifier.background(
                Brush.linearGradient(
                  listOf(extended.heroGradientStart, extended.heroGradientEnd)
                )
              )
              .padding(OceanSpacing.lg)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.md)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Image(
                painter = painterResource(id = R.drawable.skyplus_money_icon_1795025038411),
                contentDescription = stringResource(R.string.cd_brand_emblem),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
              )
              Spacer(modifier = Modifier.width(OceanSpacing.md))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = uiState.appName,
                  style = MaterialTheme.typography.headlineMedium,
                  color = Color.White,
                )
                Text(
                  text = uiState.tagline,
                  style = MaterialTheme.typography.bodyMedium,
                  color = extended.gold,
                )
              }
            }

            // Multi-currency portfolio totals (never mixing currencies)
            if (balancesByCurrency.isNotEmpty()) {
              Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.xxs)) {
                Text(
                  text = "Portfolio Balances (${activeAccounts.size} Active Accounts)",
                  style = MaterialTheme.typography.labelMedium,
                  color = Color(0xFFD3E4F4),
                )
                balancesByCurrency.forEach { (curr, totalMoney) ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(text = curr, style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(
                      text = FormatMoneyUseCase().format(totalMoney),
                      style = OceanNumericStyle,
                      color = extended.gold,
                    )
                  }
                }
              }
            }

            Row(
              horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OceanStatusBadge(
                text = if (authenticatedUser != null) "Phase 2 • Encrypted Ledger" else stringResource(R.string.phase_zero_badge),
                tone = BadgeTone.POSITIVE,
              )
              OceanStatusBadge(
                text = "SQLCipher + Keystore",
                tone = BadgeTone.GOLD,
              )
            }
          }
        }
      }
    }

    // Quick Finance Actions
    item {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          Button(
            onClick = onOpenAccounts,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            modifier = Modifier.weight(1f).testTag("home_accounts_button"),
          ) {
            Icon(Icons.Rounded.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Accounts (${accounts.size})")
          }
          Button(
            onClick = onOpenTransactions,
            colors = ButtonDefaults.buttonColors(containerColor = OceanTheme.colors.gold, contentColor = Color.Black),
            modifier = Modifier.weight(1f).testTag("home_ledger_button"),
          ) {
            Text("Ledger (${recentTransactions.size})")
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          OutlinedButton(
            onClick = onOpenIncomeManagement,
            modifier = Modifier.weight(1f).testTag("home_income_button"),
          ) {
            Icon(Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp), tint = extended.positive)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Income")
          }
          OutlinedButton(
            onClick = onOpenExpenseManagement,
            modifier = Modifier.weight(1f).testTag("home_expense_button"),
          ) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp), tint = extended.negative)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Expenses")
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          OutlinedButton(
            onClick = onOpenSmartDetection,
            modifier = Modifier.weight(1f).testTag("home_smart_detection_button"),
          ) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Smart Detection")
          }
          OutlinedButton(
            onClick = onOpenReviewQueue,
            modifier = Modifier.weight(1f).testTag("home_review_queue_button"),
          ) {
            Icon(Icons.Rounded.RateReview, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (reviewQueueCount > 0) extended.warning else MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Review ($reviewQueueCount)")
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          OutlinedButton(
            onClick = onOpenBudgets,
            modifier = Modifier.weight(1f).testTag("home_budgets_button"),
          ) {
            Icon(Icons.Rounded.PieChart, contentDescription = null, modifier = Modifier.size(16.dp), tint = OceanTheme.colors.gold)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Budgets (${budgets.size})")
          }
          OutlinedButton(
            onClick = onOpenInvestments,
            modifier = Modifier.weight(1f).testTag("home_investments_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = extended.positive)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Investments (${investments.size})")
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
        ) {
          OutlinedButton(
            onClick = onOpenLoans,
            modifier = Modifier.weight(1f).testTag("home_loans_button"),
          ) {
            Icon(Icons.Rounded.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Loans (${loans.size})")
          }
          OutlinedButton(
            onClick = onOpenEmiCalculator,
            modifier = Modifier.weight(1f).testTag("home_emi_calculator_button"),
          ) {
            Icon(Icons.Rounded.Calculate, contentDescription = null, modifier = Modifier.size(16.dp), tint = OceanTheme.colors.gold)
            Spacer(modifier = Modifier.width(4.dp))
            Text("EMI Calculator")
          }
        }
        Button(
          onClick = onOpenReports,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
          modifier = Modifier.fillMaxWidth().testTag("home_reports_button"),
        ) {
          Icon(Icons.Rounded.Assessment, contentDescription = null, modifier = Modifier.size(18.dp), tint = OceanTheme.colors.gold)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Reports, Net Worth & Exports", fontWeight = FontWeight.SemiBold)
        }
      }
    }

    // Compact Budget Health Card if budgets exist
    if (budgets.isNotEmpty()) {
      item {
        val totalAllocated = budgets.sumOf { it.budget.amountMinor }
        val totalSpent = budgets.sumOf { it.spentMoney.minorUnits }
        val currency = budgets.first().budget.currencyCode
        val overspentCount = budgets.count { it.alertStatus == com.example.domain.model.BudgetAlertStatus.EXCEEDED_100 }

        OceanCard(
          onClick = onOpenBudgets,
          modifier = Modifier.widthIn(max = 760.dp).testTag("home_budget_overview_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.PieChart, contentDescription = null, tint = OceanTheme.colors.gold)
              Spacer(modifier = Modifier.width(OceanSpacing.xs))
              Text("Budget Health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (overspentCount > 0) {
              OceanStatusBadge(text = "$overspentCount Overspent", tone = BadgeTone.NEGATIVE)
            } else {
              OceanStatusBadge(text = "${budgets.size} Active", tone = BadgeTone.POSITIVE)
            }
          }
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(
            text = "Total Spent: ${FormatMoneyUseCase().format(Money.fromMinorUnits(totalSpent, currency))} of ${FormatMoneyUseCase().format(Money.fromMinorUnits(totalAllocated, currency))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Compact Loan Overview Card if loans exist
    if (loans.isNotEmpty()) {
      item {
        val primaryCurrency = loans.first().currencyCode
        val sameCurrencyLoans = loans.filter { it.currencyCode.equals(primaryCurrency, ignoreCase = true) }
        val totalOutstanding = sameCurrencyLoans.sumOf { it.outstandingPrincipalMinor }
        val totalMonthlyEmi = sameCurrencyLoans.filter { it.status != com.example.domain.model.LoanStatus.COMPLETED }.sumOf { it.monthlyEmiMinor }
        val overdueCount = sameCurrencyLoans.count { it.status == com.example.domain.model.LoanStatus.OVERDUE }

        OceanCard(
          onClick = onOpenLoans,
          modifier = Modifier.widthIn(max = 760.dp).testTag("home_loan_overview_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
              Spacer(modifier = Modifier.width(OceanSpacing.xs))
              Text("Loans & EMIs ($primaryCurrency)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (overdueCount > 0) {
              OceanStatusBadge(text = "$overdueCount Overdue", tone = BadgeTone.NEGATIVE)
            } else {
              OceanStatusBadge(text = "${sameCurrencyLoans.size} Tracked", tone = BadgeTone.INFO)
            }
          }
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(
            text = "Outstanding: ${FormatMoneyUseCase().format(Money.fromMinorUnits(totalOutstanding, primaryCurrency))} • Monthly EMI: ${FormatMoneyUseCase().format(Money.fromMinorUnits(totalMonthlyEmi, primaryCurrency))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Compact Investment Overview Card if investments exist
    if (investments.isNotEmpty()) {
      item {
        val primaryCurrency = investments.first().currencyCode
        val sameCurrency = investments.filter { it.currencyCode.equals(primaryCurrency, ignoreCase = true) }
        val totalValuation = sameCurrency.sumOf { it.currentValuationMinor }
        val totalInvested = sameCurrency.sumOf { it.totalInvestedMinor }
        val unrealizedPnl = totalValuation - totalInvested

        OceanCard(
          onClick = onOpenInvestments,
          modifier = Modifier.widthIn(max = 760.dp).testTag("home_investment_overview_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, tint = extended.positive)
              Spacer(modifier = Modifier.width(OceanSpacing.xs))
              Text("Investments ($primaryCurrency)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OceanStatusBadge(
              text = "${sameCurrency.size} Holdings",
              tone = if (unrealizedPnl >= 0L) BadgeTone.POSITIVE else BadgeTone.NEGATIVE,
            )
          }
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(
            text = "Value: ${FormatMoneyUseCase().format(Money.fromMinorUnits(totalValuation, primaryCurrency))} • Unrealized P&L: ${FormatMoneyUseCase().format(Money.fromMinorUnits(unrealizedPnl, primaryCurrency))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Recent Transactions Section
    if (recentTransactions.isNotEmpty()) {
      item {
        OceanSectionHeader(
          title = "Recent Transactions",
          subtitle = "Latest encrypted offline ledger entries",
          modifier = Modifier.widthIn(max = 760.dp),
        )
      }
      items(recentTransactions.take(5), key = { it.id }) { txn ->
        val accountName = accounts.firstOrNull { it.id == txn.accountId }?.name ?: "Account"
        TransactionListItem(
          transaction = txn,
          accountName = accountName,
          onClick = onOpenTransactions,
          modifier = Modifier.widthIn(max = 760.dp),
        )
      }
    }

    // Security & Auth Card (preserves Phase 1 testTag)
    item {
      OceanCard(
        modifier = Modifier.widthIn(max = 760.dp).testTag("auth_gate_notice_card"),
        onClick = onOpenAuthReadiness,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = if (authenticatedUser != null) Icons.Rounded.VerifiedUser else Icons.Rounded.Lock,
            contentDescription = stringResource(R.string.home_foundation_notice_title),
            tint = if (authenticatedUser != null) extended.positive else extended.warning,
            modifier = Modifier.size(22.dp),
          )
          Spacer(modifier = Modifier.width(OceanSpacing.xs))
          Text(
            text =
              if (authenticatedUser != null) {
                stringResource(
                  R.string.auth_signed_in_as,
                  authenticatedUser.email ?: authenticatedUser.displayName ?: authenticatedUser.uid,
                )
              } else {
                stringResource(R.string.home_foundation_notice_title)
              },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Spacer(modifier = Modifier.height(OceanSpacing.xs))
        Text(
          text =
            if (authenticatedUser != null) {
              "Authenticated via Firebase Auth (Google) with Legal Consent v1.0.0. Local financial ledger is encrypted via SQLCipher + Android Keystore."
            } else {
              stringResource(R.string.home_foundation_notice_body)
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(OceanSpacing.sm))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Package ID: ${uiState.packageId}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
          Text(
            text = "Session & Security →",
            style = MaterialTheme.typography.labelLarge,
            color = extended.gold,
          )
        }
      }
    }

    // Architecture Precision & Security Cards
    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        OceanCard(modifier = Modifier.weight(1f)) {
          Icon(imageVector = Icons.Rounded.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(text = "No Guest Mode", style = MaterialTheme.typography.titleSmall)
          Text(text = "Auth & Consent enforced", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OceanCard(modifier = Modifier.weight(1f)) {
          Icon(imageVector = Icons.Rounded.Shield, contentDescription = null, tint = extended.gold)
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(text = "Encrypted", style = MaterialTheme.typography.titleSmall)
          Text(text = "SQLCipher • allowBackup=false", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    item {
      OceanSectionHeader(
        title = stringResource(R.string.home_section_roadmap),
        subtitle = "Phase-by-phase test-driven delivery",
        modifier = Modifier.widthIn(max = 760.dp),
      )
    }

    items(uiState.roadmap, key = { it.phaseNumber }) { item ->
      val (badgeLabel, badgeTone) =
        when (item.state) {
          PhaseDeliveryState.COMPLETE_IN_PROGRESS -> "ACTIVE / READY" to BadgeTone.POSITIVE
          PhaseDeliveryState.PLANNED_NEXT -> "NEXT PHASE" to BadgeTone.GOLD
          PhaseDeliveryState.SCHEDULED -> "SCHEDULED" to BadgeTone.INFO
        }
      OceanCard(modifier = Modifier.widthIn(max = 760.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(OceanSpacing.xs))
          OceanStatusBadge(text = badgeLabel, tone = badgeTone)
        }
        Spacer(modifier = Modifier.height(OceanSpacing.xxs))
        Text(
          text = item.summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
