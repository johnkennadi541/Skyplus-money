package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.DatabaseMigrations
import com.example.data.repository.BudgetRepositoryImpl
import com.example.domain.model.Budget
import com.example.domain.model.BudgetPeriodType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3BudgetDatabaseAndRepoTest {

  private lateinit var context: Context
  private lateinit var database: AppDatabase
  private lateinit var budgetRepo: BudgetRepositoryImpl

  private val testDispatchers =
    object : DispatcherProvider {
      private val d = UnconfinedTestDispatcher()
      override val main: CoroutineDispatcher = d
      override val io: CoroutineDispatcher = d
      override val default: CoroutineDispatcher = d
    }

  private val userA = "user_alpha_1"
  private val userB = "user_beta_2"

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    budgetRepo = BudgetRepositoryImpl(database, database.budgetDao(), testDispatchers)
  }

  @After
  fun tearDown() {
    database.close()
  }

  private fun buildBudget(
    userId: String,
    name: String = "Dining Out",
    category: String = "Food & Dining",
    amountMinor: Long = 150_00L,
    currency: String = "USD",
    startDate: Long = 1_000_000L,
    endDate: Long = 2_000_000L,
  ): Budget =
    Budget(
      id = UUID.randomUUID().toString(),
      userId = userId,
      name = name,
      category = category,
      amountMinor = amountMinor,
      currencyCode = currency,
      periodType = BudgetPeriodType.MONTHLY,
      startDate = startDate,
      endDate = endDate,
      warningThresholdPercent = 75,
    )

  @Test
  fun `createBudget stores budget and retrieves by user ID and budget ID`() = runTest {
    val budget = buildBudget(userA)
    val createResult = budgetRepo.createBudget(userA, budget)
    assertTrue("Budget creation should succeed: ${createResult.exceptionOrNull()?.message}", createResult.isSuccess)

    val list = budgetRepo.getBudgets(userA).first()
    assertEquals(1, list.size)
    assertEquals(budget.id, list.first().id)
    assertEquals("Dining Out", list.first().name)
    assertEquals(150_00L, list.first().amountMinor)

    val single = budgetRepo.getBudgetById(userA, budget.id)
    assertNotNull(single)
    assertEquals(budget.id, single?.id)
  }

  @Test
  fun `strict UID isolation - user B cannot see or query user A budgets`() = runTest {
    val budgetA = buildBudget(userA, name = "User A Budget")
    budgetRepo.createBudget(userA, budgetA)

    val userABudgets = budgetRepo.getBudgets(userA).first()
    assertEquals(1, userABudgets.size)

    val userBBudgets = budgetRepo.getBudgets(userB).first()
    assertTrue("User B must see empty list of budgets", userBBudgets.isEmpty())

    val userBQueryA = budgetRepo.getBudgetById(userB, budgetA.id)
    assertNull("User B cannot fetch user A budget by ID", userBQueryA)
  }

  @Test
  fun `cross-user update attempt is rejected and does not mutate budget`() = runTest {
    val budgetA = buildBudget(userA, name = "Original A")
    budgetRepo.createBudget(userA, budgetA)

    // User B attempts to overwrite User A's budget
    val maliciousUpdate = budgetA.copy(name = "Hacked By User B", userId = userB)
    val result = budgetRepo.updateBudget(userB, maliciousUpdate)
    assertTrue("Cross-user update must fail", result.isFailure)

    // Verify original budget remains unchanged
    val unchanged = budgetRepo.getBudgetById(userA, budgetA.id)
    assertEquals("Original A", unchanged?.name)
    assertEquals(userA, unchanged?.userId)
  }

  @Test
  fun `cross-user delete attempt is rejected and preserves original budget`() = runTest {
    val budgetA = buildBudget(userA, name = "Protected A")
    budgetRepo.createBudget(userA, budgetA)

    // User B attempts to delete User A's budget
    val deleteResult = budgetRepo.deleteBudget(userB, budgetA.id)
    assertTrue("Cross-user delete must fail", deleteResult.isFailure)

    // Verify budget still exists
    val stillExists = budgetRepo.getBudgetById(userA, budgetA.id)
    assertNotNull("Budget must still exist after rejected deletion", stillExists)
  }

  @Test
  fun `conflicting budget creation for same category and currency in overlapping range is rejected`() = runTest {
    val budget1 = buildBudget(userA, name = "January Dining", startDate = 1000L, endDate = 2000L)
    val r1 = budgetRepo.createBudget(userA, budget1)
    assertTrue(r1.isSuccess)

    // Overlapping dates (1500 to 2500) for same category and currency
    val budget2 = buildBudget(userA, name = "Duplicate Dining", startDate = 1500L, endDate = 2500L)
    val r2 = budgetRepo.createBudget(userA, budget2)
    assertTrue("Conflicting budget in overlapping range must be rejected", r2.isFailure)
    assertTrue(r2.exceptionOrNull()?.message?.contains("already exists") == true)
  }

  @Test
  fun `validation rejects non-positive amounts, invalid currency, or invalid date range`() = runTest {
    val zeroAmount = buildBudget(userA, amountMinor = 0L)
    assertTrue(budgetRepo.createBudget(userA, zeroAmount).isFailure)

    val invalidCurrency = buildBudget(userA, currency = "INVALID")
    assertTrue(budgetRepo.createBudget(userA, invalidCurrency).isFailure)

    val invalidDates = buildBudget(userA, startDate = 2000L, endDate = 1000L)
    assertTrue(budgetRepo.createBudget(userA, invalidDates).isFailure)
  }

  @Test
  fun `migration 2 to 3 creates budgets table with required indexes non-destructively`() {
    val dbFile = File(context.cacheDir, "test_migration_2_3.db")
    if (dbFile.exists()) dbFile.delete()

    val helper =
      FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(dbFile.absolutePath)
          .callback(
            object : SupportSQLiteOpenHelper.Callback(2) {
              override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                  """
                  CREATE TABLE IF NOT EXISTS financial_accounts (
                    id TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    name TEXT NOT NULL
                  )
                  """.trimIndent()
                )
                db.execSQL(
                  "INSERT INTO financial_accounts VALUES ('acc_mig', 'user_1', 'Checking')"
                )
              }

              override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }
          )
          .build()
      )

    val db = helper.writableDatabase
    DatabaseMigrations.MIGRATION_2_3.migrate(db)

    // Verify budgets table and indexes exist
    val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='budgets'")
    assertTrue("Budgets table must be created", cursor.moveToFirst())
    cursor.close()

    val accountCursor = db.query("SELECT name FROM financial_accounts WHERE id = 'acc_mig'")
    assertTrue("Existing accounts must survive migration", accountCursor.moveToFirst())
    assertEquals("Checking", accountCursor.getString(0))
    accountCursor.close()

    db.close()
    helper.close()
    dbFile.delete()
  }
}
