package com.example

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.core.security.AppLockState
import com.example.core.security.DatabaseSecurityMonitor
import com.example.domain.model.AuthSessionState
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
class MainActivity : FragmentActivity() {
  @Inject lateinit var databaseSecurityMonitor: DatabaseSecurityMonitor

  private var openReportsRequest by mutableStateOf(0L)
  private var openCardsRequest by mutableStateOf(0L)
  private var appLockState by mutableStateOf(AppLockState())
  private var biometricPromptActive by mutableStateOf(false)
  private var biometricError by mutableStateOf(false)
  private var credentialPromptActive = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    if (intent.getBooleanExtra(EXTRA_OPEN_DAILY_REPORT, false)) {
      openReportsRequest = System.currentTimeMillis()
    }
    if (intent.getBooleanExtra(EXTRA_OPEN_CREDIT_CARDS, false)) {
      openCardsRequest = System.currentTimeMillis()
    }
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
      val profileFirstName by settingsViewModel.profileFirstName.collectAsStateWithLifecycle()
      LaunchedEffect(authUiState.sessionState) {
        val authenticated = authUiState.sessionState as? AuthSessionState.Authenticated
        appLockState = appLockState.onAuthenticationChanged(authenticated != null)
        if (authenticated != null) {
          settingsViewModel.bindProfile(authenticated.user.uid, authenticated.user.displayName)
          if (appLockState.requiresUnlock && !credentialPromptActive) requestBiometricUnlock()
        } else {
          biometricError = false
          biometricPromptActive = false
        }
      }
      val databaseSecurityError by databaseSecurityMonitor.securityIssue.collectAsStateWithLifecycle()

      SkyplusOceanTheme(themePreference = themePreference) {
        if (appLockState.requiresUnlock) {
          BiometricLockScreen(
            isPromptActive = biometricPromptActive,
            hasError = biometricError,
            onRetry = ::requestBiometricUnlock,
          )
        } else {
          SkyplusAuthGateRoot(
          authUiState = authUiState,
          openReportsRequest = openReportsRequest,
          openCardsRequest = openCardsRequest,
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
          profileFirstName = profileFirstName,
          onSaveProfileFirstName = { firstName ->
            val authenticated = authUiState.sessionState as? AuthSessionState.Authenticated
            if (authenticated != null) {
              settingsViewModel.saveProfileFirstName(authenticated.user.uid, firstName)
            }
          },
          )
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    if (appLockState.requiresUnlock && !credentialPromptActive) requestBiometricUnlock()
  }

  override fun onStop() {
    if (appLockState.isAuthenticated) {
      appLockState = appLockState.onBackground()
      biometricPromptActive = false
    }
    super.onStop()
  }

  private fun requestBiometricUnlock() {
    if (!appLockState.requiresUnlock || biometricPromptActive || credentialPromptActive) return

    val biometricOnly = BiometricManager.Authenticators.BIOMETRIC_STRONG
    // Android 11+ supports biometric + device credential in one prompt. Older
    // Android versions must use separate biometric and Keyguard credential flows.
    val authenticators =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        biometricOnly or BiometricManager.Authenticators.DEVICE_CREDENTIAL
      } else {
        biometricOnly
      }
    val availability = BiometricManager.from(this).canAuthenticate(authenticators)
    if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && launchDeviceCredentialFallback()) return
      biometricError = true
      return
    }

    biometricError = false
    biometricPromptActive = true
    val prompt =
      BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            biometricPromptActive = false
            biometricError = false
            appLockState = appLockState.onUnlockSucceeded()
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            biometricPromptActive = false
            if (
              Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
              errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
            ) {
              if (!launchDeviceCredentialFallback()) biometricError = true
            } else {
              biometricError = true
            }
          }

          override fun onAuthenticationFailed() {
            biometricError = true
          }
        },
      )
    val promptInfoBuilder =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock SKYPLUS MONEY")
        .setSubtitle("Verify your identity to access the entire financial app")
        .setAllowedAuthenticators(authenticators)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      promptInfoBuilder.setNegativeButtonText("Use device screen lock")
    }
    prompt.authenticate(promptInfoBuilder.build())
  }

  @Suppress("DEPRECATION")
  private fun launchDeviceCredentialFallback(): Boolean {
    val keyguard = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager ?: return false
    if (!keyguard.isDeviceSecure) return false
    val credentialIntent =
      keyguard.createConfirmDeviceCredentialIntent(
        "Unlock SKYPLUS MONEY",
        "Confirm your device screen lock to continue",
      ) ?: return false

    credentialPromptActive = true
    return try {
      startActivityForResult(credentialIntent, REQUEST_DEVICE_CREDENTIAL)
      true
    } catch (_: Exception) {
      credentialPromptActive = false
      false
    }
  }

  @Deprecated("Deprecated in Android; retained for pre-Android 11 device-credential fallback.")
  override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: android.content.Intent?,
  ) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode != REQUEST_DEVICE_CREDENTIAL) return
    credentialPromptActive = false
    if (resultCode == RESULT_OK) {
      biometricError = false
      appLockState = appLockState.onUnlockSucceeded()
    } else {
      biometricError = true
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.getBooleanExtra(EXTRA_OPEN_DAILY_REPORT, false)) {
      openReportsRequest = System.currentTimeMillis()
    }
    if (intent.getBooleanExtra(EXTRA_OPEN_CREDIT_CARDS, false)) {
      openCardsRequest = System.currentTimeMillis()
    }
  }

  companion object {
    private const val REQUEST_DEVICE_CREDENTIAL = 9107
    const val EXTRA_OPEN_DAILY_REPORT = "com.skyplus.money.OPEN_DAILY_AUTO_ENTRY_REPORT"
    const val EXTRA_OPEN_CREDIT_CARDS = "com.skyplus.money.OPEN_CREDIT_CARDS"
  }
}


@androidx.compose.runtime.Composable
private fun BiometricLockScreen(
  isPromptActive: Boolean,
  hasError: Boolean,
  onRetry: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = "SKYPLUS MONEY is locked",
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = if (hasError) {
        "Authentication was not completed. Retry with your fingerprint or device screen lock."
      } else {
        "Verify your identity to open your financial workspace."
      },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(vertical = 16.dp),
    )
    Button(onClick = onRetry, enabled = !isPromptActive) {
      Text(if (isPromptActive) "Waiting for verification…" else "Unlock App")
    }
  }
}
