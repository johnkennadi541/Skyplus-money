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
 * Exercises the migration through Room itself: a complete v8 entity schema is created by Room,
 * then the production v9 AppDatabase opens the same file and validates its complete schema.
 */
@androidx.room.Entity(
  tableName = "credit_cards",
  indices = [
    androidx.room.Index(value = ["accountId"], unique = true),
    androidx.room.Index(value = ["userId"]),
  ],
)
data class CreditCardVersion8Entity(
  @androidx.room.PrimaryKey val id: String,
  val userId: String,
  val accountId: String,
  val issuerName: String,
  val network: String,
  val last4: String,
  val creditLimitMinor: Long,
  val currencyCode: String,
  val billingCycleDay: Int,
  val paymentDueDayOffset: Int,
  val aprPercent: String,
  val isActive: Boolean,
  val createdAt: Long,
  val updatedAt: Long,
)

@androidx.room.Database(
  entities = [
    com.example.data.local.db.entity.FinancialAccountEntity::class,
    com.example.data.local.db.entity.FinancialTransactionEntity::class,
    com.example.data.local.db.entity.TransactionDetectionEventEntity::class,
    com.example.data.local.db.entity.AccountMappingRuleEntity::class,
    com.example.data.local.db.entity.BudgetEntity::class,
    com.example.data.local.db.entity.BudgetPeriodEntity::class,
    CreditCardVersion8Entity::class,
    com.example.data.local.db.entity.LoanEntity::class,
    com.example.data.local.db.entity.LoanPaymentEntity::class,
    com.example.data.local.db.entity.InvestmentHoldingEntity::class,
    com.example.data.local.db.entity.InvestmentTransactionEntity::class,
    com.example.data.local.db.entity.InvestmentPriceHistoryEntity::class,
    com.example.data.local.db.entity.ShoppingListEntity::class,
    com.example.data.local.db.entity.ShoppingItemEntity::class,
    com.example.data.local.db.entity.ShoppingStorePriceEntity::class,
  ],
  version = 8,
  exportSchema = false,
)
abstract class SavingsGoalVersion8Database : androidx.room.RoomDatabase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SavingsGoalRoomMigrationIntegrationTest {

  @Test
  fun `Room opens a complete version 8 database through the production 8 to 10 migrations`() {
    val context = RuntimeEnvironment.getApplication()
    val dbName = "savings-room-migration-integration.db"
    context.deleteDatabase(dbName)

    val version8 =
      androidx.room.Room.databaseBuilder(
        context,
        SavingsGoalVersion8Database::class.java,
        dbName,
      ).build()

    version8.openHelper.writableDatabase.execSQL(
      """
      INSERT INTO financial_accounts
        (id, userId, name, accountType, currencyCode, initialBalanceMinor, currentBalanceMinor,
         institutionName, accountNumberLast4, colorHex, isActive, createdAt, updatedAt)
      VALUES ('room_acc_1', 'uid_room_test', 'Salary', 'BANK', 'INR', 0, 250000,
              'SBI', '9999', '#0A2540', 1, 111, 222)
      """.trimIndent()
    )
    version8.close()

    val migrated =
      androidx.room.Room.databaseBuilder(
        context,
        com.example.data.local.db.AppDatabase::class.java,
        dbName,
      )
        .addMigrations(DatabaseMigrations.MIGRATION_8_9, DatabaseMigrations.MIGRATION_9_10)
        .build()

    try {
      val database = migrated.openHelper.writableDatabase

      assertEquals(10, database.version)
      assertEquals(1, database.query("SELECT COUNT(*) FROM financial_accounts").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
      })
      database.query(
        "SELECT name, currentBalanceMinor, createdAt, updatedAt FROM financial_accounts WHERE id = 'room_acc_1'"
      ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("Salary", cursor.getString(0))
        assertEquals(250_000L, cursor.getLong(1))
        assertEquals(111L, cursor.getLong(2))
        assertEquals(222L, cursor.getLong(3))
      }

      for (table in listOf("savings_goals", "savings_contributions")) {
        database.query(
          "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
          arrayOf<Any>(table),
        ).use { cursor -> assertTrue("Missing migrated table: $table", cursor.moveToFirst()) }
      }
      for (index in listOf(
        "index_savings_goals_userId",
        "index_savings_goals_userId_currencyCode",
        "index_savings_contributions_userId",
        "index_savings_contributions_userId_goalId",
        "index_savings_contributions_userId_goalId_contributedAt",
      )) {
        database.query(
          "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
          arrayOf<Any>(index),
        ).use { cursor -> assertTrue("Missing migrated index: $index", cursor.moveToFirst()) }
      }
    } finally {
      migrated.close()
      context.deleteDatabase(dbName)
    }
  }
}
