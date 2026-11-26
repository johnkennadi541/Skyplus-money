package com.example.domain.usecase

import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardNetwork
import com.example.domain.model.CreditCardSummary
import com.example.domain.repository.CreditCardRepository
import com.example.domain.repository.FinancialAccountRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Cards joined with their ledger account, scoped to the signed-in user. */
@Singleton
class ObserveCreditCardsUseCase
@Inject
constructor(
  private val creditCardRepository: CreditCardRepository,
  private val accountRepository: FinancialAccountRepository,
  private val calculationEngine: CreditCardCalculationEngine,
) {
  operator fun invoke(userId: String): Flow<List<CreditCardSummary>> =
    combine(
      creditCardRepository.observeCards(userId),
      accountRepository.getAccounts(userId),
    ) { cards, accounts -> calculationEngine.summariseAll(cards, accounts) }
}

@Singleton
class CreateCreditCardUseCase
@Inject
constructor(private val creditCardRepository: CreditCardRepository) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    accountId: String,
    issuerName: String,
    network: CreditCardNetwork,
    last4: String,
    creditLimitMinor: Long,
    currencyCode: String,
    billingCycleDay: Int = 1,
    paymentDueDayOffset: Int = 20,
    aprPercent: String = "0",
  ): Result<CreditCard> {
    val card =
      CreditCard(
        userId = authenticatedUserId,
        accountId = accountId,
        issuerName = issuerName.trim(),
        network = network,
        last4 = last4.trim(),
        creditLimitMinor = creditLimitMinor,
        currencyCode = currencyCode.uppercase().trim(),
        billingCycleDay = billingCycleDay,
        paymentDueDayOffset = paymentDueDayOffset,
        aprPercent = aprPercent.trim(),
      )
    return creditCardRepository.createCard(authenticatedUserId, card)
  }
}

@Singleton
class UpdateCreditCardUseCase
@Inject
constructor(private val creditCardRepository: CreditCardRepository) {
  suspend operator fun invoke(authenticatedUserId: String, card: CreditCard): Result<Unit> =
    creditCardRepository.updateCard(authenticatedUserId, card)
}

@Singleton
class SetCreditCardArchivedUseCase
@Inject
constructor(private val creditCardRepository: CreditCardRepository) {
  suspend operator fun invoke(userId: String, cardId: String, archived: Boolean): Result<Unit> =
    creditCardRepository.setCardArchived(userId, cardId, archived)
}

