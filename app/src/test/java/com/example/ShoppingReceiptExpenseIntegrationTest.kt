package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.entity.FinancialAccountEntity
import com.example.data.repository.ShoppingExtendedRepositoryImpl
import com.example.domain.model.ShoppingReceipt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShoppingReceiptExpenseIntegrationTest {
  private lateinit var db: AppDatabase
  private lateinit var repository: ShoppingExtendedRepositoryImpl
  private val dispatchers = object : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
  }

  @Before fun setUp() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = ShoppingExtendedRepositoryImpl(
      database = db,
      shoppingDao = db.shoppingExtendedDao(),
      accountDao = db.financialAccountDao(),
      transactionDao = db.financialTransactionDao(),
      dispatchers = dispatchers,
    )
  }

  @After fun tearDown() { db.close() }

  @Test fun confirmedReceiptPostsExactlyOneExpenseAndAdjustsOnlySelectedAccount() = runTest {
    db.financialAccountDao().insertAccount(
      FinancialAccountEntity(
        id = "account-1", userId = "user-1", name = "Cash", accountType = "CASH",
        currencyCode = "KWD", initialBalanceMinor = 10_000L, currentBalanceMinor = 10_000L,
        institutionName = null, accountNumberLast4 = null, colorHex = "#123456",
        isActive = true, createdAt = 1L, updatedAt = 1L,
      )
    )
    val receipt = repository.saveReceiptReview(
      "user-1",
      ShoppingReceipt(
        userId = "user-1", fingerprint = "a".repeat(64), merchant = "Market",
        currencyCode = "KWD", totalMinor = 1_250L,
      ),
    ).getOrThrow()

    val posted = repository.postReceiptExpense("user-1", receipt.id, "account-1").getOrThrow()
    assertEquals("POSTED", posted.status)
    assertTrue(posted.financialTransactionId != null)
    assertEquals(8_750L, db.financialAccountDao().getAccountById("user-1", "account-1")!!.currentBalanceMinor)

    val postedAgain = repository.postReceiptExpense("user-1", receipt.id, "account-1").getOrThrow()
    assertEquals(posted.financialTransactionId, postedAgain.financialTransactionId)
    assertEquals(8_750L, db.financialAccountDao().getAccountById("user-1", "account-1")!!.currentBalanceMinor)
    val transactions = db.financialTransactionDao().getAllTransactions("user-1").first()
    assertEquals(1, transactions.size)
    assertEquals("EXPENSE", transactions.single().type)
    assertEquals(1_250L, transactions.single().amountMinor)
  }

  @Test fun crossUserCannotPostAnotherUsersReceipt() = runTest {
    val receipt = repository.saveReceiptReview(
      "owner",
      ShoppingReceipt(userId = "owner", fingerprint = "b".repeat(64), currencyCode = "USD", totalMinor = 500L),
    ).getOrThrow()
    val result = repository.postReceiptExpense("attacker", receipt.id, "missing-account")
    assertTrue(result.isFailure)
  }
}
