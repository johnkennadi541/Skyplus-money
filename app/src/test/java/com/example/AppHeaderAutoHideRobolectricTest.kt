package com.example

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.PhaseDeliveryState
import com.example.domain.model.RoadmapPhaseItem
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
class AppHeaderAutoHideRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `header hides on upward content scroll and returns on downward scroll`() {
    val homeState =
      HomeFoundationUiState(
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

    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        SkyplusAppShell(
          homeUiState = homeState,
          themePreference = ThemePreference.DARK,
          onToggleTheme = {},
          onSelectTheme = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("skyplus_compact_app_header").assertIsDisplayed()

    composeTestRule.onNodeWithTag("home_screen").performTouchInput { swipeUp() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("skyplus_compact_app_header").assertDoesNotExist()

    composeTestRule.onNodeWithTag("home_screen").performTouchInput { swipeDown() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("skyplus_compact_app_header").assertIsDisplayed()
  }
}
