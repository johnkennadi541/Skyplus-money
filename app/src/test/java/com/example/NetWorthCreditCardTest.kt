package com.example

import com.example.domain.model.AccountType
import com.example.domain.model.FinancialAccount
import com.example.domain.usecase.BudgetCalculationEngine
import com.example.domain.usecase.EmiCalculationEngine
import com.example.domain.usecase.InvestmentCalculationEngine
import com.example.domain.usecase.ReportsCalculationEngine
import com.example.domain.usecase.ShoppingCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A credit card is a liability whatever the sign of its balance, and it can never appear in the
 * asset side of Net Worth.
 */
class NetWorthCreditCardTest {

  private lateinit var engine: ReportsCalculationEngine

  private val uid = "uid_net_worth"

  @Before
  fun setUp() {
    engine =
      ReportsCalculationEngine(
        budgetEngine = BudgetCalculationEngine(),
        emiEngine = EmiCalculationEngine(),
        investmentEngine = InvestmentCalculationEngine(),
        shoppingEngine = ShoppingCalculationEngine(),
      )
  }

  private fun account(
    id: String,
    type: AccountType,
    balanceMinor: Long,
    currency: String = "INR",
    isActive: Boolean = true,
  ): FinancialAccount =
    FinancialAccount(
      id = id,
      userId = uid,
      name = "Account $id",
      type = type,
      currencyCode = currency,
      initialBalanceMinor = 0L,
      currentBalanceMinor = balanceMinor,
      isActive = isActive,
    )

  /**
   * Every case below passes ONLY accounts to computeNetWorth: no card list, no card record, no
   * join. Classification is by account type, so a card row can never be required for correctness.
   */
  @Test
  fun `a credit card account with no card record and a positive balance is a liability`() {
    val breakdown =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts = listOf(account("card_no_record", AccountType.CREDIT_CARD, 42_000L)),
        holdings = emptyList(),
        loans = emptyList(),
      )

