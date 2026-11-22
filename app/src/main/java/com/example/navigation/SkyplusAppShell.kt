package com.example.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.core.designsystem.component.BadgeTone
import com.example.core.designsystem.component.OceanCard
import com.example.core.designsystem.component.OceanStatusBadge
import com.example.core.designsystem.theme.OceanSpacing
import com.example.core.designsystem.component.SkyplusNavItem
import com.example.core.designsystem.component.SkyplusNavigationBar
import com.example.core.designsystem.theme.OceanTheme
import com.example.core.security.DatabaseSecurityException
import com.example.domain.model.AuthSessionState
import com.example.domain.model.AuthenticatedUser
import com.example.domain.model.ThemePreference
import com.example.feature.accounts.AccountDetailScreen
import com.example.feature.accounts.AccountsContent
import com.example.feature.accounts.AccountsScreen
import com.example.feature.accounts.AccountsViewModel
import com.example.feature.authentication.AuthFoundationScreen
import com.example.feature.authentication.AuthLoadingScreen
import com.example.feature.authentication.AuthUiState
import com.example.feature.authentication.GoogleAuthScreen
import com.example.feature.budgets.BudgetsScreen
import com.example.feature.budgets.BudgetsViewModel
import com.example.feature.consent.LegalConsentPreviewScreen
import com.example.feature.consent.LegalConsentScreen
import com.example.feature.dashboard.HomeDashboardScreen
import com.example.feature.dashboard.HomeDashboardUiState
import com.example.feature.dashboard.HomeDashboardViewModel
import com.example.feature.dashboard.HomeFoundationUiState
import com.example.feature.investments.InvestmentDetailContent
import com.example.feature.investments.InvestmentDetailScreen
import com.example.feature.investments.InvestmentsContent
import com.example.feature.investments.InvestmentsScreen
import com.example.feature.investments.InvestmentsViewModel
import com.example.feature.loans.EmiCalculatorContent
import com.example.feature.loans.EmiCalculatorScreen
import com.example.feature.loans.LoanDetailContent
import com.example.feature.loans.LoanDetailScreen
import com.example.feature.loans.LoansContent
import com.example.feature.loans.LoansScreen
import com.example.feature.loans.LoansViewModel
import com.example.feature.reports.ReportsContent
import com.example.feature.reports.ReportsScreen
import com.example.feature.reports.ReportsViewModel
import com.example.feature.reports.emptyReportSnapshot
import com.example.feature.settings.MoreScreen
import com.example.feature.settings.ProfileScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.shopping.ShoppingListContent
import com.example.feature.shopping.ShoppingListScreen
import com.example.feature.shopping.ShoppingViewModel
import com.example.domain.usecase.ShoppingWorkspaceSnapshot
import com.example.feature.smartdetection.AccountMappingScreen
import com.example.feature.smartdetection.DetectionHistoryScreen
import com.example.feature.smartdetection.NotificationAccessSetupScreen
import com.example.feature.smartdetection.ReviewQueueScreen
import com.example.feature.smartdetection.SmartDetectionSettingsScreen
import com.example.feature.smartdetection.SmartDetectionViewModel
import com.example.feature.smartdetection.SmsPolicyStatusScreen
import com.example.feature.transactions.ExpenseManagementScreen
import com.example.feature.transactions.IncomeManagementScreen
import com.example.feature.transactions.TransactionsContent
import com.example.feature.transactions.TransactionsFilterState
import com.example.feature.transactions.TransactionsScreen
import com.example.feature.tools.ToolsScreen
import com.example.feature.transactions.TransactionsViewModel

/**
 * Mandatory root authentication gate for SKYPLUS MONEY.
 * Unauthenticated users cannot access financial tabs or Guest Mode.
 */
