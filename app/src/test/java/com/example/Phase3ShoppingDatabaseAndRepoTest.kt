package com.example

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.DatabaseMigrations
import com.example.data.repository.ShoppingRepositoryImpl
import com.example.domain.usecase.ShoppingCalculationEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3ShoppingDatabaseAndRepoTest {

  private lateinit var context: android.content.Context
  private lateinit var db: AppDatabase
  private lateinit var repo: ShoppingRepositoryImpl
  private val engine = ShoppingCalculationEngine()

  private val testDispatchers =
    object : DispatcherProvider {
      override val main: CoroutineDispatcher = Dispatchers.Unconfined
      override val io: CoroutineDispatcher = Dispatchers.Unconfined
      override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    repo =
      ShoppingRepositoryImpl(
        database = db,
        shoppingDao = db.shoppingDao(),
        calculationEngine = engine,
        dispatchers = testDispatchers,
      )
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `multiple lists items purchase tracking and store price comparison persist accurately`() = runTest {
    val groceryList = repo.createList("user_1", "Grocery", "INR", 2000_00L, 80).getOrThrow()
    val kitchenList = repo.createList("user_1", "Kitchen", "USD", 150_00L, 75).getOrThrow()

    assertEquals(2, repo.getLists("user_1").first().size)

    // Add item with fractional quantity
    val rice =
      repo.addItem(
        authenticatedUserId = "user_1",
        listId = groceryList.id,
        name = "Basmati Rice",
        category = "Groceries",
        quantityDecimal = "2.5",
        unit = "kg",
        estimatedUnitPriceDecimal = "120.00",
        storeName = "SuperMart",
        notes = "Long grain",
      ).getOrThrow()
    assertEquals(300_00L, rice.estimatedTotalMinor)
    assertFalse(rice.isPurchased)

    // Currency change should be blocked while list has items
    val currencyChangeAttempt =
      repo.updateList("user_1", groceryList.copy(currencyCode = "USD"))
    assertTrue(currencyChangeAttempt.isFailure)

    // Record manual store price comparison quotes
    val q1 = repo.recordStorePrice("user_1", rice.id, "FreshCorner", "115.00", recordedAt = 1_000L).getOrThrow()
    val q2 = repo.recordStorePrice("user_1", rice.id, "DailyMart", "105.00", recordedAt = 2_000L).getOrThrow()
    val prices = repo.getStorePricesForList("user_1", groceryList.id).first()
    // Explicit quotes q1 and q2 recorded, ordered newest-first
    assertEquals(2, prices.size)
    assertEquals(q2.id, prices.first().id)
    assertTrue(prices.any { it.id == q1.id })

    // Mark purchased at lower actual price -> preserves estimated and records savings + purchase quote
    val purchased =
      repo.markItemPurchased(
        authenticatedUserId = "user_1",
        itemId = rice.id,
        actualUnitPriceDecimal = "100.00",
        actualTotalOverrideDecimal = null,
        purchasedAt = 5_000L,
        storeNameOverride = "DailyMart",
      ).getOrThrow()
    assertTrue(purchased.isPurchased)
    assertEquals(300_00L, purchased.estimatedTotalMinor)
    assertEquals(250_00L, purchased.actualTotalMinor)
    assertEquals(5_000L, purchased.purchasedAt)
    assertEquals(50_00L, engine.calculateSavingsOrOverspendMinor(purchased))

    val pricesAfterPurchase = repo.getStorePricesForList("user_1", groceryList.id).first()
    assertEquals(3, pricesAfterPurchase.size)
    assertEquals("DailyMart", pricesAfterPurchase.first().storeName)

    // Unpurchase restores pending state while keeping estimated values intact
    repo.markItemUnpurchased("user_1", rice.id).getOrThrow()
    val unpurchased = repo.getItemsForList("user_1", groceryList.id).first().single()
    assertFalse(unpurchased.isPurchased)
    assertNull(unpurchased.actualTotalMinor)
    assertEquals(300_00L, unpurchased.estimatedTotalMinor)

    // Legacy delete action archives instead of deleting the list or its history.
    repo.deleteList("user_1", kitchenList.id).getOrThrow()
    assertEquals(1, repo.getLists("user_1").first().size)
    assertEquals(kitchenList.id, repo.observeArchivedLists("user_1").first().single().id)
    repo.restoreList("user_1", kitchenList.id).getOrThrow()
    assertEquals(2, repo.getLists("user_1").first().size)
  }

  @Test
  fun `archiving shopping items and lists preserves purchase and store price history`() = runTest {
    val list = repo.createList("user_1", "History", "INR", 500_00L, 80).getOrThrow()
    val item = repo.addItem("user_1", list.id, "Rice", "Groceries", "2", "kg", "80.00", "Market", null).getOrThrow()
    repo.markItemPurchased("user_1", item.id, "75.00", purchasedAt = 1234L, storeNameOverride = "Market").getOrThrow()
    repo.recordStorePrice("user_1", item.id, "Other Market", "70.00", recordedAt = 2345L).getOrThrow()
    val priceCountBefore = repo.getStorePricesForList("user_1", list.id).first().size

    repo.deleteItem("user_1", item.id).getOrThrow()
    assertTrue(repo.getItemsForList("user_1", list.id).first().isEmpty())
    val archivedItem = repo.observeArchivedItems("user_1").first().single()
    assertEquals(item.id, archivedItem.id)
    assertTrue(archivedItem.isPurchased)
    assertEquals(1234L, archivedItem.purchasedAt)
    assertEquals(priceCountBefore, repo.getStorePricesForList("user_1", list.id).first().size)

    repo.restoreItem("user_1", item.id).getOrThrow()
    assertEquals(1, repo.getItemsForList("user_1", list.id).first().size)

    repo.deleteList("user_1", list.id).getOrThrow()
    assertTrue(repo.getLists("user_1").first().none { it.id == list.id })
    assertEquals(priceCountBefore, repo.getStorePricesForList("user_1", list.id).first().size)
    repo.restoreList("user_1", list.id).getOrThrow()
    assertEquals(1, repo.getLists("user_1").first().count { it.id == list.id })
  }

  @Test
  fun `uid isolation blocks cross-user reads updates purchases store quotes and deletes`() = runTest {
    val ownerList = repo.createList("owner_uid", "Private Pantry", "USD", 100_00L, 80).getOrThrow()
    val ownerItem =
      repo.addItem(
        authenticatedUserId = "owner_uid",
        listId = ownerList.id,
        name = "Coffee Beans",
        category = "Groceries",
        quantityDecimal = "1",
        unit = "packet",
        estimatedUnitPriceDecimal = "18.00",
        storeName = "Roasters",
        notes = null,
      ).getOrThrow()

    assertTrue(repo.getLists("attacker_uid").first().isEmpty())
    assertNull(repo.getListById("attacker_uid", ownerList.id))
    assertTrue(repo.getItemsForList("attacker_uid", ownerList.id).first().isEmpty())

    assertTrue(repo.updateList("attacker_uid", ownerList.copy(name = "Hacked")).isFailure)
    assertTrue(repo.deleteList("attacker_uid", ownerList.id).isFailure)
    assertTrue(
      repo.addItem(
        "attacker_uid",
        ownerList.id,
        "Injected",
        "Other",
        "1",
        "piece",
        "5.00",
        null,
        null,
      ).isFailure
    )
    assertTrue(repo.markItemPurchased("attacker_uid", ownerItem.id, "10.00").isFailure)
    assertTrue(repo.recordStorePrice("attacker_uid", ownerItem.id, "SpoofStore", "9.00").isFailure)
    assertTrue(repo.deleteItem("attacker_uid", ownerItem.id).isFailure)

    // Owner data remains intact
    val stillThere = repo.getItemsForList("owner_uid", ownerList.id).first()
    assertEquals(1, stillThere.size)
    assertEquals("Coffee Beans", stillThere.single().name)
  }

  @Test
  fun `non destructive migration 5 to 6 creates shopping tables and preserves existing v5 data`() = runTest {
    val dbFile = File.createTempFile("skyplus_mig56_", ".db", context.cacheDir)
    dbFile.delete()

    val factory = FrameworkSQLiteOpenHelperFactory()
    val helper: SupportSQLiteOpenHelper =
      factory.create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(dbFile.absolutePath)
          .callback(
            object : SupportSQLiteOpenHelper.Callback(5) {
              override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                  """
                  CREATE TABLE IF NOT EXISTS financial_accounts (
                    id TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    accountType TEXT NOT NULL,
                    currencyCode TEXT NOT NULL,
                    initialBalanceMinor INTEGER NOT NULL,
                    currentBalanceMinor INTEGER NOT NULL,
                    institutionName TEXT,
                    accountNumberLast4 TEXT,
                    colorHex TEXT NOT NULL,
                    isActive INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                  )
                  """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_accounts_userId ON financial_accounts(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_accounts_userId_name ON financial_accounts(userId, name)")

                db.execSQL(
                  """
                  CREATE TABLE IF NOT EXISTS financial_transactions (
                    id TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    accountId TEXT NOT NULL,
                    type TEXT NOT NULL,
                    status TEXT NOT NULL,
                    amountMinor INTEGER NOT NULL,
                    currencyCode TEXT NOT NULL,
                    category TEXT NOT NULL,
                    merchantOrPayee TEXT,
                    notes TEXT,
                    transactionDate INTEGER NOT NULL,
                    linkedTransferId TEXT,
                    transferDestinationAccountId TEXT,
                    isAutomaticallyDetected INTEGER NOT NULL,
                    sourceApplication TEXT,
                    referenceId TEXT,
                    confidenceScore REAL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    normalizedMerchant TEXT
                  )
                  """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId ON financial_transactions(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_accountId ON financial_transactions(userId, accountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_transactionDate ON financial_transactions(userId, transactionDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_referenceId ON financial_transactions(userId, referenceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_linkedTransferId ON financial_transactions(userId, linkedTransferId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_normalizedMerchant ON financial_transactions(userId, normalizedMerchant)")

                db.execSQL(
                  """
                  CREATE TABLE IF NOT EXISTS transaction_detection_events (
                    id TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    rawPackageName TEXT NOT NULL,
                    senderOrTitle TEXT NOT NULL,
                    detectedAmountMinor INTEGER NOT NULL,
                    detectedCurrency TEXT NOT NULL,
                    detectedType TEXT NOT NULL,
                    detectedMerchant TEXT,
                    detectedAccountLast4 TEXT,
                    referenceId TEXT,
                    confidence REAL NOT NULL,
                    confidenceLevel TEXT NOT NULL,
                    confidenceReasonsJoined TEXT NOT NULL,
                    dedupFingerprint TEXT NOT NULL,
                    status TEXT NOT NULL,
                    mappedAccountId TEXT,
                    createdTransactionId TEXT,
                    receivedAt INTEGER NOT NULL
                  )
                  """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_detection_events_userId ON transaction_detection_events(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_detection_events_userId_status ON transaction_detection_events(userId, status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_detection_events_userId_dedupFingerprint ON transaction_detection_events(userId, dedupFingerprint)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_detection_events_userId_referenceId ON transaction_detection_events(userId, referenceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_detection_events_userId_receivedAt ON transaction_detection_events(userId, receivedAt)")

                db.execSQL(
                  """
                  CREATE TABLE IF NOT EXISTS account_mapping_rules (
                    id TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    accountId TEXT NOT NULL,
                    matchPattern TEXT NOT NULL,
                    institutionHint TEXT,
                    isAutoMapped INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                  )
                  """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_account_mapping_rules_userId ON account_mapping_rules(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_account_mapping_rules_userId_accountId ON account_mapping_rules(userId, accountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_account_mapping_rules_userId_matchPattern ON account_mapping_rules(userId, matchPattern)")

                db.execSQL(
                  """
                  CREATE TABLE IF NOT EXISTS budgets (
                    id TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    amountMinor INTEGER NOT NULL,
                    currencyCode TEXT NOT NULL,
                    periodType TEXT NOT NULL,
                    startDate INTEGER NOT NULL,
                    endDate INTEGER NOT NULL,
                    warningThresholdPercent INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                  )
                  """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_userId_category ON budgets(userId, category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_userId_currencyCode ON budgets(userId, currencyCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_userId_startDate_endDate ON budgets(userId, startDate, endDate)")

                DatabaseMigrations.MIGRATION_3_4.migrate(db)
                DatabaseMigrations.MIGRATION_4_5.migrate(db)

                db.execSQL(
                  "INSERT INTO financial_accounts VALUES ('acc_v5', 'u_mig5', 'Primary Checking', 'BANK', 'USD', 250000, 250000, 'Ocean Bank', '4242', '#1E3A8A', 1, 1000, 1000)"
                )
                db.execSQL(
                  "INSERT INTO investment_holdings VALUES ('inv_v5', 'u_mig5', 'Nifty 50 ETF', 'ETFS', 'NIFTYBEES', NULL, 'Zerodha', 'INR', 1000, '10', 250000, 250000, 0, 0, '260', 260000, 1000, 'MANUAL', NULL, 0, NULL, 1000, 1000)"
                )
              }

              override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }
          )
          .build()
      )

    helper.writableDatabase.close()
    helper.close()

    val migratedDb =
      Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
        .allowMainThreadQueries()
        .addMigrations(
          DatabaseMigrations.MIGRATION_1_2,
          DatabaseMigrations.MIGRATION_2_3,
          DatabaseMigrations.MIGRATION_3_4,
          DatabaseMigrations.MIGRATION_4_5,
          DatabaseMigrations.MIGRATION_5_6,
          DatabaseMigrations.MIGRATION_6_7,
          DatabaseMigrations.MIGRATION_7_8,
          DatabaseMigrations.MIGRATION_8_9,
          DatabaseMigrations.MIGRATION_9_10,
          DatabaseMigrations.MIGRATION_10_11,
        )
        .build()

    try {
      val preservedAccount = migratedDb.financialAccountDao().getAccountById("u_mig5", "acc_v5")
      assertNotNull(preservedAccount)
      assertEquals("Primary Checking", preservedAccount?.name)

      val preservedHolding = migratedDb.investmentDao().getHoldingById("u_mig5", "inv_v5")
      assertNotNull(preservedHolding)
      assertEquals("Nifty 50 ETF", preservedHolding?.name)

      val shoppingLists = migratedDb.shoppingDao().getAllLists("u_mig5").first()
      assertTrue(shoppingLists.isEmpty())
    } finally {
      migratedDb.close()
      dbFile.delete()
    }
  }
}
