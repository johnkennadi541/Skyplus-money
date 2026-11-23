package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.AuthProviderType
import com.example.domain.model.AuthenticatedUser
import com.example.domain.model.ThemePreference
import com.example.feature.settings.ProfileScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileFirstNameRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `profile shows Google seeded name and saves edited first name`() {
    var savedName: String? = null
    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        ProfileScreen(
          authenticatedUser = AuthenticatedUser(
            uid = "uid_profile_test",
            displayName = "Alex Morgan",
            email = "alex@example.test",
            photoUrl = null,
            isEmailVerified = true,
            providers = setOf(AuthProviderType.GOOGLE),
          ),
          onOpenSettings = {},
          onOpenAuthReadiness = {},
          onOpenSmartDetection = {},
          onNavigateBack = {},
          profileFirstName = "Alex",
          onSaveProfileFirstName = { savedName = it },
        )
      }
    }

    composeTestRule.onNodeWithTag("profile_first_name_input").assertIsDisplayed()
      .assertTextEquals("Alex")
    composeTestRule.onNodeWithTag("profile_first_name_input").performTextInput("andra")
    composeTestRule.onNodeWithTag("profile_save_name_button").performClick()
    composeTestRule.runOnIdle { assertEquals("Alexandra", savedName) }
  }
}
