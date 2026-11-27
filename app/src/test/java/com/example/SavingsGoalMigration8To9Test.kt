package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.data.local.db.DatabaseMigrations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Migration 8 → 9: the savings tables appear, their indices exist, and every existing financial
 * row is preserved untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SavingsGoalMigration8To9Test {

  private lateinit var context: Context
  private lateinit var dbName: String
  private lateinit var helper: SupportSQLiteOpenHelper
  private lateinit var db: SupportSQLiteDatabase

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    dbName = "savings-migration-8-9-${System.nanoTime()}.db"
    context.deleteDatabase(dbName)

    val configuration =
      SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(dbName)
        .callback(
          object : SupportSQLiteOpenHelper.Callback(8) {
            override fun onCreate(database: SupportSQLiteDatabase) {
              createVersion8Schema(database)
              insertExistingData(database)
            }

            override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
              // Not used: the helper is always opened at version 8.
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

  private fun createVersion8Schema(database: SupportSQLiteDatabase) {
    database.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `financial_accounts` (
        `id` TEXT NOT NULL,
        `userId` TEXT NOT NULL,
        `name` TEXT NOT NULL,
        `accountType` TEXT NOT NULL,
        `currencyCode` TEXT NOT NULL,
        `initialBalanceMinor` INTEGER NOT NULL,
        `currentBalanceMinor` INTEGER NOT NULL,
        `institutionName` TEXT,
        `accountNumberLast4` TEXT,
        `colorHex` TEXT NOT NULL,
        `isActive` INTEGER NOT NULL,
        `createdAt` INTEGER NOT NULL,
        `updatedAt` INTEGER NOT NULL,
        PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )
    database.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `financial_transactions` (
        `id` TEXT NOT NULL,
        `userId` TEXT NOT NULL,
        `accountId` TEXT NOT NULL,
        `amountMinor` INTEGER NOT NULL,
        `currencyCode` TEXT NOT NULL,
        PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )
  }

  private fun insertExistingData(database: SupportSQLiteDatabase) {
    database.execSQL(
      """
      INSERT INTO financial_accounts
        (id, userId, name, accountType, currencyCode, initialBalanceMinor, currentBalanceMinor,
         institutionName, accountNumberLast4, colorHex, isActive, createdAt, updatedAt)
      VALUES ('acc_1', 'uid_goals', 'Salary', 'BANK', 'INR', 0, 250000, 'SBI', '9999', '#0A2540', 1, 111, 222)
      """.trimIndent()
    )
    database.execSQL(
      """
      INSERT INTO financial_transactions (id, userId, accountId, amountMinor, currencyCode)
      VALUES ('txn_1', 'uid_goals', 'acc_1', 25000, 'INR')
      """.trimIndent()
    )
  }

  private fun tableExists(name: String): Boolean =
    db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf<Any>(name))
      .use { it.moveToFirst() }

  private fun indexExists(name: String): Boolean =
    db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf<Any>(name))
      .use { it.moveToFirst() }

  private fun countOf(table: String): Int =
    db.query("SELECT COUNT(*) FROM $table").use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    }

  @Test
  fun `both savings tables and their indices exist after the migration`() {
    DatabaseMigrations.MIGRATION_8_9.migrate(db)

    assertTrue(tableExists("savings_goals"))
    assertTrue(tableExists("savings_contributions"))
    assertTrue(indexExists("index_savings_goals_userId"))
    assertTrue(indexExists("index_savings_goals_userId_currencyCode"))
    assertTrue(indexExists("index_savings_contributions_userId"))
    assertTrue(indexExists("index_savings_contributions_userId_goalId"))
    assertTrue(indexExists("index_savings_contributions_userId_goalId_contributedAt"))
  }

  @Test
  fun `the new tables start empty`() {
    DatabaseMigrations.MIGRATION_8_9.migrate(db)

    assertEquals(0, countOf("savings_goals"))
    assertEquals(0, countOf("savings_contributions"))
  }

  @Test
  fun `existing accounts and transactions are preserved exactly`() {
    DatabaseMigrations.MIGRATION_8_9.migrate(db)

    db.query(
      "SELECT id, name, accountType, currencyCode, currentBalanceMinor, isActive, createdAt, updatedAt FROM financial_accounts"
    ).use { cursor ->
      assertEquals(1, cursor.count)
      cursor.moveToFirst()
      assertEquals("acc_1", cursor.getString(0))
      assertEquals("Salary", cursor.getString(1))
      assertEquals("BANK", cursor.getString(2))
      assertEquals("INR", cursor.getString(3))
      assertEquals(250_000L, cursor.getLong(4))
      assertEquals(1, cursor.getInt(5))
      assertEquals(111L, cursor.getLong(6))
      assertEquals(222L, cursor.getLong(7))
    }

    db.query("SELECT id, amountMinor FROM financial_transactions").use { cursor ->
      assertEquals(1, cursor.count)
      cursor.moveToFirst()
      assertEquals("txn_1", cursor.getString(0))
      assertEquals(25_000L, cursor.getLong(1))
    }
  }

  @Test
  fun `the migration is idempotent`() {
    DatabaseMigrations.MIGRATION_8_9.migrate(db)
    DatabaseMigrations.MIGRATION_8_9.migrate(db)

    assertTrue(tableExists("savings_goals"))
    assertEquals(1, countOf("financial_accounts"))
  }

  @Test
  fun `savings rows can be written after the migration`() {
    DatabaseMigrations.MIGRATION_8_9.migrate(db)

    db.execSQL(
      """
      INSERT INTO savings_goals
        (id, userId, name, currencyCode, targetMinor, deadlineMillis, note, createdAt, updatedAt)
      VALUES ('goal_1', 'uid_goals', 'Emergency fund', 'INR', 100000, NULL, NULL, 1, 1)
      """.trimIndent()
    )
    db.execSQL(
      """
      INSERT INTO savings_contributions
        (id, userId, goalId, amountMinor, currencyCode, contributedAt, note, createdAt)
      VALUES ('c_1', 'uid_goals', 'goal_1', 25000, 'INR', 1, NULL, 1)
      """.trimIndent()
    )

    assertEquals(1, countOf("savings_goals"))
    assertEquals(1, countOf("savings_contributions"))
  }
}


