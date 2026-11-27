package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.CreditCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditCardDao {

  @Query("SELECT * FROM credit_cards WHERE userId = :userId ORDER BY createdAt DESC")
  fun observeCards(userId: String): Flow<List<CreditCardEntity>>

  @Query("SELECT * FROM credit_cards WHERE userId = :userId AND id = :cardId LIMIT 1")
  suspend fun getCardById(userId: String, cardId: String): CreditCardEntity?

  @Query("SELECT * FROM credit_cards WHERE userId = :userId AND accountId = :accountId LIMIT 1")
  suspend fun getCardByAccountId(userId: String, accountId: String): CreditCardEntity?

  @Query("SELECT COUNT(*) FROM credit_cards WHERE userId = :userId")
  suspend fun countCards(userId: String): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertCard(card: CreditCardEntity)

  @Query(
    """
    UPDATE credit_cards
    SET issuerName = :issuerName,
        network = :network,
        last4 = :last4,
        creditLimitMinor = :creditLimitMinor,
        billingCycleDay = :billingCycleDay,
        paymentDueDayOffset = :paymentDueDayOffset,
        aprPercent = :aprPercent,
        isActive = :isActive,
        updatedAt = :updatedAt
    WHERE id = :id AND userId = :userId
    """
  )
  suspend fun updateCardScoped(
    id: String,
    userId: String,
    issuerName: String,
    network: String,
    last4: String,
    creditLimitMinor: Long,
    billingCycleDay: Int,
    paymentDueDayOffset: Int,
    aprPercent: String,
    isActive: Boolean,
    updatedAt: Long,
  ): Int

  /** Deactivates the card record while retaining its metadata and payment history. */
  @Query("UPDATE credit_cards SET isActive = 0, updatedAt = :updatedAt WHERE userId = :userId AND id = :id")
  suspend fun deactivateCard(userId: String, id: String, updatedAt: Long): Int
}
