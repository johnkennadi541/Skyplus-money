package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.SavingsContributionEntity
import com.example.data.local.db.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {

  @Query("SELECT * FROM savings_goals WHERE userId = :userId ORDER BY createdAt DESC")
  fun observeGoals(userId: String): Flow<List<SavingsGoalEntity>>

  @Query("SELECT * FROM savings_goals WHERE userId = :userId AND id = :goalId LIMIT 1")
  suspend fun getGoalById(userId: String, goalId: String): SavingsGoalEntity?

  @Query(
    "SELECT * FROM savings_contributions WHERE userId = :userId ORDER BY contributedAt DESC"
  )
  fun observeContributions(userId: String): Flow<List<SavingsContributionEntity>>

  @Query(
    """
    SELECT * FROM savings_contributions
    WHERE userId = :userId AND goalId = :goalId
    ORDER BY contributedAt DESC
    """
  )
  fun observeContributionsForGoal(
    userId: String,
    goalId: String,
  ): Flow<List<SavingsContributionEntity>>

  @Query("SELECT COUNT(*) FROM savings_contributions WHERE userId = :userId AND goalId = :goalId")
  suspend fun countContributions(userId: String, goalId: String): Int

  @Query("SELECT * FROM savings_contributions WHERE userId = :userId AND id = :id LIMIT 1")
  suspend fun getContributionById(userId: String, id: String): SavingsContributionEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertGoal(goal: SavingsGoalEntity)

  @Query(
    """
    UPDATE savings_goals
    SET name = :name,
        currencyCode = :currencyCode,
        targetMinor = :targetMinor,
        deadlineMillis = :deadlineMillis,
        note = :note,
        updatedAt = :updatedAt
    WHERE id = :id AND userId = :userId
    """
  )
  suspend fun updateGoalScoped(
    id: String,
    userId: String,
    name: String,
    currencyCode: String,
    targetMinor: Long,
    deadlineMillis: Long?,
    note: String?,
    updatedAt: Long,
  ): Int


  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertContribution(contribution: SavingsContributionEntity)

  @Query("UPDATE savings_goals SET isArchived = :archived, updatedAt = :updatedAt WHERE userId = :userId AND id = :goalId")
  suspend fun setGoalArchivedScoped(userId: String, goalId: String, archived: Boolean, updatedAt: Long): Int

  @Query("UPDATE savings_contributions SET isVoided = 1, voidedAt = :voidedAt WHERE userId = :userId AND id = :contributionId AND isVoided = 0")
  suspend fun voidContributionScoped(userId: String, contributionId: String, voidedAt: Long): Int
}
