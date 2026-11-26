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

/** Migration 9 -> 10 adds archive metadata without changing card or ledger data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CreditCardArchiveMigration9To10Test {
  private lateinit var context: Context
  private lateinit var dbName: String
  private lateinit var helper: SupportSQLiteOpenHelper
  private lateinit var db: SupportSQLiteDatabase

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    dbName = "credit-card-archive-9-10-${System.nanoTime()}.db"
    context.deleteDatabase(dbName)
    val configuration =
      SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(dbName)
        .callback(
          object : SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(database: SupportSQLiteDatabase) {
              database.execSQL(
                """
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
                """.trimIndent()
              )
              database.execSQL(
                """
                CREATE TABLE financial_accounts (
                  id TEXT NOT NULL PRIMARY KEY,
                  userId TEXT NOT NULL,
                  currentBalanceMinor INTEGER NOT NULL
                )
                """.trimIndent()
              )
              database.execSQL(
                """
                CREATE TABLE financial_transactions (
                  id TEXT NOT NULL PRIMARY KEY,
                  userId TEXT NOT NULL,
                  accountId TEXT NOT NULL,
                  amountMinor INTEGER NOT NULL
                )
                """.trimIndent()
              )
              database.execSQL(
                """
                INSERT INTO credit_cards VALUES
                  ('card_1', 'uid_1', 'acc_1', 'HDFC', 'VISA', '1234', 100000,
                   'INR', 1, 20, '42', 1, 111, 222)
                """.trimIndent()
              )
              database.execSQL("INSERT INTO financial_accounts VALUES ('acc_1', 'uid_1', -42500)")
              database.execSQL(
                "INSERT INTO financial_transactions VALUES ('txn_1', 'uid_1', 'acc_1', 42500)"
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
  fun `migration adds default archive flag and preserves card ledger data`() {
    DatabaseMigrations.MIGRATION_9_10.migrate(db)

    db.query(
      "SELECT id, userId, accountId, issuerName, creditLimitMinor, isArchived FROM credit_cards"
    ).use { cursor ->
      assertEquals(1, cursor.count)
      cursor.moveToFirst()
      assertEquals("card_1", cursor.getString(0))
      assertEquals("uid_1", cursor.getString(1))
      assertEquals("acc_1", cursor.getString(2))
      assertEquals("HDFC", cursor.getString(3))
      assertEquals(100000L, cursor.getLong(4))
      assertEquals(0, cursor.getInt(5))
    }

    db.query("SELECT currentBalanceMinor FROM financial_accounts WHERE id = 'acc_1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(-42500L, cursor.getLong(0))
    }
    db.query("SELECT amountMinor FROM financial_transactions WHERE id = 'txn_1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(42500L, cursor.getLong(0))
    }
  }
}
