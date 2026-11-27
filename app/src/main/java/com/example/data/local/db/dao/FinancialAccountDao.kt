package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.FinancialAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialAccountDao {
  @Query("SELECT * FROM financial_accounts WHERE userId = :userId ORDER BY isActive DESC, name ASC")
  fun getAllAccounts(userId: String): Flow<List<FinancialAccountEntity>>

  @Query("SELECT * FROM financial_accounts WHERE userId = :userId AND isActive = 1 ORDER BY name ASC")
  suspend fun getActiveAccountsSync(userId: String): List<FinancialAccountEntity>

  @Query("SELECT * FROM financial_accounts WHERE userId = :userId AND id = :accountId LIMIT 1")
  suspend fun getAccountById(userId: String, accountId: String): FinancialAccountEntity?

  @Query("SELECT * FROM financial_accounts WHERE userId = :userId AND id = :accountId LIMIT 1")
  fun getAccountByIdFlow(userId: String, accountId: String): Flow<FinancialAccountEntity?>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertAccount(account: FinancialAccountEntity)

  @Query(
    """
    UPDATE financial_accounts
    SET name = :name,
        accountType = :accountType,
        currencyCode = :currencyCode,
        initialBalanceMinor = :initialBalanceMinor,
        currentBalanceMinor = :currentBalanceMinor,
        institutionName = :institutionName,
        accountNumberLast4 = :accountNumberLast4,
        colorHex = :colorHex,
        isActive = :isActive,
        updatedAt = :updatedAt
    WHERE id = :id AND userId = :userId
    """
  )
  suspend fun updateAccountScoped(
    id: String,
    userId: String,
    name: String,
    accountType: String,
    currencyCode: String,
    initialBalanceMinor: Long,
    currentBalanceMinor: Long,
    institutionName: String?,
    accountNumberLast4: String?,
    colorHex: String,
    isActive: Boolean,
    updatedAt: Long,
  ): Int

  suspend fun updateAccount(account: FinancialAccountEntity, authenticatedUserId: String): Int {
    if (authenticatedUserId.isBlank() || account.userId != authenticatedUserId) return 0
    return updateAccountScoped(
      id = account.id,
      userId = authenticatedUserId,
      name = account.name,
      accountType = account.accountType,
      currencyCode = account.currencyCode,
      initialBalanceMinor = account.initialBalanceMinor,
      currentBalanceMinor = account.currentBalanceMinor,
      institutionName = account.institutionName,
      accountNumberLast4 = account.accountNumberLast4,
      colorHex = account.colorHex,
      isActive = account.isActive,
      updatedAt = account.updatedAt,
    )
  }

  @Query(
    "UPDATE financial_accounts SET currentBalanceMinor = currentBalanceMinor + :deltaMinor, updatedAt = :updatedAt WHERE userId = :userId AND id = :accountId"
  )
  suspend fun adjustBalance(
    userId: String,
    accountId: String,
    deltaMinor: Long,
    updatedAt: Long = System.currentTimeMillis(),
  )

  @Query(
    "UPDATE financial_accounts SET currentBalanceMinor = :newBalanceMinor, updatedAt = :updatedAt WHERE userId = :userId AND id = :accountId"
  )
  suspend fun setBalance(
    userId: String,
    accountId: String,
    newBalanceMinor: Long,
    updatedAt: Long = System.currentTimeMillis(),
  )

  @Query(
    "UPDATE financial_accounts SET isActive = :isActive, updatedAt = :updatedAt WHERE userId = :userId AND id = :accountId"
  )
  suspend fun setActive(
    userId: String,
    accountId: String,
    isActive: Boolean,
    updatedAt: Long = System.currentTimeMillis(),
  ): Int
}
