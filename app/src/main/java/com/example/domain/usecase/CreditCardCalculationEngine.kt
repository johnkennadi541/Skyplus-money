package com.example.domain.usecase

import com.example.domain.model.AccountType
import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardSummary
import com.example.domain.model.FinancialAccount
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

/**
 * Credit card maths. Pure, exact, and never touched by a `Double`.
 *
 * The outstanding amount is read straight from the linked account's balance: a card purchase
 * lowers that balance, so the money owed is the absolute value of it.
 *
 * Every public function here validates its inputs and fails fast with [IllegalArgumentException]
 * rather than returning a quietly wrong number.
 */
@Singleton
class CreditCardCalculationEngine @Inject constructor() {

  /**
   * Money owed right now. Never negative.
   *
   * `abs(Long.MIN_VALUE)` returns `Long.MIN_VALUE` in Kotlin, which would report a *negative*
   * outstanding amount and under-report debt. Such a balance cannot come from real data, so it
   * saturates at [Long.MAX_VALUE] and stays a positive liability.
   */
  fun outstandingMinor(account: FinancialAccount): Long =
    if (account.currentBalanceMinor == Long.MIN_VALUE) {
      Long.MAX_VALUE
    } else {
      abs(account.currentBalanceMinor)
    }

  /**
   * Credit still available. Both inputs must be non-negative, and the subtraction is guarded so it
   * can never overflow: anything at or above the limit simply leaves zero available.
   */
  fun availableCreditMinor(creditLimitMinor: Long, outstandingMinor: Long): Long {
    require(creditLimitMinor >= 0L) { "Credit limit cannot be negative" }
    require(outstandingMinor >= 0L) { "Outstanding amount cannot be negative" }
    return if (outstandingMinor >= creditLimitMinor) 0L else creditLimitMinor - outstandingMinor
  }

  /**
   * Utilisation as an exact percentage.
   *
   * Over-limit cards are deliberately **not capped**: a card at 150% of its limit reports 150.00,
   * because that is the honest figure and the UI already flags it separately. Zero when no limit
   * has been set. Both inputs must be non-negative, so the result can never be negative.
   */
  fun utilisationPercent(creditLimitMinor: Long, outstandingMinor: Long): BigDecimal {
    require(creditLimitMinor >= 0L) { "Credit limit cannot be negative" }
    require(outstandingMinor >= 0L) { "Outstanding amount cannot be negative" }
    if (creditLimitMinor == 0L) return BigDecimal.ZERO
    return BigDecimal.valueOf(outstandingMinor)
      .multiply(HUNDRED)
      .divide(BigDecimal.valueOf(creditLimitMinor), 2, RoundingMode.HALF_EVEN)
  }

  /**
   * True when the card and the account may be combined: same owner, the card points at that
   * account, and the currencies agree. Used to reject cross-user or mismatched summaries.
   */
  fun isSameOwnerPair(card: CreditCard, account: FinancialAccount): Boolean =
    card.userId == account.userId &&
      card.accountId == account.id &&
      card.currencyCode.equals(account.currencyCode, ignoreCase = true)

  /**
   * Summarises one card against its account.
   *
   * @throws IllegalArgumentException when the card and account do not belong to the same user, the
   *   card does not point at this account, or the currencies differ. A cross-user summary is never
   *   produced silently.
   */
  fun summarise(card: CreditCard, account: FinancialAccount): CreditCardSummary {
    require(card.userId == account.userId) {
      "Card and account belong to different users"
    }
    require(card.accountId == account.id) { "Card does not belong to this account" }
    require(card.currencyCode.equals(account.currencyCode, ignoreCase = true)) {
      "Card currency must match the account currency"
    }

    val outstanding = outstandingMinor(account)
    return CreditCardSummary(
      card = card,
      account = account,
      outstandingMinor = outstanding,
      availableCreditMinor = availableCreditMinor(card.creditLimitMinor, outstanding),
      utilisationPercent = utilisationPercent(card.creditLimitMinor, outstanding),
    )
  }

  /**
   * Joins cards to their accounts, keeping only valid same-user pairs. A card whose account is
   * missing, owned by someone else, or in another currency is dropped rather than shown.
   */
  fun summariseAll(
    cards: List<CreditCard>,
    accounts: List<FinancialAccount>,
  ): List<CreditCardSummary> {
    val byId = accounts.associateBy { it.id }
    return cards
      .mapNotNull { card ->
        val account = byId[card.accountId] ?: return@mapNotNull null
        if (!isSameOwnerPair(card, account)) return@mapNotNull null
        summarise(card, account)
      }
      .sortedByDescending { it.outstandingMinor }
  }

  /**
   * Sums outstanding amounts, failing fast on overflow.
   *
   * `sumOf` wraps silently at the Long boundary and would report a wrong liability; `addExact`
   * raises [ArithmeticException] instead.
   */
  fun totalOutstandingMinor(summaries: List<CreditCardSummary>): Long =
    summaries.fold(0L) { total, summary -> Math.addExact(total, summary.outstandingMinor) }

  private companion object {
    val HUNDRED: BigDecimal = BigDecimal.valueOf(100)
  }
}

/**
 * True when this account must be treated as a liability rather than an asset.
 *
 * A credit card is a liability whatever the sign of its balance, so it can never inflate assets.
 */
fun FinancialAccount.isCreditCardLiability(): Boolean = type == AccountType.CREDIT_CARD
