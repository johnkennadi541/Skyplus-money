package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.core.security.DatabaseSecurityMonitor
import com.example.feature.accounts.AccountsViewModel
import com.example.feature.authentication.AuthViewModel
import com.example.feature.budgets.BudgetsViewModel
import com.example.feature.cards.CardsViewModel
import com.example.feature.goals.SavingsGoalsViewModel
import com.example.feature.dashboard.HomeDashboardViewModel
import com.example.feature.dashboard.HomeViewModel
import com.example.feature.investments.InvestmentsViewModel
import com.example.feature.loans.LoansViewModel
import com.example.feature.reports.ReportsViewModel
import com.example.feature.settings.SettingsViewModel
import com.example.feature.shopping.ShoppingViewModel
import com.example.feature.smartdetection.SmartDetectionViewModel
import com.example.feature.transactions.TransactionsViewModel
import com.example.navigation.SkyplusAuthGateRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  @Inject lateinit var databaseSecurityMonitor: DatabaseSecurityMonitor

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val authViewModel: AuthViewModel = hiltViewModel()
      val homeViewModel: HomeViewModel = hiltViewModel()
      val homeDashboardViewModel: HomeDashboardViewModel = hiltViewModel()
      val cardsViewModel: CardsViewModel = hiltViewModel()
      val savingsGoalsViewModel: SavingsGoalsViewModel = hiltViewModel()
      val settingsViewModel: SettingsViewModel = hiltViewModel()
      val accountsViewModel: AccountsViewModel = hiltViewModel()
      val transactionsViewModel: TransactionsViewModel = hiltViewModel()
      val smartDetectionViewModel: SmartDetectionViewModel = hiltViewModel()
      val budgetsViewModel: BudgetsViewModel = hiltViewModel()
      val loansViewModel: LoansViewModel = hiltViewModel()
      val investmentsViewModel: InvestmentsViewModel = hiltViewModel()
      val shoppingViewModel: ShoppingViewModel = hiltViewModel()
      val reportsViewModel: ReportsViewModel = hiltViewModel()

      val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
      val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
      val themePreference by settingsViewModel.themePreference.collectAsStateWithLifecycle()
      val databaseSecurityError by databaseSecurityMonitor.securityIssue.collectAsStateWithLifecycle()

      SkyplusOceanTheme(themePreference = themePreference) {
        SkyplusAuthGateRoot(
          authUiState = authUiState,
          homeUiState = homeUiState,
          themePreference = themePreference,
          onContinueWithGoogle = authViewModel::onContinueWithGoogle,
          onDismissAuthFeedback = authViewModel::onDismissFeedback,
          onSignOut = authViewModel::onSignOut,
          onToggleTheme = homeViewModel::toggleDarkLightTheme,
          onSelectTheme = settingsViewModel::onThemeSelected,
          onAcceptConsent = authViewModel::onAcceptConsent,
          onRetryConsentCheck = authViewModel::onRetryConsentCheck,
          databaseSecurityError = databaseSecurityError,
          onRetryDatabaseInit = { databaseSecurityMonitor.retryInitialization() },
          accountsViewModel = accountsViewModel,
          transactionsViewModel = transactionsViewModel,
          smartDetectionViewModel = smartDetectionViewModel,
          budgetsViewModel = budgetsViewModel,
          loansViewModel = loansViewModel,
          investmentsViewModel = investmentsViewModel,
          shoppingViewModel = shoppingViewModel,
          reportsViewModel = reportsViewModel,
          homeDashboardViewModel = homeDashboardViewModel,
          cardsViewModel = cardsViewModel,
          savingsGoalsViewModel = savingsGoalsViewModel,
        )
      }
    }
  }
}
