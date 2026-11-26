package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.AccountMappingRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountMappingRuleDao {
  @Query("SELECT * FROM account_mapping_rules WHERE userId = :userId ORDER BY createdAt DESC")
  fun getAllRules(userId: String): Flow<List<AccountMappingRuleEntity>>

  @Query("SELECT * FROM account_mapping_rules WHERE userId = :userId AND matchPattern = :pattern LIMIT 1")
  suspend fun findRuleForPattern(userId: String, pattern: String): AccountMappingRuleEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertRule(rule: AccountMappingRuleEntity)

  @Query("DELETE FROM account_mapping_rules WHERE userId = :userId AND id = :id")
  suspend fun deleteRule(userId: String, id: String)

  @Query("DELETE FROM account_mapping_rules WHERE userId = :userId AND accountId = :accountId")
  suspend fun deleteRulesForAccount(userId: String, accountId: String)
}
