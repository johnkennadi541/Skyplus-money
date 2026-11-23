package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.InvestmentTransaction
import com.example.domain.model.InvestmentTransactionType

@Entity(
  tableName = "investment_transactions",
  foreignKeys = [
    ForeignKey(
      entity = InvestmentHoldingEntity::class,
      parentColumns = ["id"],
      childColumns = ["holdingId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["holdingId"]),
    Index(value = ["userId", "holdingId", "transactionDate"]),
  ],
)
data class InvestmentTransactionEntity(
  @PrimaryKey val id: String,
  val holdingId: String,
  val userId: String,
  val type: String,
  val transactionDate: Long,
  val quantityDecimal: String,
  val unitPriceDecimal: String,
  val grossAmountMinor: Long,
  val chargesMinor: Long,
  val netAmountMinor: Long,
  val realizedPnlMinor: Long,
  val costBasisAllocatedMinor: Long,
  val notes: String? = null,
  val createdAt: Long,
) {
  fun toDomain(): InvestmentTransaction =
    InvestmentTransaction(
      id = id,
      holdingId = holdingId,
      userId = userId,
      type =
        try {
          InvestmentTransactionType.valueOf(type)
        } catch (_: IllegalArgumentException) {
          InvestmentTransactionType.BUY
        },
      transactionDate = transactionDate,
      quantityDecimal = quantityDecimal,
      unitPriceDecimal = unitPriceDecimal,
      grossAmountMinor = grossAmountMinor,
      chargesMinor = chargesMinor,
      netAmountMinor = netAmountMinor,
      realizedPnlMinor = realizedPnlMinor,
      costBasisAllocatedMinor = costBasisAllocatedMinor,
      notes = notes,
      createdAt = createdAt,
    )

  companion object {
    fun fromDomain(transaction: InvestmentTransaction): InvestmentTransactionEntity =
      InvestmentTransactionEntity(
        id = transaction.id,
        holdingId = transaction.holdingId,
        userId = transaction.userId,
        type = transaction.type.name,
        transactionDate = transaction.transactionDate,
        quantityDecimal = transaction.quantityDecimal,
        unitPriceDecimal = transaction.unitPriceDecimal,
        grossAmountMinor = transaction.grossAmountMinor,
        chargesMinor = transaction.chargesMinor,
        netAmountMinor = transaction.netAmountMinor,
        realizedPnlMinor = transaction.realizedPnlMinor,
        costBasisAllocatedMinor = transaction.costBasisAllocatedMinor,
        notes = transaction.notes,
        createdAt = transaction.createdAt,
      )
  }
}
