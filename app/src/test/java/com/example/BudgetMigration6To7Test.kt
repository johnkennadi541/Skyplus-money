package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.data.local.db.DatabaseMigrations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Proves migration 6 → 7 preserves every existing budget and switches `isRecurring` on only for
 * budgets that are genuinely eligible.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BudgetMigration6To7Test {

  private lateinit var context: Context
  private lateinit var dbName: String
  private lateinit var helper: SupportSQLiteOpenHelper
  private lateinit var db: SupportSQLiteDatabase

  private val monthlyEligible = "b_monthly_eligible"
  private val customBudget = "b_custom"
  private val zeroLimitMonthly = "b_zero_limit"
  private val malformedMonthly = "b_malformed"

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    dbName = "budget-migration-6-7-${System.nanoTime()}.db"
    context.deleteDatabase(dbName)

    val configuration =
      SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(dbName)
        .callback(
          object : SupportSQLiteOpenHelper.Callback(6) {
            override fun onCreate(database: SupportSQLiteDatabase) {
              createVersion6Schema(database)
              insertVersion6Rows(database)
            }

            override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
              // Not used: the helper is always opened at version 6.
            }
          }
        )
        .build()

    helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
    db = helper.writableDatabase
  }

  @After
  fun tearDown() {
    runCatching { db.close() }
    runCatching { helper.close() }
    context.deleteDatabase(dbName)
  }

  private fun createVersion6Schema(database: SupportSQLiteDatabase) {
    database.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `budgets` (
        `id` TEXT NOT NULL,
        `userId` TEXT NOT NULL,
        `name` TEXT NOT NULL,
        `category` TEXT NOT NULL,
        `amountMinor` INTEGER NOT NULL,
        `currencyCode` TEXT NOT NULL,
        `periodType` TEXT NOT NULL,
        `startDate` INTEGER NOT NULL,
        `endDate` INTEGER NOT NULL,
        `warningThresholdPercent` INTEGER NOT NULL,
        `createdAt` INTEGER NOT NULL,
        `updatedAt` INTEGER NOT NULL,
        PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_userId_category ON budgets(userId, category)")
    database.execSQL(
      "CREATE INDEX IF NOT EXISTS index_budgets_userId_currencyCode ON budgets(userId, currencyCode)"
    )
    database.execSQL(
      "CREATE INDEX IF NOT EXISTS index_budgets_userId_startDate_endDate ON budgets(userId, startDate, endDate)"
    )
  }

  private fun insertVersion6Rows(database: SupportSQLiteDatabase) {
    insertBudget(database, monthlyEligible, "MONTHLY", 100_00L, 1_000L, 2_000L)
    insertBudget(database, customBudget, "CUSTOM", 100_00L, 1_000L, 2_000L)
    insertBudget(database, zeroLimitMonthly, "MONTHLY", 0L, 1_000L, 2_000L)
    insertBudget(database, malformedMonthly, "MONTHLY", 100_00L, 2_000L, 1_000L)
  }

  private fun insertBudget(
    database: SupportSQLiteDatabase,
    id: String,
    periodType: String,
    amountMinor: Long,
    startDate: Long,
    endDate: Long,
  ) {
    database.execSQL(
      """
      INSERT INTO budgets
        (id, userId, name, category, amountMinor, currencyCode, periodType,
         startDate, endDate, warningThresholdPercent, createdAt, updatedAt)
      VALUES (?, 'uid_1', ?, 'Food & Dining', ?, 'INR', ?, ?, ?, 75, 111, 222)
      """.trimIndent(),
      arrayOf<Any?>(id, "Budget $id", amountMinor, periodType, startDate, endDate),
    )
  }

  private fun isRecurringOf(id: String): Int =
    db.query("SELECT isRecurring FROM budgets WHERE id = ?", arrayOf<Any>(id)).use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    }

  private fun tableExists(name: String): Boolean =
    db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf<Any>(name))
      .use { it.moveToFirst() }

  private fun indexExists(name: String): Boolean =
    db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf<Any>(name))
      .use { it.moveToFirst() }

  @Test
  fun `every existing budget survives the migration unchanged`() {
    DatabaseMigrations.MIGRATION_6_7.migrate(db)

    db.query("SELECT * FROM budgets ORDER BY id").use { cursor ->
      assertEquals(4, cursor.count)
      cursor.moveToFirst()
      var seen = 0
      while (!cursor.isAfterLast) {
        seen++
        assertEquals("uid_1", cursor.getString(cursor.getColumnIndexOrThrow("userId")))
        assertEquals("Food & Dining", cursor.getString(cursor.getColumnIndexOrThrow("category")))
        assertEquals("INR", cursor.getString(cursor.getColumnIndexOrThrow("currencyCode")))
        assertEquals(75, cursor.getInt(cursor.getColumnIndexOrThrow("warningThresholdPercent")))
        assertEquals(111L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
        assertEquals(222L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
        cursor.moveToNext()
      }
      assertEquals(4, seen)
    }
  }

  @Test
  fun `an eligible monthly budget becomes recurring`() {
    DatabaseMigrations.MIGRATION_6_7.migrate(db)

    assertEquals(1, isRecurringOf(monthlyEligible))
  }

  @Test
  fun `a custom budget stays non-recurring`() {
    DatabaseMigrations.MIGRATION_6_7.migrate(db)

    assertEquals(0, isRecurringOf(customBudget))
  }

  @Test
  fun `a monthly budget with a zero limit stays non-recurring`() {
    DatabaseMigrations.MIGRATION_6_7.migrate(db)

    assertEquals(0, isRecurringOf(zeroLimitMonthly))
  }

  @Test
  fun `a monthly budget with a malformed window stays non-recurring`() {
    DatabaseMigrations.MIGRATION_6_7.migrate(db)

    assertEquals(0, isRecurringOf(malformedMonthly))
  }

  @Test
  fun `lastRenewedAt starts at zero for every budget`() {
    DatabaseMigrations.MIGRATION_6_7.migrate(db)

    db.query("SELECT lastRenewedAt FROM budgets").use { cursor ->
      cursor.moveToFirst()
      while (!cursor.isAfterLast) {
        assertEquals(0L, cursor.getLong(0))
        cursor.moveToNext()
      }
    }
  }

  @Test
  fun `the archive table and its indices exist after the migration`() {
    DatabaseMigrations.MIGRATION_6_7.migrate(db)

    assertNotNull(db)
    assertEquals(true, tableExists("budget_periods"))
    assertEquals(true, indexExists("index_budget_periods_userId"))
    assertEquals(true, indexExists("index_budget_periods_userId_budgetId"))
    assertEquals(true, indexExists("index_budget_periods_userId_periodEnd"))
  }

  @Test
  fun `the archive starts empty so no historical data is invented`() {
    DatabaseMigrations.MIGRATION_6_7.migrate(db)

    db.query("SELECT COUNT(*) FROM budget_periods").use { cursor ->
      cursor.moveToFirst()
      assertEquals(0, cursor.getInt(0))
    }
  }
}
