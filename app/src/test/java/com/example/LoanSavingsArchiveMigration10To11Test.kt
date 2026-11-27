package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.DatabaseMigrations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LoanSavingsArchiveMigration10To11Test {
  private lateinit var context: Context
  private lateinit var helper: SupportSQLiteOpenHelper
  private lateinit var db: SupportSQLiteDatabase
  private lateinit var name: String

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    name = "loan-savings-archive-10-11-${System.nanoTime()}.db"
    val config = SupportSQLiteOpenHelper.Configuration.builder(context)
      .name(name)
      .callback(object : SupportSQLiteOpenHelper.Callback(10) {
        override fun onCreate(database: SupportSQLiteDatabase) {
          database.execSQL("CREATE TABLE loans (id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, name TEXT NOT NULL)")
          database.execSQL("CREATE TABLE savings_goals (id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, name TEXT NOT NULL)")
          database.execSQL("INSERT INTO loans (id, userId, name) VALUES ('loan_keep', 'uid_a', 'Car loan')")
          database.execSQL("INSERT INTO savings_goals (id, userId, name) VALUES ('goal_keep', 'uid_a', 'Emergency fund')")
        }
        override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
      }).build()
    helper = FrameworkSQLiteOpenHelperFactory().create(config)
    db = helper.writableDatabase
  }

  @After
  fun tearDown() {
    db.close()
    helper.close()
    context.deleteDatabase(name)
  }

  @Test
  fun `migration adds archive flags and preserves existing records`() {
    DatabaseMigrations.MIGRATION_10_11.migrate(db)

    db.query("SELECT id, name, isArchived FROM loans WHERE id = 'loan_keep'").use { cursor ->
      assertEquals(1, cursor.count)
      cursor.moveToFirst()
      assertEquals("loan_keep", cursor.getString(0))
      assertEquals("Car loan", cursor.getString(1))
      assertEquals(0, cursor.getInt(2))
    }
    db.query("SELECT id, name, isArchived FROM savings_goals WHERE id = 'goal_keep'").use { cursor ->
      assertEquals(1, cursor.count)
      cursor.moveToFirst()
      assertEquals("goal_keep", cursor.getString(0))
      assertEquals("Emergency fund", cursor.getString(1))
      assertEquals(0, cursor.getInt(2))
    }
  }
}