/**
 * Migration 9 → 10 adds only an archive flag. Existing holding values are retained and
 * the default keeps every previously-created holding visible as active.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InvestmentArchiveMigration9To10Test {
  private lateinit var context: Context
  private lateinit var dbName: String
  private lateinit var helper: SupportSQLiteOpenHelper
  private lateinit var db: SupportSQLiteDatabase

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    dbName = "investment-archive-migration-9-10-${System.nanoTime()}.db"
    context.deleteDatabase(dbName)
    val configuration =
      SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(dbName)
        .callback(
          object : SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(database: SupportSQLiteDatabase) {
              database.execSQL(
                """
                CREATE TABLE investment_holdings (
                  id TEXT NOT NULL PRIMARY KEY,
                  userId TEXT NOT NULL,
                  name TEXT NOT NULL,
                  category TEXT NOT NULL,
                  symbolOrTicker TEXT,
                  isin TEXT,
                  brokerOrPlatform TEXT,
                  currencyCode TEXT NOT NULL,
                  purchaseDate INTEGER NOT NULL,
                  quantityDecimal TEXT NOT NULL,
                  totalInvestedMinor INTEGER NOT NULL,
                  totalBuyCostMinor INTEGER NOT NULL,
                  realizedPnlMinor INTEGER NOT NULL,
                  totalChargesMinor INTEGER NOT NULL,
                  currentMarketPriceDecimal TEXT NOT NULL,
                  currentValuationMinor INTEGER NOT NULL,
                  lastPriceUpdateDate INTEGER NOT NULL,
                  lastPriceSource TEXT NOT NULL,
                  lastPriceProviderLabel TEXT,
                  autoPriceUpdateEnabled INTEGER NOT NULL,
                  notes TEXT,
                  createdAt INTEGER NOT NULL,
                  updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
              )
              database.execSQL(
                """
                INSERT INTO investment_holdings
                  (id, userId, name, category, currencyCode, purchaseDate, quantityDecimal,
                   totalInvestedMinor, totalBuyCostMinor, realizedPnlMinor, totalChargesMinor,
                   currentMarketPriceDecimal, currentValuationMinor, lastPriceUpdateDate,
                   lastPriceSource, autoPriceUpdateEnabled, createdAt, updatedAt)
                VALUES ('holding_1', 'uid_archive', 'Index Fund', 'MUTUAL_FUNDS', 'INR',
                        100, '10', 50000, 50000, 0, 0, '50', 50000, 100,
                        'MANUAL', 0, 10, 20)
                """.trimIndent()
              )
            }

            override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
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

  @Test
  fun `archive migration preserves existing holding and defaults it to active`() {
    DatabaseMigrations.MIGRATION_9_10.migrate(db)

    db.query(
      "SELECT id, name, quantityDecimal, totalInvestedMinor, currentValuationMinor, isArchived FROM investment_holdings WHERE id = 'holding_1'"
    ).use { cursor ->
      assertEquals(1, cursor.count)
      cursor.moveToFirst()
      assertEquals("holding_1", cursor.getString(0))
      assertEquals("Index Fund", cursor.getString(1))
      assertEquals("10", cursor.getString(2))
      assertEquals(50_000L, cursor.getLong(3))
      assertEquals(50_000L, cursor.getLong(4))
      assertEquals(0, cursor.getInt(5))
    }
  }
}
