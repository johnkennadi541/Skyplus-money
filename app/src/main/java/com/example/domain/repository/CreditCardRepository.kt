package com.example.domain.repository

import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardStatement
import com.example.domain.model.CreditCardRewardEvent
import kotlinx.coroutines.flow.Flow

interface CreditCardRepository {

  fun observeCards(userId: String): Flow<List<CreditCard>>

  suspend fun getCard(userId: String, cardId: String): CreditCard?

  suspend fun getCardForAccount(userId: String, accountId: String): CreditCard?

  /**
   * Creates the card record for an existing `CREDIT_CARD` account owned by
   * [authenticatedUserId]. Rejects a blank or mismatched UID, a missing account, an account of the
   * wrong type, a second card for the same account, and a currency that does not match the account.
   */
  suspend fun createCard(authenticatedUserId: String, card: CreditCard): Result<CreditCard>

  suspend fun updateCard(authenticatedUserId: String, card: CreditCard): Result<Unit>

  fun observeStatements(userId: String, cardId: String): Flow<List<CreditCardStatement>>

  suspend fun createStatement(authenticatedUserId: String, statement: CreditCardStatement): Result<CreditCardStatement>

  suspend fun updateStatement(authenticatedUserId: String, statement: CreditCardStatement): Result<Unit>

  suspend fun deleteStatement(userId: String, statementId: String): Result<Unit>

  fun observeRewardEvents(userId: String, cardId: String): Flow<List<CreditCardRewardEvent>>

  suspend fun addRewardEvent(authenticatedUserId: String, event: CreditCardRewardEvent): Result<CreditCardRewardEvent>

  suspend fun getRewardPointsBalance(userId: String, cardId: String): Long

  suspend fun setCardFrozen(userId: String, cardId: String, isFrozen: Boolean): Result<Unit>

  /** Removes only the card record. The account and its transactions are never deleted. */
  suspend fun deleteCard(userId: String, cardId: String): Result<Unit>
}
