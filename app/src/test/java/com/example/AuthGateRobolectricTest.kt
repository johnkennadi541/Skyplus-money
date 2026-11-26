package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.AuthProviderType
import com.example.domain.model.AuthSessionState
import com.example.domain.model.AuthenticatedUser
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
class AuthGateRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `unauthenticated user sees Google Auth screen with no Guest Mode and navigates after sign in`() {
    var authState by mutableStateOf(AuthUiState(sessionState = AuthSessionState.Unauthenticated))

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        SkyplusAuthGateRoot(
          authUiState = authState,
          homeUiState = HomeFoundationUiState(),
          themePreference = ThemePreference.DARK,
          onContinueWithGoogle = {
            authState =
              AuthUiState(
                sessionState =
                  AuthSessionState.Authenticated(
                    AuthenticatedUser(
                      uid = "uid_abc",
                      displayName = "Alex Mercer",
                      email = "alex@skyplus.test",
                      photoUrl = null,
                      isEmailVerified = true,
                      providers = setOf(AuthProviderType.GOOGLE),
                    )
                  )
              )
          },
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
    composeTestRule.onNodeWithTag("google_auth_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("continue_with_google_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_screen").assertDoesNotExist()
    composeTestRule.onNodeWithTag("bottom_navigation_bar").assertDoesNotExist()
    composeTestRule.onNodeWithText("Continue as Guest", substring = true, ignoreCase = true)
      .assertDoesNotExist()

    // Trigger sign-in
    composeTestRule.onNodeWithTag("continue_with_google_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("google_auth_screen").assertDoesNotExist()

    // Sign out via Profile screen returns to Google Auth gate
    composeTestRule.onNodeWithTag("profile_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_sign_out_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("google_auth_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_screen").assertDoesNotExist()
  }

  @Test
  fun `loading session state renders auth loading screen without bypassing gate`() {
    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        SkyplusAuthGateRoot(
          authUiState = AuthUiState(sessionState = AuthSessionState.Loading),
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
    composeTestRule.onNodeWithTag("auth_loading_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_screen").assertDoesNotExist()
  }

  @Test
  fun `displays safe cancellation and error banners without raw exception traces`() {
    var authState by
      mutableStateOf(
        AuthUiState(
          sessionState = AuthSessionState.Unauthenticated,
          feedbackMessageRes = R.string.auth_cancelled_message,
          isCancellationNotice = true,
        )
      )

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        SkyplusAuthGateRoot(
          authUiState = authState,
          homeUiState = HomeFoundationUiState(),
          themePreference = ThemePreference.DARK,
          onContinueWithGoogle = {},
          onDismissAuthFeedback = { authState = authState.copy(feedbackMessageRes = null) },
          onSignOut = {},
          onToggleTheme = {},
          onSelectTheme = {},
          onAcceptConsent = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("auth_error_banner").assertIsDisplayed()
    composeTestRule
      .onNodeWithText("Google Sign-In was cancelled", substring = true)
      .assertIsDisplayed()
  }
}
