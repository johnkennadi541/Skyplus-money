package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.data.local.db.DatabaseMigrations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Migration 10 -> 11 retains every savings goal and contribution row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SavingsGoalArchiveMigration10To11Test {
  private lateinit var context: Context
  private lateinit var dbName: String
  private lateinit var helper: SupportSQLiteOpenHelper
  private lateinit var db: SupportSQLiteDatabase

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    dbName = "savings-goal-retention-10-11-${System.nanoTime()}.db"
    context.deleteDatabase(dbName)
    val configuration =
      SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(dbName)
        .callback(
          object : SupportSQLiteOpenHelper.Callback(10) {
            override fun onCreate(database: SupportSQLiteDatabase) {
              database.execSQL(
                """
                CREATE TABLE savings_goals (
                  id TEXT NOT NULL PRIMARY KEY,
                  userId TEXT NOT NULL,
                  name TEXT NOT NULL,
                  currencyCode TEXT NOT NULL,
                  targetMinor INTEGER NOT NULL,
                  deadlineMillis INTEGER,
                  note TEXT,
                  createdAt INTEGER NOT NULL,
                  updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
              )
              database.execSQL(
                """
                CREATE TABLE savings_contributions (
                  id TEXT NOT NULL PRIMARY KEY,
                  userId TEXT NOT NULL,
                  goalId TEXT NOT NULL,
                  amountMinor INTEGER NOT NULL,
                  currencyCode TEXT NOT NULL,
                  contributedAt INTEGER NOT NULL,
                  note TEXT,
                  createdAt INTEGER NOT NULL
                )
                """.trimIndent()
              )
              database.execSQL(
                """
                INSERT INTO savings_goals VALUES
                  ('goal_1', 'uid_1', 'Emergency fund', 'INR', 100000, NULL, 'keep',
                   111, 222)
                """.trimIndent()
              )
              database.execSQL(
                """
                INSERT INTO savings_contributions VALUES
                  ('contrib_1', 'uid_1', 'goal_1', 25000, 'INR', 333, 'first deposit', 444)
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
  fun `migration adds archive and void markers without changing saved history`() {
    DatabaseMigrations.MIGRATION_10_11.migrate(db)

    db.query(
      "SELECT id, name, targetMinor, isArchived FROM savings_goals WHERE id = 'goal_1'"
    ).use { cursor ->
      assertEquals(1, cursor.count)
      cursor.moveToFirst()
      assertEquals("goal_1", cursor.getString(0))
      assertEquals("Emergency fund", cursor.getString(1))
      assertEquals(100000L, cursor.getLong(2))
      assertFalse(cursor.getInt(3) == 1)
    }

    db.query(
      "SELECT id, amountMinor, contributedAt, note, isVoided, voidedAt FROM savings_contributions WHERE id = 'contrib_1'"
    ).use { cursor ->
      assertEquals(1, cursor.count)
      cursor.moveToFirst()
      assertEquals("contrib_1", cursor.getString(0))
      assertEquals(25000L, cursor.getLong(1))
      assertEquals(333L, cursor.getLong(2))
      assertEquals("first deposit", cursor.getString(3))
      assertFalse(cursor.getInt(4) == 1)
      assertTrue(cursor.isNull(5))
    }
  }
}
