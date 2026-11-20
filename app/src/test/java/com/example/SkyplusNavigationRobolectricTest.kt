package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.PhaseDeliveryState
import com.example.domain.model.RoadmapPhaseItem
import com.example.domain.model.ThemePreference
import com.example.feature.dashboard.HomeUiState
import com.example.navigation.SkyplusAppShell
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SkyplusNavigationRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val sampleState =
    HomeUiState(
      roadmap =
        listOf(
          RoadmapPhaseItem(
            0,
            "Phase 0 — Foundation",
            "Clean foundation",
            PhaseDeliveryState.COMPLETE_IN_PROGRESS,
          )
        )
    )

  @Test
  fun `navigate across four tabs and profile menu`() {
    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        SkyplusAppShell(
          homeUiState = sampleState,
          themePreference = ThemePreference.DARK,
          onToggleTheme = {},
          onSelectTheme = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_tab_transactions").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("transactions_screen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_tab_budget").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("budgets_screen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_tab_shopping_list").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("shopping_screen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("profile_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_screen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("profile_settings_item").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_screen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("profile_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("shopping_screen").assertIsDisplayed()
  }
}
