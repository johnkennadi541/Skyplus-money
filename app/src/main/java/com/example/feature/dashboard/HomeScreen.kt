package com.example.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanEmptyState
import com.example.core.designsystem.component.OceanErrorState
import com.example.core.designsystem.component.OceanLoadingState
import com.example.core.designsystem.component.OceanPrimaryButton
import com.example.core.designsystem.component.OceanSectionHeader
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.theme.OceanNumericStyle
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.AuthenticatedUser
import com.example.domain.model.AutoSavedTransactionNotice
import com.example.domain.model.BudgetStatus
import com.example.domain.model.FinancialAccount
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.InvestmentHolding
import com.example.domain.model.Loan
import com.example.feature.transactions.TransactionListItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SKYPLUS MONEY — Home dashboard (Phase 2).
 *
 * Presentation only. Every figure arrives already computed and already formatted in
 * [HomeUiState]; this screen never calculates money and never invents a value.
 *
 * Currency rule: one currency at a time. A currency selector appears only when the
 * user actually holds more than one currency, and no total ever mixes currencies.
 */
@Composable
fun HomeScreen(
  uiState: HomeUiState,
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
  onRetryLoad: () -> Unit = {},
  onSelectCurrency: (String) -> Unit = {},
  budgets: List<BudgetStatus> = emptyList(),
  loans: List<Loan> = emptyList(),
  investments: List<InvestmentHolding> = emptyList(),
) {
  val section = uiState.selectedSection

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("home_screen"),
    contentPadding = PaddingValues(OceanSpacing.md),
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (autoSavedNotice != null) {
      item {
        OceanCard(modifier = Modifier.widthIn(max = 760.dp).testTag("auto_saved_notice_banner")) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.NotificationsActive,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.width(OceanSpacing.xs))
            Text(
              text = "A transaction was saved automatically. Review it in the Ledger.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismissAutoSavedNotice) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    item {
      HomeHeaderCard(
        uiState = uiState,
        authenticatedUser = authenticatedUser,
        modifier = Modifier.widthIn(max = 760.dp),
      )
    }

    when {
      uiState.isLoading -> {
        item {
          OceanCard(modifier = Modifier.widthIn(max = 760.dp)) {
            OceanLoadingState(message = "Loading your financial overview…")
          }
        }
      }

      uiState.errorMessage != null -> {
        item {
          OceanErrorState(
            message =
              "Your financial overview could not be loaded. Your data is safe on this device.",
            modifier = Modifier.widthIn(max = 760.dp),
            title = "Overview unavailable",
            retryLabel = "Try again",
            onRetry = onRetryLoad,
            testTag = "home_error_state",
          )
        }
      }

      section == null -> {
        item {
          OceanCard(modifier = Modifier.widthIn(max = 760.dp)) {
            OceanEmptyState(
              title = "No accounts yet",
              message =
                "Add your first bank, cash or wallet account and your balance, income and spending will appear here.",
              icon = Icons.Rounded.AccountBalance,
              actionLabel = "Add an account",
              onAction = onOpenAccounts,
              testTag = "home_empty_state",
            )
          }
        }
      }

      else -> {
        if (uiState.hasMultipleCurrencies) {
          item {
            HomeCurrencySelector(
              sections = uiState.sections,
              selectedCurrencyCode = section.currencyCode,
              onSelectCurrency = onSelectCurrency,
              modifier = Modifier.widthIn(max = 760.dp),
            )
          }
        }

        item {
          HomeNetWorthCard(
            section = section,
            modifier = Modifier.widthIn(max = 760.dp),
          )
        }

        item {
          HomeIncomeExpenseCard(
            section = section,
            modifier = Modifier.widthIn(max = 760.dp),
          )
        }

        if (section.trends.isNotEmpty()) {
          item {
            HomeTrendCard(
              currencyCode = section.currencyCode,
              trends = section.trends,
              modifier = Modifier.widthIn(max = 760.dp).testTag("home_income_expense_chart"),
            )
          }
        }

        section.budget?.let { budget ->
          item {
            HomeBudgetOverviewCard(
              currencyCode = section.currencyCode,
              budget = budget,
              modifier = Modifier.widthIn(max = 760.dp).testTag("home_budget_overview_card"),
            )
          }
        }

        section.loans?.let { loan ->
          item {
            HomeLoansOverviewCard(
              currencyCode = section.currencyCode,
              loan = loan,
              modifier = Modifier.widthIn(max = 760.dp).testTag("home_loan_overview_card"),
            )
          }
        }

        section.investments?.let { investment ->
          item {
            HomeInvestmentsOverviewCard(
              currencyCode = section.currencyCode,
              investment = investment,
              modifier = Modifier.widthIn(max = 760.dp).testTag("home_investment_overview_card"),
            )
          }
        }

        if (section.allocation.isNotEmpty()) {
          item {
            HomeAllocationCard(
              currencyCode = section.currencyCode,
              slices = section.allocation,
              totalLabel = section.investments?.currentValue?.formatted,
              modifier = Modifier.widthIn(max = 760.dp).testTag("home_allocation_card"),
            )
          }
        }

        if (section.holdings.isNotEmpty()) {
          item {
            HomeHoldingsCard(
              currencyCode = section.currencyCode,
              holdings = section.holdings,
              modifier = Modifier.widthIn(max = 760.dp).testTag("home_holdings_card"),
            )
          }
        }
      }
    }

    item {
      HomeQuickActions(
        accountsCount = accounts.size,
        transactionsCount = recentTransactions.size,
        budgetsCount = budgets.size,
        loansCount = loans.size,
        investmentsCount = investments.size,
        reviewQueueCount = reviewQueueCount,
        onOpenAccounts = onOpenAccounts,
        onOpenTransactions = onOpenTransactions,
        onOpenIncomeManagement = onOpenIncomeManagement,
        onOpenExpenseManagement = onOpenExpenseManagement,
        onOpenSmartDetection = onOpenSmartDetection,
        onOpenReviewQueue = onOpenReviewQueue,
        onOpenBudgets = onOpenBudgets,
        onOpenInvestments = onOpenInvestments,
        onOpenLoans = onOpenLoans,
        onOpenEmiCalculator = onOpenEmiCalculator,
        onOpenReports = onOpenReports,
        modifier = Modifier.widthIn(max = 760.dp),
      )
    }

    if (recentTransactions.isNotEmpty()) {
      item {
        OceanSectionHeader(
          title = "Recent transactions",
          subtitle = "Latest activity across your accounts",
          modifier = Modifier.widthIn(max = 760.dp),
        )
      }
      items(recentTransactions.take(5), key = { it.id }) { transaction ->
        Box(modifier = Modifier.widthIn(max = 760.dp)) {
          TransactionListItem(
            transaction = transaction,
            accountName =
              accounts.firstOrNull { it.id == transaction.accountId }?.name ?: "Account",
            onClick = onOpenTransactions,
          )
        }
      }
    }

    item {
      HomeSecurityCard(
        uiState = uiState,
        onOpenAuthReadiness = onOpenAuthReadiness,
        modifier = Modifier.widthIn(max = 760.dp).testTag("auth_gate_notice_card"),
      )
    }
  }
}

