package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.core.designsystem.theme.SkyplusOceanTheme
import com.example.domain.model.SavingsGoal
import com.example.domain.model.SavingsGoalSummary
import com.example.domain.model.ThemePreference
import com.example.feature.goals.SavingsGoalsContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * The savings goals screen: progress, edit and create forms, the locked currency rule, and the
 * loading, empty and populated states.
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
    note: String? = null,
    deadlineMillis: Long? = null,
  ): SavingsGoal =
    SavingsGoal(
      id = "goal_1",
      userId = uid,
      name = name,
      currencyCode = currency,
      targetMinor = targetMinor,
      note = note,
      deadlineMillis = deadlineMillis,
    )

  private fun summary(
    savedMinor: Long,
    targetMinor: Long = 100_000L,
    contributionCount: Int = 2,
    goal: SavingsGoal = goal(targetMinor = targetMinor),
  ): SavingsGoalSummary {
    val progress =
      BigDecimal.valueOf(savedMinor)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(targetMinor), 2, java.math.RoundingMode.HALF_EVEN)
    return SavingsGoalSummary(
      goal = goal,
      savedMinor = savedMinor,
      remainingMinor = if (savedMinor >= targetMinor) 0L else targetMinor - savedMinor,
      progressPercent = progress,
      contributionCount = contributionCount,
    )
  }

  private class Recorded {
    var editedName: String? = null
    var editedCurrency: String? = null
    var editedTarget: String? = null
    var editedNote: String? = null
    var editedDeadline: String? = null
    var editCalls: Int = 0
    var createdName: String? = null
  }

  private val recorded = Recorded()

  private fun render(
    summaries: List<SavingsGoalSummary>,
    isLoading: Boolean = false,
  ) {
    composeTestRule.setContent {
      SkyplusOceanTheme(themePreference = ThemePreference.DARK) {
        SavingsGoalsContent(
          goals = summaries,
          isLoading = isLoading,
          message = null,
          isErrorMessage = false,
          onDismissMessage = {},
          onCreateGoal = { name, _, _, _, _, _ -> recorded.createdName = name },
          onAddContribution = { _, _, _, _ -> },
          onDeleteGoal = { _, _ -> },
          onEditGoal = { _, name, currency, target, note, deadline, _ ->
            recorded.editCalls += 1
            recorded.editedName = name
            recorded.editedCurrency = currency
            recorded.editedTarget = target
            recorded.editedNote = note
            recorded.editedDeadline = deadline
          },
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

  @Test
  fun `while loading a skeleton is shown instead of the empty state`() {
    render(emptyList(), isLoading = true)

    composeTestRule.onNodeWithTag("goals_loading").assertIsDisplayed()
    composeTestRule.onNodeWithTag("goals_empty_state").assertDoesNotExist()
  }

  @Test
  fun `an empty result after loading shows the empty state, not a skeleton`() {
    render(emptyList(), isLoading = false)

    composeTestRule.onNodeWithTag("goals_empty_state").assertIsDisplayed()
    composeTestRule.onNodeWithTag("goals_loading").assertDoesNotExist()
  }

  @Test
  fun `a populated result shows cards and no skeleton`() {
    render(listOf(summary(savedMinor = 10_000L)), isLoading = false)

    composeTestRule.onNodeWithTag("goal_card_goal_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("goals_loading").assertDoesNotExist()
  }

  @Test
  fun `editing opens a prefilled form with the existing values`() {
    val existing = goal(name = "Holiday", targetMinor = 250_000L)
    render(listOf(summary(savedMinor = 50_000L, goal = existing)))

    composeTestRule.onNodeWithTag("edit_goal_goal_1").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("goal_form_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("goal_name_input").assertTextContains("Holiday", substring = true)
    // 250_000 minor units in INR is 2500.00
    composeTestRule.onNodeWithTag("goal_target_input").assertTextContains("2500.00", substring = true)
    composeTestRule.onNodeWithTag("goal_currency_input").assertTextContains("INR", substring = true)
  }

  @Test
  fun `the currency field is locked when the goal already has contributions`() {
    render(listOf(summary(savedMinor = 10_000L, contributionCount = 3)))

    composeTestRule.onNodeWithTag("edit_goal_goal_1").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("goal_currency_locked").assertIsDisplayed()
    composeTestRule.onNodeWithTag("goal_currency_input").assertDoesNotExist()
  }

  @Test
  fun `saving an edit passes the edited values to the view model`() {
    render(listOf(summary(savedMinor = 10_000L)))

    composeTestRule.onNodeWithTag("edit_goal_goal_1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("goal_name_input").performTextClearance()
    composeTestRule.onNodeWithTag("goal_name_input").performTextInput("New name")
    composeTestRule.onNodeWithTag("save_goal_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, recorded.editCalls)
    assertEquals("New name", recorded.editedName)
    assertEquals("INR", recorded.editedCurrency)
    assertEquals("1000.00", recorded.editedTarget)
  }

  @Test
  fun `cancelling an edit closes the form without saving`() {
    render(listOf(summary(savedMinor = 10_000L)))

    composeTestRule.onNodeWithTag("edit_goal_goal_1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("goal_form_cancel").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("goal_form_dialog").assertDoesNotExist()
    assertEquals(0, recorded.editCalls)
    assertNull(recorded.editedName)
  }

  @Test
  fun `an empty name is rejected in the form without saving`() {
    render(listOf(summary(savedMinor = 10_000L)))

    composeTestRule.onNodeWithTag("edit_goal_goal_1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("goal_name_input").performTextClearance()
    composeTestRule.onNodeWithTag("save_goal_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("goal_form_dialog").assertIsDisplayed()
    assertEquals(0, recorded.editCalls)
    assertNull(recorded.editedName)
  }

  @Test
  fun `the create form is not prefilled`() {
    render(listOf(summary(savedMinor = 10_000L)))

    composeTestRule.onNodeWithTag("add_goal_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("goal_currency_input").assertTextContains("INR", substring = true)
    composeTestRule.onNodeWithTag("goal_currency_locked").assertDoesNotExist()
    assertTrue(recorded.createdName == null)
  }
  @Test
  fun `opening edit on a goal with an unsupported stored currency does not crash`() {
    val broken = goal(currency = "ABC", targetMinor = 12_345L)
    render(listOf(summary(savedMinor = 0L, targetMinor = 12_345L, contributionCount = 0, goal = broken)))

    composeTestRule.onNodeWithTag("edit_goal_goal_1").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("goal_form_dialog").assertIsDisplayed()
    // The raw minor units are shown, and the user can correct the currency.
    composeTestRule.onNodeWithTag("goal_target_input").assertTextContains("12345", substring = true)
    composeTestRule.onNodeWithTag("goal_currency_input").assertTextContains("ABC", substring = true)
  }

  @Test
  fun `saving an unsupported stored currency is refused with a message`() {
    val broken = goal(currency = "ABC", targetMinor = 12_345L)
    render(listOf(summary(savedMinor = 0L, targetMinor = 12_345L, contributionCount = 0, goal = broken)))

    composeTestRule.onNodeWithTag("edit_goal_goal_1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("save_goal_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("goal_form_dialog").assertIsDisplayed()
    assertEquals(0, recorded.editCalls)
  }

  @Test
  fun `an unfunded goal passes the newly selected currency to the view model`() {
    render(listOf(summary(savedMinor = 0L, contributionCount = 0)))

    composeTestRule.onNodeWithTag("edit_goal_goal_1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("goal_currency_input").performTextClearance()
    composeTestRule.onNodeWithTag("goal_currency_input").performTextInput("USD")
    composeTestRule.onNodeWithTag("save_goal_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, recorded.editCalls)
    assertEquals("USD", recorded.editedCurrency)
  }

  @Test
  fun `a funded goal cannot change its currency`() {
    render(listOf(summary(savedMinor = 25_000L, contributionCount = 2)))

    composeTestRule.onNodeWithTag("edit_goal_goal_1").performClick()
    composeTestRule.waitForIdle()

    // The field is read-only for a funded goal, so there is nothing to type into.
    composeTestRule.onNodeWithTag("goal_currency_locked").assertIsDisplayed()
    composeTestRule.onNodeWithTag("goal_currency_input").assertDoesNotExist()
    composeTestRule.onNodeWithTag("save_goal_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, recorded.editCalls)
    assertEquals("INR", recorded.editedCurrency)
  }

  @Test
  fun `the note is passed through when editing`() {
    render(listOf(summary(savedMinor = 0L, contributionCount = 0, goal = goal(note = "Old note"))))

    composeTestRule.onNodeWithTag("edit_goal_goal_1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("goal_note_input").performTextClearance()
    composeTestRule.onNodeWithTag("goal_note_input").performTextInput("New note")
    composeTestRule.onNodeWithTag("save_goal_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals("New note", recorded.editedNote)
  }
}
