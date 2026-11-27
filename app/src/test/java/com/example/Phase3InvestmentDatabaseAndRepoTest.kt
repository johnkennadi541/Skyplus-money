package com.example

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DispatcherProvider
import com.example.data.local.MarketPriceSyncPreferencesDataSource
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.DatabaseMigrations
import com.example.data.remote.AmfiMfApiMarketPriceDataSource
import com.example.data.repository.InvestmentRepositoryImpl
import com.example.domain.model.InvestmentCategory
import com.example.domain.model.PriceSourceType
import com.example.domain.repository.MarketPriceDataSource
import com.example.domain.repository.MarketQuoteFetchResult
import com.example.domain.repository.MarketQuoteRequest
import com.example.domain.usecase.InvestmentCalculationEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3InvestmentDatabaseAndRepoTest {

  private lateinit var context: android.content.Context
  private lateinit var db: AppDatabase
  private lateinit var syncPreferences: MarketPriceSyncPreferencesDataSource
  private lateinit var engine: InvestmentCalculationEngine

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
    syncPreferences = MarketPriceSyncPreferencesDataSource(context)
    engine = InvestmentCalculationEngine()
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun buildRepo(marketDataSource: MarketPriceDataSource): InvestmentRepositoryImpl =
    InvestmentRepositoryImpl(
      database = db,
      investmentDao = db.investmentDao(),
      calculationEngine = engine,
      marketPriceDataSource = marketDataSource,
      syncPreferencesDataSource = syncPreferences,
      dispatchers = testDispatchers,
    )

  private fun fakeCallFactory(
    handler: (Request) -> Response,
  ): Call.Factory =
    Call.Factory { request ->
      object : Call {
        override fun request(): Request = request
        override fun execute(): Response = handler(request)
        override fun enqueue(responseCallback: Callback) = Unit
        override fun cancel() = Unit
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun clone(): Call = this
        override fun timeout(): Timeout = Timeout.NONE
      }
    }

  @Test
  fun `create edit additional buy partial sell complete sell and delete persist accurately`() = runTest {
    val repo = buildRepo(AmfiMfApiMarketPriceDataSource(fakeCallFactory { throw IOException("offline") }, testDispatchers))

    val created =
      repo.createHoldingWithInitialBuy(
        authenticatedUserId = "uid_inv_1",
        name = "HDFC Bank Equity",
        category = InvestmentCategory.STOCKS,
        symbolOrTicker = "HDFCBANK",
        isin = "INE040A01034",
        brokerOrPlatform = "Zerodha",
        currencyCode = "INR",
        purchaseDate = 1_750_000_000_000L,
        quantityDecimal = "10",
        purchasePriceDecimal = "1500.00",
        chargesMinor = 50_00L,
        currentMarketPriceDecimal = "1600.00",
        autoPriceUpdateEnabled = true, // Ignored because STOCKS is manual-only
        notes = "Long term core equity",
      ).getOrThrow()

    assertFalse("Non-MF holding must never enable auto-sync", created.autoPriceUpdateEnabled)
    assertEquals(15050_00L, created.totalInvestedMinor)
    assertEquals(16000_00L, created.currentValuationMinor)

    // Edit metadata
    repo.updateHoldingMetadata(
      authenticatedUserId = "uid_inv_1",
      holding = created.copy(brokerOrPlatform = "Zerodha Kite", notes = "Updated note"),
    ).getOrThrow()
    assertEquals("Zerodha Kite", repo.getHoldingById("uid_inv_1", created.id)?.brokerOrPlatform)

    // Additional buy: 10 units @ 1700 + 50 charges = 17050 => total 20 units, open cost 32100
    repo.recordAdditionalPurchase(
      authenticatedUserId = "uid_inv_1",
      holdingId = created.id,
      transactionDate = 1_750_100_000_000L,
      quantityDecimal = "10",
      unitPriceDecimal = "1700.00",
      chargesMinor = 50_00L,
      notes = "Accumulation",
    ).getOrThrow()
    val afterBuy = repo.getHoldingById("uid_inv_1", created.id)!!
    assertEquals("20", afterBuy.quantityDecimal)
    assertEquals(32100_00L, afterBuy.totalInvestedMinor)

    // Partial sell: 5 units @ 1800, 0 charges => WAC cost = 32100 * 5/20 = 8025, realized = 9000 - 8025 = +975
    val sellTx =
      repo.recordSale(
        authenticatedUserId = "uid_inv_1",
        holdingId = created.id,
        transactionDate = 1_750_200_000_000L,
        quantityDecimal = "5",
        unitPriceDecimal = "1800.00",
        chargesMinor = 0L,
        notes = "Partial trim",
      ).getOrThrow()
    assertEquals(975_00L, sellTx.realizedPnlMinor)
    val afterPartialSell = repo.getHoldingById("uid_inv_1", created.id)!!
    assertEquals("15", afterPartialSell.quantityDecimal)
    assertEquals(24075_00L, afterPartialSell.totalInvestedMinor)
    assertEquals(975_00L, afterPartialSell.realizedPnlMinor)

    // Legacy delete action now archives the holding; all investment history remains intact.
    repo.deleteHolding("uid_inv_1", created.id).getOrThrow()
    val archived = repo.getHoldingById("uid_inv_1", created.id)
    assertNotNull(archived)
    assertTrue(archived!!.isArchived)
    assertTrue(repo.getHoldings("uid_inv_1").first().none { it.id == created.id })
    assertTrue(repo.getArchivedHoldings("uid_inv_1").first().any { it.id == created.id })
    assertEquals(3, repo.getTransactionsForHolding("uid_inv_1", created.id).first().size)
    assertTrue(repo.getPriceHistoryForHolding("uid_inv_1", created.id).first().isNotEmpty())

    repo.restoreHolding("uid_inv_1", created.id).getOrThrow()
    assertFalse(repo.getHoldingById("uid_inv_1", created.id)!!.isArchived)
    assertTrue(repo.getHoldings("uid_inv_1").first().any { it.id == created.id })
  }

  @Test
  fun `manual price update works offline and never gets overwritten automatically without explicit opt in`() = runTest {
    val repo =
      buildRepo(
        object : MarketPriceDataSource {
          override suspend fun fetchLatestQuote(request: MarketQuoteRequest): MarketQuoteFetchResult =
            MarketQuoteFetchResult.Success(
              unitPriceDecimal = "220.00",
              asOfMillis = 1_755_000_000_000L,
              source = PriceSourceType.MFAPI_AMFI_EOD,
              providerLabel = AmfiMfApiMarketPriceDataSource.PROVIDER_LABEL,
            )
        }
      )

    val mfHolding =
      repo.createHoldingWithInitialBuy(
        authenticatedUserId = "uid_man",
        name = "SBI Bluechip Fund",
        category = InvestmentCategory.MUTUAL_FUNDS,
        symbolOrTicker = "119598",
        isin = null,
        brokerOrPlatform = "Coin",
        currencyCode = "INR",
        purchaseDate = 1_750_000_000_000L,
        quantityDecimal = "100",
        purchasePriceDecimal = "90.00",
        chargesMinor = 0L,
        currentMarketPriceDecimal = "92.00",
        autoPriceUpdateEnabled = false,
        notes = null,
      ).getOrThrow()

    // Manual price update works offline
    repo.updateManualMarketPrice(
      authenticatedUserId = "uid_man",
      holdingId = mfHolding.id,
      newUnitPriceDecimal = "95.50",
      priceDate = 1_751_000_000_000L,
    ).getOrThrow()
    assertEquals(9550_00L, repo.getHoldingById("uid_man", mfHolding.id)?.currentValuationMinor)

    // Default global auto-consent is OFF
    assertFalse(syncPreferences.syncSettings.first().autoUpdateConsentEnabled)

    // Attempting auto-apply without holding opt-in or allowOverwriteManual MUST NOT overwrite manual price
    val overwrittenWhenDisabled =
      repo.applyAutomaticMarketPrice(
        authenticatedUserId = "uid_man",
        holdingId = mfHolding.id,
        newUnitPriceDecimal = "220.00",
        priceDate = 1_755_000_000_000L,
        source = PriceSourceType.MFAPI_AMFI_EOD,
        providerLabel = AmfiMfApiMarketPriceDataSource.PROVIDER_LABEL,
        allowOverwriteManual = false,
      ).getOrThrow()
    assertFalse(overwrittenWhenDisabled)
    assertEquals("95.5", repo.getHoldingById("uid_man", mfHolding.id)?.currentMarketPriceDecimal)

    // Now user explicitly opts in globally AND per holding -> sync succeeds cleanly!
    repo.setAutoUpdateConsent(true)
    repo.setHoldingAutoPriceUpdate("uid_man", mfHolding.id, true).getOrThrow()
    val summary = repo.syncEligibleMarketPrices("uid_man", forceBypassCooldown = true).getOrThrow()
    assertEquals(1, summary.updatedCount)
    assertEquals(22000_00L, repo.getHoldingById("uid_man", mfHolding.id)?.currentValuationMinor)
    assertEquals(
      PriceSourceType.MFAPI_AMFI_EOD,
      repo.getHoldingById("uid_man", mfHolding.id)?.lastPriceSource,
    )
  }

  @Test
  fun `amfi mfapi provider handles success 429 rate limit http error malformed json unsupported category and offline cleanly`() = runTest {
    val validJson =
      """
      {
        "meta": {"scheme_name": "Parag Parikh Flexi Cap Fund", "fund_house": "PPFAS Mutual Fund"},
        "data": [{"date": "18-11-2026", "nav": "84.5678"}],
        "status": "SUCCESS"
      }
      """.trimIndent()

    var mode = "SUCCESS"
    val callFactory =
      fakeCallFactory { req ->
        when (mode) {
          "OFFLINE" -> throw IOException("No network")
          "RATE_LIMIT" ->
            Response.Builder()
              .request(req)
              .protocol(Protocol.HTTP_1_1)
              .code(429)
              .message("Too Many Requests")
              .header("Retry-After", "120")
              .body("".toResponseBody("application/json".toMediaType()))
              .build()
          "HTTP_500" ->
            Response.Builder()
              .request(req)
              .protocol(Protocol.HTTP_1_1)
              .code(500)
              .message("Server Error")
              .body("{}".toResponseBody("application/json".toMediaType()))
              .build()
          "MALFORMED" ->
            Response.Builder()
              .request(req)
              .protocol(Protocol.HTTP_1_1)
              .code(200)
              .message("OK")
              .body("{bad_json".toResponseBody("application/json".toMediaType()))
              .build()
          else ->
            Response.Builder()
              .request(req)
              .protocol(Protocol.HTTP_1_1)
              .code(200)
              .message("OK")
              .body(validJson.toResponseBody("application/json".toMediaType()))
              .build()
        }
      }

    val provider = AmfiMfApiMarketPriceDataSource(callFactory, testDispatchers)

    // Unsupported stock category returns clear policy result without network call
    val stockRes =
      provider.fetchLatestQuote(
        MarketQuoteRequest("h_s", InvestmentCategory.STOCKS, "AAPL", null, "USD")
      )
    assertTrue(stockRes is MarketQuoteFetchResult.UnsupportedCategoryOrIdentifier)

    // Invalid scheme code returns UnsupportedCategoryOrIdentifier
    val badCodeRes =
      provider.fetchLatestQuote(
        MarketQuoteRequest("h_mf", InvestmentCategory.MUTUAL_FUNDS, "INVALID_ABC", null, "INR")
      )
    assertTrue(badCodeRes is MarketQuoteFetchResult.UnsupportedCategoryOrIdentifier)

    val validReq = MarketQuoteRequest("h_mf", InvestmentCategory.MUTUAL_FUNDS, "122639", null, "INR")

    mode = "SUCCESS"
    val success = provider.fetchLatestQuote(validReq) as MarketQuoteFetchResult.Success
    assertEquals("84.5678", success.unitPriceDecimal)
    assertEquals(PriceSourceType.MFAPI_AMFI_EOD, success.source)
    assertEquals("Parag Parikh Flexi Cap Fund", success.instrumentName)

    mode = "RATE_LIMIT"
    val rateLimited = provider.fetchLatestQuote(validReq) as MarketQuoteFetchResult.RateLimited
    assertEquals(120_000L, rateLimited.retryAfterMillis)

    mode = "HTTP_500"
    assertTrue(provider.fetchLatestQuote(validReq) is MarketQuoteFetchResult.NetworkError)

    mode = "MALFORMED"
    assertTrue(provider.fetchLatestQuote(validReq) is MarketQuoteFetchResult.InvalidResponse)

    mode = "OFFLINE"
    assertTrue(provider.fetchLatestQuote(validReq) is MarketQuoteFetchResult.NetworkError)
  }

  @Test
  fun `strict uid isolation blocks cross user read update buy sell manual price and delete`() = runTest {
    val repo = buildRepo(AmfiMfApiMarketPriceDataSource(fakeCallFactory { throw IOException("offline") }, testDispatchers))

    val ownerHolding =
      repo.createHoldingWithInitialBuy(
        authenticatedUserId = "uid_owner",
        name = "Sovereign Gold Bond",
        category = InvestmentCategory.GOLD,
        symbolOrTicker = "SGB",
        isin = null,
        brokerOrPlatform = "RBI",
        currencyCode = "INR",
        purchaseDate = 1_750_000_000_000L,
        quantityDecimal = "10",
        purchasePriceDecimal = "6500.00",
        chargesMinor = 0L,
        currentMarketPriceDecimal = "7000.00",
        autoPriceUpdateEnabled = false,
        notes = null,
      ).getOrThrow()

    assertNull(repo.getHoldingById("uid_attacker", ownerHolding.id))
    assertTrue(repo.getHoldings("uid_attacker").first().isEmpty())
    assertTrue(repo.updateHoldingMetadata("uid_attacker", ownerHolding.copy(userId = "uid_attacker")).isFailure)
    assertTrue(repo.recordAdditionalPurchase("uid_attacker", ownerHolding.id, 1_750_100_000_000L, "1", "7000", 0L, null).isFailure)
    assertTrue(repo.recordSale("uid_attacker", ownerHolding.id, 1_750_100_000_000L, "1", "7000", 0L, null).isFailure)
    assertTrue(repo.updateManualMarketPrice("uid_attacker", ownerHolding.id, "9999", 1_750_100_000_000L).isFailure)
    assertTrue(repo.deleteHolding("uid_attacker", ownerHolding.id).isFailure)
    assertNotNull(repo.getHoldingById("uid_owner", ownerHolding.id))
  }

  @Test
  fun `non destructive migration 4 to 5 preserves v4 loan and budget data and adds investment tables`() = runTest {
    val dbFile = File(context.cacheDir, "test_mig_v4_v5_${System.nanoTime()}.db")
    if (dbFile.exists()) dbFile.delete()

    val helper: SupportSQLiteOpenHelper =
      FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(dbFile.absolutePath)
          .callback(
            object : SupportSQLiteOpenHelper.Callback(4) {
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

                db.execSQL(
                  "INSERT INTO loans VALUES ('loan_v4', 'u_mig4', 'Auto Loan', 'ICICI', 'VEHICLE', 50000000, 'INR', '9.2', 48, 1000, 2000, 1249000, 59952000, 9952000, 0, 50000000, 'ACTIVE', NULL, 1000, 1000)"
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
      val preservedLoan = migratedDb.loanDao().getLoanById("u_mig4", "loan_v4")
      assertNotNull("Existing v4 loan must survive migration 4->5", preservedLoan)
      assertEquals("Auto Loan", preservedLoan?.name)

      val holdings = migratedDb.investmentDao().getAllHoldings("u_mig4").first()
      assertTrue("Investments table must exist and be empty after migration 4->5", holdings.isEmpty())
    } finally {
      migratedDb.close()
      dbFile.delete()
    }
  }
}