// ---- Header ---------------------------------------------------------------

@Composable
private fun HomeHeaderCard(
  uiState: HomeUiState,
  authenticatedUser: AuthenticatedUser?,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val name =
    authenticatedUser?.displayName?.takeIf { it.isNotBlank() }
      ?: authenticatedUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
  val greeting = remember { greetingForHour() }
  val initials = remember(name) { initialsOf(name) }

  Surface(
    modifier = modifier.fillMaxWidth(),
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
      Column(verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = initials,
              style = MaterialTheme.typography.titleSmall,
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
            )
          }
          Spacer(modifier = Modifier.width(OceanSpacing.sm))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = greeting,
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.80f),
            )
            Text(
              text = name ?: "Welcome back",
              style = MaterialTheme.typography.titleLarge,
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
            )
          }
          OceanStatusBadge(
            text = uiState.windowLabel.ifBlank { "This month" },
            tone = BadgeTone.GOLD,
          )
        }

        Text(
          text = "SKYPLUS MONEY",
          style = MaterialTheme.typography.labelSmall,
          color = Color.White.copy(alpha = 0.70f),
        )

        if (uiState.lastUpdatedMillis != null) {
          Text(
            text = "Updated ${formatTimestamp(uiState.lastUpdatedMillis)}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.70f),
          )
        }
      }
    }
  }
}

