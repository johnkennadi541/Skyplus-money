package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.repository.SavingsGoalRepositoryImpl
import com.example.domain.model.SavingsContribution
import com.example.domain.model.SavingsGoal
import com.example.domain.model.SavingsGoalConflictException
import com.example.domain.usecase.SavingsGoalCalculationEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Savings goal persistence: ownership, currency integrity, derived totals and retained history (archive/void), with user isolation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SavingsGoalDatabaseAndRepoTest {

  private lateinit var context: Context
  private lateinit var database: AppDatabase
  private lateinit var repository: SavingsGoalRepositoryImpl
  private val engine = SavingsGoalCalculationEngine()

  private val userA = "user_goals_a"
  private val userB = "user_goals_b"

  private val dispatchers =
    object : DispatcherProvider {
      private val d = UnconfinedTestDispatcher()
      override val main: CoroutineDispatcher = d
      override val io: CoroutineDispatcher = d
      override val default: CoroutineDispatcher = d
    }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    repository =
      SavingsGoalRepositoryImpl(
        database = database,
        savingsGoalDao = database.savingsGoalDao(),
        dispatchers = dispatchers,
      )
  }

  @After
  fun tearDown() {
    database.close()
  }

  private fun goal(
    userId: String = userA,
    id: String = "goal_1",
    currency: String = "INR",
    targetMinor: Long = 100_000L,
    name: String = "Emergency fund",
  ): SavingsGoal =
    SavingsGoal(
      id = id,
      userId = userId,
      name = name,
      currencyCode = currency,
      targetMinor = targetMinor,
    )

  private fun contribution(
    amountMinor: Long = 10_000L,
    goalId: String = "goal_1",
    currency: String = "INR",
    userId: String = userA,
    id: String = "c_1",
  ): SavingsContribution =
    SavingsContribution(
      id = id,
      userId = userId,
      goalId = goalId,
      amountMinor = amountMinor,
      currencyCode = currency,
    )

  @Test
  fun `a goal is created and observed for its owner only`() = runTest {
    repository.createGoal(userA, goal()).getOrThrow()

    assertEquals(1, repository.observeGoals(userA).first().size)
    assertTrue(repository.observeGoals(userB).first().isEmpty())
  }

  @Test
  fun `contributions add up to the saved amount`() = runTest {
    repository.createGoal(userA, goal()).getOrThrow()
    repository.addContribution(userA, contribution(30_000L, id = "c_1")).getOrThrow()
    repository.addContribution(userA, contribution(20_000L, id = "c_2")).getOrThrow()

    val stored = repository.observeContributions(userA).first()
    val summary = engine.summarise(repository.getGoal(userA, "goal_1")!!, stored)

    assertEquals(2, summary.contributionCount)
    assertEquals(50_000L, summary.savedMinor)
    assertEquals(50_000L, summary.remainingMinor)
  }

  @Test
  fun `a contribution in another currency is rejected`() = runTest {
    repository.createGoal(userA, goal(currency = "INR")).getOrThrow()

    val result = repository.addContribution(userA, contribution(currency = "USD"))

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("currency"))
    assertTrue(repository.observeContributions(userA).first().isEmpty())
  }

  @Test
  fun `a non positive contribution is rejected`() = runTest {
    repository.createGoal(userA, goal()).getOrThrow()

    assertTrue(repository.addContribution(userA, contribution(amountMinor = 0L)).isFailure)
    assertTrue(repository.addContribution(userA, contribution(amountMinor = -100L, id = "c_neg")).isFailure)
    assertTrue(repository.observeContributions(userA).first().isEmpty())
  }

  @Test
  fun `a contribution to another user's goal is rejected`() = runTest {
    repository.createGoal(userA, goal()).getOrThrow()

    val result = repository.addContribution(userB, contribution(userId = userB))

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is SavingsGoalConflictException)
    assertTrue(repository.observeContributions(userB).first().isEmpty())
  }

  @Test
  fun `the same contribution id cannot be inserted twice`() = runTest {
    repository.createGoal(userA, goal()).getOrThrow()

    val first = repository.addContribution(userA, contribution(10_000L, id = "dup"))
    val second = repository.addContribution(userA, contribution(10_000L, id = "dup"))

    assertTrue(first.isSuccess)
    assertTrue(second.isFailure)
    assertEquals(1, repository.observeContributions(userA).first().size)
    // The saved total must not double because a duplicate was attempted.
    assertEquals(
      10_000L,
      engine.savedMinor(repository.getGoal(userA, "goal_1")!!, repository.observeContributions(userA).first()),
    )
  }

  @Test
  fun `the currency of a funded goal cannot change`() = runTest {
    repository.createGoal(userA, goal(currency = "INR")).getOrThrow()
    repository.addContribution(userA, contribution(10_000L)).getOrThrow()

    val result = repository.updateGoal(userA, goal(currency = "USD"))

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("cannot be changed"))
    assertEquals("INR", repository.getGoal(userA, "goal_1")?.currencyCode)
  }

  @Test
  fun `editing a goal leaves its contributions and saved total untouched`() = runTest {
    repository.createGoal(userA, goal()).getOrThrow()
    repository.addContribution(userA, contribution(30_000L, id = "c_1")).getOrThrow()
    repository.addContribution(userA, contribution(20_000L, id = "c_2")).getOrThrow()

    val savedBefore =
      engine.savedMinor(repository.getGoal(userA, "goal_1")!!, repository.observeContributions(userA).first())

    repository
      .updateGoal(
        userA,
        goal(name = "Renamed", targetMinor = 200_000L),
      )
      .getOrThrow()

    val after = repository.getGoal(userA, "goal_1")!!
    assertEquals("Renamed", after.name)
    assertEquals(200_000L, after.targetMinor)
    assertEquals(2, repository.observeContributions(userA).first().size)
    assertEquals(savedBefore, engine.savedMinor(after, repository.observeContributions(userA).first()))
    assertEquals(50_000L, savedBefore)
  }

  @Test
  fun `the currency of an unfunded goal can still change`() = runTest {
    repository.createGoal(userA, goal(currency = "INR")).getOrThrow()

    repository.updateGoal(userA, goal(currency = "USD")).getOrThrow()

    assertEquals("USD", repository.getGoal(userA, "goal_1")?.currencyCode)
  }

  @Test
  fun `another user cannot update or archive a goal`() = runTest {
    repository.createGoal(userA, goal()).getOrThrow()

    assertTrue(repository.updateGoal(userB, goal(userId = userB)).isFailure)
    assertTrue(repository.setGoalArchived(userB, "goal_1", archived = true).isFailure)
    assertNotNull(repository.getGoal(userA, "goal_1"))
  }

  @Test
  fun `archiving and restoring a goal preserves all contributions and saved totals`() = runTest {
    repository.createGoal(userA, goal()).getOrThrow()
    repository.addContribution(userA, contribution(10_000L, id = "c_1")).getOrThrow()
    repository.addContribution(userA, contribution(5_000L, id = "c_2")).getOrThrow()

    repository.setGoalArchived(userA, "goal_1", archived = true).getOrThrow()

    val archived = repository.getGoal(userA, "goal_1")!!
    val retained = repository.observeContributions(userA).first()
    assertTrue(archived.isArchived)
    assertEquals(2, retained.size)
    assertEquals(15_000L, engine.savedMinor(archived, retained))

    assertTrue(repository.addContribution(userA, contribution(1_000L, id = "c_3")).isFailure)

    repository.setGoalArchived(userA, "goal_1", archived = false).getOrThrow()
    val restored = repository.getGoal(userA, "goal_1")!!
    assertTrue(!restored.isArchived)
    assertEquals(2, repository.observeContributions(userA).first().size)
    assertEquals(15_000L, engine.savedMinor(restored, repository.observeContributions(userA).first()))
  }

  @Test
  fun `voiding a contribution retains its original row and excludes it from saved totals`() = runTest {
    repository.createGoal(userA, goal()).getOrThrow()
    repository.addContribution(userA, contribution(10_000L, id = "c_1")).getOrThrow()
    repository.addContribution(userA, contribution(5_000L, id = "c_2")).getOrThrow()

    repository.voidContribution(userA, "c_1").getOrThrow()

    val history = repository.observeContributions(userA).first()
    val voided = history.single { it.id == "c_1" }
    assertEquals(2, history.size)
    assertTrue(voided.isVoided)
    assertTrue(voided.voidedAt != null)
    assertEquals(10_000L, voided.amountMinor)
    assertEquals(1, engine.summarise(repository.getGoal(userA, "goal_1")!!, history).contributionCount)
    assertEquals(5_000L, engine.savedMinor(repository.getGoal(userA, "goal_1")!!, history))
    assertTrue(repository.voidContribution(userA, "c_1").isFailure)
  }

  @Test
  fun `another user cannot void a contribution`() = runTest {
    repository.createGoal(userA, goal()).getOrThrow()
    repository.addContribution(userA, contribution(10_000L, id = "c_1")).getOrThrow()

    assertTrue(repository.voidContribution(userB, "c_1").isFailure)
    assertEquals(1, repository.observeContributions(userA).first().size)
    assertTrue(!repository.observeContributions(userA).first().single().isVoided)
  }

  @Test
  fun `contributions are scoped to their goal when observed`() = runTest {
    repository.createGoal(userA, goal(id = "goal_1")).getOrThrow()
    repository.createGoal(userA, goal(id = "goal_2", name = "Laptop")).getOrThrow()
    repository.addContribution(userA, contribution(10_000L, goalId = "goal_1", id = "c_1")).getOrThrow()
    repository.addContribution(userA, contribution(7_000L, goalId = "goal_2", id = "c_2")).getOrThrow()

    val forGoalTwo = repository.observeContributionsForGoal(userA, "goal_2").first()

    assertEquals(1, forGoalTwo.size)
    assertEquals(7_000L, forGoalTwo.single().amountMinor)
  }
}
