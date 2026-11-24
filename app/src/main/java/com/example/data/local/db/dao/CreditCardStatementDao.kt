package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.CreditCardStatementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditCardStatementDao {
  @Query("SELECT * FROM credit_card_statements WHERE userId = :userId AND cardId = :cardId ORDER BY statementDate DESC")
  fun observeStatements(userId: String, cardId: String): Flow<List<CreditCardStatementEntity>>

  @Query("SELECT * FROM credit_card_statements WHERE userId = :userId AND cardId = :cardId ORDER BY statementDate DESC")
  suspend fun getStatements(userId: String, cardId: String): List<CreditCardStatementEntity>

  @Query("SELECT * FROM credit_card_statements WHERE userId = :userId AND id = :statementId LIMIT 1")
  suspend fun getStatementById(userId: String, statementId: String): CreditCardStatementEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertStatement(statement: CreditCardStatementEntity)

  @Query("""
    UPDATE credit_card_statements
    SET statementDate = :statementDate,
        dueDate = :dueDate,
        statementBalanceMinor = :statementBalanceMinor,
        minimumDueMinor = :minimumDueMinor,
        feesMinor = :feesMinor,
        lateFeeMinor = :lateFeeMinor,
        interestMinor = :interestMinor,
        notes = :notes,
        updatedAt = :updatedAt
    WHERE id = :id AND userId = :userId AND cardId = :cardId
  """)
  suspend fun updateStatementScoped(
    id: String,
    userId: String,
    cardId: String,
    statementDate: Long,
    dueDate: Long,
    statementBalanceMinor: Long,
    minimumDueMinor: Long,
    feesMinor: Long,
    lateFeeMinor: Long,
    interestMinor: Long,
    notes: String?,
    updatedAt: Long,
  ): Int

  @Query("DELETE FROM credit_card_statements WHERE userId = :userId AND id = :statementId")
  suspend fun deleteStatement(userId: String, statementId: String): Int

  @Query("SELECT * FROM credit_card_statements WHERE userId = :userId AND dueDate BETWEEN :startMillis AND :endMillis ORDER BY dueDate ASC")
  fun observeDueStatements(userId: String, startMillis: Long, endMillis: Long): Flow<List<CreditCardStatementEntity>>
}