// ---- Currency selector ----------------------------------------------------

@Composable
private fun HomeCurrencySelector(
  sections: List<HomeCurrencySection>,
  selectedCurrencyCode: String,
  onSelectCurrency: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().testTag("home_currency_selector"),
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.xxs),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
    ) {
      sections.forEach { section ->
        FilterChip(
          selected = section.currencyCode == selectedCurrencyCode,
          onClick = { onSelectCurrency(section.currencyCode) },
          label = {
            Text(
              text = section.currencyCode,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
          },
          shape = MaterialTheme.shapes.small,
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
      }
    }
    Text(
      text = "Currencies are shown separately. No exchange-rate conversion is applied.",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

// ---- Net worth ------------------------------------------------------------

@Composable
private fun HomeNetWorthCard(
  section: HomeCurrencySection,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors

  OceanCard(modifier = modifier.testTag("home_net_worth_card")) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Net worth",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OceanStatusBadge(text = section.currencyCode, tone = BadgeTone.INFO)
    }
    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    Text(
      text = section.netWorth.formatted,
      style = MaterialTheme.typography.displaySmall,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.sm))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
    ) {
      HomeMiniStat(
        label = "Assets",
        value = section.assets.formatted,
        tint = extended.positive,
        modifier = Modifier.weight(1f),
      )
      HomeMiniStat(
        label = "Liabilities",
        value = section.liabilities.formatted,
        tint = extended.negative,
        modifier = Modifier.weight(1f),
      )
      HomeMiniStat(
        label = "Accounts",
        value = section.accountCount.toString(),
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun HomeMiniStat(
  label: String,
  value: String,
  tint: Color,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = OceanNumericStyle,
      color = tint,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
    )
  }
}

// ---- Income versus expense ------------------------------------------------

@Composable
private fun HomeIncomeExpenseCard(
  section: HomeCurrencySection,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val targetExpenseShare = section.expenseShare
  val expenseShare by
    animateFloatAsState(
      targetValue = targetExpenseShare,
      animationSpec = tween(durationMillis = 450),
      label = "expenseShare",
    )

  OceanCard(modifier = modifier.testTag("home_income_expense_card")) {
    Text(
      text = "Income vs expense",
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.sm))

    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Rounded.ArrowDownward,
        contentDescription = null,
        tint = extended.positive,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(OceanSpacing.xs))
      Text(
        text = "Income",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = section.income.formatted,
        style = OceanNumericStyle,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
      )
    }
    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    HomeShareBar(
      fraction = 1f - expenseShare,
      tint = extended.positive,
      track = MaterialTheme.colorScheme.surfaceVariant,
    )

    Spacer(modifier = Modifier.height(OceanSpacing.sm))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Rounded.ArrowUpward,
        contentDescription = null,
        tint = extended.negative,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(OceanSpacing.xs))
      Text(
        text = "Expenses",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = section.expenses.formatted,
        style = OceanNumericStyle,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
      )
    }
    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    HomeShareBar(
      fraction = expenseShare,
      tint = extended.negative,
      track = MaterialTheme.colorScheme.surfaceVariant,
    )

    Spacer(modifier = Modifier.height(OceanSpacing.sm))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = if (section.savings.minorUnits >= 0L) "Saved this period" else "Overspent this period",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = section.savings.formatted,
        style = OceanNumericStyle,
        color = if (section.savings.minorUnits >= 0L) extended.positive else extended.negative,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun HomeShareBar(
  fraction: Float,
  tint: Color,
  track: Color,
) {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .height(10.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(track)
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f))
          .height(10.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(tint)
    )
  }
}

// ---- Budget ---------------------------------------------------------------

