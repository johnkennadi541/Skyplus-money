package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.InvestmentHoldingEntity
import com.example.data.local.db.entity.InvestmentPriceHistoryEntity
import com.example.data.local.db.entity.InvestmentTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestmentDao {

  @Query("SELECT * FROM investment_holdings WHERE userId = :userId AND isArchived = 0 ORDER BY updatedAt DESC, createdAt DESC")
  fun getAllHoldings(userId: String): Flow<List<InvestmentHoldingEntity>>

  @Query("SELECT * FROM investment_holdings WHERE userId = :userId ORDER BY updatedAt DESC, createdAt DESC")
  suspend fun getAllHoldingsSync(userId: String): List<InvestmentHoldingEntity>

  @Query("SELECT * FROM investment_holdings WHERE userId = :userId AND isArchived = 1 ORDER BY updatedAt DESC, createdAt DESC")
  fun getArchivedHoldings(userId: String): Flow<List<InvestmentHoldingEntity>>

  @Query("SELECT * FROM investment_holdings WHERE userId = :userId AND id = :holdingId LIMIT 1")
  fun getHoldingByIdFlow(userId: String, holdingId: String): Flow<InvestmentHoldingEntity?>

  @Query("SELECT * FROM investment_holdings WHERE userId = :userId AND id = :holdingId LIMIT 1")
  suspend fun getHoldingById(userId: String, holdingId: String): InvestmentHoldingEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertHolding(holding: InvestmentHoldingEntity)

  @Query(
    """
    UPDATE investment_holdings
    SET name = :name,
        category = :category,
        symbolOrTicker = :symbolOrTicker,
        isin = :isin,
        brokerOrPlatform = :brokerOrPlatform,
        currencyCode = :currencyCode,
        purchaseDate = :purchaseDate,
        quantityDecimal = :quantityDecimal,
        totalInvestedMinor = :totalInvestedMinor,
        totalBuyCostMinor = :totalBuyCostMinor,
        realizedPnlMinor = :realizedPnlMinor,
        totalChargesMinor = :totalChargesMinor,
        currentMarketPriceDecimal = :currentMarketPriceDecimal,
        currentValuationMinor = :currentValuationMinor,
        lastPriceUpdateDate = :lastPriceUpdateDate,
        lastPriceSource = :lastPriceSource,
        lastPriceProviderLabel = :lastPriceProviderLabel,
        autoPriceUpdateEnabled = :autoPriceUpdateEnabled,
        notes = :notes,
        updatedAt = :updatedAt
    WHERE id = :id AND userId = :userId
    """
  )
  suspend fun updateHoldingScoped(
    id: String,
    userId: String,
    name: String,
    category: String,
    symbolOrTicker: String?,
    isin: String?,
    brokerOrPlatform: String?,
    currencyCode: String,
    purchaseDate: Long,
    quantityDecimal: String,
    totalInvestedMinor: Long,
    totalBuyCostMinor: Long,
    realizedPnlMinor: Long,
    totalChargesMinor: Long,
    currentMarketPriceDecimal: String,
    currentValuationMinor: Long,
    lastPriceUpdateDate: Long,
    lastPriceSource: String,
    lastPriceProviderLabel: String?,
    autoPriceUpdateEnabled: Boolean,
    notes: String?,
    updatedAt: Long,
  ): Int

  suspend fun updateHolding(
    holding: InvestmentHoldingEntity,
    authenticatedUserId: String,
  ): Int {
    if (authenticatedUserId.isBlank() || holding.userId != authenticatedUserId) return 0
    return updateHoldingScoped(
      id = holding.id,
      userId = authenticatedUserId,
      name = holding.name,
      category = holding.category,
      symbolOrTicker = holding.symbolOrTicker,
      isin = holding.isin,
      brokerOrPlatform = holding.brokerOrPlatform,
      currencyCode = holding.currencyCode,
      purchaseDate = holding.purchaseDate,
      quantityDecimal = holding.quantityDecimal,
      totalInvestedMinor = holding.totalInvestedMinor,
      totalBuyCostMinor = holding.totalBuyCostMinor,
      realizedPnlMinor = holding.realizedPnlMinor,
      totalChargesMinor = holding.totalChargesMinor,
      currentMarketPriceDecimal = holding.currentMarketPriceDecimal,
      currentValuationMinor = holding.currentValuationMinor,
      lastPriceUpdateDate = holding.lastPriceUpdateDate,
      lastPriceSource = holding.lastPriceSource,
      lastPriceProviderLabel = holding.lastPriceProviderLabel,
      autoPriceUpdateEnabled = holding.autoPriceUpdateEnabled,
      notes = holding.notes,
      updatedAt = holding.updatedAt,
    )
  }

  @Query("UPDATE investment_holdings SET isArchived = :archived, updatedAt = :updatedAt WHERE userId = :userId AND id = :holdingId")
  suspend fun setHoldingArchived(userId: String, holdingId: String, archived: Boolean, updatedAt: Long): Int

  @Query(
    """
    SELECT * FROM investment_transactions
    WHERE userId = :userId AND holdingId = :holdingId
    ORDER BY transactionDate DESC, createdAt DESC
    """
  )
  fun getTransactionsForHolding(
    userId: String,
    holdingId: String,
  ): Flow<List<InvestmentTransactionEntity>>

  @Query(
    """
    SELECT * FROM investment_transactions
    WHERE userId = :userId
    ORDER BY transactionDate DESC, createdAt DESC
    """
  )
  fun getAllTransactionsForUser(userId: String): Flow<List<InvestmentTransactionEntity>>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertTransaction(transaction: InvestmentTransactionEntity)

  @Query("DELETE FROM investment_transactions WHERE userId = :userId AND holdingId = :holdingId")
  suspend fun deleteTransactionsForHolding(userId: String, holdingId: String): Int

  @Query(
    """
    SELECT * FROM investment_price_history
    WHERE userId = :userId AND holdingId = :holdingId
    ORDER BY priceDate DESC, createdAt DESC
    """
  )
  fun getPriceHistoryForHolding(
    userId: String,
    holdingId: String,
  ): Flow<List<InvestmentPriceHistoryEntity>>

  @Query(
    """
    SELECT * FROM investment_price_history
    WHERE userId = :userId
    ORDER BY priceDate ASC, createdAt ASC
    """
  )
  fun getAllPriceHistory(userId: String): Flow<List<InvestmentPriceHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertPriceHistory(history: InvestmentPriceHistoryEntity)

  @Query("DELETE FROM investment_price_history WHERE userId = :userId AND holdingId = :holdingId")
  suspend fun deletePriceHistoryForHolding(userId: String, holdingId: String): Int
}
