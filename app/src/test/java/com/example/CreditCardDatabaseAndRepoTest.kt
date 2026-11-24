package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.repository.CreditCardRepositoryImpl
import com.example.data.repository.FinancialAccountRepositoryImpl
import com.example.domain.model.AccountType
import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardNetwork
import com.example.domain.model.DuplicateCreditCardException
import com.example.domain.model.FinancialAccount
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/**
 * Credit card persistence: association with the ledger account, validation, UID isolation, and the
 * guarantee that removing a card never removes the account or its transactions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CreditCardDatabaseAndRepoTest {

  private lateinit var context: Context
  private lateinit var database: AppDatabase
  private lateinit var cardRepository: CreditCardRepositoryImpl
  private lateinit var accountRepository: FinancialAccountRepositoryImpl

  private val userA = "user_cards_a"
  private val userB = "user_cards_b"

  private val dispatchers =
    object : DispatcherProvider {
      private val d = UnconfinedTestDispatcher()
      override val main: CoroutineDispatcher = d
      override val io: CoroutineDispatcher = d
      override val default: CoroutineDispatcher = d
    }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    cardRepository =
      CreditCardRepositoryImpl(
        database = database,
        creditCardDao = database.creditCardDao(),
        statementDao = database.creditCardStatementDao(),
        rewardEventDao = database.creditCardRewardEventDao(),
        accountDao = database.financialAccountDao(),
        dispatchers = dispatchers,
      )
    accountRepository =
      FinancialAccountRepositoryImpl(
        database = database,
        accountDao = database.financialAccountDao(),
        transactionDao = database.financialTransactionDao(),
        mappingRuleDao = database.accountMappingRuleDao(),
        dispatchers = dispatchers,
      )
  }

  @After
  fun tearDown() {
    database.close()
  }

  private suspend fun createAccount(
    userId: String,
    id: String,
    type: AccountType = AccountType.CREDIT_CARD,
    currency: String = "INR",
    balanceMinor: Long = 0L,
  ): FinancialAccount {
    val account =
      FinancialAccount(
        id = id,
        userId = userId,
        name = "Card $id",
        type = type,
        currencyCode = currency,
        initialBalanceMinor = balanceMinor,
        currentBalanceMinor = balanceMinor,
        institutionName = "HDFC Bank",
        accountNumberLast4 = "1234",
      )
    return accountRepository.createAccount(userId, account).getOrThrow()
  }

  private fun card(
    userId: String,
    accountId: String,
    currency: String = "INR",
    limitMinor: Long = 100_000L,
  ): CreditCard =
    CreditCard(
      userId = userId,
      accountId = accountId,
      issuerName = "HDFC Bank",
      network = CreditCardNetwork.VISA,
      last4 = "1234",
      creditLimitMinor = limitMinor,
      currencyCode = currency,
    )

  private suspend fun insertTransaction(accountId: String, transactionId: String) {
    val transaction =
      com.example.domain.model.FinancialTransaction(
        id = transactionId,
        userId = userA,
        accountId = accountId,
        type = com.example.domain.model.TransactionType.EXPENSE,
        amountMinor = 10_000L,
        currencyCode = "INR",
        category = "Shopping",
      )
    database
      .financialTransactionDao()
      .insertTransaction(
        com.example.data.local.db.entity.FinancialTransactionEntity.fromDomain(transaction)
      )
  }

  @Test
  fun `a card is created and associated with its credit card account`() = runTest {
    val account = createAccount(userA, "acc_card_1")

    val created = cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    assertEquals(account.id, created.accountId)
    assertEquals(userA, created.userId)

    val stored = cardRepository.getCardForAccount(userA, account.id)
    assertNotNull(stored)
    assertEquals(created.id, stored?.id)
    assertEquals(1, cardRepository.observeCards(userA).first().size)
  }

  @Test
  fun `inactive card status is persisted and preserved on reload`() = runTest {
    val account = createAccount(userA, "acc_card_inactive")
    val created = cardRepository.createCard(userA, card(userA, account.id).copy(isActive = false)).getOrThrow()

    assertFalse(created.isActive)
    assertFalse(cardRepository.getCard(userA, created.id)!!.isActive)
  }

  @Test
  fun `a second card for the same account is rejected`() = runTest {
    val account = createAccount(userA, "acc_card_2")
    cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    val second = cardRepository.createCard(userA, card(userA, account.id))

    assertTrue(second.isFailure)
    assertEquals(1, cardRepository.observeCards(userA).first().size)
  }

  @Test
  fun `a card cannot be attached to a non credit card account`() = runTest {
    val bank = createAccount(userA, "acc_bank_1", type = AccountType.BANK)

    val result = cardRepository.createCard(userA, card(userA, bank.id))

    assertTrue(result.isFailure)
    assertTrue(cardRepository.observeCards(userA).first().isEmpty())
  }

  @Test
  fun `a card currency must match its account currency`() = runTest {
    val account = createAccount(userA, "acc_card_3", currency = "INR")

    val result = cardRepository.createCard(userA, card(userA, account.id, currency = "USD"))

    assertTrue(result.isFailure)
  }

  @Test
  fun `another user can never create or read a card`() = runTest {
    val account = createAccount(userA, "acc_card_4")
    cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    // Ownership mismatch on the record itself.
    assertTrue(cardRepository.createCard(userB, card(userA, account.id)).isFailure)
    // Nothing is visible under the other UID.
    assertTrue(cardRepository.observeCards(userB).first().isEmpty())
    assertEquals(1, cardRepository.observeCards(userA).first().size)
  }

  @Test
  fun `only an accountId uniqueness violation becomes a duplicate card error`() = runTest {
    val first = createAccount(userA, "acc_card_x1")
    val second = createAccount(userA, "acc_card_x2")
    val third = createAccount(userA, "acc_card_x3")
    val existing = cardRepository.createCard(userA, card(userA, first.id)).getOrThrow()
    cardRepository.createCard(userA, card(userA, second.id)).getOrThrow()

    // Same primary key, but a third account that has no card: the only violated constraint is the
    // id, so this must NOT be translated into "this account already has a credit card".
    val duplicateId = card(userA, third.id).copy(id = existing.id)
    val result = cardRepository.createCard(userA, duplicateId)

    assertTrue(result.isFailure)
    val error = result.exceptionOrNull()
    assertFalse(error is DuplicateCreditCardException)
    assertEquals(2, cardRepository.observeCards(userA).first().size)
  }

  @Test
  fun `updating a card never moves it to another account`() = runTest {
    val accountA = createAccount(userA, "acc_card_m1")
    val accountB = createAccount(userA, "acc_card_m2")
    val created = cardRepository.createCard(userA, card(userA, accountA.id)).getOrThrow()
    cardRepository.createCard(userA, card(userA, accountB.id)).getOrThrow()

    // accountId is immutable by design, so an attempt to re-point the card cannot collide with
    // the other account's card and cannot silently steal it.
    val moveAttempt =
      cardRepository.updateCard(userA, created.copy(accountId = accountB.id, creditLimitMinor = 500_000L))

    assertTrue(moveAttempt.isFailure)
    val reloaded = cardRepository.getCard(userA, created.id)
    assertEquals(accountA.id, reloaded?.accountId)
    assertEquals(100_000L, reloaded?.creditLimitMinor)
    assertEquals(2, cardRepository.observeCards(userA).first().size)
  }

  @Test
  fun `updating a card that does not exist is refused`() = runTest {
    val account = createAccount(userA, "acc_card_m3")
    val created = cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    val result = cardRepository.updateCard(userA, created.copy(id = "missing-card-id"))

    assertTrue(result.isFailure)
  }

  @Test
  fun `deleting a card keeps the account transactions`() = runTest {
    val account = createAccount(userA, "acc_card_t1", balanceMinor = -10_000L)
    val created = cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    val transaction =
      com.example.domain.model.FinancialTransaction(
        id = "txn_on_card",
        userId = userA,
        accountId = account.id,
        type = com.example.domain.model.TransactionType.EXPENSE,
        amountMinor = 10_000L,
        currencyCode = "INR",
        category = "Shopping",
      )
    database
      .financialTransactionDao()
      .insertTransaction(
        com.example.data.local.db.entity.FinancialTransactionEntity.fromDomain(transaction)
      )

    cardRepository.deleteCard(userA, created.id).getOrThrow()

    val surviving =
      database.financialTransactionDao().getTransactionById(userA, "txn_on_card")
    assertNotNull(surviving)
    assertNotNull(accountRepository.getAccountById(userA, account.id))
  }

  @Test
  fun `a card can be recreated after deletion`() = runTest {
    val account = createAccount(userA, "acc_card_r1")
    val first = cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()
    cardRepository.deleteCard(userA, first.id).getOrThrow()

    val second = cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    assertEquals(account.id, second.accountId)
    assertEquals(1, cardRepository.observeCards(userA).first().size)
  }

  @Test
  fun `statement history is scoped to the owning user and card`() = runTest {
    val account = createAccount(userA, "acc_card_statement")
    val created = cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()
    val statement = com.example.domain.model.CreditCardStatement(
      userId = userA,
      cardId = created.id,
      currencyCode = "INR",
      statementDate = 1_700_000_000_000L,
      dueDate = 1_700_100_000_000L,
      statementBalanceMinor = 50_000L,
      minimumDueMinor = 5_000L,
    )

    cardRepository.createStatement(userA, statement).getOrThrow()

    assertEquals(1, cardRepository.observeStatements(userA, created.id).first().size)
    assertTrue(cardRepository.observeStatements(userB, created.id).first().isEmpty())
    assertTrue(cardRepository.createStatement(userB, statement.copy(id = "foreign-statement")).isFailure)
  }

  @Test
  fun `reward points are immutable ledger events and cannot be over-redeemed`() = runTest {
    val account = createAccount(userA, "acc_card_rewards")
    val created = cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()
    val earn = com.example.domain.model.CreditCardRewardEvent(
      userId = userA,
      cardId = created.id,
      eventDate = 1_700_000_000_000L,
      eventType = com.example.domain.model.CreditCardRewardEventType.EARN,
      pointsDelta = 1_000L,
      currencyCode = "INR",
      description = "Welcome points",
    )
    cardRepository.addRewardEvent(userA, earn).getOrThrow()

    assertEquals(1_000L, cardRepository.getRewardPointsBalance(userA, created.id))
    val overRedeem = earn.copy(
      id = "over-redeem",
      eventType = com.example.domain.model.CreditCardRewardEventType.REDEEM,
      pointsDelta = -1_001L,
      description = "Too many points",
    )
    assertTrue(cardRepository.addRewardEvent(userA, overRedeem).isFailure)
    assertEquals(1_000L, cardRepository.getRewardPointsBalance(userA, created.id))
    assertEquals(1, cardRepository.observeRewardEvents(userA, created.id).first().size)
  }

  @Test
  fun `freeze status updates without changing ledger account or card balance`() = runTest {
    val account = createAccount(userA, "acc_card_freeze", balanceMinor = -25_000L)
    val created = cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    cardRepository.setCardFrozen(userA, created.id, true).getOrThrow()

    assertTrue(cardRepository.getCard(userA, created.id)!!.isFrozen)
    assertEquals(-25_000L, accountRepository.getAccountById(userA, account.id)!!.currentBalanceMinor)
  }

  @Test
  fun `a blank user id cannot create update or delete`() = runTest {
    val account = createAccount(userA, "acc_card_u1")
    val created = cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    assertTrue(cardRepository.createCard("", card(userA, account.id)).isFailure)
    assertTrue(cardRepository.updateCard("", created).isFailure)
    assertTrue(cardRepository.deleteCard("", created.id).isFailure)
    assertEquals(1, cardRepository.observeCards(userA).first().size)
  }

  @Test
  fun `a card cannot be attached to another user's account`() = runTest {
    val foreignAccount = createAccount(userB, "acc_card_foreign")

    // The card claims userA, but the account belongs to userB: the account lookup is UID-scoped,
    // so it is invisible and the create is refused.
    val result = cardRepository.createCard(userA, card(userA, foreignAccount.id))

    assertTrue(result.isFailure)
    assertTrue(cardRepository.observeCards(userA).first().isEmpty())
    assertTrue(cardRepository.observeCards(userB).first().isEmpty())
  }

  @Test
  fun `a non numeric last four is rejected before it reaches the database`() = runTest {
    val account = createAccount(userA, "acc_card_v1")

    val result = cardRepository.createCard(userA, card(userA, account.id).copy(last4 = "12ab"))

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("empty or exactly four digits"))
    assertTrue(cardRepository.observeCards(userA).first().isEmpty())
  }

  @Test
  fun `an invalid APR is rejected before it reaches the database`() = runTest {
    val account = createAccount(userA, "acc_card_v2")

    val result = cardRepository.createCard(userA, card(userA, account.id).copy(aprPercent = "42%"))

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("decimal number"))
    assertTrue(cardRepository.observeCards(userA).first().isEmpty())
  }

  @Test
  fun `update rejects an invalid APR too`() = runTest {
    val account = createAccount(userA, "acc_card_v3")
    val created = cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    val aprBefore = cardRepository.getCard(userA, created.id)?.aprPercent

    val result = cardRepository.updateCard(userA, created.copy(aprPercent = "abc"))

    assertTrue(result.isFailure)
    // A rejected update must leave the stored card untouched.
    assertEquals(aprBefore, cardRepository.getCard(userA, created.id)?.aprPercent)
  }

  @Test
  fun `the type of a card-linked account cannot be changed`() = runTest {
    val account = createAccount(userA, "acc_card_type")
    cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    val result = accountRepository.updateAccount(userA, account.copy(type = AccountType.BANK))

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Cannot change the type"))
    assertEquals(
      AccountType.CREDIT_CARD,
      accountRepository.getAccountById(userA, account.id)?.type,
    )
  }

  @Test
  fun `the currency of a card-linked account cannot be changed`() = runTest {
    val account = createAccount(userA, "acc_card_cur")
    cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    val result = accountRepository.updateAccount(userA, account.copy(currencyCode = "USD"))

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Cannot change the currency"))
    assertEquals("INR", accountRepository.getAccountById(userA, account.id)?.currencyCode)
  }

  @Test
  fun `other fields of a card-linked account can still be edited`() = runTest {
    val account = createAccount(userA, "acc_card_name")
    cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    accountRepository.updateAccount(userA, account.copy(name = "Renamed card")).getOrThrow()

    assertEquals("Renamed card", accountRepository.getAccountById(userA, account.id)?.name)
    assertEquals(1, cardRepository.observeCards(userA).first().size)
  }

  @Test
  fun `an account without a card can still change its type`() = runTest {
    val bank = createAccount(userA, "acc_bank_free", type = AccountType.BANK)

    accountRepository.updateAccount(userA, bank.copy(type = AccountType.SAVINGS)).getOrThrow()

    assertEquals(AccountType.SAVINGS, accountRepository.getAccountById(userA, bank.id)?.type)
  }

  @Test
  fun `deleting an account removes its card record`() = runTest {
    val account = createAccount(userA, "acc_card_del")
    cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    accountRepository.deleteAccount(userA, account.id, deleteLinkedTransactions = false).getOrThrow()

    assertNull(accountRepository.getAccountById(userA, account.id))
    assertTrue(cardRepository.observeCards(userA).first().isEmpty())
  }

  @Test
  fun `deleting an account with transactions still requires confirmation and keeps the card`() = runTest {
    val account = createAccount(userA, "acc_card_guard", balanceMinor = -10_000L)
    cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()
    insertTransaction(account.id, "txn_guard")

    val refused = accountRepository.deleteAccount(userA, account.id, deleteLinkedTransactions = false)

    assertTrue(refused.isFailure)
    assertNotNull(accountRepository.getAccountById(userA, account.id))
    assertEquals(1, cardRepository.observeCards(userA).first().size)
    assertNotNull(database.financialTransactionDao().getTransactionById(userA, "txn_guard"))
  }

  @Test
  fun `a confirmed account deletion removes the card and its transactions together`() = runTest {
    val account = createAccount(userA, "acc_card_full", balanceMinor = -10_000L)
    cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()
    insertTransaction(account.id, "txn_full")

    accountRepository.deleteAccount(userA, account.id, deleteLinkedTransactions = true).getOrThrow()

    assertNull(accountRepository.getAccountById(userA, account.id))
    assertTrue(cardRepository.observeCards(userA).first().isEmpty())
    assertNull(database.financialTransactionDao().getTransactionById(userA, "txn_full"))
  }

  @Test
  fun `a duplicate creation returns a controlled conflict, not a raw database error`() = runTest {
    val account = createAccount(userA, "acc_card_9")
    cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    val second = cardRepository.createCard(userA, card(userA, account.id))

    assertTrue(second.isFailure)
    val error = second.exceptionOrNull()
    // The in-transaction pre-check now raises the controlled type itself, rather than a generic
    // check() failure.
    assertTrue(error is DuplicateCreditCardException)
    assertTrue(error?.message.orEmpty().contains("already has a credit card"))
    assertEquals(1, cardRepository.observeCards(userA).first().size)
  }

  @Test
  fun `two attempts at the same account leave exactly one card`() = runTest {
    val account = createAccount(userA, "acc_card_race")

    val results =
      listOf(
        async { cardRepository.createCard(userA, card(userA, account.id)) },
        async { cardRepository.createCard(userA, card(userA, account.id)) },
      ).awaitAll()

    assertEquals(1, results.count { it.isSuccess })
    assertEquals(1, cardRepository.observeCards(userA).first().size)
  }

  @Test
  fun `different accounts can each have their own card`() = runTest {
    val first = createAccount(userA, "acc_card_a")
    val second = createAccount(userA, "acc_card_b")

    cardRepository.createCard(userA, card(userA, first.id)).getOrThrow()
    cardRepository.createCard(userA, card(userA, second.id)).getOrThrow()

    val cards = cardRepository.observeCards(userA).first()
    assertEquals(2, cards.size)
    assertEquals(setOf(first.id, second.id), cards.map { it.accountId }.toSet())
  }

  @Test
  fun `net worth is identical with and without a card record`() = runTest {
    val account = createAccount(userA, "acc_card_nw", balanceMinor = -35_000L)
    val engine =
      com.example.domain.usecase.ReportsCalculationEngine(
        budgetEngine = com.example.domain.usecase.BudgetCalculationEngine(),
        emiEngine = com.example.domain.usecase.EmiCalculationEngine(),
        investmentEngine = com.example.domain.usecase.InvestmentCalculationEngine(),
        shoppingEngine = com.example.domain.usecase.ShoppingCalculationEngine(),
      )

    val withoutCard =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts = accountRepository.getAccounts(userA).first(),
        holdings = emptyList(),
        loans = emptyList(),
      )
    assertEquals(0L, withoutCard.totalAssetsMinor)
    assertEquals(35_000L, withoutCard.accountOrCardLiabilitiesMinor)

    cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    val withCard =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts = accountRepository.getAccounts(userA).first(),
        holdings = emptyList(),
        loans = emptyList(),
      )
    assertEquals(withoutCard.accountOrCardLiabilitiesMinor, withCard.accountOrCardLiabilitiesMinor)
    assertEquals(withoutCard.totalAssetsMinor, withCard.totalAssetsMinor)
    assertEquals(withoutCard.netWorthMinor, withCard.netWorthMinor)
  }

  @Test
  fun `deleting a card leaves the account and its balance untouched`() = runTest {
    val account = createAccount(userA, "acc_card_5", balanceMinor = -25_000L)
    val created = cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    cardRepository.deleteCard(userA, created.id).getOrThrow()

    assertTrue(cardRepository.observeCards(userA).first().isEmpty())
    val stillThere = accountRepository.getAccountById(userA, account.id)
    assertNotNull(stillThere)
    assertEquals(-25_000L, stillThere?.currentBalanceMinor)
  }

  @Test
  fun `deleting another user's card is refused`() = runTest {
    val account = createAccount(userA, "acc_card_6")
    val created = cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    val result = cardRepository.deleteCard(userB, created.id)

    assertTrue(result.isFailure)
    assertEquals(1, cardRepository.observeCards(userA).first().size)
  }

  @Test
  fun `a card update is scoped to its owner`() = runTest {
    val account = createAccount(userA, "acc_card_7")
    val created = cardRepository.createCard(userA, card(userA, account.id)).getOrThrow()

    assertTrue(cardRepository.updateCard(userB, created.copy(creditLimitMinor = 1L)).isFailure)

    cardRepository.updateCard(userA, created.copy(creditLimitMinor = 250_000L)).getOrThrow()
    assertEquals(250_000L, cardRepository.getCard(userA, created.id)?.creditLimitMinor)
  }

  @Test
  fun `a fresh install can store and read a card with no migration involved`() = runTest {
    val account = createAccount(userA, "acc_card_8", currency = "USD", balanceMinor = -12_34L)
    val created = cardRepository.createCard(userA, card(userA, account.id, currency = "USD")).getOrThrow()

    val stored = cardRepository.getCard(userA, created.id)
    assertNotNull(stored)
    assertEquals("USD", stored?.currencyCode)
    assertFalse(stored?.id.isNullOrBlank())
  }
}
