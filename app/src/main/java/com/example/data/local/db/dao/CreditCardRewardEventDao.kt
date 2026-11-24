package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.CreditCardRewardEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditCardRewardEventDao {
  @Query("SELECT * FROM credit_card_reward_events WHERE userId = :userId AND cardId = :cardId ORDER BY eventDate DESC, createdAt DESC")
  fun observeEvents(userId: String, cardId: String): Flow<List<CreditCardRewardEventEntity>>

  @Query("SELECT coalesce(sum(pointsDelta), 0) FROM credit_card_reward_events WHERE userId = :userId AND cardId = :cardId")
  suspend fun getPointsBalance(userId: String, cardId: String): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertEvent(event: CreditCardRewardEventEntity)

  @Query("DELETE FROM credit_card_reward_events WHERE userId = :userId AND id = :eventId")
  suspend fun deleteEvent(userId: String, eventId: String): Int
}