    assertEquals(0L, breakdown.cashAndBankAssetsMinor)
    assertEquals(0L, breakdown.totalAssetsMinor)
    assertEquals(42_000L, breakdown.accountOrCardLiabilitiesMinor)
    assertEquals(-42_000L, breakdown.netWorthMinor)
  }

  @Test
  fun `a credit card account with no card record and a negative balance is a liability`() {
    val breakdown =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts = listOf(account("card_no_record", AccountType.CREDIT_CARD, -42_000L)),
        holdings = emptyList(),
        loans = emptyList(),
      )

    assertEquals(0L, breakdown.totalAssetsMinor)
    assertEquals(42_000L, breakdown.accountOrCardLiabilitiesMinor)
    assertEquals(-42_000L, breakdown.netWorthMinor)
  }

  @Test
  fun `a credit card account with no card record and a zero balance adds nothing`() {
    val breakdown =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts = listOf(account("card_no_record", AccountType.CREDIT_CARD, 0L)),
        holdings = emptyList(),
        loans = emptyList(),
      )

    assertEquals(0L, breakdown.cashAndBankAssetsMinor)
    assertEquals(0L, breakdown.accountOrCardLiabilitiesMinor)
    assertEquals(0L, breakdown.netWorthMinor)
    assertEquals(1, breakdown.activeAccountCount)
  }

  @Test
  fun `a normal bank account keeps its existing behaviour`() {
    val positive =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts = listOf(account("bank", AccountType.BANK, 10_000L)),
        holdings = emptyList(),
        loans = emptyList(),
      )
    assertEquals(10_000L, positive.cashAndBankAssetsMinor)
    assertEquals(0L, positive.accountOrCardLiabilitiesMinor)

    val negative =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts = listOf(account("bank", AccountType.BANK, -4_000L)),
        holdings = emptyList(),
        loans = emptyList(),
      )
    assertEquals(0L, negative.cashAndBankAssetsMinor)
    assertEquals(4_000L, negative.accountOrCardLiabilitiesMinor)
  }

  @Test
  fun `a negative card balance is a liability and not an asset`() {
    val breakdown =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts = listOf(account("card", AccountType.CREDIT_CARD, -50_000L)),
        holdings = emptyList(),
        loans = emptyList(),
      )

    assertEquals(0L, breakdown.cashAndBankAssetsMinor)
    assertEquals(0L, breakdown.totalAssetsMinor)
    assertEquals(50_000L, breakdown.accountOrCardLiabilitiesMinor)
    assertEquals(50_000L, breakdown.totalLiabilitiesMinor)
    assertEquals(-50_000L, breakdown.netWorthMinor)
  }

  @Test
  fun `a positive card balance is still a liability, never an asset`() {
    // Some users record what they owe as a positive figure; that must not inflate assets.
    val breakdown =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts = listOf(account("card", AccountType.CREDIT_CARD, 50_000L)),
        holdings = emptyList(),
        loans = emptyList(),
      )

    assertEquals(0L, breakdown.cashAndBankAssetsMinor)
    assertEquals(0L, breakdown.totalAssetsMinor)
    assertEquals(50_000L, breakdown.accountOrCardLiabilitiesMinor)
    assertEquals(-50_000L, breakdown.netWorthMinor)
  }

  @Test
  fun `a zero card balance adds nothing on either side`() {
    val breakdown =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts = listOf(account("card", AccountType.CREDIT_CARD, 0L)),
        holdings = emptyList(),
        loans = emptyList(),
      )

    assertEquals(0L, breakdown.cashAndBankAssetsMinor)
    assertEquals(0L, breakdown.accountOrCardLiabilitiesMinor)
    assertEquals(0L, breakdown.netWorthMinor)
  }

  @Test
  fun `net worth combines bank, savings, wallet and card correctly`() {
    val breakdown =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts =
          listOf(
            account("bank", AccountType.BANK, 100_000L),
            account("savings", AccountType.SAVINGS, 40_000L),
            account("wallet", AccountType.WALLET, 5_000L),
            account("cash", AccountType.CASH, 2_000L),
            account("card", AccountType.CREDIT_CARD, -30_000L),
          ),
        holdings = emptyList(),
        loans = emptyList(),
      )

    assertEquals(147_000L, breakdown.cashAndBankAssetsMinor)
    assertEquals(147_000L, breakdown.totalAssetsMinor)
    assertEquals(30_000L, breakdown.accountOrCardLiabilitiesMinor)
    assertEquals(117_000L, breakdown.netWorthMinor)
    assertEquals(5, breakdown.activeAccountCount)
  }

  @Test
  fun `an overdrawn bank account is still a liability`() {
    val breakdown =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts = listOf(account("bank", AccountType.BANK, -7_500L)),
        holdings = emptyList(),
        loans = emptyList(),
      )

    assertEquals(0L, breakdown.cashAndBankAssetsMinor)
    assertEquals(7_500L, breakdown.accountOrCardLiabilitiesMinor)
    assertEquals(-7_500L, breakdown.netWorthMinor)
  }

  @Test
  fun `an inactive card is excluded entirely`() {
    val breakdown =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts = listOf(account("card", AccountType.CREDIT_CARD, -50_000L, isActive = false)),
        holdings = emptyList(),
        loans = emptyList(),
      )

    assertEquals(0L, breakdown.accountOrCardLiabilitiesMinor)
    assertEquals(0L, breakdown.netWorthMinor)
    assertEquals(0, breakdown.activeAccountCount)
  }

  @Test
  fun `Long MIN_VALUE never becomes a negative liability`() {
    val breakdown =
      engine.computeNetWorth(
        currencyCode = "INR",
        accounts = listOf(account("card", AccountType.CREDIT_CARD, Long.MIN_VALUE)),
        holdings = emptyList(),
        loans = emptyList(),
      )

    // abs(Long.MIN_VALUE) is still Long.MIN_VALUE, which would under-report debt enormously.
    // The magnitude saturates instead, so the figure stays a liability.
    assertEquals(Long.MAX_VALUE, breakdown.accountOrCardLiabilitiesMinor)
    assertEquals(0L, breakdown.totalAssetsMinor)
    assertEquals(-Long.MAX_VALUE, breakdown.netWorthMinor)
  }

  @Test
  fun `a total that overflows fails fast instead of reporting a wrong number`() {
    val accounts =
      listOf(
        account("card_a", AccountType.CREDIT_CARD, Long.MIN_VALUE),
        account("card_b", AccountType.CREDIT_CARD, Long.MIN_VALUE),
      )

    val failed =
      runCatching {
        engine.computeNetWorth(
          currencyCode = "INR",
          accounts = accounts,
          holdings = emptyList(),
          loans = emptyList(),
        )
      }

    assertTrue(failed.isFailure)
    assertTrue(failed.exceptionOrNull() is ArithmeticException)
  }

  @Test
  fun `currency is reported exactly as supplied`() {
    val breakdown =
      engine.computeNetWorth(
        currencyCode = "USD",
        accounts = listOf(account("card", AccountType.CREDIT_CARD, -12_34L, currency = "USD")),
        holdings = emptyList(),
        loans = emptyList(),
      )

    assertEquals("USD", breakdown.currencyCode)
    assertEquals(12_34L, breakdown.accountOrCardLiabilitiesMinor)
    assertEquals(-12_34L, breakdown.netWorthMinor)
  }
}
