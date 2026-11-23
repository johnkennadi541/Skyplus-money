package com.example

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.DatabaseMigrations
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShoppingExtendedMigrationTest {
  @Test
  fun migration10To11AddsShoppingTablesWithoutDroppingExistingTables() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val databaseName = "shopping_extended_migration_test.db"
    context.deleteDatabase(databaseName)
    val helper = FrameworkSQLiteOpenHelperFactory().create(
      SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(databaseName)
        .callback(object : SupportSQLiteOpenHelper.Callback(10) {
          override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE financial_transactions (id TEXT NOT NULL PRIMARY KEY)")
            db.execSQL("CREATE TABLE shopping_items (id TEXT NOT NULL PRIMARY KEY)")
          }
          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        })
        .build()
    )
    try {
      val db = helper.writableDatabase
      db.execSQL("INSERT INTO financial_transactions(id) VALUES ('existing-ledger-row')")
      db.execSQL("INSERT INTO shopping_items(id) VALUES ('existing-shopping-row')")
      DatabaseMigrations.MIGRATION_10_11.migrate(db)
      fun exists(table: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor ->
          return cursor.moveToFirst()
        }
      }
      assertTrue(exists("financial_transactions"))
      assertTrue(exists("shopping_items"))
      assertTrue(exists("shopping_pantry"))
      assertTrue(exists("shopping_meal_plans"))
      assertTrue(exists("shopping_meal_ingredients"))
      assertTrue(exists("shopping_receipts"))
      db.query("SELECT id FROM financial_transactions").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertTrue(cursor.getString(0) == "existing-ledger-row")
      }
      db.query("SELECT id FROM shopping_items").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertTrue(cursor.getString(0) == "existing-shopping-row")
      }
    } finally {
      helper.close()
    }
  }
}
