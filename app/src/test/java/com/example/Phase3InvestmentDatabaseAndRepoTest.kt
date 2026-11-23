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
import com.example.domain.model.InvestmentTransactionType
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
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
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
  fun `first opted in mutual fund NAV can be older than purchase day`() = runTest {
    val uid = "uid_first_nav"
    syncPreferences.setAutoUpdateConsent(uid, true)
    val repo = buildRepo(
      object : MarketPriceDataSource {
        override suspend fun fetchLatestQuote(request: MarketQuoteRequest): MarketQuoteFetchResult =
          MarketQuoteFetchResult.UnsupportedCategoryOrIdentifier("not used")
      }
    )
    val now = System.currentTimeMillis()
    val holding =
      repo.createHoldingWithInitialBuy(
        authenticatedUserId = uid,
        name = "Test Mutual Fund",
        category = InvestmentCategory.MUTUAL_FUNDS,
        symbolOrTicker = "119598",
        isin = null,
        brokerOrPlatform = null,
        currencyCode = "INR",
        purchaseDate = now,
        quantityDecimal = "1",
        purchasePriceDecimal = "100",
        chargesMinor = 0L,
        currentMarketPriceDecimal = "100",
        autoPriceUpdateEnabled = true,
        notes = null,
      ).getOrThrow()

    val applied = repo.applyAutomaticMarketPrice(
      authenticatedUserId = uid,
      holdingId = holding.id,
      newUnitPriceDecimal = "101",
      priceDate = now - 86_400_000L,
      source = PriceSourceType.MFAPI_AMFI_EOD,
      providerLabel = AmfiMfApiMarketPriceDataSource.PROVIDER_LABEL,
      allowOverwriteManual = false,
    ).getOrThrow()

    assertTrue(applied)
    assertEquals("101", repo.getHoldingById(uid, holding.id)?.currentMarketPriceDecimal)
  }

  @Test
  fun `missing initial market price is not recorded as a fresh quote or price history point`() = runTest {
    val uid = "uid_awaiting_market_price"
    val repo = buildRepo(
      object : MarketPriceDataSource {
        override suspend fun fetchLatestQuote(request: MarketQuoteRequest): MarketQuoteFetchResult =
          MarketQuoteFetchResult.UnsupportedCategoryOrIdentifier("manual only")
      }
    )

    val holding = repo.createHoldingWithInitialBuy(
      authenticatedUserId = uid,
      name = "Manual-only Equity",
      category = InvestmentCategory.STOCKS,
      symbolOrTicker = "TEST",
      isin = null,
      brokerOrPlatform = null,
      currencyCode = "INR",
      purchaseDate = System.currentTimeMillis(),
      quantityDecimal = "2",
      purchasePriceDecimal = "100",
      chargesMinor = 0L,
      currentMarketPriceDecimal = null,
      autoPriceUpdateEnabled = false,
      notes = null,
    ).getOrThrow()

    assertEquals("100", holding.currentMarketPriceDecimal) // Cost-price fallback only.
    assertEquals(0L, holding.lastPriceUpdateDate)
    assertEquals("Awaiting current price", holding.lastPriceProviderLabel)
    assertTrue(repo.getPriceHistoryForHolding(uid, holding.id).first().isEmpty())
    assertEquals(1, repo.getTransactionsForHolding(uid, holding.id).first().size)
  }

  @Test
  fun `market sync rotates through holdings beyond the five request cycle cap`() = runTest {
    val uid = "uid_market_rotation"
    syncPreferences.setAutoUpdateConsent(uid, true)
    var fetchCount = 0
    val repo = buildRepo(
      object : MarketPriceDataSource {
        override suspend fun fetchLatestQuote(request: MarketQuoteRequest): MarketQuoteFetchResult {
          fetchCount++
          return MarketQuoteFetchResult.Success(
            unitPriceDecimal = "105.00",
            asOfMillis = System.currentTimeMillis(),
            source = PriceSourceType.MFAPI_AMFI_EOD,
            providerLabel = AmfiMfApiMarketPriceDataSource.PROVIDER_LABEL,
          )
        }
      }
    )

    val created = (1..6).map { index ->
      repo.createHoldingWithInitialBuy(
        authenticatedUserId = uid,
        name = "Fund $index",
        category = InvestmentCategory.MUTUAL_FUNDS,
        symbolOrTicker = (12000 + index).toString(),
        isin = null,
        brokerOrPlatform = null,
        currencyCode = "INR",
        purchaseDate = System.currentTimeMillis() - 10_000L,
        quantityDecimal = "1",
        purchasePriceDecimal = "100",
        chargesMinor = 0L,
        currentMarketPriceDecimal = null,
        autoPriceUpdateEnabled = true,
        notes = null,
      ).getOrThrow()
    }

    val firstCycle = repo.syncEligibleMarketPrices(uid, forceBypassCooldown = true).getOrThrow()
    assertEquals(5, firstCycle.updatedCount)
    assertEquals(5, fetchCount)

    // A second cycle must prioritize the one never-quoted holding instead of repeating
    // the first five holdings that now have a fresh EOD quote.
    val secondCycle = repo.syncEligibleMarketPrices(uid, forceBypassCooldown = false).getOrThrow()
    assertEquals(1, secondCycle.updatedCount)
    assertEquals(6, fetchCount)
    assertTrue(created.all { repo.getHoldingById(uid, it.id)?.lastPriceSource == PriceSourceType.MFAPI_AMFI_EOD })
  }

  @Test
  fun `new investment holdings reject non INR currencies`() = runTest {
    val repo = buildRepo(
      object : MarketPriceDataSource {
        override suspend fun fetchLatestQuote(request: MarketQuoteRequest): MarketQuoteFetchResult =
          MarketQuoteFetchResult.UnsupportedCategoryOrIdentifier("manual only")
      }
    )
    val result =
      repo.createHoldingWithInitialBuy(
        authenticatedUserId = "uid_india_only",
        name = "Non-India Asset",
        category = InvestmentCategory.STOCKS,
        symbolOrTicker = "ABC",
        isin = null,
        brokerOrPlatform = null,
        currencyCode = "USD",
        purchaseDate = System.currentTimeMillis(),
        quantityDecimal = "1",
        purchasePriceDecimal = "10",
        chargesMinor = 0L,
        currentMarketPriceDecimal = null,
        autoPriceUpdateEnabled = false,
        notes = null,
      )

    assertTrue(result.isFailure)
    assertTrue(db.investmentDao().getAllHoldingsSync("uid_india_only").isEmpty())
  }

  @Test
  fun `market sync preferences are isolated by authenticated UID`() = runTest {
    syncPreferences.setAutoUpdateConsent("uid_market_a", true)
    syncPreferences.setSyncOnlyOnWifi("uid_market_a", false)

    val userA = syncPreferences.syncSettings("uid_market_a").first()
    val userB = syncPreferences.syncSettings("uid_market_b").first()

    assertTrue(userA.autoUpdateConsentEnabled)
    assertFalse(userB.autoUpdateConsentEnabled)
    assertFalse(userA.syncOnlyOnWifi)
    assertTrue(userB.syncOnlyOnWifi)
  }

  @Test
  fun `market feed rejects oversized unknown length response without parsing it`() = runTest {
    val oversizedJson = """{"status":"SUCCESS","data":[{"date":"01-01-2026","nav":"100.00"}]}""" +
      " ".repeat(70 * 1024)
    val provider =
      AmfiMfApiMarketPriceDataSource(
        fakeCallFactory { request ->
          Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(
              object : ResponseBody() {
                override fun contentType() = "application/json".toMediaType()
                override fun contentLength(): Long = -1L
                override fun source() = Buffer().writeUtf8(oversizedJson)
              }
            )
            .build()
        },
        testDispatchers,
      )

    val result =
      provider.fetchLatestQuote(
        MarketQuoteRequest("holding_size_test", InvestmentCategory.MUTUAL_FUNDS, "12345", null, "INR")
      )

    assertTrue(
      "Oversized response with unknown content length must be rejected",
      result is MarketQuoteFetchResult.InvalidResponse,
    )
  }

  @Test
  fun `manual overwrite flag cannot bypass global market update opt in`() = runTest {
    syncPreferences.setAutoUpdateConsent("uid_opt_in_guard", false)
    // A different Google account's consent must not authorize this user's requests.
    syncPreferences.setAutoUpdateConsent("uid_other_account", true)
    var fetchCount = 0
    val dataSource =
      object : MarketPriceDataSource {
        override suspend fun fetchLatestQuote(request: MarketQuoteRequest): MarketQuoteFetchResult {
          fetchCount++
          return MarketQuoteFetchResult.Success(
            unitPriceDecimal = "125.00",
            asOfMillis = System.currentTimeMillis(),
            source = PriceSourceType.MFAPI_AMFI_EOD,
            providerLabel = AmfiMfApiMarketPriceDataSource.PROVIDER_LABEL,
            instrumentName = "Test Fund",
          )
        }
      }
    val repo = buildRepo(dataSource)
    val holding =
      repo.createHoldingWithInitialBuy(
        authenticatedUserId = "uid_opt_in_guard",
        name = "Test Mutual Fund",
        category = InvestmentCategory.MUTUAL_FUNDS,
        symbolOrTicker = "12345",
        isin = null,
        brokerOrPlatform = null,
        currencyCode = "INR",
        purchaseDate = 1_750_000_000_000L,
        quantityDecimal = "2",
        purchasePriceDecimal = "100.00",
        chargesMinor = 0L,
        currentMarketPriceDecimal = "110.00",
        autoPriceUpdateEnabled = true,
        notes = null,
      ).getOrThrow()

    val result =
      repo.syncEligibleMarketPrices(
        authenticatedUserId = "uid_opt_in_guard",
        allowOverwriteManual = true,
        forceBypassCooldown = true,
      ).getOrThrow()

    assertEquals("Global opt-in must block all provider requests", 0, fetchCount)
    assertEquals(0, result.updatedCount)
    assertEquals(
      "110",
      repo.getHoldingById("uid_opt_in_guard", holding.id)?.currentMarketPriceDecimal,
    )
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
    assertEquals(1, repo.getPriceHistoryForHolding("uid_inv_1", created.id).first().size)

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
    // A BUY creates a portfolio valuation snapshot from the last known quote,
    // but does not claim that a new market quote was received.
    val historyAfterBuy = repo.getPriceHistoryForHolding("uid_inv_1", created.id).first()
    assertEquals(2, historyAfterBuy.size)
    assertEquals("Portfolio snapshot after BUY", historyAfterBuy.first().providerLabel)

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
    // A SELL records the changed portfolio valuation without changing quote provenance.
    val historyAfterSell = repo.getPriceHistoryForHolding("uid_inv_1", created.id).first()
    assertEquals(3, historyAfterSell.size)
    assertEquals("Portfolio snapshot after SELL", historyAfterSell.first().providerLabel)

    // Delete holding cascades transactions and history
    repo.deleteHolding("uid_inv_1", created.id).getOrThrow()
    assertNull(repo.getHoldingById("uid_inv_1", created.id))
    assertTrue(repo.getTransactionsForHolding("uid_inv_1", created.id).first().isEmpty())
    assertTrue(repo.getPriceHistoryForHolding("uid_inv_1", created.id).first().isEmpty())
  }

  @Test
  fun `complete sale closes holding and a later new buy reopens it without changing old ledger rows`() = runTest {
    val repo = buildRepo(
      AmfiMfApiMarketPriceDataSource(fakeCallFactory { throw IOException("offline") }, testDispatchers)
    )
    val uid = "uid_complete_exit"
    val purchaseDate = System.currentTimeMillis() - 5_000L
    val holding = repo.createHoldingWithInitialBuy(
      authenticatedUserId = uid,
      name = "Complete Exit Test",
      category = InvestmentCategory.STOCKS,
      symbolOrTicker = "EXIT",
      isin = null,
      brokerOrPlatform = null,
      currencyCode = "INR",
      purchaseDate = purchaseDate,
      quantityDecimal = "10",
      purchasePriceDecimal = "100",
      chargesMinor = 1_000L,
      currentMarketPriceDecimal = "105",
      autoPriceUpdateEnabled = false,
      notes = null,
    ).getOrThrow()

    val sellDate = purchaseDate + 1_000L
    val sale = repo.recordSale(
      authenticatedUserId = uid,
      holdingId = holding.id,
      transactionDate = sellDate,
      quantityDecimal = "10",
      unitPriceDecimal = "110",
      chargesMinor = 500L,
      notes = "Full exit",
    ).getOrThrow()

    assertEquals(110_000L, sale.grossAmountMinor)
    assertEquals(109_500L, sale.netAmountMinor)
    assertEquals(101_000L, sale.costBasisAllocatedMinor)
    assertEquals(8_500L, sale.realizedPnlMinor)

    val exited = repo.getHoldingById(uid, holding.id)!!
    assertEquals("0", exited.quantityDecimal)
    assertEquals(0L, exited.totalInvestedMinor)
    assertEquals(0L, exited.currentValuationMinor)
    assertEquals(8_500L, exited.realizedPnlMinor)
    assertEquals(101_000L, exited.totalBuyCostMinor)

    assertTrue(
      "A further sale after complete exit must be rejected",
      repo.recordSale(uid, holding.id, sellDate, "1", "110", 0L, null).isFailure,
    )

    // A later BUY is a new position; historical realized P&L and original ledger rows remain.
    repo.recordAdditionalPurchase(uid, holding.id, sellDate, "1", "120", 0L, "Re-entry").getOrThrow()
    val reopened = repo.getHoldingById(uid, holding.id)!!
    assertEquals("1", reopened.quantityDecimal)
    assertEquals(12_000L, reopened.totalInvestedMinor)
    assertEquals(113_000L, reopened.totalBuyCostMinor)
    assertEquals(8_500L, reopened.realizedPnlMinor)

    val ledger = repo.getTransactionsForHolding(uid, holding.id).first()
    assertEquals(3, ledger.size)
    assertEquals(InvestmentTransactionType.BUY, ledger.first { it.notes == "Initial purchase" }.type)
    assertEquals(InvestmentTransactionType.SELL, ledger.first { it.notes == "Full exit" }.type)
    assertEquals(InvestmentTransactionType.BUY, ledger.first { it.notes == "Re-entry" }.type)
  }

  @Test
  fun `buy and sell transactions reject dates earlier than latest recorded transaction`() = runTest {
    val repo = buildRepo(AmfiMfApiMarketPriceDataSource(fakeCallFactory { throw IOException("offline") }, testDispatchers))
    val uid = "uid_transaction_order"
    val purchaseDate = 1_750_000_000_000L
    val holding = repo.createHoldingWithInitialBuy(
      authenticatedUserId = uid, name = "Chronology Test Fund", category = InvestmentCategory.STOCKS,
      symbolOrTicker = "TEST", isin = null, brokerOrPlatform = null, currencyCode = "INR",
      purchaseDate = purchaseDate, quantityDecimal = "10", purchasePriceDecimal = "100",
      chargesMinor = 0L, currentMarketPriceDecimal = "100", autoPriceUpdateEnabled = false, notes = null,
    ).getOrThrow()
    val latestTradeDate = purchaseDate + 86_400_000L
    repo.recordAdditionalPurchase(uid, holding.id, latestTradeDate, "5", "110", 0L, null).getOrThrow()
    val backdatedBuy = repo.recordAdditionalPurchase(uid, holding.id, latestTradeDate - 1L, "1", "105", 0L, null)
    val backdatedSell = repo.recordSale(uid, holding.id, latestTradeDate - 1L, "1", "120", 0L, null)
    assertTrue("Backdated buy must be rejected to preserve WAC chronology", backdatedBuy.isFailure)
    assertTrue("Backdated sell must be rejected to preserve WAC chronology", backdatedSell.isFailure)
    assertEquals("15", repo.getHoldingById(uid, holding.id)?.quantityDecimal)
    assertEquals(2, repo.getTransactionsForHolding(uid, holding.id).first().size)
  }
  @Test
  fun `rejected automatic quote is counted as an error not a manual skip`() = runTest {
    val uid = "uid_invalid_quote_source"
    syncPreferences.setAutoUpdateConsent(uid, true)
    val repo =
      buildRepo(
        object : MarketPriceDataSource {
          override suspend fun fetchLatestQuote(request: MarketQuoteRequest): MarketQuoteFetchResult =
            MarketQuoteFetchResult.Success(
              unitPriceDecimal = "125.00",
              asOfMillis = System.currentTimeMillis(),
              // A provider must never be able to label a quote as a manual price.
              source = PriceSourceType.MANUAL,
              providerLabel = "Unapproved source",
            )
        }
      )
    val now = System.currentTimeMillis()
    val holding =
      repo.createHoldingWithInitialBuy(
        authenticatedUserId = uid,
        name = "Guarded Mutual Fund",
        category = InvestmentCategory.MUTUAL_FUNDS,
        symbolOrTicker = "119598",
        isin = null,
        brokerOrPlatform = null,
        currencyCode = "INR",
        purchaseDate = now,
        quantityDecimal = "10",
        purchasePriceDecimal = "100.00",
        chargesMinor = 0L,
        currentMarketPriceDecimal = "100.00",
        autoPriceUpdateEnabled = true,
        notes = null,
      ).getOrThrow()

    val summary = repo.syncEligibleMarketPrices(uid, forceBypassCooldown = true).getOrThrow()

    assertEquals(0, summary.updatedCount)
    assertEquals(0, summary.skippedManualCount)
    assertEquals(1, summary.errorCount)
    assertEquals("100", repo.getHoldingById(uid, holding.id)?.currentMarketPriceDecimal)
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

    // Reset persisted DataStore state so this test is isolated from earlier tests.
    // The preference store is application-scoped and can survive between Robolectric tests.
    repo.setAutoUpdateConsent("uid_man", false)
    assertFalse(syncPreferences.syncSettings("uid_man").first().autoUpdateConsentEnabled)

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
      )
    assertTrue("Direct quote application must also enforce global opt-in", overwrittenWhenDisabled.isFailure)
    assertEquals("95.5", repo.getHoldingById("uid_man", mfHolding.id)?.currentMarketPriceDecimal)

    // Now user explicitly opts in globally AND per holding -> sync succeeds cleanly!
    repo.setAutoUpdateConsent("uid_man", true)
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
  fun `buy cost basis is immutable to metadata edits and older or failed quotes preserve last valuation`() = runTest {
    val repo =
      buildRepo(
        object : MarketPriceDataSource {
          override suspend fun fetchLatestQuote(request: MarketQuoteRequest): MarketQuoteFetchResult =
            MarketQuoteFetchResult.NetworkError("offline")
        }
      )

    val created =
      repo.createHoldingWithInitialBuy(
        authenticatedUserId = "uid_quote_guard",
        name = "Index Fund",
        category = InvestmentCategory.MUTUAL_FUNDS,
        symbolOrTicker = "119598",
        isin = null,
        brokerOrPlatform = "Broker",
        currencyCode = "INR",
        purchaseDate = 1_750_000_000_000L,
        quantityDecimal = "10",
        purchasePriceDecimal = "100.00",
        chargesMinor = 25L,
        currentMarketPriceDecimal = "105.00",
        autoPriceUpdateEnabled = false,
        notes = null,
      ).getOrThrow()

    val originalBuy = repo.getTransactionsForHolding("uid_quote_guard", created.id).first().single()
    val originalCostBasis = created.totalInvestedMinor

    assertTrue(
      "Metadata editing must not rewrite the recorded purchase date",
      repo.updateHoldingMetadata(
        "uid_quote_guard",
        created.copy(purchaseDate = created.purchaseDate + 86_400_000L),
      ).isFailure,
    )
    assertTrue(
      "Metadata editing must not change the holding currency",
      repo.updateHoldingMetadata(
        "uid_quote_guard",
        created.copy(currencyCode = "USD"),
      ).isFailure,
    )
    assertTrue(
      "Metadata editing must not change the instrument category",
      repo.updateHoldingMetadata(
        "uid_quote_guard",
        created.copy(category = InvestmentCategory.STOCKS),
      ).isFailure,
    )
    assertTrue(
      "Metadata editing must not change the AMFI scheme code after purchase",
      repo.updateHoldingMetadata(
        "uid_quote_guard",
        created.copy(symbolOrTicker = "119599"),
      ).isFailure,
    )
    assertTrue(
      "Metadata editing must not change the ISIN after purchase",
      repo.updateHoldingMetadata(
        "uid_quote_guard",
        created.copy(isin = "INE123456789"),
      ).isFailure,
    )

    repo.setAutoUpdateConsent("uid_quote_guard", true)
    repo.setHoldingAutoPriceUpdate("uid_quote_guard", created.id, true).getOrThrow()
    val currentQuoteDate = System.currentTimeMillis() - 2L * 24L * 60L * 60L * 1000L
    repo.updateManualMarketPrice(
      authenticatedUserId = "uid_quote_guard",
      holdingId = created.id,
      newUnitPriceDecimal = "110.00",
      priceDate = currentQuoteDate,
    ).getOrThrow()
    val rejectedOlderQuote =
      repo.applyAutomaticMarketPrice(
        authenticatedUserId = "uid_quote_guard",
        holdingId = created.id,
        newUnitPriceDecimal = "90.00",
        priceDate = currentQuoteDate - 86_400_000L,
        source = PriceSourceType.MFAPI_AMFI_EOD,
        providerLabel = AmfiMfApiMarketPriceDataSource.PROVIDER_LABEL,
        allowOverwriteManual = false,
      ).getOrThrow()
    assertFalse("Older quote must not roll back the current valuation", rejectedOlderQuote)

    val futureQuote =
      repo.applyAutomaticMarketPrice(
        authenticatedUserId = "uid_quote_guard",
        holdingId = created.id,
        newUnitPriceDecimal = "999.00",
        priceDate = System.currentTimeMillis() + 86_400_000L,
        source = PriceSourceType.MFAPI_AMFI_EOD,
        providerLabel = AmfiMfApiMarketPriceDataSource.PROVIDER_LABEL,
        allowOverwriteManual = false,
      )
    assertTrue("Future-dated provider quote must be rejected", futureQuote.isFailure)

    val beforeOfflineSync = repo.getHoldingById("uid_quote_guard", created.id)!!
    val sync = repo.syncEligibleMarketPrices("uid_quote_guard", forceBypassCooldown = true).getOrThrow()
    val afterOfflineSync = repo.getHoldingById("uid_quote_guard", created.id)!!

    assertEquals(1, sync.errorCount)
    assertEquals(beforeOfflineSync.currentMarketPriceDecimal, afterOfflineSync.currentMarketPriceDecimal)
    assertEquals(beforeOfflineSync.currentValuationMinor, afterOfflineSync.currentValuationMinor)
    assertEquals(originalCostBasis, afterOfflineSync.totalInvestedMinor)
    val buyAfterSync = repo.getTransactionsForHolding("uid_quote_guard", created.id).first().single()
    assertEquals(originalBuy.unitPriceDecimal, buyAfterSync.unitPriceDecimal)
    assertEquals(originalBuy.transactionDate, buyAfterSync.transactionDate)
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
    val invalidDateJson =
      """
      {
        "meta": {"scheme_name": "Parag Parikh Flexi Cap Fund"},
        "data": [{"date": "31-02-2026", "nav": "84.5678"}],
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
          "INVALID_DATE" ->
            Response.Builder()
              .request(req)
              .protocol(Protocol.HTTP_1_1)
              .code(200)
              .message("OK")
              .body(invalidDateJson.toResponseBody("application/json".toMediaType()))
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
    val wrongCurrencyReq = MarketQuoteRequest("h_mf_usd", InvestmentCategory.MUTUAL_FUNDS, "122639", null, "USD")
    assertTrue(
      "An INR NAV must never be applied to a non-INR holding",
      provider.fetchLatestQuote(wrongCurrencyReq) is MarketQuoteFetchResult.UnsupportedCategoryOrIdentifier,
    )

    mode = "SUCCESS"
    val success = provider.fetchLatestQuote(validReq) as MarketQuoteFetchResult.Success
    assertEquals("84.5678", success.unitPriceDecimal)
    assertEquals(PriceSourceType.MFAPI_AMFI_EOD, success.source)
    assertEquals("Parag Parikh Flexi Cap Fund", success.instrumentName)
    assertTrue("AMFI EOD quote timestamp must not be future-dated", success.asOfMillis <= System.currentTimeMillis())

    mode = "INVALID_DATE"
    assertTrue(
      "An invalid NAV date must not be stamped with the current time",
      provider.fetchLatestQuote(validReq) is MarketQuoteFetchResult.InvalidResponse,
    )

    mode = "RATE_LIMIT"
    val rateLimited = provider.fetchLatestQuote(validReq) as MarketQuoteFetchResult.RateLimited
    assertEquals(120_000L, rateLimited.retryAfterMillis)

    mode = "HTTP_500"
    assertTrue(provider.fetchLatestQuote(validReq) is MarketQuoteFetchResult.NetworkError)

    mode = "MALFORMED"
    assertTrue(provider.fetchLatestQuote(validReq) is MarketQuoteFetchResult.InvalidResponse)

    mode = "OFFLINE"
    assertTrue(provider.fetchLatestQuote(validReq) is MarketQuoteFetchResult.NetworkError)

    // Provider payload validation: never accept zero/negative NAV, impossible/future
    // dates, or a SUCCESS response with no NAV observations.
    val zeroNav = provider.parseMfApiResponse(
      """{"status":"SUCCESS","data":[{"date":"18-11-2026","nav":"0"}]}"""
    )
    assertTrue(zeroNav is MarketQuoteFetchResult.InvalidResponse)

    val negativeNav = provider.parseMfApiResponse(
      """{"status":"SUCCESS","data":[{"date":"18-11-2026","nav":"-1"}]}"""
    )
    assertTrue(negativeNav is MarketQuoteFetchResult.InvalidResponse)

    val impossibleDate = provider.parseMfApiResponse(
      """{"status":"SUCCESS","data":[{"date":"31-02-2026","nav":"100"}]}"""
    )
    assertTrue(impossibleDate is MarketQuoteFetchResult.InvalidResponse)

    val futureDate = provider.parseMfApiResponse(
      """{"status":"SUCCESS","data":[{"date":"31-12-9999","nav":"100"}]}"""
    )
    assertTrue(futureDate is MarketQuoteFetchResult.InvalidResponse)

    val emptyData = provider.parseMfApiResponse(
      """{"status":"SUCCESS","data":[]}"""
    )
    assertTrue(emptyData is MarketQuoteFetchResult.InvalidResponse)

    val exponentNav = provider.parseMfApiResponse(
      """{"status":"SUCCESS","data":[{"date":"18-11-2026","nav":"1e999999999"}]}"""
    )
    assertTrue(exponentNav is MarketQuoteFetchResult.InvalidResponse)

    val excessivePrecisionNav = provider.parseMfApiResponse(
      """{"status":"SUCCESS","data":[{"date":"18-11-2026","nav":"1234567890123.12345678901"}]}"""
    )
    assertTrue(excessivePrecisionNav is MarketQuoteFetchResult.InvalidResponse)
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