@Composable
fun SkyplusAuthGateRoot(
  authUiState: AuthUiState,
  homeUiState: HomeFoundationUiState,
  themePreference: ThemePreference,
  onContinueWithGoogle: (android.content.Context) -> Unit,
  onDismissAuthFeedback: () -> Unit,
  onSignOut: () -> Unit,
  onToggleTheme: () -> Unit,
  onSelectTheme: (ThemePreference) -> Unit,
  onAcceptConsent: () -> Unit,
  modifier: Modifier = Modifier,
  onRetryConsentCheck: (() -> Unit)? = null,
  databaseSecurityError: DatabaseSecurityException? = null,
  onRetryDatabaseInit: (() -> Unit)? = null,
  accountsViewModel: AccountsViewModel? = null,
  transactionsViewModel: TransactionsViewModel? = null,
  smartDetectionViewModel: SmartDetectionViewModel? = null,
  budgetsViewModel: BudgetsViewModel? = null,
  loansViewModel: LoansViewModel? = null,
  investmentsViewModel: InvestmentsViewModel? = null,
  shoppingViewModel: ShoppingViewModel? = null,
  reportsViewModel: ReportsViewModel? = null,
  homeDashboardViewModel: HomeDashboardViewModel? = null,
) {
  if (databaseSecurityError != null) {
    DatabaseSecurityErrorScreen(
      error = databaseSecurityError,
      onRetry = onRetryDatabaseInit,
      modifier = modifier,
    )
    return
  }

  when (val session = authUiState.sessionState) {
    AuthSessionState.Loading -> AuthLoadingScreen(modifier = modifier)
    AuthSessionState.Unauthenticated ->
      GoogleAuthScreen(
        uiState = authUiState,
        onContinueWithGoogle = onContinueWithGoogle,
        onDismissFeedback = onDismissAuthFeedback,
        onToggleTheme = onToggleTheme,
        modifier = modifier,
      )
    is AuthSessionState.Authenticated ->
      key(session.user.uid) {
        if (authUiState.isConsentLoading) {
          AuthLoadingScreen(modifier = modifier)
        } else if (authUiState.requiresConsent) {
          LegalConsentScreen(
            onAccept = onAcceptConsent,
            onSignOut = onSignOut,
            consentErrorMessageRes = authUiState.consentErrorMessageRes,
            isSavingConsent = authUiState.isSavingConsent,
            onRetryConsentCheck = onRetryConsentCheck,
            modifier = modifier,
          )
        } else {
          SkyplusAppShell(
            homeUiState = homeUiState,
            themePreference = themePreference,
            onToggleTheme = onToggleTheme,
            onSelectTheme = onSelectTheme,
            authenticatedUser = session.user,
            onSignOut = onSignOut,
            accountsViewModel = accountsViewModel,
            transactionsViewModel = transactionsViewModel,
            smartDetectionViewModel = smartDetectionViewModel,
            budgetsViewModel = budgetsViewModel,
            loansViewModel = loansViewModel,
            investmentsViewModel = investmentsViewModel,
            shoppingViewModel = shoppingViewModel,
            reportsViewModel = reportsViewModel,
            homeDashboardViewModel = homeDashboardViewModel,
            modifier = modifier,
          )
        }
      }
  }
}

