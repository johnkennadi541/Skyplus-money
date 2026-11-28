package com.example.feature.dashboard

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.component.SkyplusSectionCard
import com.example.core.designsystem.component.OceanSectionHeader
import com.example.core.designsystem.component.SkyplusBottomSheet
import com.example.core.designsystem.component.SkyplusChartContainer
import com.example.core.designsystem.component.SkyplusDeltaTone
import com.example.core.designsystem.component.SkyplusEmptyState
import com.example.core.designsystem.component.SkyplusFinancialSummaryCard
import com.example.core.designsystem.component.SkyplusIconButton
import com.example.core.designsystem.component.SkyplusInlineMessage
import com.example.core.designsystem.component.SkyplusMessageTone
import com.example.core.designsystem.component.SkyplusMetricTile
import com.example.core.designsystem.component.SkyplusSkeletonCard
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.domain.model.AutoSavedTransactionNotice
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.core.designsystem.theme.SkyplusIconSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stage 2 Home dashboard.
 *
 * Every number here comes from [HomeDashboardUiState], which is produced from the real ledger by
 * [HomeDashboardViewModel]. The screen only lays things out; it performs no financial arithmetic.
 */
@Composable
fun HomeDashboardScreen(
  uiState: HomeDashboardUiState,
  onOpenAccounts: () -> Unit,
  onOpenTransactions: () -> Unit,
  onOpenPortfolio: () -> Unit,
  onOpenReports: () -> Unit,
  onOpenEmiCalculator: () -> Unit,
  onOpenLoans: () -> Unit,
  onOpenSmartDetection: () -> Unit,
  onOpenReviewQueue: () -> Unit,
  onToggleCard: (HomeCardId) -> Unit,
  onMoveCard: (HomeCardId, Boolean) -> Unit,
  onResetLayout: () -> Unit,
  modifier: Modifier = Modifier,
  autoSavedNotice: AutoSavedTransactionNotice? = null,
  onDismissAutoSavedNotice: () -> Unit = {},
) {
  var showCustomizeSheet by remember { mutableStateOf(false) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .testTag("home_screen")
        .verticalScroll(rememberScrollState())
        .padding(horizontal = OceanSpacing.screenHorizontal, vertical = OceanSpacing.md),
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
  ) {
    if (autoSavedNotice != null) {
      SkyplusSectionCard(
        modifier = Modifier.fillMaxWidth().testTag("auto_saved_notice_banner")
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = OceanTheme.colors.positive,
            )
            Spacer(modifier = Modifier.width(OceanSpacing.xs))
            Column {
              Text(
                text = "Auto-saved: ${FormatMoneyUseCase().format(autoSavedNotice.money)}",
                style = MaterialTheme.typography.titleSmall,
                color = OceanTheme.colors.positive,
              )
              Text(
                text = "${autoSavedNotice.merchantOrPayee} · ${autoSavedNotice.accountName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          SkyplusIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = "Dismiss notification",
            onClick = onDismissAutoSavedNotice,
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OceanSectionHeader(
        title = "Overview",
        subtitle = "This month · ${uiState.currencyCode}",
        modifier = Modifier.weight(1f),
      )
      SkyplusIconButton(
        icon = Icons.Rounded.Tune,
        contentDescription = "Customise dashboard",
        onClick = { showCustomizeSheet = true },
        modifier = Modifier.testTag("home_customize_button"),
      )
    }

    when {
      uiState.isLoading -> {
        SkyplusSkeletonCard()
        SkyplusSkeletonCard()
      }
      uiState.hasError -> {
        SkyplusInlineMessage(
          text = "Could not load your dashboard. Pull the figures again from Reports.",
          tone = SkyplusMessageTone.ERROR,
          icon = Icons.Rounded.TrendingDown,
        )
      }
      uiState.isEmpty -> {
        SkyplusEmptyState(
          title = "No accounts yet",
          message = "Add your first account and record an income or expense to see your balance here.",
          icon = Icons.Rounded.AccountBalanceWallet,
          action = {
            com.example.core.designsystem.component.SkyplusPrimaryButton(
              text = "Open accounts",
              onClick = onOpenAccounts,
            )
          },
        )
        QuickActionsCard(
          onOpenAccounts = onOpenAccounts,
          onOpenTransactions = onOpenTransactions,
          onOpenPortfolio = onOpenPortfolio,
          onOpenReports = onOpenReports,
          onOpenEmiCalculator = onOpenEmiCalculator,
          onOpenLoans = onOpenLoans,
          onOpenSmartDetection = onOpenSmartDetection,
          onOpenReviewQueue = onOpenReviewQueue,
        )
      }
      else -> {
        uiState.cards.filterNot { uiState.hiddenCards.contains(it) }.forEach { card ->
          when (card) {
            HomeCardId.SUMMARY -> SummaryCard(uiState = uiState, onOpenAccounts = onOpenAccounts)
            HomeCardId.NET_WORTH -> NetWorthCard(uiState = uiState, onOpenPortfolio = onOpenPortfolio)
            HomeCardId.CASH_FLOW -> CashFlowCard(uiState = uiState)
            HomeCardId.CATEGORY_BREAKDOWN -> CategoryCard(uiState = uiState)
            HomeCardId.RECENT_TRANSACTIONS ->
              RecentTransactionsCard(uiState = uiState, onOpenTransactions = onOpenTransactions)
          }
        }

        QuickActionsCard(
          onOpenAccounts = onOpenAccounts,
          onOpenTransactions = onOpenTransactions,
          onOpenPortfolio = onOpenPortfolio,
          onOpenReports = onOpenReports,
          onOpenEmiCalculator = onOpenEmiCalculator,
          onOpenLoans = onOpenLoans,
          onOpenSmartDetection = onOpenSmartDetection,
          onOpenReviewQueue = onOpenReviewQueue,
        )
      }
    }

    Spacer(modifier = Modifier.height(OceanSpacing.xs))
  }

  if (showCustomizeSheet) {
    CustomiseSheet(
      uiState = uiState,
      onToggleCard = onToggleCard,
      onMoveCard = onMoveCard,
      onResetLayout = onResetLayout,
      onDismiss = { showCustomizeSheet = false },
    )
  }
}

@Composable
private fun SummaryCard(uiState: HomeDashboardUiState, onOpenAccounts: () -> Unit) {
  SkyplusFinancialSummaryCard(
    label = "Total balance",
    amount = uiState.totalBalanceText,
    caption = "Across your active accounts",
    icon = Icons.Rounded.AccountBalanceWallet,
    onClick = onOpenAccounts,
    modifier = Modifier.testTag("home_total_balance_card"),
  )

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(OceanSpacing.md),
  ) {
    SkyplusMetricTile(
      label = "Income",
      value = uiState.monthlyIncomeText,
      tone = SkyplusDeltaTone.POSITIVE,
      modifier = Modifier.weight(1f),
    )
    SkyplusMetricTile(
      label = "Expenses",
      value = uiState.monthlyExpenseText,
      tone = SkyplusDeltaTone.NEGATIVE,
      modifier = Modifier.weight(1f),
    )
    SkyplusMetricTile(
      label = "Saved",
      value = uiState.netSavingsText,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun NetWorthCard(uiState: HomeDashboardUiState, onOpenPortfolio: () -> Unit) {
  SkyplusFinancialSummaryCard(
    label = "Net worth",
    amount = uiState.netWorthText,
    caption = uiState.netWorthCaption,
    icon = Icons.Rounded.PieChart,
    onClick = onOpenPortfolio,
    modifier = Modifier.testTag("home_net_worth_card"),
  )
}

@Composable
private fun CashFlowCard(uiState: HomeDashboardUiState) {
  val extended = OceanTheme.colors
  SkyplusChartContainer(
    title = "Income vs expenses",
    subtitle = "Last ${uiState.cashFlow.size} months",
    isEmpty = uiState.cashFlow.isEmpty(),
    emptyMessage = "No completed transactions in this period yet.",
    modifier = Modifier.testTag("home_cash_flow_card"),
  ) {
    val maxValue =
      uiState.cashFlow.maxOfOrNull { maxOf(it.incomeMinor, it.expenseMinor) } ?: 0L
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .height(150.dp)
          .semantics {
            contentDescription =
              "Income and expense chart for the last ${uiState.cashFlow.size} months"
          },
      horizontalArrangement = Arrangement.spacedBy(OceanSpacing.xs),
      verticalAlignment = Alignment.Bottom,
    ) {
      uiState.cashFlow.forEach { bar ->
        Column(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Bottom,
        ) {
          Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
          ) {
            ChartBar(
              fraction = fractionOf(bar.incomeMinor, maxValue),
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f),
            )
            ChartBar(
              fraction = fractionOf(bar.expenseMinor, maxValue),
              color = extended.negative,
              modifier = Modifier.weight(1f),
            )
          }
          Spacer(modifier = Modifier.height(OceanSpacing.xxs))
          Text(
            text = bar.monthLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun ChartBar(fraction: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
  Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .fillMaxHeight(fraction.coerceIn(0.02f, 1f))
          .clip(RoundedCornerShape(4.dp))
          .background(color)
    )
  }
}

private fun fractionOf(value: Long, maxValue: Long): Float =
  if (maxValue <= 0L) 0f else (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)

@Composable
private fun CategoryCard(uiState: HomeDashboardUiState) {
  val extended = OceanTheme.colors
  SkyplusChartContainer(
    title = "Where the money went",
    subtitle = "Expenses by category",
    isEmpty = uiState.categories.isEmpty(),
    emptyMessage = "No expenses recorded in this period.",
    modifier = Modifier.testTag("home_category_card"),
  ) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
      uiState.categories.forEach { slice ->
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = slice.category,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(OceanSpacing.xs))
            Text(
              text = slice.amountText,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(modifier = Modifier.height(OceanSpacing.xxs))
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
          ) {
            Box(
              modifier =
                Modifier.fillMaxWidth(slice.percentage / 100f)
                  .fillMaxHeight()
                  .clip(RoundedCornerShape(3.dp))
                  .background(MaterialTheme.colorScheme.primary)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RecentTransactionsCard(uiState: HomeDashboardUiState, onOpenTransactions: () -> Unit) {
  SkyplusChartContainer(
    title = "Recent transactions",
    subtitle = "This month",
    isEmpty = uiState.recentTransactions.isEmpty(),
    emptyMessage = "Nothing recorded yet this month.",
    minHeight = 60,
    actions = {
      SkyplusIconButton(
        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
        contentDescription = "Open all transactions",
        onClick = onOpenTransactions,
      )
    },
    modifier = Modifier.testTag("home_recent_transactions_card"),
  ) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm)) {
      uiState.recentTransactions.forEach { row ->
        RecentTransactionRow(row = row)
      }
    }
  }
}

@Composable
private fun RecentTransactionRow(row: HomeTransactionRow) {
  val extended = OceanTheme.colors
  val dateText =
    remember(row.dateMillis) {
      SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(row.dateMillis))
    }
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
  ) {
    Box(
      modifier =
        Modifier.size(SkyplusIconSize.lg)
          .clip(CircleShape)
          .background(
            if (row.isIncome) extended.positive.copy(alpha = 0.16f)
            else extended.negative.copy(alpha = 0.16f)
          ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = if (row.isIncome) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
        contentDescription = null,
        tint = if (row.isIncome) extended.positive else extended.negative,
        modifier = Modifier.size(SkyplusIconSize.sm),
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = row.title,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "${row.category} · $dateText",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = row.amountText,
      style = MaterialTheme.typography.labelMedium,
      color = if (row.isIncome) extended.positive else MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
    )
  }
}

@Composable
private fun QuickActionsCard(
  onOpenAccounts: () -> Unit,
  onOpenTransactions: () -> Unit,
  onOpenPortfolio: () -> Unit,
  onOpenReports: () -> Unit,
  onOpenEmiCalculator: () -> Unit,
  onOpenLoans: () -> Unit,
  onOpenSmartDetection: () -> Unit,
  onOpenReviewQueue: () -> Unit,
) {
  SkyplusChartContainer(
    title = "Quick actions",
    subtitle = "Jump straight to a task",
    minHeight = 0,
    modifier = Modifier.testTag("home_quick_actions_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        QuickAction(
          icon = Icons.Rounded.AccountBalanceWallet,
          label = "Accounts",
          testTag = "home_accounts_button",
          onClick = onOpenAccounts,
          modifier = Modifier.weight(1f),
        )
        QuickAction(
          icon = Icons.Rounded.List,
          label = "Loans",
          testTag = "home_loans_button",
          onClick = onOpenLoans,
          modifier = Modifier.weight(1f),
        )
        QuickAction(
          icon = Icons.Rounded.PieChart,
          label = "Portfolio",
          testTag = "home_investments_button",
          onClick = onOpenPortfolio,
          modifier = Modifier.weight(1f),
        )
        QuickAction(
          icon = Icons.Rounded.Calculate,
          label = "EMI",
          testTag = "home_emi_calculator_button",
          onClick = onOpenEmiCalculator,
          modifier = Modifier.weight(1f),
        )
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        QuickAction(
          icon = Icons.Rounded.Search,
          label = "Detection",
          testTag = "home_smart_detection_button",
          onClick = onOpenSmartDetection,
          modifier = Modifier.weight(1f),
        )
        QuickAction(
          icon = Icons.Rounded.KeyboardArrowUp,
          label = "Review",
          testTag = "home_review_queue_button",
          onClick = onOpenReviewQueue,
          modifier = Modifier.weight(1f),
        )
        QuickAction(
          icon = Icons.AutoMirrored.Rounded.ReceiptLong,
          label = "Entries",
          testTag = "home_transactions_button",
          onClick = onOpenTransactions,
          modifier = Modifier.weight(1f),
        )
        QuickAction(
          icon = Icons.Rounded.Assessment,
          label = "Reports",
          testTag = "home_reports_button",
          onClick = onOpenReports,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun QuickAction(
  icon: ImageVector,
  label: String,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extended = OceanTheme.colors
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier =
        Modifier.size(SkyplusIconSize.minTouchTarget)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      SkyplusIconButton(
        icon = icon,
        contentDescription = label,
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
        tint = MaterialTheme.colorScheme.onTertiaryContainer,
      )
    }
    Spacer(modifier = Modifier.height(OceanSpacing.xxs))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun CustomiseSheet(
  uiState: HomeDashboardUiState,
  onToggleCard: (HomeCardId) -> Unit,
  onMoveCard: (HomeCardId, Boolean) -> Unit,
  onResetLayout: () -> Unit,
  onDismiss: () -> Unit,
) {
  SkyplusBottomSheet(onDismissRequest = onDismiss, title = "Customise dashboard") {
    Text(
      text = "Choose which cards appear on Home and in what order.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(OceanSpacing.xs))
    uiState.cards.forEachIndexed { index, card ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = cardLabel(card),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
        )
        SkyplusIconButton(
          icon = Icons.Rounded.KeyboardArrowUp,
          contentDescription = "Move ${cardLabel(card)} up",
          onClick = { onMoveCard(card, true) },
          enabled = index > 0,
        )
        SkyplusIconButton(
          icon = Icons.Rounded.KeyboardArrowDown,
          contentDescription = "Move ${cardLabel(card)} down",
          onClick = { onMoveCard(card, false) },
          enabled = index < uiState.cards.lastIndex,
        )
        Switch(
          checked = !uiState.hiddenCards.contains(card),
          onCheckedChange = { onToggleCard(card) },
        )
      }
    }
    Spacer(modifier = Modifier.height(OceanSpacing.sm))
    com.example.core.designsystem.component.SkyplusSecondaryButton(
      text = "Reset layout",
      onClick = onResetLayout,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

private fun cardLabel(card: HomeCardId): String =
  when (card) {
    HomeCardId.SUMMARY -> "Balance and monthly figures"
    HomeCardId.NET_WORTH -> "Net worth"
    HomeCardId.CASH_FLOW -> "Income vs expenses chart"
    HomeCardId.CATEGORY_BREAKDOWN -> "Category breakdown"
    HomeCardId.RECENT_TRANSACTIONS -> "Recent transactions"
  }
