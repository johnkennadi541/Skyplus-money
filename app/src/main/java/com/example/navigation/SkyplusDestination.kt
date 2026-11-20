package com.example.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PieChart
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
  BUDGET(
    route = SkyplusRoute.Budgets,
    labelRes = R.string.nav_budget,
    selectedIcon = Icons.Rounded.PieChart,
    unselectedIcon = Icons.Outlined.PieChart,
    testTag = "nav_tab_budget",
  ),
  SHOPPING_LIST(
    route = SkyplusRoute.ShoppingList,
    labelRes = R.string.nav_shopping_list,
    selectedIcon = Icons.Rounded.ShoppingCart,
    unselectedIcon = Icons.Outlined.ShoppingCart,
    testTag = "nav_tab_shopping_list",
  ),
}