@Composable
private fun DatabaseSecurityErrorScreen(
  error: DatabaseSecurityException,
  onRetry: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize().testTag("database_security_error_screen"),
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(OceanSpacing.md),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(OceanSpacing.md),
      ) {
        OceanCard {
          OceanStatusBadge(text = "Security Lock Active", tone = BadgeTone.WARNING)
          Spacer(modifier = Modifier.height(OceanSpacing.sm))
          Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = OceanTheme.colors.negative,
          )
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(
            text = stringResource(R.string.security_db_error_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(OceanSpacing.xs))
          Text(
            text =
              if (error.isRecoverable) stringResource(R.string.security_db_error_recoverable)
              else stringResource(R.string.security_db_error_corrupted),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (error.isRecoverable && onRetry != null) {
            Spacer(modifier = Modifier.height(OceanSpacing.md))
            Button(
              onClick = onRetry,
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("retry_db_unlock_button"),
            ) {
              Text(text = stringResource(R.string.security_db_retry_button))
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyplusAppShell(
  homeUiState: HomeFoundationUiState,
  themePreference: ThemePreference,
  onToggleTheme: () -> Unit,
  onSelectTheme: (ThemePreference) -> Unit,
  modifier: Modifier = Modifier,
  navController: NavHostController = rememberNavController(),
  authenticatedUser: AuthenticatedUser? = null,
  onSignOut: (() -> Unit)? = null,
  accountsViewModel: AccountsViewModel? = null,
  transactionsViewModel: TransactionsViewModel? = null,
  smartDetectionViewModel: SmartDetectionViewModel? = null,
  budgetsViewModel: BudgetsViewModel? = null,
  loansViewModel: LoansViewModel? = null,
  investmentsViewModel: InvestmentsViewModel? = null,
  shoppingViewModel: ShoppingViewModel? = null,
  reportsViewModel: ReportsViewModel? = null,
  homeDashboardViewModel: HomeDashboardViewModel? = null,
) {
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = backStackEntry?.destination
  val showTopLevelNav =
    TopLevelTab.entries.any { tab -> currentDestination?.hasRoute(tab.route::class) == true } ||
      currentDestination == null

  val navigateToTab: (TopLevelTab) -> Unit = { tab ->
    navController.navigate(tab.route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  val accounts = accountsViewModel?.accounts?.collectAsStateWithLifecycle()?.value ?: emptyList()
  val transactions = transactionsViewModel?.allTransactions?.collectAsStateWithLifecycle()?.value ?: emptyList()
  val reviewQueue = smartDetectionViewModel?.reviewQueue?.collectAsStateWithLifecycle()?.value ?: emptyList()
  val autoSavedNotice = smartDetectionViewModel?.latestAutoSavedNotice?.collectAsStateWithLifecycle()?.value
  val budgetStatuses = budgetsViewModel?.budgets?.collectAsStateWithLifecycle()?.value ?: emptyList()
  val loans = loansViewModel?.loans?.collectAsStateWithLifecycle()?.value ?: emptyList()
  val investments = investmentsViewModel?.holdings?.collectAsStateWithLifecycle()?.value ?: emptyList()

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets.safeDrawing,
      containerColor = MaterialTheme.colorScheme.background,
      topBar = {
        if (showTopLevelNav) {
          CenterAlignedTopAppBar(
            title = {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            actions = {
              IconButton(
                onClick = { navController.navigate(SkyplusRoute.Profile) },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("profile_menu_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.AccountCircle,
                  contentDescription = stringResource(R.string.cd_profile_menu),
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
              IconButton(
                onClick = onToggleTheme,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("theme_toggle_button"),
              ) {
                Icon(
                  imageVector =
                    if (OceanTheme.colors.isDark) Icons.Rounded.LightMode
                    else Icons.Rounded.DarkMode,
                  contentDescription = stringResource(R.string.cd_toggle_theme),
                  tint = OceanTheme.colors.gold,
                )
              }
            },
            colors =
              TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
              ),
          )
        }
      },
      bottomBar = {
        if (showTopLevelNav && !useNavigationRail) {
          val selectedTabIndex =
            TopLevelTab.entries.indexOfFirst { tab ->
              currentDestination?.hasRoute(tab.route::class) == true
            }
          SkyplusNavigationBar(
            items =
              TopLevelTab.entries.map { tab ->
                SkyplusNavItem(
                  label = stringResource(tab.labelRes),
                  selectedIcon = tab.selectedIcon,
                  unselectedIcon = tab.unselectedIcon,
                  testTag = tab.testTag,
                )
              },
            selectedIndex = if (selectedTabIndex < 0) 0 else selectedTabIndex,
            onSelect = { index -> navigateToTab(TopLevelTab.entries[index]) },
            modifier = Modifier.testTag("bottom_navigation_bar"),
          )
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (showTopLevelNav && useNavigationRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight().testTag("navigation_rail"),
          ) {
            TopLevelTab.entries.forEach { tab ->
              val selected = currentDestination?.hasRoute(tab.route::class) == true
              val label = stringResource(tab.labelRes)
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(tab) },
                modifier = Modifier.testTag(tab.testTag),
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = {
                  Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          NavHost(
            navController = navController,
            startDestination = SkyplusRoute.Home,
            enterTransition = { fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) },
            exitTransition = { fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) },
          ) {
            composable<SkyplusRoute.Home> {
              val dashboardState =
                homeDashboardViewModel
                  ?.uiState
                  ?.collectAsStateWithLifecycle()
                  ?.value
                  ?: HomeDashboardUiState(isLoading = false, isEmpty = true)
              HomeDashboardScreen(
                uiState = dashboardState,
                onOpenAccounts = { navController.navigate(SkyplusRoute.Accounts) },
                onOpenTransactions = { navigateToTab(TopLevelTab.TRANSACTIONS) },
                onOpenPortfolio = { navigateToTab(TopLevelTab.PORTFOLIO) },
                onOpenReports = { navigateToTab(TopLevelTab.REPORTS) },
                onOpenEmiCalculator = { navController.navigate(SkyplusRoute.EmiCalculator) },
                onOpenLoans = { navController.navigate(SkyplusRoute.Loans) },
                onOpenSmartDetection = { navController.navigate(SkyplusRoute.SmartDetectionSettings) },
                onOpenReviewQueue = { navController.navigate(SkyplusRoute.ReviewQueue) },
                onToggleCard = { card -> homeDashboardViewModel?.toggleCardVisibility(card) },
                onMoveCard = { card, moveUp ->
                  homeDashboardViewModel?.moveCard(card, moveUp)
                },
                onResetLayout = { homeDashboardViewModel?.resetCardLayout() },
                autoSavedNotice = autoSavedNotice,
                onDismissAutoSavedNotice = {
                  smartDetectionViewModel?.dismissAutoSavedNotice()
                },
              )
            }
            composable<SkyplusRoute.Tools> {
              ToolsScreen(
                onOpenAccounts = { navController.navigate(SkyplusRoute.Accounts) },
                onOpenBudgets = { navController.navigate(SkyplusRoute.Budgets) },
                onOpenLoans = { navController.navigate(SkyplusRoute.Loans) },
                onOpenEmiCalculator = { navController.navigate(SkyplusRoute.EmiCalculator) },
                onOpenShoppingList = { navController.navigate(SkyplusRoute.ShoppingList) },
                onOpenSmartDetection = { navController.navigate(SkyplusRoute.SmartDetectionSettings) },
                onOpenReports = { navigateToTab(TopLevelTab.REPORTS) },
                onOpenInvestments = { navigateToTab(TopLevelTab.PORTFOLIO) },
                onOpenSettings = { navController.navigate(SkyplusRoute.Settings) },
              )
            }
            composable<SkyplusRoute.Transactions> {
              if (transactionsViewModel != null) {
                TransactionsScreen(
                  viewModel = transactionsViewModel,
                  onOpenIncomeManagement = { navController.navigate(SkyplusRoute.IncomeManagement) },
                  onOpenExpenseManagement = { navController.navigate(SkyplusRoute.ExpenseManagement) },
                  onOpenReports = { navController.navigate(SkyplusRoute.Reports) },
                )
              } else {
                TransactionsContent(
                  transactions = emptyList(),
                  accounts = emptyList(),
                  filterState = TransactionsFilterState(),
                  onOpenReports = { navController.navigate(SkyplusRoute.Reports) },
                )
              }
            }
            composable<SkyplusRoute.Budgets> {
              if (budgetsViewModel != null) {
                BudgetsScreen(viewModel = budgetsViewModel)
              } else {
                com.example.feature.budgets.BudgetsContent(
                  budgets = emptyList(),
                  summaries = emptyMap(),
                  accounts = emptyList(),
                )
              }
            }
            composable<SkyplusRoute.ShoppingList> {
              if (shoppingViewModel != null) {
                ShoppingListScreen(viewModel = shoppingViewModel)
              } else {
                ShoppingListContent(
                  workspace = ShoppingWorkspaceSnapshot(),
                  accounts = accounts,
                )
              }
            }
            composable<SkyplusRoute.Profile> {
              ProfileScreen(
                authenticatedUser = authenticatedUser,
                onOpenSettings = { navController.navigate(SkyplusRoute.Settings) },
                onOpenAuthReadiness = { navController.navigate(SkyplusRoute.AuthReadiness) },
                onOpenSmartDetection = { navController.navigate(SkyplusRoute.SmartDetectionSettings) },
                onNavigateBack = { navController.popBackStack() },
                onOpenInvestments = { navController.navigate(SkyplusRoute.Investments) },
                onSignOut = onSignOut,
              )
            }
            composable<SkyplusRoute.Investments> {
              if (investmentsViewModel != null) {
                InvestmentsScreen(
                  viewModel = investmentsViewModel,
                  onNavigateBack = { navController.popBackStack() },
                  onOpenHoldingDetail = { holdingId ->
                    navController.navigate(SkyplusRoute.InvestmentDetail(holdingId))
                  },
                )
              } else {
                InvestmentsContent(
                  performances = emptyList(),
                  summaries = emptyMap(),
                  accounts = emptyList(),
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
            composable<SkyplusRoute.InvestmentDetail> { entry ->
              val detailRoute = entry.toRoute<SkyplusRoute.InvestmentDetail>()
              if (investmentsViewModel != null) {
                InvestmentDetailScreen(
                  holdingId = detailRoute.holdingId,
                  viewModel = investmentsViewModel,
                  onNavigateBack = { navController.popBackStack() },
                )
              } else {
                InvestmentDetailContent(
                  holding = null,
                  performance = null,
                  transactions = emptyList(),
                  priceHistory = emptyList(),
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
            composable<SkyplusRoute.Reports> {
              if (reportsViewModel != null) {
                ReportsScreen(
                  viewModel = reportsViewModel,
                  onNavigateBack = { navController.popBackStack() },
                  defaultRecipientEmail = authenticatedUser?.email.orEmpty(),
                )
              } else {
                ReportsContent(
                  snapshot = emptyReportSnapshot(),
                  defaultRecipientEmail = authenticatedUser?.email.orEmpty(),
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
            composable<SkyplusRoute.More> {
              MoreScreen(
                onOpenSettings = { navController.navigate(SkyplusRoute.Settings) },
                onOpenAuthReadiness = { navController.navigate(SkyplusRoute.AuthReadiness) },
                onOpenLegalPreview = { navController.navigate(SkyplusRoute.LegalPreview) },
                onOpenAccounts = { navController.navigate(SkyplusRoute.Accounts) },
                onOpenSmartDetection = { navController.navigate(SkyplusRoute.SmartDetectionSettings) },
                onSignOut = onSignOut,
              )
            }
            composable<SkyplusRoute.Settings> {
              SettingsScreen(
                selectedTheme = themePreference,
                onSelectTheme = onSelectTheme,
                onNavigateBack = { navController.popBackStack() },
              )
            }
            composable<SkyplusRoute.AuthReadiness> {
              AuthFoundationScreen(
                onNavigateBack = { navController.popBackStack() },
                authenticatedUser = authenticatedUser,
                onSignOut = onSignOut ?: {},
              )
            }
            composable<SkyplusRoute.LegalPreview> {
              LegalConsentPreviewScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<SkyplusRoute.Accounts> {
              if (accountsViewModel != null) {
                AccountsScreen(
                  viewModel = accountsViewModel,
                  onOpenAccountDetail = { accountId ->
                    navController.navigate(SkyplusRoute.AccountDetail(accountId))
                  },
                )
              } else {
                AccountsContent(accounts = emptyList())
              }
            }
            composable<SkyplusRoute.AccountDetail> { entry ->
              val detailRoute = entry.toRoute<SkyplusRoute.AccountDetail>()
              if (accountsViewModel != null && transactionsViewModel != null) {
                AccountDetailScreen(
                  accountId = detailRoute.accountId,
                  accountsViewModel = accountsViewModel,
                  transactionsViewModel = transactionsViewModel,
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
            composable<SkyplusRoute.IncomeManagement> {
              if (transactionsViewModel != null) {
                IncomeManagementScreen(
                  viewModel = transactionsViewModel,
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
            composable<SkyplusRoute.ExpenseManagement> {
              if (transactionsViewModel != null) {
                ExpenseManagementScreen(
                  viewModel = transactionsViewModel,
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
            composable<SkyplusRoute.SmartDetectionSettings> {
              if (smartDetectionViewModel != null) {
                SmartDetectionSettingsScreen(
                  viewModel = smartDetectionViewModel,
                  onOpenReviewQueue = { navController.navigate(SkyplusRoute.ReviewQueue) },
                  onOpenAccountMapping = { navController.navigate(SkyplusRoute.AccountMapping) },
                  onOpenDetectionHistory = { navController.navigate(SkyplusRoute.DetectionHistory) },
                  onOpenNotificationSetup = { navController.navigate(SkyplusRoute.NotificationAccessSetup) },
                  onOpenSmsPolicyStatus = { navController.navigate(SkyplusRoute.SmsPolicyStatus) },
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
            composable<SkyplusRoute.NotificationAccessSetup> {
              if (smartDetectionViewModel != null) {
                NotificationAccessSetupScreen(
                  viewModel = smartDetectionViewModel,
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
            composable<SkyplusRoute.SmsPolicyStatus> {
              SmsPolicyStatusScreen(
                onOpenNotificationSetup = { navController.navigate(SkyplusRoute.NotificationAccessSetup) },
                onNavigateBack = { navController.popBackStack() },
              )
            }
            composable<SkyplusRoute.ReviewQueue> {
              if (smartDetectionViewModel != null) {
                ReviewQueueScreen(
                  viewModel = smartDetectionViewModel,
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
            composable<SkyplusRoute.AccountMapping> {
              if (smartDetectionViewModel != null) {
                AccountMappingScreen(
                  viewModel = smartDetectionViewModel,
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
            composable<SkyplusRoute.DetectionHistory> {
              if (smartDetectionViewModel != null) {
                DetectionHistoryScreen(
                  viewModel = smartDetectionViewModel,
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
            composable<SkyplusRoute.Loans> {
              if (loansViewModel != null) {
                LoansScreen(
                  viewModel = loansViewModel,
                  onNavigateBack = { navController.popBackStack() },
                  onOpenLoanDetail = { loanId ->
                    navController.navigate(SkyplusRoute.LoanDetail(loanId))
                  },
                  onOpenEmiCalculator = { navController.navigate(SkyplusRoute.EmiCalculator) },
                )
              } else {
                LoansContent(
                  loans = emptyList(),
                  summaries = emptyMap(),
                  accounts = emptyList(),
                  onNavigateBack = { navController.popBackStack() },
                  onOpenEmiCalculator = { navController.navigate(SkyplusRoute.EmiCalculator) },
                )
              }
            }
            composable<SkyplusRoute.LoanDetail> { entry ->
              val detailRoute = entry.toRoute<SkyplusRoute.LoanDetail>()
              if (loansViewModel != null) {
                LoanDetailScreen(
                  loanId = detailRoute.loanId,
                  viewModel = loansViewModel,
                  onNavigateBack = { navController.popBackStack() },
                )
              } else {
                LoanDetailContent(
                  loan = null,
                  payments = emptyList(),
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
            composable<SkyplusRoute.EmiCalculator> {
              if (loansViewModel != null) {
                EmiCalculatorScreen(
                  viewModel = loansViewModel,
                  onNavigateBack = { navController.popBackStack() },
                  onSaveAsLoan = { navController.navigate(SkyplusRoute.Loans) },
                )
              } else {
                EmiCalculatorContent(
                  onNavigateBack = { navController.popBackStack() },
                  onSaveAsLoan = { navController.navigate(SkyplusRoute.Loans) },
                )
              }
            }
          }
        }
      }
    }
  }
}
