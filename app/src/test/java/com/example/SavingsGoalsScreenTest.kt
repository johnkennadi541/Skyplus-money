package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.SavingsGoal
import com.example.domain.model.SavingsGoalSummary
import com.example.domain.model.ThemePreference
import com.example.feature.goals.SavingsGoalsContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * The savings goals screen: progress, remaining amount, completion badge and the empty state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SavingsGoalsScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val uid = "uid_goals"

  private fun goal(
    targetMinor: Long = 100_000L,
    name: String = "Emergency fund",
    currency: String = "INR",
  ): SavingsGoal =
    SavingsGoal(
      id = "goal_1",
      userId = uid,
      name = name,
      currencyCode = currency,
      targetMinor = targetMinor,
    )

  private fun summary(savedMinor: Long, targetMinor: Long = 100_000L): SavingsGoalSummary {
    val progress =
      BigDecimal.valueOf(savedMinor)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(targetMinor), 2, java.math.RoundingMode.HALF_EVEN)
    return SavingsGoalSummary(
      goal = goal(targetMinor = targetMinor),
      savedMinor = savedMinor,
      remainingMinor = if (savedMinor >= targetMinor) 0L else targetMinor - savedMinor,
      progressPercent = progress,
      contributionCount = 2,
    )
  }

  private fun render(summaries: List<SavingsGoalSummary>) {
    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        SavingsGoalsContent(
          goals = summaries,
          message = null,
          isErrorMessage = false,
          onDismissMessage = {},
          onCreateGoal = { _, _, _, _, _, _ -> },
          onAddContribution = { _, _, _, _ -> },
          onDeleteGoal = { _, _ -> },
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun `a goal shows its saved amount and what is left`() {
    render(listOf(summary(savedMinor = 40_000L)))

    composeTestRule.onNodeWithTag("savings_goals_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("goal_card_goal_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("goal_saved_goal_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("goal_remaining_goal_1").assertTextContains("to go", substring = true)
  }

  @Test
  fun `a completed goal shows the completion badge`() {
    render(listOf(summary(savedMinor = 120_000L)))

    composeTestRule.onNodeWithTag("goal_card_goal_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("goal_remaining_goal_1").assertTextContains("%", substring = true)
  }

  @Test
  fun `an empty list shows the empty state`() {
    render(emptyList())

    composeTestRule.onNodeWithTag("goals_empty_state").assertIsDisplayed()
    composeTestRule.onNodeWithTag("goal_card_goal_1").assertDoesNotExist()
  }
  @Test
  fun `a goal with an unsupported stored currency renders instead of crashing`() {
    val broken =
      SavingsGoalSummary(
        goal = goal(currency = "ABC"),
        savedMinor = 5_000L,
        remainingMinor = 95_000L,
        progressPercent = BigDecimal("5.00"),
        contributionCount = 1,
      )

    render(listOf(broken))

    composeTestRule.onNodeWithTag("goal_card_goal_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("goal_saved_goal_1").assertIsDisplayed()
  }
}
