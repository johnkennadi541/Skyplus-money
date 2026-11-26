package com.example.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation Compose route definitions for SKYPLUS MONEY.
 */
sealed interface SkyplusRoute {
  @Serializable data object Home : SkyplusRoute

  @Serializable data object Transactions : SkyplusRoute

  @Serializable data object Budgets : SkyplusRoute

  @Serializable data object ShoppingList : SkyplusRoute

  @Serializable data object Investments : SkyplusRoute

  @Serializable data object Reports : SkyplusRoute

  @Serializable data object More : SkyplusRoute

  @Serializable data object Tools : SkyplusRoute

  @Serializable data object Cards : SkyplusRoute

  @Serializable data object SavingsGoals : SkyplusRoute

  @Serializable data object Settings : SkyplusRoute

  @Serializable data object Profile : SkyplusRoute

  @Serializable data object AuthReadiness : SkyplusRoute

  @Serializable data object LegalPreview : SkyplusRoute

  @Serializable data object Accounts : SkyplusRoute

  @Serializable data class AccountDetail(val accountId: String) : SkyplusRoute

  @Serializable data object IncomeManagement : SkyplusRoute

  @Serializable data object ExpenseManagement : SkyplusRoute

  @Serializable data object SmartDetectionSettings : SkyplusRoute

  @Serializable data object NotificationAccessSetup : SkyplusRoute

  @Serializable data object SmsPolicyStatus : SkyplusRoute

  @Serializable data object ReviewQueue : SkyplusRoute

  @Serializable data object AccountMapping : SkyplusRoute

  @Serializable data object DetectionHistory : SkyplusRoute

  @Serializable data object Loans : SkyplusRoute

  @Serializable data class LoanDetail(val loanId: String) : SkyplusRoute

  @Serializable data object EmiCalculator : SkyplusRoute

  @Serializable data class InvestmentDetail(val holdingId: String) : SkyplusRoute
}

enum class TopLevelTab(
  val route: SkyplusRoute,
  @StringRes val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
) {
  HOME(
    route = SkyplusRoute.Home,
    labelRes = R.string.nav_home,
    selectedIcon = Icons.Rounded.Home,
    unselectedIcon = Icons.Outlined.Home,
    testTag = "nav_tab_home",
  ),
  TRANSACTIONS(
    route = SkyplusRoute.Transactions,
    labelRes = R.string.nav_transactions,
    selectedIcon = Icons.AutoMirrored.Rounded.ReceiptLong,
    unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
    testTag = "nav_tab_transactions",
  ),
  PORTFOLIO(
    route = SkyplusRoute.Investments,
    labelRes = R.string.nav_portfolio,
    selectedIcon = Icons.Rounded.PieChart,
    unselectedIcon = Icons.Outlined.PieChart,
    testTag = "nav_tab_portfolio",
  ),
  TOOLS(
    route = SkyplusRoute.Tools,
    labelRes = R.string.nav_tools,
    selectedIcon = Icons.Rounded.Build,
    unselectedIcon = Icons.Outlined.Build,
    testTag = "nav_tab_tools",
  ),
  REPORTS(
    route = SkyplusRoute.Reports,
    labelRes = R.string.nav_reports,
    selectedIcon = Icons.Rounded.Assessment,
    unselectedIcon = Icons.Outlined.Assessment,
    testTag = "nav_tab_reports",
  ),
  SETTINGS(
    route = SkyplusRoute.Settings,
    labelRes = R.string.nav_settings,
    selectedIcon = Icons.Rounded.Settings,
    unselectedIcon = Icons.Outlined.Settings,
    testTag = "nav_tab_settings",
  ),
}
