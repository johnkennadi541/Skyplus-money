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
 * Migration 7 → 8: existing CREDIT_CARD accounts are backfilled with safe defaults, nothing else is
 * touched, and running the migration again cannot duplicate a card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CreditCardMigration7To8Test {

  private lateinit var context: Context
  private lateinit var dbName: String
  private lateinit var helper: SupportSQLiteOpenHelper
  private lateinit var db: SupportSQLiteDatabase

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    dbName = "credit-card-migration-7-8-${System.nanoTime()}.db"
    context.deleteDatabase(dbName)

    val configuration =
      SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(dbName)
        .callback(
          object : SupportSQLiteOpenHelper.Callback(7) {
            override fun onCreate(database: SupportSQLiteDatabase) {
              createVersion7AccountsTable(database)
              insertAccounts(database)
            }

            override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
              // Not used: the helper is always opened at version 7.
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

  private fun createVersion7AccountsTable(database: SupportSQLiteDatabase) {
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
      "CREATE INDEX IF NOT EXISTS index_financial_accounts_userId ON financial_accounts(userId)"
    )
  }

  private fun insertAccounts(database: SupportSQLiteDatabase) {
    insertAccount(database, "acc_card_1", "HDFC Platinum", "CREDIT_CARD", "HDFC Bank", "1234", -25_000L)
    insertAccount(database, "acc_card_2", "ICICI Card", "CREDIT_CARD", null, null, 0L)
    insertAccount(database, "acc_bank_1", "Salary", "BANK", "SBI", "9999", 100_000L)
  }

  private fun insertAccount(
    database: SupportSQLiteDatabase,
    id: String,
    name: String,
    type: String,
    institution: String?,
    last4: String?,
    balance: Long,
  ) {
    database.execSQL(
      """
      INSERT INTO financial_accounts
        (id, userId, name, accountType, currencyCode, initialBalanceMinor, currentBalanceMinor,
         institutionName, accountNumberLast4, colorHex, isActive, createdAt, updatedAt)
      VALUES (?, 'uid_cards', ?, ?, 'INR', 0, ?, ?, ?, '#0A2540', 1, 111, 222)
      """.trimIndent(),
      arrayOf<Any?>(id, name, type, balance, institution, last4),
    )
  }

  private fun tableExists(name: String): Boolean =
    db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf<Any>(name))
      .use { it.moveToFirst() }

  private fun indexExists(name: String): Boolean =
    db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf<Any>(name))
      .use { it.moveToFirst() }

  private fun cardCount(): Int =
    db.query("SELECT COUNT(*) FROM credit_cards").use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    }

  @Test
  fun `the credit card table and its indices exist after the migration`() {
    DatabaseMigrations.MIGRATION_7_8.migrate(db)

    assertTrue(tableExists("credit_cards"))
    assertTrue(indexExists("index_credit_cards_accountId"))
    assertTrue(indexExists("index_credit_cards_userId"))
  }

  @Test
  fun `the accountId index is UNIQUE in the migrated schema`() {
    DatabaseMigrations.MIGRATION_7_8.migrate(db)

    var found = false
    db.query("PRAGMA index_list('credit_cards')").use { cursor ->
      val nameIndex = cursor.getColumnIndexOrThrow("name")
      val uniqueIndex = cursor.getColumnIndexOrThrow("unique")
      while (cursor.moveToNext()) {
        if (cursor.getString(nameIndex) == "index_credit_cards_accountId") {
          found = true
          assertEquals(1, cursor.getInt(uniqueIndex))
        }
      }
    }
    assertTrue(found)
  }

  @Test
  fun `the database itself rejects a second card for the same account`() {
    DatabaseMigrations.MIGRATION_7_8.migrate(db)

    val insertSecond =
      runCatching {
        db.execSQL(
          """
          INSERT INTO credit_cards
            (id, userId, accountId, issuerName, network, last4, creditLimitMinor, currencyCode,
             billingCycleDay, paymentDueDayOffset, aprPercent, isActive, createdAt, updatedAt)
          VALUES ('card_second', 'uid_cards', 'acc_card_1', 'Other', 'VISA', '9999', 1, 'INR',
                  1, 20, '0', 1, 1, 1)
          """
        )
      }

    assertTrue(insertSecond.isFailure)
    assertEquals(2, cardCount())
  }

  @Test
  fun `every existing credit card account is backfilled and nothing else is`() {
    DatabaseMigrations.MIGRATION_7_8.migrate(db)

    assertEquals(2, cardCount())

    db.query("SELECT accountId FROM credit_cards ORDER BY accountId").use { cursor ->
      val ids = mutableListOf<String>()
      cursor.moveToFirst()
      while (!cursor.isAfterLast) {
        ids += cursor.getString(0)
        cursor.moveToNext()
      }
      assertEquals(listOf("acc_card_1", "acc_card_2"), ids)
    }
  }

  @Test
  fun `backfilled cards carry safe defaults`() {
    DatabaseMigrations.MIGRATION_7_8.migrate(db)

    db.query(
      """
      SELECT issuerName, network, last4, creditLimitMinor, currencyCode, billingCycleDay,
             paymentDueDayOffset, aprPercent, isActive
      FROM credit_cards WHERE accountId = 'acc_card_1'
      """.trimIndent()
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("HDFC Bank", cursor.getString(0))
      assertEquals("OTHER", cursor.getString(1))
      assertEquals("1234", cursor.getString(2))
      assertEquals(0L, cursor.getLong(3))
      assertEquals("INR", cursor.getString(4))
      assertEquals(1, cursor.getInt(5))
      assertEquals(20, cursor.getInt(6))
      assertEquals("0", cursor.getString(7))
      assertEquals(1, cursor.getInt(8))
    }
  }

  @Test
  fun `an account without an institution falls back to its name and an empty last four`() {
    DatabaseMigrations.MIGRATION_7_8.migrate(db)

    db.query("SELECT issuerName, last4 FROM credit_cards WHERE accountId = 'acc_card_2'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("ICICI Card", cursor.getString(0))
      assertEquals("", cursor.getString(1))
    }
  }

  @Test
  fun `no existing account row is changed by the migration`() {
    DatabaseMigrations.MIGRATION_7_8.migrate(db)

    db.query(
      "SELECT id, name, accountType, currencyCode, currentBalanceMinor, institutionName, accountNumberLast4, isActive, createdAt, updatedAt FROM financial_accounts ORDER BY id"
    ).use { cursor ->
      assertEquals(3, cursor.count)
      cursor.moveToFirst()
      assertEquals("acc_bank_1", cursor.getString(0))
      assertEquals("Salary", cursor.getString(1))
      assertEquals("BANK", cursor.getString(2))
      assertEquals("INR", cursor.getString(3))
      assertEquals(100_000L, cursor.getLong(4))
      assertEquals("SBI", cursor.getString(5))
      assertEquals("9999", cursor.getString(6))
      assertEquals(1, cursor.getInt(7))
      assertEquals(111L, cursor.getLong(8))
      assertEquals(222L, cursor.getLong(9))
    }
  }

  @Test
  fun `running the migration again cannot duplicate a backfilled card`() {
    DatabaseMigrations.MIGRATION_7_8.migrate(db)
    DatabaseMigrations.MIGRATION_7_8.migrate(db)

    assertEquals(2, cardCount())
  }

  @Test
  fun `an installation with no credit card accounts migrates with no card rows`() {
    db.execSQL("DELETE FROM financial_accounts WHERE accountType = 'CREDIT_CARD'")

    DatabaseMigrations.MIGRATION_7_8.migrate(db)

    assertEquals(0, cardCount())
    assertTrue(tableExists("credit_cards"))
  }
}
