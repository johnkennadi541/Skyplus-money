package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.repository.BudgetRepositoryImpl
import com.example.domain.model.Budget
import com.example.domain.model.BudgetPeriodType
import com.example.domain.usecase.BudgetCalculationEngine
import com.example.domain.usecase.BudgetRenewalEngine
import com.example.domain.usecase.BudgetRenewalPlan
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves that a second renewal of the same budget window is a safe no-op and can never write
 * duplicate archive history. The compare-and-set lives in the database, so this runs against a
 * real Room database rather than a fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BudgetRenewalConcurrencyTest {

  private lateinit var context: Context
  private lateinit var database: AppDatabase
  private lateinit var repository: BudgetRepositoryImpl
  private lateinit var engine: BudgetRenewalEngine

  private val uid = "user_concurrent_1"
  private val otherUid = "user_concurrent_2"

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
    repository = BudgetRepositoryImpl(database, database.budgetDao(), dispatchers)
    engine = BudgetRenewalEngine(BudgetCalculationEngine())
  }

  @After
  fun tearDown() {
    database.close()
  }

  private fun at(year: Int, monthZeroBased: Int, day: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.clear()
    calendar.set(year, monthZeroBased, day, 0, 0, 0)
    return calendar.timeInMillis
  }

  private fun lastMillisOfMonth(year: Int, monthZeroBased: Int): Long =
    engine.endOfWindow(at(year, monthZeroBased, 1))

  private fun januaryBudget(userId: String = uid): Budget =
    Budget(
      id = UUID.randomUUID().toString(),
      userId = userId,
      name = "Groceries",
      category = "Food & Dining",
      amountMinor = 100_00L,
      currencyCode = "INR",
      periodType = BudgetPeriodType.MONTHLY,
      startDate = at(2026, 0, 1),
      endDate = lastMillisOfMonth(2026, 0),
      isRecurring = true,
    )

  private suspend fun applyPlan(plan: BudgetRenewalPlan.Due, userId: String = uid): Boolean =
    repository
      .applyRenewal(
        authenticatedUserId = userId,
        budgetId = plan.budgetId,
        expectedStart = plan.expectedStart,
        expectedEnd = plan.expectedEnd,
        closedPeriods = plan.closedPeriods,
        nextStart = plan.nextStart,
        nextEnd = plan.nextEnd,
        renewedAt = plan.renewedAt,
      )
      .getOrThrow()

  @Test
  fun `the first renewal wins and the second is a safe no-op`() = runTest {
    val budget = januaryBudget()
    repository.createBudget(uid, budget).getOrThrow()

    val plan = engine.plan(budget, emptyList(), at(2026, 1, 20)) as BudgetRenewalPlan.Due

    assertTrue(applyPlan(plan))
    // The same plan again: the window has already moved, so the compare-and-set must refuse it.
    assertFalse(applyPlan(plan))

    val periods = repository.observeBudgetPeriods(uid).first()
    assertEquals(1, periods.size)
  }

  @Test
  fun `two plans built from the same stale window archive history exactly once`() = runTest {
    val budget = januaryBudget()
    repository.createBudget(uid, budget).getOrThrow()

    val now = at(2026, 2, 10) // March: two windows have elapsed
    val firstPlan = engine.plan(budget, emptyList(), now) as BudgetRenewalPlan.Due
    val secondPlan = engine.plan(budget, emptyList(), now) as BudgetRenewalPlan.Due

    assertTrue(applyPlan(firstPlan))
    assertFalse(applyPlan(secondPlan))

    val periods = repository.observeBudgetPeriods(uid).first()
    assertEquals(2, periods.size)
    assertEquals(2, periods.map { it.id }.toSet().size)
  }

  @Test
  fun `archive ids are deterministic for the same budget window`() = runTest {
    val budget = januaryBudget()
    val now = at(2026, 2, 10)

    val first = engine.plan(budget, emptyList(), now) as BudgetRenewalPlan.Due
    val second = engine.plan(budget, emptyList(), now) as BudgetRenewalPlan.Due

    assertEquals(
      first.closedPeriods.map { it.id },
      second.closedPeriods.map { it.id },
    )
  }

  @Test
  fun `a renewal for another user writes nothing`() = runTest {
    val budget = januaryBudget()
    repository.createBudget(uid, budget).getOrThrow()

    val plan = engine.plan(budget, emptyList(), at(2026, 1, 20)) as BudgetRenewalPlan.Due

    val result = runCatching { applyPlan(plan, userId = otherUid) }
    assertTrue(result.isFailure)
    assertTrue(repository.observeBudgetPeriods(otherUid).first().isEmpty())
    assertTrue(repository.observeBudgetPeriods(uid).first().isEmpty())
  }

  @Test
  fun `the archived band is the band the budget had, not period ended`() = runTest {
    val budget = januaryBudget()
    repository.createBudget(uid, budget).getOrThrow()

    val plan = engine.plan(budget, emptyList(), at(2026, 1, 20)) as BudgetRenewalPlan.Due
    assertTrue(applyPlan(plan))

    val archived = repository.observeBudgetPeriods(uid).first().single()
    assertEquals(
      com.example.domain.model.BudgetAlertStatus.NORMAL,
      archived.alertStatus,
    )
  }
}