@Composable
private fun HomeBudgetOverviewCard(
  currencyCode: String,
  budget: HomeBudgetSection,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val progress = (budget.utilizationPercent.toFloat() / 100f).coerceIn(0f, 1f)
  val animated by
    animateFloatAsState(
      targetValue = progress,
      animationSpec = tween(durationMillis = 450),
      label = "budgetProgress",
    )

  OceanCard(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Budget health",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
      )
      OceanStatusBadge(
        text = if (budget.overspentCount > 0) "${budget.overspentCount} overspent" else "On track",
        tone = if (budget.overspentCount > 0) BadgeTone.NEGATIVE else BadgeTone.POSITIVE,
      )
    }
    Spacer(modifier = Modifier.height(OceanSpacing.sm))
    Text(
      text = "${budget.spent.formatted} of ${budget.allocated.formatted} spent",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    LinearProgressIndicator(
      progress = { animated },
      modifier =
        Modifier.fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp)),
      color = if (budget.overspentCount > 0) extended.negative else MaterialTheme.colorScheme.secondary,
      trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "${budget.activeCount} active budget(s) · $currencyCode",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = "${budget.remaining.formatted} left",
        style = MaterialTheme.typography.labelSmall,
        color = if (budget.remaining.minorUnits >= 0L) extended.positive else extended.negative,
      )
    }
  }
}

// ---- Loans ----------------------------------------------------------------

@Composable
private fun HomeLoansOverviewCard(
  currencyCode: String,
  loan: HomeLoanSection,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors

  OceanCard(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Loans & EMIs · $currencyCode",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
      )
      if (loan.overdueCount > 0) {
        OceanStatusBadge(text = "${loan.overdueCount} overdue", tone = BadgeTone.NEGATIVE)
      }
    }
    Spacer(modifier = Modifier.height(OceanSpacing.sm))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
    ) {
      HomeMiniStat(
        label = "Outstanding",
        value = loan.outstanding.formatted,
        tint = extended.negative,
        modifier = Modifier.weight(1f),
      )
      HomeMiniStat(
        label = "Monthly EMI",
        value = loan.monthlyEmi.formatted,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
      )
      HomeMiniStat(
        label = "Active",
        value = loan.activeCount.toString(),
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

// ---- Investments ----------------------------------------------------------

@Composable
private fun HomeInvestmentsOverviewCard(
  currencyCode: String,
  investment: HomeInvestmentSection,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors

  OceanCard(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Investments · $currencyCode",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
      )
      OceanStatusBadge(
        text = "${investment.returnPercent.setScale(1, java.math.RoundingMode.HALF_UP)}%",
        tone = if (investment.unrealisedPnl.minorUnits >= 0L) BadgeTone.POSITIVE else BadgeTone.NEGATIVE,
      )
    }
    Spacer(modifier = Modifier.height(OceanSpacing.sm))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
    ) {
      HomeMiniStat(
        label = "Value",
        value = investment.currentValue.formatted,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
      )
      HomeMiniStat(
        label = "Invested",
        value = investment.invested.formatted,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      HomeMiniStat(
        label = "Unrealised",
        value = investment.unrealisedPnl.formatted,
        tint = if (investment.unrealisedPnl.minorUnits >= 0L) extended.positive else extended.negative,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

// ---- Charts and portfolio -------------------------------------------------

@Composable
private fun HomeTrendCard(
  currencyCode: String,
  trends: List<HomeTrendPoint>,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val latest = trends.lastOrNull()

  // Selection is real state: tapping a bar changes what this panel shows.
  var selection by remember(trends) {
    mutableStateOf<TrendSelection?>(latest?.let { TrendSelection(it.monthKey, TrendSeries.INCOME) })
  }
  val selectedPoint =
    trends.firstOrNull { it.monthKey == selection?.monthKey } ?: latest

  OceanCard(modifier = modifier) {
    Text(
      text = "Income vs expenses · $currencyCode",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = "Tap a bar to see that month's exact figures",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.sm))

    // The series is chosen here, so each bar in the chart can be a full-size month
    // target instead of two narrow ones.
    Row(horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs)) {
      TrendSeriesChip(
        label = "Income",
        selected = selection?.series != TrendSeries.EXPENSES,
        onClick = {
          selection = TrendSelection(selection?.monthKey ?: latest?.monthKey ?: "", TrendSeries.INCOME)
        },
      )
      TrendSeriesChip(
        label = "Expenses",
        selected = selection?.series == TrendSeries.EXPENSES,
        onClick = {
          selection =
            TrendSelection(selection?.monthKey ?: latest?.monthKey ?: "", TrendSeries.EXPENSES)
        },
      )
    }
    Spacer(modifier = Modifier.height(OceanSpacing.xs))

    IncomeExpenseTrendChart(
      trends = trends,
      selection = selection,
      onSelect = { tapped -> selection = tapped },
    )

    if (selectedPoint != null) {
      Spacer(modifier = Modifier.height(OceanSpacing.sm))
      SelectedTrendPanel(
        point = selectedPoint,
        selection = selection,
        modifier = Modifier.testTag("home_trend_selection"),
      )
    }
  }
}

@Composable
private fun TrendSeriesChip(label: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    colors =
      FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
      ),
  )
}

