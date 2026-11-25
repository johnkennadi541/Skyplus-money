package com.example.domain.repository

import com.example.domain.model.CreditCard
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

  /** Archives or restores card metadata without touching its ledger account or transactions. */
  suspend fun setCardArchived(userId: String, cardId: String, archived: Boolean): Result<Unit>

  /** Removes only the card record. The account and its transactions are never deleted. */
  suspend fun deleteCard(userId: String, cardId: String): Result<Unit>
}
