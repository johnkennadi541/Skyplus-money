package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.AuthProviderType
import com.example.domain.model.AuthSessionState
import com.example.domain.model.AuthenticatedUser
import com.example.domain.model.ConsentRecord
import com.example.domain.model.ThemePreference
import com.example.feature.authentication.AuthUiState
import com.example.feature.dashboard.HomeFoundationUiState
import com.example.navigation.SkyplusAuthGateRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LegalConsentGateRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `first time user without consent is blocked from dashboard until consent is granted`() {
    val user = AuthenticatedUser(
      uid = "uid_user_first_time",
      displayName = "First Time User",
      email = "first@skyplus.test",
      photoUrl = null,
      isEmailVerified = true,
      providers = setOf(AuthProviderType.GOOGLE),
    )

    var authState by mutableStateOf(
      AuthUiState(
        sessionState = AuthSessionState.Authenticated(user),
        requiresConsent = true,
      )
    )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        SkyplusAuthGateRoot(
          authUiState = authState,
          homeUiState = HomeFoundationUiState(),
          themePreference = ThemePreference.DARK,
          onContinueWithGoogle = {},
          onDismissAuthFeedback = {},
          onSignOut = {
            authState = AuthUiState(sessionState = AuthSessionState.Unauthenticated)
          },
          onToggleTheme = {},
          onSelectTheme = {},
          onAcceptConsent = {
            authState = authState.copy(requiresConsent = false)
          },
        )
      }
    }

    composeTestRule.waitForIdle()
    // Dashboard is blocked
    composeTestRule.onNodeWithTag("legal_consent_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_screen").assertDoesNotExist()

    // Accept button disabled initially
    composeTestRule.onNodeWithTag("accept_consent_button").performScrollTo().assertIsNotEnabled()

    // Check terms
    composeTestRule.onNodeWithTag("terms_checkbox_row").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("accept_consent_button").performScrollTo().assertIsNotEnabled()

    // Check privacy
    composeTestRule.onNodeWithTag("privacy_checkbox_row").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("accept_consent_button").performScrollTo().assertIsEnabled()

    // Accept consent opens dashboard
    composeTestRule.onNodeWithTag("accept_consent_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("legal_consent_screen").assertDoesNotExist()
  }

  @Test
  fun `user with up to date accepted consent bypasses consent screen and enters dashboard directly`() {
    val user = AuthenticatedUser(
      uid = "uid_user_accepted",
      displayName = "Accepted User",
      email = "accepted@skyplus.test",
      photoUrl = null,
      isEmailVerified = true,
      providers = setOf(AuthProviderType.GOOGLE),
    )

    val authState = AuthUiState(
      sessionState = AuthSessionState.Authenticated(user),
      requiresConsent = false,
      consentRecord = ConsentRecord(
        termsVersion = ConsentRecord.CURRENT_TERMS_VERSION,
        privacyVersion = ConsentRecord.CURRENT_PRIVACY_VERSION,
        timestamp = System.currentTimeMillis(),
      ),
    )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        SkyplusAuthGateRoot(
          authUiState = authState,
          homeUiState = HomeFoundationUiState(),
          themePreference = ThemePreference.DARK,
          onContinueWithGoogle = {},
          onDismissAuthFeedback = {},
          onSignOut = {},
          onToggleTheme = {},
          onSelectTheme = {},
          onAcceptConsent = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("legal_consent_screen").assertDoesNotExist()
  }

  @Test
  fun `changed terms version requires re-consent and blocks dashboard access`() {
    val user = AuthenticatedUser(
      uid = "uid_user_old_terms",
      displayName = "Old Terms User",
      email = "oldterms@skyplus.test",
      photoUrl = null,
      isEmailVerified = true,
      providers = setOf(AuthProviderType.GOOGLE),
    )

    val oldTermsRecord = ConsentRecord(
      termsVersion = "0.9.0", // older terms version
      privacyVersion = ConsentRecord.CURRENT_PRIVACY_VERSION,
      timestamp = System.currentTimeMillis() - 100000,
    )

    val authState = AuthUiState(
      sessionState = AuthSessionState.Authenticated(user),
      requiresConsent = !oldTermsRecord.isUpToDate,
      consentRecord = oldTermsRecord,
    )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        SkyplusAuthGateRoot(
          authUiState = authState,
          homeUiState = HomeFoundationUiState(),
          themePreference = ThemePreference.DARK,
          onContinueWithGoogle = {},
          onDismissAuthFeedback = {},
          onSignOut = {},
          onToggleTheme = {},
          onSelectTheme = {},
          onAcceptConsent = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("legal_consent_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_screen").assertDoesNotExist()
  }

  @Test
  fun `changed privacy policy version requires re-consent and blocks dashboard access`() {
    val user = AuthenticatedUser(
      uid = "uid_user_old_privacy",
      displayName = "Old Privacy User",
      email = "oldprivacy@skyplus.test",
      photoUrl = null,
      isEmailVerified = true,
      providers = setOf(AuthProviderType.GOOGLE),
    )

    val oldPrivacyRecord = ConsentRecord(
      termsVersion = ConsentRecord.CURRENT_TERMS_VERSION,
      privacyVersion = "0.8.0", // older privacy version
      timestamp = System.currentTimeMillis() - 100000,
    )

    val authState = AuthUiState(
      sessionState = AuthSessionState.Authenticated(user),
      requiresConsent = !oldPrivacyRecord.isUpToDate,
      consentRecord = oldPrivacyRecord,
    )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        SkyplusAuthGateRoot(
          authUiState = authState,
          homeUiState = HomeFoundationUiState(),
          themePreference = ThemePreference.DARK,
          onContinueWithGoogle = {},
          onDismissAuthFeedback = {},
          onSignOut = {},
          onToggleTheme = {},
          onSelectTheme = {},
          onAcceptConsent = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("legal_consent_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_screen").assertDoesNotExist()
  }

  @Test
  fun `signing out from consent screen returns to unauthenticated gate without granting consent`() {
    val user = AuthenticatedUser(
      uid = "uid_user_signout",
      displayName = "Sign Out User",
      email = "signout@skyplus.test",
      photoUrl = null,
      isEmailVerified = true,
      providers = setOf(AuthProviderType.GOOGLE),
    )

    var authState by mutableStateOf(
      AuthUiState(
        sessionState = AuthSessionState.Authenticated(user),
        requiresConsent = true,
      )
    )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        SkyplusAuthGateRoot(
          authUiState = authState,
          homeUiState = HomeFoundationUiState(),
          themePreference = ThemePreference.DARK,
          onContinueWithGoogle = {},
          onDismissAuthFeedback = {},
          onSignOut = {
            authState = AuthUiState(sessionState = AuthSessionState.Unauthenticated)
          },
          onToggleTheme = {},
          onSelectTheme = {},
          onAcceptConsent = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("legal_consent_screen").assertIsDisplayed()

    // Sign out from consent screen
    composeTestRule.onNodeWithTag("legal_sign_out_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("google_auth_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("legal_consent_screen").assertDoesNotExist()
    composeTestRule.onNodeWithTag("home_screen").assertDoesNotExist()
  }
}