/** The tooltip panel: the selected month, with the selected series emphasised. */
@Composable
private fun SelectedTrendPanel(
  point: HomeTrendPoint,
  selection: TrendSelection?,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val incomeSelected = selection?.series == TrendSeries.INCOME
  val expensesSelected = selection?.series == TrendSeries.EXPENSES

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(OceanSpacing.sm)) {
      Text(
        text = point.label,
        modifier = Modifier.testTag("home_trend_selection_month"),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(OceanSpacing.xxs))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
      ) {
        TrendValueRow(
          label = "Income",
          value = point.income.formatted,
          tint = extended.positive,
          selected = incomeSelected,
          modifier = Modifier.weight(1f),
        )
        TrendValueRow(
          label = "Expenses",
          value = point.expenses.formatted,
          tint = extended.negative,
          selected = expensesSelected,
          modifier = Modifier.weight(1f),
        )
        TrendValueRow(
          label = "Saved",
          value = point.savings.formatted,
          tint = if (point.savings.minorUnits >= 0L) extended.positive else extended.negative,
          selected = false,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun TrendValueRow(
  label: String,
  value: String,
  tint: Color,
  selected: Boolean,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      color = if (selected) tint else MaterialTheme.colorScheme.onSurface,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1,
    )
  }
}

@Composable
private fun HomeAllocationCard(
  currencyCode: String,
  slices: List<HomeAllocationSlice>,
  totalLabel: String?,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val colors = remember(slices, extended.isDark) {
    com.example.core.designsystem.theme.PortfolioPalette.assign(slices.map { it.colorKey }, extended.isDark)
  }
  var selectedKey by remember(slices) { mutableStateOf<String?>(null) }
  val selectedSlice =
    slices.firstOrNull {
      com.example.core.designsystem.theme.PortfolioPalette.canonical(it.colorKey) == selectedKey
    }

  OceanCard(modifier = modifier) {
    Text(
      text = "Portfolio allocation · $currencyCode",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = "Tap a segment or a row to see that category",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.sm))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AllocationRing(
        slices = slices,
        isDark = extended.isDark,
        selectedKey = selectedKey,
        onSelect = { key -> selectedKey = if (selectedKey == key) null else key },
        centerLabel = totalLabel,
        centerCaption = "Total value",
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.xxs),
      ) {
        slices.forEach { slice ->
          val canonicalKey =
            com.example.core.designsystem.theme.PortfolioPalette.canonical(slice.colorKey)
          val isSelected = canonicalKey == selectedKey
          val rowDescription =
            "${slice.categoryName}, ${slice.sharePercent}% of portfolio, ${slice.value.formatted}"
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .testTag("home_allocation_row_$canonicalKey")
                .semantics(mergeDescendants = true) {
                  contentDescription = rowDescription
                  role = Role.Button
                  selected = isSelected
                  stateDescription = if (isSelected) "Selected" else "Not selected"
                  onClick(label = if (isSelected) "Clear selection" else "Show details") {
                    selectedKey = if (isSelected) null else canonicalKey
                    true
                  }
                }
                .clickable {
                  selectedKey = if (isSelected) null else canonicalKey
                }
                .padding(vertical = OceanSpacing.xxs, horizontal = OceanSpacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.size(if (isSelected) 10.dp else 8.dp)
                  .clip(CircleShape)
                  .background(colors[canonicalKey] ?: MaterialTheme.colorScheme.primary)
            )
            Text(
              text = slice.categoryName,
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
              maxLines = 1,
            )
            Text(
              text = "${slice.sharePercent.setScale(1, java.math.RoundingMode.HALF_UP)}%",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = slice.value.formatted,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = OceanSpacing.md),
          )
        }
      }
    }

    if (selectedSlice != null) {
      Spacer(modifier = Modifier.height(OceanSpacing.xs))
      Surface(
        modifier = Modifier.fillMaxWidth().testTag("home_allocation_selection"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(OceanSpacing.sm),
          horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(10.dp)
                .clip(CircleShape)
                .background(
                  colors[com.example.core.designsystem.theme.PortfolioPalette.canonical(
                    selectedSlice.colorKey
                  )] ?: MaterialTheme.colorScheme.primary
                )
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = selectedSlice.categoryName,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text =
                "${selectedSlice.holdingCount} holding${if (selectedSlice.holdingCount == 1) "" else "s"}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = selectedSlice.value.formatted,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text =
                "${selectedSlice.sharePercent.setScale(1, java.math.RoundingMode.HALF_UP)}% · ${selectedSlice.gainLoss.formatted}",
              style = MaterialTheme.typography.labelSmall,
              color =
                if (selectedSlice.gainLoss.minorUnits >= 0L) extended.positive
                else extended.negative,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HomeHoldingsCard(
  currencyCode: String,
  holdings: List<HomeHoldingRow>,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  val colors = remember(holdings, extended.isDark) {
    com.example.core.designsystem.theme.PortfolioPalette.assign(
      holdings.map { it.colorKey },
      extended.isDark,
    )
  }

  OceanCard(modifier = modifier) {
    Text(
      text = "Holdings · $currencyCode",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = "Largest ${holdings.size} by current value",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.sm))

    holdings.forEachIndexed { index, row ->
      if (index > 0) {
        Box(
          modifier =
            Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant)
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OceanSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier =
            Modifier.size(8.dp)
              .clip(CircleShape)
              .background(colors[row.colorKey] ?: MaterialTheme.colorScheme.primary)
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = row.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
          )
          Text(
            text =
              "${row.categoryName} · ${row.sharePercent.setScale(1, java.math.RoundingMode.HALF_UP)}%" +
                if (row.isStalePrice) " · price may be stale" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = row.value.formatted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
          )
          Text(
            text =
              "${row.gainLoss.formatted} (${row.returnPercent.setScale(1, java.math.RoundingMode.HALF_UP)}%)",
            style = MaterialTheme.typography.labelSmall,
            color = if (row.gainLoss.minorUnits >= 0L) extended.positive else extended.negative,
            maxLines = 1,
          )
        }
      }
    }
  }
}

