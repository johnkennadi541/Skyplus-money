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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CreditCardMigration9To10Test {

  private lateinit var context: Context
  private lateinit var helper: SupportSQLiteOpenHelper
  private lateinit var db: SupportSQLiteDatabase
  private lateinit var dbName: String

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    dbName = "credit-card-migration-9-10-${System.nanoTime()}.db"
    context.deleteDatabase(dbName)
    val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
      .name(dbName)
      .callback(object : SupportSQLiteOpenHelper.Callback(9) {
        override fun onCreate(database: SupportSQLiteDatabase) {
          createVersion9Schema(database)
        }
        override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
      })
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

  private fun createVersion9Schema(database: SupportSQLiteDatabase) {
    database.execSQL("""
      CREATE TABLE credit_cards (
        id TEXT NOT NULL PRIMARY KEY,
        userId TEXT NOT NULL,
        accountId TEXT NOT NULL,
        issuerName TEXT NOT NULL,
        network TEXT NOT NULL,
        last4 TEXT NOT NULL,
        creditLimitMinor INTEGER NOT NULL,
        currencyCode TEXT NOT NULL,
        billingCycleDay INTEGER NOT NULL,
        paymentDueDayOffset INTEGER NOT NULL,
        aprPercent TEXT NOT NULL,
        isActive INTEGER NOT NULL,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL
      )
    """.trimIndent())
    database.execSQL("CREATE UNIQUE INDEX index_credit_cards_accountId ON credit_cards(accountId)")
    database.execSQL("CREATE INDEX index_credit_cards_userId ON credit_cards(userId)")
    database.execSQL("""
      INSERT INTO credit_cards
        (id,userId,accountId,issuerName,network,last4,creditLimitMinor,currencyCode,
         billingCycleDay,paymentDueDayOffset,aprPercent,isActive,createdAt,updatedAt)
      VALUES ('card_1','uid_migration','account_1','HDFC','VISA','4321',100000,'INR',5,20,'36',1,111,222)
    """.trimIndent())
  }

  private fun tableExists(name: String) =
    db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf<Any>(name))
      .use { it.moveToFirst() }

  private fun columnExists(table: String, column: String) =
    db.query("PRAGMA table_info($table)").use { cursor ->
      val nameIndex = cursor.getColumnIndex("name")
      var found = false
      while (cursor.moveToNext()) {
        if (cursor.getString(nameIndex) == column) found = true
      }
      found
    }

  private fun indexExists(name: String) =
    db.query("SELECT name FROM sqlite_master WHERE type='index' AND name=?", arrayOf<Any>(name))
      .use { it.moveToFirst() }

  @Test
  fun `migration adds safe card defaults and new statement and reward tables`() {
    DatabaseMigrations.MIGRATION_9_10.migrate(db)

    assertTrue(tableExists("credit_card_statements"))
    assertTrue(tableExists("credit_card_reward_events"))
    assertTrue(columnExists("credit_cards", "joiningFeeMinor"))
    assertTrue(columnExists("credit_card_statements", "lateFeeMinor"))
    assertTrue(indexExists("index_credit_card_statements_cardId"))
    assertTrue(indexExists("index_credit_card_reward_events_cardId"))

    db.query("""
      SELECT joiningFeeMinor, annualFeeMinor, annualFeeMonth, rewardProgramName, isFrozen,
             reminderEnabled, reminderDaysBefore, notes
      FROM credit_cards WHERE id='card_1'
    """.trimIndent()).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(0L, cursor.getLong(0))
      assertEquals(0L, cursor.getLong(1))
      assertEquals(1, cursor.getInt(2))
      assertTrue(cursor.isNull(3))
      assertEquals(0, cursor.getInt(4))
      assertEquals(1, cursor.getInt(5))
      assertEquals(3, cursor.getInt(6))
      assertTrue(cursor.isNull(7))
    }
  }

  @Test
  fun `migration preserves every existing card field and row`() {
    DatabaseMigrations.MIGRATION_9_10.migrate(db)

    db.query("""
      SELECT userId, accountId, issuerName, network, last4, creditLimitMinor,
             currencyCode, billingCycleDay, paymentDueDayOffset, aprPercent,
             isActive, createdAt, updatedAt
      FROM credit_cards WHERE id='card_1'
    """.trimIndent()).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("uid_migration", cursor.getString(0))
      assertEquals("account_1", cursor.getString(1))
      assertEquals("HDFC", cursor.getString(2))
      assertEquals("VISA", cursor.getString(3))
      assertEquals("4321", cursor.getString(4))
      assertEquals(100000L, cursor.getLong(5))
      assertEquals("INR", cursor.getString(6))
      assertEquals(5, cursor.getInt(7))
      assertEquals(20, cursor.getInt(8))
      assertEquals("36", cursor.getString(9))
      assertEquals(1, cursor.getInt(10))
      assertEquals(111L, cursor.getLong(11))
      assertEquals(222L, cursor.getLong(12))
    }
  }

  @Test
  fun `migration rejects duplicate application without changing existing card data`() {
    DatabaseMigrations.MIGRATION_9_10.migrate(db)
    val second = runCatching { DatabaseMigrations.MIGRATION_9_10.migrate(db) }
    assertTrue(second.isFailure)
    db.query("SELECT COUNT(*) FROM credit_cards").use { cursor ->
      cursor.moveToFirst()
      assertEquals(1, cursor.getInt(0))
    }
  }
}
