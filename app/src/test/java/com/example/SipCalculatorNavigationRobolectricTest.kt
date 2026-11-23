package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.ThemePreference
import com.example.feature.dashboard.HomeFoundationUiState
import com.example.navigation.SkyplusAppShell
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SipCalculatorNavigationRobolectricTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `tools hub opens SIP calculator with projection summary`() {
    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.LIGHT) {
        SkyplusAppShell(
          homeUiState = HomeFoundationUiState(),
          themePreference = ThemePreference.LIGHT,
          onToggleTheme = {},
          onSelectTheme = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_tab_tools").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("tools_sip_item").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("sip_calculator_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sip_monthly_investment_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sip_annual_rate_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sip_tenure_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sip_result_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sip_maturity_value").assertIsDisplayed()
  }
}