// ---- Quick actions --------------------------------------------------------

@Composable
private fun HomeQuickActions(
  accountsCount: Int,
  transactionsCount: Int,
  budgetsCount: Int,
  loansCount: Int,
  investmentsCount: Int,
  reviewQueueCount: Int,
  onOpenAccounts: () -> Unit,
  onOpenTransactions: () -> Unit,
  onOpenIncomeManagement: () -> Unit,
  onOpenExpenseManagement: () -> Unit,
  onOpenSmartDetection: () -> Unit,
  onOpenReviewQueue: () -> Unit,
  onOpenBudgets: () -> Unit,
  onOpenInvestments: () -> Unit,
  onOpenLoans: () -> Unit,
  onOpenEmiCalculator: () -> Unit,
  onOpenReports: () -> Unit,
  modifier: Modifier = Modifier,
) {
  OceanCard(modifier = modifier) {
    Text(
      text = "Quick actions",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.sm))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
    ) {
      HomeActionButton(
        label = "Accounts ($accountsCount)",
        icon = Icons.Rounded.AccountBalance,
        onClick = onOpenAccounts,
        testTag = "home_accounts_button",
        modifier = Modifier.weight(1f),
      )
      HomeActionButton(
        label = "Ledger ($transactionsCount)",
        icon = Icons.Rounded.Assessment,
        onClick = onOpenTransactions,
        testTag = "home_ledger_button",
        modifier = Modifier.weight(1f),
      )
      HomeActionButton(
        label = "Income",
        icon = Icons.Rounded.ArrowDownward,
        onClick = onOpenIncomeManagement,
        testTag = "home_income_button",
        modifier = Modifier.weight(1f),
      )
    }
    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
    ) {
      HomeActionButton(
        label = "Expenses",
        icon = Icons.Rounded.ArrowUpward,
        onClick = onOpenExpenseManagement,
        testTag = "home_expense_button",
        modifier = Modifier.weight(1f),
      )
      HomeActionButton(
        label = "Detection",
        icon = Icons.Rounded.Shield,
        onClick = onOpenSmartDetection,
        testTag = "home_smart_detection_button",
        modifier = Modifier.weight(1f),
      )
      HomeActionButton(
        label = "Review ($reviewQueueCount)",
        icon = Icons.Rounded.RateReview,
        onClick = onOpenReviewQueue,
        testTag = "home_review_queue_button",
        modifier = Modifier.weight(1f),
      )
    }
    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
    ) {
      HomeActionButton(
        label = "Budgets ($budgetsCount)",
        icon = Icons.Rounded.PieChart,
        onClick = onOpenBudgets,
        testTag = "home_budgets_button",
        modifier = Modifier.weight(1f),
      )
      HomeActionButton(
        label = "Invest ($investmentsCount)",
        icon = Icons.Rounded.Assessment,
        onClick = onOpenInvestments,
        testTag = "home_investments_button",
        modifier = Modifier.weight(1f),
      )
      HomeActionButton(
        label = "Loans ($loansCount)",
        icon = Icons.Rounded.AccountBalance,
        onClick = onOpenLoans,
        testTag = "home_loans_button",
        modifier = Modifier.weight(1f),
      )
    }
    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
    ) {
      HomeActionButton(
        label = "EMI Calculator",
        icon = Icons.Rounded.Calculate,
        onClick = onOpenEmiCalculator,
        testTag = "home_emi_calculator_button",
        modifier = Modifier.weight(1f),
      )
      HomeActionButton(
        label = "Reports",
        icon = Icons.Rounded.Assessment,
        onClick = onOpenReports,
        testTag = "home_reports_button",
        modifier = Modifier.weight(2f),
      )
    }
  }
}

