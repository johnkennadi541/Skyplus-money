package com.example.feature.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanSectionHeader
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.theme.OceanTheme
import com.example.core.designsystem.theme.SkyplusIconSize

/**
 * Tools hub.
 *
 * Stage 2 moves the secondary modules (accounts, budgets, shopping, detection, settings) behind a
 * single Tools tab so the bottom bar can carry the six primary sections. Every entry opens a real,
 * already implemented screen — nothing here is a placeholder.
 */
@Composable
fun ToolsScreen(
  onOpenAccounts: () -> Unit,
  onOpenBudgets: () -> Unit,
  onOpenLoans: () -> Unit,
  onOpenEmiCalculator: () -> Unit,
  onOpenShoppingList: () -> Unit,
  onOpenSmartDetection: () -> Unit,
  onOpenReports: () -> Unit,
  onOpenInvestments: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenCards: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .testTag("tools_screen")
        .verticalScroll(rememberScrollState())
        .padding(horizontal = OceanSpacing.screenHorizontal, vertical = OceanSpacing.md)
        .widthIn(max = 720.dp),
    verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
  ) {
    OceanSectionHeader(
      title = "Tools",
      subtitle = "Everything that is not on the main tabs lives here.",
    )

    ToolGroup(title = "Money") {
      ToolRow(
        icon = Icons.Rounded.AccountBalanceWallet,
        title = "Accounts",
        subtitle = "Bank, cash, wallet and card balances",
        testTag = "tools_accounts_item",
        onClick = onOpenAccounts,
      )
      ToolRow(
        icon = Icons.Rounded.PieChart,
        title = "Budgets",
        subtitle = "Monthly limits and spending alerts",
        testTag = "tools_budgets_item",
        onClick = onOpenBudgets,
      )
      ToolRow(
        icon = Icons.Rounded.CreditCard,
        title = "Credit cards",
        subtitle = "Limits, outstanding balances and utilisation",
        testTag = "tools_cards_item",
        onClick = onOpenCards,
      )
      ToolRow(
        icon = Icons.Rounded.ShoppingCart,
        title = "Shopping list",
        subtitle = "Plan a purchase and track the actual spend",
        testTag = "tools_shopping_item",
        onClick = onOpenShoppingList,
      )
    }

    ToolGroup(title = "Loans and planning") {
      ToolRow(
        icon = Icons.Rounded.List,
        title = "Loans",
        subtitle = "Active loans, schedules and payments",
        testTag = "tools_loans_item",
        onClick = onOpenLoans,
      )
      ToolRow(
        icon = Icons.Rounded.Calculate,
        title = "EMI calculator",
        subtitle = "Work out an EMI before you borrow",
        testTag = "tools_emi_item",
        onClick = onOpenEmiCalculator,
      )
    }

    ToolGroup(title = "Insight") {
      ToolRow(
        icon = Icons.Rounded.Assessment,
        title = "Reports",
        subtitle = "Income, expense, budgets, loans and exports",
        testTag = "tools_reports_item",
        onClick = onOpenReports,
      )
      ToolRow(
        icon = Icons.Rounded.PieChart,
        title = "Portfolio",
        subtitle = "Holdings, returns and price history",
        testTag = "tools_portfolio_item",
        onClick = onOpenInvestments,
      )
      ToolRow(
        icon = Icons.Rounded.Search,
        title = "Smart detection",
        subtitle = "On-device transaction detection and review queue",
        testTag = "tools_detection_item",
        onClick = onOpenSmartDetection,
      )
    }

    ToolGroup(title = "App") {
      ToolRow(
        icon = Icons.Rounded.Settings,
        title = "Settings",
        subtitle = "Theme, legal and account preferences",
        testTag = "tools_settings_item",
        onClick = onOpenSettings,
      )
    }

    Spacer(modifier = Modifier.height(OceanSpacing.lg))
  }
}

@Composable
private fun ToolGroup(title: String, content: @Composable () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = OceanSpacing.xs),
    )
    OceanCard { content() }
  }
}

@Composable
private fun ToolRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  testTag: String,
  onClick: () -> Unit,
) {
  val extended = OceanTheme.colors
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(OceanSpacing.sm),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = extended.accent,
      modifier = Modifier.size(SkyplusIconSize.md),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Icon(
      imageVector = Icons.Rounded.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(SkyplusIconSize.sm),
    )
  }
}
