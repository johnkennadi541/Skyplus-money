package com.example.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.dao.CreditCardDao
import com.example.data.local.db.dao.FinancialAccountDao
import com.example.data.local.db.entity.CreditCardEntity
import com.example.domain.model.AccountType
import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardNetwork
import com.example.domain.model.CreditCardValidation
import com.example.domain.model.DuplicateCreditCardException
import com.example.domain.repository.CreditCardRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class CreditCardRepositoryImpl
@Inject
constructor(
  private val database: AppDatabase,
  private val creditCardDao: CreditCardDao,
  private val accountDao: FinancialAccountDao,
  private val dispatchers: DispatcherProvider,
) : CreditCardRepository {

  override fun observeCards(userId: String): Flow<List<CreditCard>> =
    creditCardDao
      .observeCards(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override suspend fun getCard(userId: String, cardId: String): CreditCard? =
    withContext(dispatchers.io) { creditCardDao.getCardById(userId, cardId)?.toDomain() }

  override suspend fun getCardForAccount(userId: String, accountId: String): CreditCard? =
    withContext(dispatchers.io) {
      creditCardDao.getCardByAccountId(userId, accountId)?.toDomain()
    }

  override suspend fun createCard(
    authenticatedUserId: String,
    card: CreditCard,
  ): Result<CreditCard> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        CreditCardValidation.requireValidCard(
          card = card,
          authenticatedUserId = authenticatedUserId,
          ownershipMessage = "Card ownership cannot be assigned across users",
        )

        database.withTransaction {
          val account =
            accountDao.getAccountById(authenticatedUserId, card.accountId)
              ?: throw SecurityException("Account does not belong to authenticated user")
          require(account.userId == authenticatedUserId) { "Account ownership verification failed" }
          require(account.accountType == AccountType.CREDIT_CARD.name) {
            "A credit card can only be attached to a CREDIT_CARD account"
          }
          require(account.currencyCode.equals(card.currencyCode, ignoreCase = true)) {
            "Card currency must match the account currency"
          }
          if (creditCardDao.getCardByAccountId(authenticatedUserId, card.accountId) != null) {
            throw DuplicateCreditCardException()
          }

          val now = System.currentTimeMillis()
          val scoped =
            card.copy(
              userId = authenticatedUserId,
              currencyCode = account.currencyCode,
              network = card.network.takeIf { it != CreditCardNetwork.OTHER } ?: CreditCardNetwork.OTHER,
              createdAt = now,
              updatedAt = now,
            )
          try {
            creditCardDao.insertCard(CreditCardEntity.fromDomain(scoped))
          } catch (constraint: SQLiteConstraintException) {
            // The pre-check above runs inside the transaction, but a racing insert can still lose
            // here. The UNIQUE index on accountId is the final authority: translate that specific
            // violation into a controlled duplicate result instead of leaking a SQL error.
            val accountDuplicate =
              constraint.message?.contains("credit_cards.accountId", ignoreCase = true) == true
            if (accountDuplicate) throw DuplicateCreditCardException()
            throw constraint
          }
          scoped
        }
      }
    }

  override suspend fun updateCard(
    authenticatedUserId: String,
    card: CreditCard,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        CreditCardValidation.requireValidCard(
          card = card,
          authenticatedUserId = authenticatedUserId,
          ownershipMessage = "Card ownership cannot be modified or transferred across users",
        )

        database.withTransaction {
          val existing =
            creditCardDao.getCardById(authenticatedUserId, card.id)
              ?: throw SecurityException("Card does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) { "Card ownership verification failed" }

          val rows =
            creditCardDao.updateCardScoped(
              id = existing.id,
              userId = authenticatedUserId,
              issuerName = card.issuerName,
              network = card.network.name,
              last4 = card.last4,
              creditLimitMinor = card.creditLimitMinor,
              billingCycleDay = card.billingCycleDay,
              paymentDueDayOffset = card.paymentDueDayOffset,
              aprPercent = card.aprPercent,
              isActive = card.isActive,
              updatedAt = System.currentTimeMillis(),
            )
          check(rows == 1) { "Card update rejected for authenticated user" }
        }
      }
    }

  override suspend fun deleteCard(userId: String, cardId: String): Result<Unit> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        require(userId.isNotBlank()) { "Authenticated user ID is required" }
        require(cardId.isNotBlank()) { "Card ID is required" }
        database.withTransaction {
          val existing =
            creditCardDao.getCardById(userId, cardId)
              ?: throw SecurityException("Card does not belong to authenticated user")
          require(existing.userId == userId) { "Card ownership verification failed" }
          val rows = creditCardDao.deactivateCard(userId, cardId, System.currentTimeMillis())
          check(rows == 1) { "Card deactivation rejected for authenticated user" }
        }
      }
    }
}