@Composable
private fun HomeActionButton(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.height(72.dp).testTag(testTag),
    onClick = onClick,
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, OceanTheme.colors.cardBorder),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(OceanSpacing.xs),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 2,
      )
    }
  }
}

// ---- Security -------------------------------------------------------------

@Composable
private fun HomeSecurityCard(
  uiState: HomeUiState,
  onOpenAuthReadiness: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors

  OceanCard(modifier = modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Rounded.VerifiedUser,
        contentDescription = null,
        tint = extended.positive,
      )
      Spacer(modifier = Modifier.width(OceanSpacing.xs))
      Text(
        text = "Protected workspace",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
      )
    }
    Spacer(modifier = Modifier.height(OceanSpacing.sm))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.md),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(OceanSpacing.xxs))
          Text(
            text = "No guest mode",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Text(
          text = "Mandatory sign-in and consent",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(OceanSpacing.xxs))
          Text(
            text = "Encrypted",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Text(
          text = "SQLCipher · backups disabled",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Spacer(modifier = Modifier.height(OceanSpacing.sm))
    OceanPrimaryButton(
      text = "Authentication status",
      onClick = onOpenAuthReadiness,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

// ---- Helpers --------------------------------------------------------------

private fun greetingForHour(): String {
  val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
  return when {
    hour < 12 -> "Good morning"
    hour < 17 -> "Good afternoon"
    else -> "Good evening"
  }
}

private fun initialsOf(name: String?): String {
  if (name.isNullOrBlank()) return "S"
  val parts = name.trim().split(" ").filter { it.isNotBlank() }
  return when (parts.size) {
    0 -> "S"
    1 -> parts.first().take(1).uppercase(Locale.getDefault())
    else ->
      (parts.first().take(1) + parts.last().take(1)).uppercase(Locale.getDefault())
  }
}

private fun formatTimestamp(millis: Long?): String {
  if (millis == null) return ""
  val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
  return formatter.format(Date(millis))
}
